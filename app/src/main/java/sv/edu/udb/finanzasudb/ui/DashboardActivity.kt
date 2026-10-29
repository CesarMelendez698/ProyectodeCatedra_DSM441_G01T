package sv.edu.udb.finanzasudb.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository
import sv.edu.udb.finanzasudb.model.ScheduleItem
import sv.edu.udb.finanzasudb.model.Transaction
import sv.edu.udb.finanzasudb.utils.AppNotificationHelper
import sv.edu.udb.finanzasudb.utils.NavTab
import sv.edu.udb.finanzasudb.utils.NavigationHelper
import sv.edu.udb.finanzasudb.utils.ThemeHelper
import java.util.Calendar
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvNetBalance: TextView
    private lateinit var tvMonthIncome: TextView
    private lateinit var tvMonthExpense: TextView
    private lateinit var tvBalanceEfectivo: TextView
    private lateinit var tvBalanceTarjeta: TextView
    private lateinit var tvBalanceBanco: TextView
    private lateinit var btnTogglePrivacy: ImageView
    private lateinit var cardNextClass: MaterialCardView

    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentCareer: TextView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var tvUpcomingSubject: TextView
    private lateinit var tvUpcomingLocation: TextView
    private lateinit var tvClassCountdown: TextView

    private var isBalanceVisible = true
    private var rawBalance = 0.0
    private var rawIncome = 0.0
    private var rawExpense = 0.0
    private var rawEfectivo = 0.0
    private var rawTarjeta = 0.0
    private var rawBanco = 0.0

    private var currentTransactions = listOf<Transaction>()
    private var currentBudgets = listOf<sv.edu.udb.finanzasudb.model.Budget>()

    private var txListener: ListenerRegistration? = null
    private var scheduleListener: ListenerRegistration? = null
    private var budgetsListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        AppNotificationHelper.initChannels(this)
        AppNotificationHelper.requestPermissionIfNecessary(this)

        initViews()
        setupListeners()
        loadUserData()
        listenToFirestore()
    }

    override fun onDestroy() {
        super.onDestroy()
        txListener?.remove()
        scheduleListener?.remove()
        budgetsListener?.remove()
        profileListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.ensureTheme(this)) return
        NavigationHelper.setupBottomNav(this, NavTab.DASHBOARD)
        checkUserStatus()
        loadUserData()
    }

    private fun checkUserStatus() {
        val user = FirebaseRepository.currentUser
        if (user == null) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        user.reload().addOnFailureListener { error ->
            if (error is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                // El usuario fue borrado de Firebase Console o Auth
                val email = user.email ?: ""
                FirebaseRepository.purgeUserData(email) {
                    FirebaseRepository.clearLocalUserData(this, email)
                    FirebaseRepository.signOut()
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finishAffinity()
                }
            }
        }
    }

    private fun initViews() {
        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvMonthIncome = findViewById(R.id.tvMonthIncome)
        tvMonthExpense = findViewById(R.id.tvMonthExpense)
        tvBalanceEfectivo = findViewById(R.id.tvBalanceEfectivo)
        tvBalanceTarjeta = findViewById(R.id.tvBalanceTarjeta)
        tvBalanceBanco = findViewById(R.id.tvBalanceBanco)
        btnTogglePrivacy = findViewById(R.id.btnToggleBalancePrivacy)
        cardNextClass = findViewById(R.id.cardNextClass)

        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentCareer = findViewById(R.id.tvStudentCareer)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        tvUpcomingSubject = findViewById(R.id.tvUpcomingSubject)
        tvUpcomingLocation = findViewById(R.id.tvUpcomingLocation)
        tvClassCountdown = findViewById(R.id.tvClassCountdown)

        updateBalanceDisplay()
    }

    private fun loadUserData() {
        val profile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
        applyUserProfile(profile)
    }

    private fun applyUserProfile(profile: sv.edu.udb.finanzasudb.model.UserProfile) {
        tvStudentName.text = profile.name
        tvAvatarInitials.text = sv.edu.udb.finanzasudb.utils.UserProfileHelper.computeInitials(profile.name)
        tvStudentCareer.text = "${profile.career} • ${profile.cycle}"
    }

    private fun listenToFirestore() {
        // Escucha en tiempo real de transacciones para calcular saldos reales
        txListener = FirebaseRepository.listenTransactions { transactions ->
            currentTransactions = transactions
            computeBalances(transactions)
            updateBalanceDisplay()
            AppNotificationHelper.checkBudgetAlerts(this, currentBudgets, currentTransactions)
        }

        // Escucha en tiempo real de presupuestos para alertas
        budgetsListener = FirebaseRepository.listenBudgets { budgets ->
            currentBudgets = budgets
            AppNotificationHelper.checkBudgetAlerts(this, currentBudgets, currentTransactions)
        }

        // Escucha en tiempo real de horarios para mostrar la próxima clase y alertar
        scheduleListener = FirebaseRepository.listenSchedule { scheduleList ->
            updateUpcomingClass(scheduleList)
            AppNotificationHelper.checkClassAlerts(this, scheduleList)
        }

        // Escucha en tiempo real del perfil del usuario (carrera, ciclo, campus)
        profileListener = sv.edu.udb.finanzasudb.utils.UserProfileHelper.listenProfile(this) { profile ->
            applyUserProfile(profile)
        }
    }

    private fun computeBalances(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            rawBalance = 0.0
            rawIncome = 0.0
            rawExpense = 0.0
            rawEfectivo = 0.0
            rawTarjeta = 0.0
            rawBanco = 0.0
            return
        }

        var inc = 0.0
        var exp = 0.0
        var ef = 0.0
        var tj = 0.0
        var bc = 0.0

        for (t in transactions) {
            val amount = t.amount
            val isIncome = t.type.equals("ingreso", ignoreCase = true)
            if (isIncome) {
                inc += amount
            } else {
                exp += amount
            }

            val delta = if (isIncome) amount else -amount
            val acc = t.accountName.lowercase().trim()

            when {
                // Cuenta de Banco, Bancaria, Ahorros, Bank
                acc.contains("banco") || acc.contains("bank") || acc.contains("cuenta") || acc.contains("ahorro") || acc.contains("bancari") -> {
                    bc += delta
                }
                // Tarjetas de Débito, Crédito
                acc.contains("tarjeta") || acc.contains("card") || acc.contains("crédito") || acc.contains("credito") || acc.contains("débito") || acc.contains("debito") -> {
                    tj += delta
                }
                // Efectivo / Cash
                acc.contains("efectivo") || acc.contains("cash") -> {
                    ef += delta
                }
                // Por defecto a Banco si es otra cuenta añadida
                else -> {
                    bc += delta
                }
            }
        }

        rawIncome = inc
        rawExpense = exp
        rawBalance = inc - exp
        rawEfectivo = ef
        rawTarjeta = tj
        rawBanco = bc
    }

    private fun updateUpcomingClass(classes: List<ScheduleItem>) {
        if (classes.isEmpty()) {
            tvUpcomingSubject.text = "Sin clases programadas"
            tvUpcomingLocation.text = "Agrega tus asignaturas en la pestaña Horario"
            tvClassCountdown.text = "DÍA LIBRE"
            return
        }

        val daysMap = mapOf(
            Calendar.MONDAY to "Lunes",
            Calendar.TUESDAY to "Martes",
            Calendar.WEDNESDAY to "Miércoles",
            Calendar.THURSDAY to "Jueves",
            Calendar.FRIDAY to "Viernes",
            Calendar.SATURDAY to "Sábado",
            Calendar.SUNDAY to "Domingo"
        )
        val cal = Calendar.getInstance()
        val currentDay = daysMap[cal.get(Calendar.DAY_OF_WEEK)] ?: "Lunes"
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val sortedClasses = classes.sortedBy { AppNotificationHelper.parseTimeToMinutes(it.startTime) }
        val todayClasses = sortedClasses.filter {
            it.dayOfWeek.equals(currentDay, ignoreCase = true) ||
                it.dayOfWeek.contains(currentDay.take(3), ignoreCase = true)
        }

        if (todayClasses.isEmpty()) {
            val nextUpcoming = sortedClasses.firstOrNull()
            if (nextUpcoming != null) {
                tvUpcomingSubject.text = nextUpcoming.subjectName
                tvUpcomingLocation.text = "${nextUpcoming.classroom} • ${nextUpcoming.startTime} - ${nextUpcoming.endTime}"
                tvClassCountdown.text = "PRÓXIMO: ${nextUpcoming.dayOfWeek.uppercase()}"
            } else {
                tvUpcomingSubject.text = "Sin clases programadas"
                tvUpcomingLocation.text = "Agrega tus asignaturas en la pestaña Horario"
                tvClassCountdown.text = "DÍA LIBRE"
            }
            return
        }

        // Buscar si hay clase en curso o próxima hoy
        var ongoingClass: ScheduleItem? = null
        var nextClass: ScheduleItem? = null
        var minUpcomingDiff = Int.MAX_VALUE

        for (item in todayClasses) {
            val startMin = AppNotificationHelper.parseTimeToMinutes(item.startTime)
            val endMin = AppNotificationHelper.parseTimeToMinutes(item.endTime)

            if (startMin > 0 && endMin > 0) {
                if (currentMinutes in startMin until endMin) {
                    ongoingClass = item
                    break
                } else if (currentMinutes < startMin) {
                    val diff = startMin - currentMinutes
                    if (diff < minUpcomingDiff) {
                        minUpcomingDiff = diff
                        nextClass = item
                    }
                }
            }
        }

        val chosen = ongoingClass ?: nextClass ?: todayClasses.first()
        tvUpcomingSubject.text = chosen.subjectName
        tvUpcomingLocation.text = "${chosen.classroom} • ${chosen.startTime} - ${chosen.endTime}"

        tvClassCountdown.text = when {
            ongoingClass != null -> "EN CURSO AHORA"
            nextClass != null -> {
                val hours = minUpcomingDiff / 60
                val mins = minUpcomingDiff % 60
                if (hours >= 1) {
                    if (mins == 0) "EN $hours ${if (hours == 1) "HORA" else "HORAS"}"
                    else "EN $hours H Y $mins MIN"
                } else {
                    if (mins <= 5) "EN BREVE" else "EN $mins MINUTOS"
                }
            }
            else -> "CLASES FINALIZADAS"
        }
    }

    private fun setupListeners() {
        btnTogglePrivacy.setOnClickListener {
            isBalanceVisible = !isBalanceVisible
            updateBalanceDisplay()
        }

        cardNextClass.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        findViewById<View>(R.id.cardServiceSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        findViewById<View>(R.id.cardServiceNews).setOnClickListener {
            startActivity(Intent(this, NewsActivity::class.java))
        }

        findViewById<View>(R.id.cardServiceProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.btnAccountProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // 🌓 Signo de interrogación en Resumen (?): Control maestro de cambio de tema en toda la app
        val btnTheme = findViewById<ImageView>(R.id.btnHeaderHelp)
        btnTheme.setOnClickListener {
            val isDark = ThemeHelper.isDarkMode(this)
            val newMode = if (isDark) ThemeHelper.MODE_LIGHT else ThemeHelper.MODE_DARK
            val msg = if (newMode == ThemeHelper.MODE_DARK) "🌙 Modo Oscuro activado en toda la aplicación" else "☀️ Modo Claro activado en toda la aplicación"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            ThemeHelper.setThemeMode(this, newMode)
        }
        btnTheme.setOnLongClickListener {
            ThemeHelper.showThemeSelectionDialog(this)
            true
        }

        // 🚪 Botón de salir idéntico a Expo
        findViewById<ImageView>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que deseas salir de tu cuenta?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar Sesión") { _, _ ->
                    FirebaseRepository.signOut()
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finishAffinity()
                }
                .show()
        }
    }

    private fun updateBalanceDisplay() {
        if (isBalanceVisible) {
            tvNetBalance.text = String.format(Locale.US, "$%.2f", rawBalance)
            tvMonthIncome.text = String.format(Locale.US, "+$%.2f", rawIncome)
            tvMonthExpense.text = String.format(Locale.US, "-$%.2f", rawExpense)
            tvBalanceEfectivo.text = String.format(Locale.US, "$%.2f", rawEfectivo)
            tvBalanceTarjeta.text = String.format(Locale.US, "$%.2f", rawTarjeta)
            tvBalanceBanco.text = String.format(Locale.US, "$%.2f", rawBanco)
        } else {
            tvNetBalance.text = "$ • • • • • •"
            tvMonthIncome.text = "$ •••"
            tvMonthExpense.text = "$ •••"
            tvBalanceEfectivo.text = "$ •••"
            tvBalanceTarjeta.text = "$ •••"
            tvBalanceBanco.text = "$ •••"
        }
    }
}
