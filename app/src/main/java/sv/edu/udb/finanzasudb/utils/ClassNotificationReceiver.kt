package sv.edu.udb.finanzasudb.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subjectName = intent.getStringExtra("subjectName") ?: "Clase UDB"
        val classroom = intent.getStringExtra("classroom") ?: "Campus"
        val startTime = intent.getStringExtra("startTime") ?: ""
        val minutesLeft = intent.getIntExtra("minutesLeft", 15)
        val typeTag = intent.getStringExtra("typeTag") ?: ""

        val countdownStr = AppNotificationHelper.formatClassCountdown(minutesLeft)
        val notifId = (3000 + (subjectName.hashCode() + minutesLeft).let { if (it < 0) -it else it } % 1000)

        AppNotificationHelper.sendClassNotification(
            context,
            notifId,
            "🎓 Clase UDB: $subjectName $typeTag".trim(),
            "$countdownStr ($startTime) en $classroom. ¡Prepárate!"
        )
    }
}
