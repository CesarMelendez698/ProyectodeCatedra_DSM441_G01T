package sv.edu.udb.finanzasudb.utils

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import sv.edu.udb.finanzasudb.model.Budget
import sv.edu.udb.finanzasudb.model.ScheduleItem
import sv.edu.udb.finanzasudb.model.Transaction
import sv.edu.udb.finanzasudb.ui.BudgetsActivity
import sv.edu.udb.finanzasudb.ui.ScheduleActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AppNotificationHelper {

    private const val CHANNEL_BUDGETS_ID = "channel_budgets_alerts"
    private const val CHANNEL_BUDGETS_NAME = "Alertas de Presupuestos UDB"
    private const val CHANNEL_CLASSES_ID = "channel_classes_reminders"
    private const val CHANNEL_CLASSES_NAME = "Recordatorio de Clases UDB"

    private const val PREFS_NOTIF = "finanzas_udb_notifications"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Canal 1: Presupuestos
            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGETS_ID,
                CHANNEL_BUDGETS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando tus gastos superan el 80% o 100% de tu límite presupuestario."
                enableVibration(true)
            }
            manager.createNotificationChannel(budgetChannel)

            // Canal 2: Clases
            val classChannel = NotificationChannel(
                CHANNEL_CLASSES_ID,
                CHANNEL_CLASSES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios de inicio de clases según tu horario institucional con conteo regresivo."
                enableVibration(true)
            }
            manager.createNotificationChannel(classChannel)
        }
    }

    fun requestPermissionIfNecessary(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    // ==========================================
    // 1. MONITOR DE ALERTAS DE PRESUPUESTOS (80% y 100%)
    // ==========================================
    fun checkBudgetAlerts(context: Context, budgets: List<Budget>, transactions: List<Transaction>) {
        if (budgets.isEmpty()) return

        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val spentMap = mutableMapOf<String, Double>()

        for (t in transactions) {
            if (t.type.equals("gasto", ignoreCase = true) && t.date.isNotEmpty()) {
                val catKey = t.category.trim().lowercase()
                spentMap[catKey] = (spentMap[catKey] ?: 0.0) + t.amount
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)

        for (b in budgets) {
            val limit = b.limitAmount
            if (limit <= 0) continue

            val catKey = b.category.trim().lowercase()
            val spent = spentMap[catKey] ?: 0.0
            val percentage = (spent / limit) * 100

            val key80 = "${currentMonthKey}_${catKey}_warned80"
            val key100 = "${currentMonthKey}_${catKey}_warned100"

            val warned80 = prefs.getBoolean(key80, false)
            val warned100 = prefs.getBoolean(key100, false)

            if (percentage >= 100 && !warned100) {
                sendBudgetNotification(
                    context,
                    notificationId = 1000 + catKey.hashCode() % 1000,
                    title = "🚨 LÍMITE EXCEDIDO (100%)",
                    message = "Gastos en \"${b.category}\" ($${String.format(Locale.US, "%.2f", spent)}) superaron tu límite de $${String.format(Locale.US, "%.2f", limit)}. ¡Toca para revisar!"
                )
                prefs.edit().putBoolean(key100, true).putBoolean(key80, true).apply()
            } else if (percentage >= 80 && percentage < 100 && !warned80) {
                sendBudgetNotification(
                    context,
                    notificationId = 2000 + catKey.hashCode() % 1000,
                    title = "⚠️ ALERTA DE PRESUPUESTO (80%)",
                    message = "Has consumido más del 80% en \"${b.category}\". Llevas: $${String.format(Locale.US, "%.2f", spent)} de $${String.format(Locale.US, "%.2f", limit)}. ¡Toca para revisar!"
                )
                prefs.edit().putBoolean(key80, true).apply()
            }

            // Si el usuario redujo o borró gastos por debajo del 80%, restablecer alertas
            if (percentage < 80 && (warned80 || warned100)) {
                prefs.edit().putBoolean(key80, false).putBoolean(key100, false).apply()
            } else if (percentage >= 80 && percentage < 100 && warned100) {
                prefs.edit().putBoolean(key100, false).apply()
            }
        }
    }

    private fun sendBudgetNotification(context: Context, notificationId: Int, title: String, message: String) {
        initChannels(context)

        val intent = Intent(context, BudgetsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_BUDGETS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    // ==========================================
    // 2. RECORDATORIO DE CLASES INSTITUCIONALES (Conteo de horas y minutos)
    // ==========================================

    /**
     * Devuelve el texto del conteo regresivo según las horas y minutos restantes:
     * - Si falta más de 1 hora: ej. "Faltan 2 horas" o "Falta 1 hora y 30 min"
     * - Si falta menos de 1 hora: ej. "Faltan 45 minutos" o "¡Comienza en 5 min!"
     */
    fun formatClassCountdown(timeDiffMinutes: Int): String {
        return when {
            timeDiffMinutes <= 0 -> "¡Tu clase está por comenzar ahora!"
            timeDiffMinutes <= 5 -> "¡Comienza en breve ($timeDiffMinutes min)!"
            timeDiffMinutes < 60 -> "Faltan $timeDiffMinutes minutos para tu clase"
            else -> {
                val hours = timeDiffMinutes / 60
                val mins = timeDiffMinutes % 60
                if (mins == 0) {
                    if (hours == 1) "Falta 1 hora para tu clase" else "Faltan $hours horas para tu clase"
                } else {
                    if (hours == 1) "Falta 1 hora y $mins min para tu clase" else "Faltan $hours horas y $mins min para tu clase"
                }
            }
        }
    }

    fun checkClassAlerts(context: Context, scheduleList: List<ScheduleItem>) {
        if (scheduleList.isEmpty()) return

        val cal = Calendar.getInstance()
        val dayOfWeekMap = mapOf(
            Calendar.MONDAY to "Lunes",
            Calendar.TUESDAY to "Martes",
            Calendar.WEDNESDAY to "Miércoles",
            Calendar.THURSDAY to "Jueves",
            Calendar.FRIDAY to "Viernes",
            Calendar.SATURDAY to "Sábado",
            Calendar.SUNDAY to "Domingo"
        )

        val currentDayName = dayOfWeekMap[cal.get(Calendar.DAY_OF_WEEK)] ?: return
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)

        // Filtrar y ordenar cronológicamente por hora de inicio
        val todayClasses = scheduleList
            .filter {
                it.dayOfWeek.equals(currentDayName, ignoreCase = true) ||
                    it.dayOfWeek.contains(currentDayName.take(3), ignoreCase = true)
            }
            .sortedBy { parseTimeToMinutes(it.startTime) }

        for (item in todayClasses) {
            val startMin = parseTimeToMinutes(item.startTime)
            if (startMin <= 0) continue

            val timeDiff = startMin - currentMinutes
            if (timeDiff < 0) continue // Clase ya inició o pasó

            val typeTag = when {
                item.activityType.equals("Laboratorio", true) -> "[Laboratorio]"
                item.activityType.equals("Teoría", true) || item.activityType.equals("Teoria", true) -> "[Teoría]"
                else -> ""
            }

            // Tiers de notificación para no duplicar spam:
            // - tier_2h: entre 90 y 150 minutos (alrededor de 2 horas antes)
            // - tier_1h: entre 45 y 89 minutos (alrededor de 1 hora antes)
            // - tier_30m: entre 20 y 44 minutos (alrededor de 30 minutos antes)
            // - tier_15m: entre 6 y 19 minutos (alrededor de 15 minutos antes)
            // - tier_5m: entre 0 y 5 minutos (inicio inminente)
            val tierKey = when {
                timeDiff in 0..5 -> "tier_5m"
                timeDiff in 6..19 -> "tier_15m"
                timeDiff in 20..44 -> "tier_30m"
                timeDiff in 45..89 -> "tier_1h"
                timeDiff in 90..150 -> "tier_2h"
                else -> null
            }

            if (tierKey != null) {
                val notifKey = "class_alert_${item.id.ifEmpty { item.subjectName }}_${todayDateStr}_$tierKey"
                val alreadyNotified = prefs.getBoolean(notifKey, false)

                if (!alreadyNotified) {
                    val notifId = (3000 + (item.subjectName.hashCode() + tierKey.hashCode()).let { if (it < 0) -it else it } % 1000)
                    val countdownStr = formatClassCountdown(timeDiff)
                    val title = "🎓 Próxima Clase UDB: ${item.subjectName} $typeTag".trim()
                    val msg = "$countdownStr (${item.startTime}) en ${item.classroom}. ¡Prepárate!"

                    sendClassNotification(context, notifId, title, msg)
                    prefs.edit().putBoolean(notifKey, true).apply()
                }
            }
        }

        // Programar alarmas en segundo plano para el día de hoy
        scheduleClassAlarms(context, todayClasses)
    }

    private fun scheduleClassAlarms(context: Context, todayClasses: List<ScheduleItem>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val calNow = Calendar.getInstance()
        val currentMinutes = calNow.get(Calendar.HOUR_OF_DAY) * 60 + calNow.get(Calendar.MINUTE)

        for (item in todayClasses) {
            val startMin = parseTimeToMinutes(item.startTime)
            if (startMin <= 0) continue

            val typeTag = when {
                item.activityType.equals("Laboratorio", true) -> "[Laboratorio]"
                item.activityType.equals("Teoría", true) || item.activityType.equals("Teoria", true) -> "[Teoría]"
                else -> ""
            }

            // Alarma 1: 60 minutos antes (1 hora)
            if (startMin - 60 > currentMinutes) {
                scheduleSingleAlarm(
                    context,
                    alarmManager,
                    item,
                    minutesBefore = 60,
                    startMin = startMin,
                    typeTag = typeTag
                )
            }

            // Alarma 2: 15 minutos antes (menos de 1 hora)
            if (startMin - 15 > currentMinutes) {
                scheduleSingleAlarm(
                    context,
                    alarmManager,
                    item,
                    minutesBefore = 15,
                    startMin = startMin,
                    typeTag = typeTag
                )
            }
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        item: ScheduleItem,
        minutesBefore: Int,
        startMin: Int,
        typeTag: String
    ) {
        val targetMin = startMin - minutesBefore
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetMin / 60)
            set(Calendar.MINUTE, targetMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (targetCal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, ClassNotificationReceiver::class.java).apply {
            putExtra("subjectName", item.subjectName)
            putExtra("classroom", item.classroom)
            putExtra("startTime", item.startTime)
            putExtra("minutesLeft", minutesBefore)
            putExtra("typeTag", typeTag)
        }

        val reqCode = (item.subjectName.hashCode() + minutesBefore).let { if (it < 0) -it else it } % 100000
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
            }
        } catch (_: SecurityException) {}
    }

    fun sendClassNotification(context: Context, notificationId: Int, title: String, message: String) {
        initChannels(context)

        val intent = Intent(context, ScheduleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CLASSES_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    fun parseTimeToMinutes(timeStr: String): Int {
        if (timeStr.isEmpty()) return -1
        return try {
            val clean = timeStr.trim().uppercase()
            val match = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""").find(clean) ?: return -1
            var hours = match.groupValues[1].toInt()
            val minutes = match.groupValues[2].toInt()
            val ampm = match.groupValues[3]

            if (ampm == "PM" && hours < 12) hours += 12
            if (ampm == "AM" && hours == 12) hours = 0

            hours * 60 + minutes
        } catch (_: Exception) {
            -1
        }
    }
}
