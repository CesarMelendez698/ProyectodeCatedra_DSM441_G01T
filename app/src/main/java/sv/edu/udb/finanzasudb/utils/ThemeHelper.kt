package sv.edu.udb.finanzasudb.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    private const val PREFS_NAME = "finanzas_udb_prefs"
    private const val KEY_THEME_MODE = "@user_theme_mode" // "light", "dark"
    private const val KEY_DARK_MODE_LEGACY = "@user_theme_preference"

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    /**
     * Aplica el modo global al iniciar la aplicación (en FinanzasApp).
     */
    fun applyTheme(context: Context) {
        val targetNightMode = if (isDarkMode(context)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(targetNightMode)
    }

    /**
     * Se invoca en onCreate() de cada Activity antes de super.onCreate().
     */
    fun applyThemeToActivity(activity: Activity) {
        val targetNightMode = if (isDarkMode(activity)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }
    }

    /**
     * Verifica que la actividad coincida con el tema seleccionado.
     * Al usar reinicio limpio de pila tras cambios de tema, las vistas ya se cargan
     * directamente con el tema correcto desde onCreate() sin parpadear.
     */
    fun ensureTheme(activity: Activity): Boolean {
        val targetNightMode = if (isDarkMode(activity)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        val currentNightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isCurrentNight = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)
        val isTargetNight = (targetNightMode == AppCompatDelegate.MODE_NIGHT_YES)

        if (isCurrentNight != isTargetNight) {
            val intent = Intent(activity, activity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            activity.finish()
            activity.overridePendingTransition(0, 0)
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
            return true
        }
        return false
    }

    fun getThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, MODE_LIGHT) ?: MODE_LIGHT
    }

    /**
     * Guarda la preferencia y reinicia de forma limpia la pantalla actual sin animación
     * ni parpadeos, asegurando que toda la aplicación y vistas posteriores adopten el tema
     * sin recargas visibles al entrar.
     */
    fun setThemeMode(context: Context, mode: String) {
        val target = if (mode == MODE_DARK) MODE_DARK else MODE_LIGHT
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_MODE, target)
            .putBoolean(KEY_DARK_MODE_LEGACY, target == MODE_DARK)
            .apply()

        val targetNightMode = if (target == MODE_DARK) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(targetNightMode)

        if (context is Activity) {
            val intent = Intent(context, context::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
            context.overridePendingTransition(0, 0)
            context.finish()
        }
    }

    fun isDarkMode(context: Context): Boolean {
        return getThemeMode(context) == MODE_DARK
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        setThemeMode(context, if (enabled) MODE_DARK else MODE_LIGHT)
    }

    fun getThemeModeLabel(context: Context): String {
        return if (isDarkMode(context)) "Modo Oscuro" else "Modo Claro"
    }

    fun showThemeSelectionDialog(activity: Activity, onThemeChanged: (() -> Unit)? = null) {
        val options = arrayOf(
            "☀️ Modo Claro (Predeterminado)",
            "🌙 Modo Oscuro"
        )
        val currentMode = getThemeMode(activity)
        val checkedItem = if (currentMode == MODE_DARK) 1 else 0

        AlertDialog.Builder(activity)
            .setTitle("Tema de Pantalla")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val chosenMode = if (which == 1) MODE_DARK else MODE_LIGHT
                dialog.dismiss()
                if (chosenMode != currentMode) {
                    val message = if (chosenMode == MODE_DARK) "🌙 Modo Oscuro activado" else "☀️ Modo Claro activado"
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    onThemeChanged?.invoke()
                    setThemeMode(activity, chosenMode)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
