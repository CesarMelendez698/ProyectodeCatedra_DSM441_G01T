package sv.edu.udb.finanzasudb.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.UserProfileChangeRequest
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository

class RegisterActivity : AppCompatActivity() {

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var btnCreateAccount: Button
    private lateinit var tvBackToLogin: TextView

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        sv.edu.udb.finanzasudb.utils.ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etRegEmail)
        etPassword = findViewById(R.id.etRegPassword)
        ivPasswordToggle = findViewById(R.id.ivRegPasswordToggle)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text.length)
        }

        btnCreateAccount.setOnClickListener {
            val first = etFirstName.text.toString().trim()
            val last = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (first.isEmpty() || last.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Por favor, llena todos los campos para crear tu cuenta.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass.length < 6) {
                Toast.makeText(this, "La contraseña debe contener al menos 6 caracteres.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnCreateAccount.isEnabled = false
            btnCreateAccount.text = "Creando cuenta..."

            // Registro en Firebase Authentication
            FirebaseRepository.auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName("$first $last")
                        .build()

                    // Purgar cualquier dato residual previo de Firestore y local si el usuario fue borrado antes de Firebase
                    FirebaseRepository.purgeUserData(email) {
                        FirebaseRepository.clearLocalUserData(this@RegisterActivity, email)

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            Toast.makeText(this@RegisterActivity, "¡Cuenta creada en Firebase exitosamente!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@RegisterActivity, DashboardActivity::class.java)
                            startActivity(intent)
                            finishAffinity()
                        }
                    }
                }
                .addOnFailureListener { error ->
                    btnCreateAccount.isEnabled = true
                    btnCreateAccount.text = "Crear Cuenta"
                    Toast.makeText(this, "Error en registro: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (sv.edu.udb.finanzasudb.utils.ThemeHelper.ensureTheme(this)) return
    }
}

