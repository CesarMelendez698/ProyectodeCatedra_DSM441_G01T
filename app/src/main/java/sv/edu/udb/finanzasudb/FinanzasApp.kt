package sv.edu.udb.finanzasudb

import android.app.Application
import sv.edu.udb.finanzasudb.utils.ThemeHelper

class FinanzasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Aplica el tema predeterminado (Modo Claro) o el guardado antes de que cualquier Activity se cree
        ThemeHelper.applyTheme(this)
    }
}
