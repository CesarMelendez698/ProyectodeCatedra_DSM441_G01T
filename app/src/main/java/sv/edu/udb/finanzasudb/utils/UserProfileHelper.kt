package sv.edu.udb.finanzasudb.utils

import android.content.Context
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ListenerRegistration
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository
import sv.edu.udb.finanzasudb.model.UserProfile

object UserProfileHelper {
    private const val PREFS_NAME = "user_academic_prefs"

    fun getProfile(context: Context): UserProfile {
        val user = FirebaseRepository.currentUser
        val email = user?.email ?: FirebaseRepository.userEmail
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedName = prefs.getString("${email}_name", null)
        val defaultName = user?.displayName?.ifBlank { null }
            ?: if (email.isNotEmpty()) {
                email.substringBefore("@").replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            } else {
                "Estudiante UDB"
            }
        val finalName = if (!savedName.isNullOrBlank()) savedName else defaultName

        val savedCareer = prefs.getString("${email}_career", "Ingeniería en Ciencias de la Computación")
            ?: "Ingeniería en Ciencias de la Computación"
        val savedCycle = prefs.getString("${email}_cycle", "Ciclo 01") ?: "Ciclo 01"
        val savedCampus = prefs.getString("${email}_campus", "Campus Soyapango") ?: "Campus Soyapango"

        return UserProfile(
            email = email,
            name = finalName,
            career = savedCareer,
            cycle = savedCycle,
            campus = savedCampus
        )
    }

    fun saveProfile(
        context: Context,
        profile: UserProfile,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val email = profile.email.ifEmpty { FirebaseRepository.userEmail }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${email}_name", profile.name)
            .putString("${email}_career", profile.career)
            .putString("${email}_cycle", profile.cycle)
            .putString("${email}_campus", profile.campus)
            .apply()

        // Actualizar nombre en Firebase Auth si está disponible
        val user = FirebaseRepository.currentUser
        if (user != null && profile.name.isNotBlank() && user.displayName != profile.name) {
            val req = UserProfileChangeRequest.Builder()
                .setDisplayName(profile.name)
                .build()
            user.updateProfile(req)
        }

        FirebaseRepository.saveUserProfile(profile, onComplete)
    }

    fun listenProfile(context: Context, onUpdate: (UserProfile) -> Unit): ListenerRegistration {
        return FirebaseRepository.listenUserProfile { profile ->
            val email = profile.email.ifEmpty { FirebaseRepository.userEmail }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (profile.name.isNotBlank()) {
                editor.putString("${email}_name", profile.name)
            }
            if (profile.career.isNotBlank()) {
                editor.putString("${email}_career", profile.career)
            }
            if (profile.cycle.isNotBlank()) {
                editor.putString("${email}_cycle", profile.cycle)
            }
            if (profile.campus.isNotBlank()) {
                editor.putString("${email}_campus", profile.campus)
            }
            editor.apply()

            val consolidated = getProfile(context)
            onUpdate(consolidated)
        }
    }

    fun computeInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "UDB"
        }
    }
}
