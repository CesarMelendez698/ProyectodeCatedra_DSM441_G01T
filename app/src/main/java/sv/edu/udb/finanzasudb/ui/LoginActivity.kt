package sv.edu.udb.finanzasudb.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var cbRemember: CheckBox
    private lateinit var btnLogin: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvGoToRegister: TextView

    private var isPasswordVisible = false
    private val PREFS_NAME = "finanzas_udb_prefs"
    private val KEY_REMEMBERED_EMAIL = "@user_remembered_email"

    override fun onCreate(savedInstanceState: Bundle?) {
        sv.edu.udb.finanzasudb.utils.ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)

        // Si ya hay una sesión activa de Firebase, entrar directamente
        if (FirebaseRepository.currentUser != null) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        initViews()
        loadRememberedEmail()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        if (sv.edu.udb.finanzasudb.utils.ThemeHelper.ensureTheme(this)) return
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        ivPasswordToggle = findViewById(R.id.ivPasswordToggle)
        cbRemember = findViewById(R.id.cbRemember)
        btnLogin = findViewById(R.id.btnLogin)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
    }

    private fun loadRememberedEmail() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEmail = prefs.getString(KEY_REMEMBERED_EMAIL, null)
        if (!savedEmail.isNullOrEmpty()) {
            etEmail.setText(savedEmail)
            cbRemember.isChecked = true
        }
    }

    private fun setupListeners() {
        ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text.length)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Por favor, ingresa tu correo y contraseña.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Iniciando sesión..."

            // Autenticación con Firebase Auth
            FirebaseRepository.auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    if (cbRemember.isChecked) {
                        prefs.edit().putString(KEY_REMEMBERED_EMAIL, email).apply()
                    } else {
                        prefs.edit().remove(KEY_REMEMBERED_EMAIL).apply()
                    }

                    Toast.makeText(this, "¡Bienvenido a Finanzas UDB!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { error ->
                    btnLogin.isEnabled = true
                    btnLogin.text = "Ingresar"
                    Toast.makeText(this, "Error de autenticación: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }

        tvForgotPassword.setOnClickListener {
            showPasswordRecoveryDialog()
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun showPasswordRecoveryDialog() {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_recovery_password, null)
        val etResetEmail = view.findViewById<EditText>(R.id.etResetEmail)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelRecovery)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitRecovery)

        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val email = etResetEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Por favor, escribe tu correo electrónico.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseRepository.auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Enlace de restablecimiento enviado a $email", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }

        dialog.show()
    }
}
