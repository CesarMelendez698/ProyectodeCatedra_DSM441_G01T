package sv.edu.udb.finanzasudb.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.firestore.ListenerRegistration
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository
import sv.edu.udb.finanzasudb.model.UserProfile
import sv.edu.udb.finanzasudb.utils.NavTab
import sv.edu.udb.finanzasudb.utils.NavigationHelper
import sv.edu.udb.finanzasudb.utils.ThemeHelper
import sv.edu.udb.finanzasudb.utils.UserProfileHelper
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvInitials: TextView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private var btnEditProfileName: View? = null

    private lateinit var rowCareer: LinearLayout
    private lateinit var tvCareer: TextView
    private lateinit var rowCycle: LinearLayout
    private lateinit var tvCycle: TextView
    private lateinit var rowCampus: LinearLayout
    private lateinit var tvCampus: TextView
    private lateinit var rowSubjects: LinearLayout
    private lateinit var tvSubjectsCount: TextView

    private lateinit var tvNetBalance: TextView
    private lateinit var rowTransactions: LinearLayout
    private lateinit var tvTxCount: TextView

    private lateinit var btnResetPass: LinearLayout
    private lateinit var btnLogout: LinearLayout

    private var txListener: ListenerRegistration? = null
    private var schedListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null

    private val campuses = listOf(
        "Campus Soyapango",
        "Campus Antiguo Cuscatlán",
        "Campus Virtual"
    )

    private val careers = listOf(
        "Ingeniería en Ciencias de la Computación",
        "Técnico en Ingeniería en Computación",
        "Ingeniería Industrial",
        "Ingeniería Mecatrónica",
        "Ingeniería Biomédica",
        "Ingeniería Aeronáutica",
        "Licenciatura en Administración de Empresas",
        "Licenciatura en Diseño Gráfico",
        "Licenciatura en Idioma Inglés"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        loadUserData()
        setupListeners()
        listenToFirestoreData()
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.ensureTheme(this)) return
        NavigationHelper.setupBottomNav(this, NavTab.PROFILE)
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

    override fun onDestroy() {
        super.onDestroy()
        txListener?.remove()
        schedListener?.remove()
        profileListener?.remove()
    }

    private fun initViews() {
        tvInitials = findViewById(R.id.tvProfileInitials)
        tvName = findViewById(R.id.tvProfileName)
        tvEmail = findViewById(R.id.tvProfileEmail)
        btnEditProfileName = findViewById(R.id.btnEditProfileName)

        rowCareer = findViewById(R.id.rowCareer)
        tvCareer = findViewById(R.id.tvProfileCareer)
        rowCycle = findViewById(R.id.rowCycle)
        tvCycle = findViewById(R.id.tvProfileCycle)
        rowCampus = findViewById(R.id.rowCampus)
        tvCampus = findViewById(R.id.tvProfileCampus)
        rowSubjects = findViewById(R.id.rowSubjects)
        tvSubjectsCount = findViewById(R.id.tvProfileSubjectsCount)

        tvNetBalance = findViewById(R.id.tvProfileNetBalance)
        rowTransactions = findViewById(R.id.rowTransactions)
        tvTxCount = findViewById(R.id.tvProfileTxCount)

        btnResetPass = findViewById(R.id.btnResetPasswordProfile)
        btnLogout = findViewById(R.id.btnLogoutProfile)
    }

    private fun loadUserData() {
        val email = FirebaseRepository.userEmail
        tvEmail.text = email

        val profile = UserProfileHelper.getProfile(this)
        applyUserProfile(profile)
    }

    private fun applyUserProfile(profile: UserProfile) {
        tvName.text = profile.name
        tvInitials.text = UserProfileHelper.computeInitials(profile.name)
        tvCareer.text = profile.career
        tvCycle.text = profile.cycle
        tvCampus.text = profile.campus
    }

    private fun saveCurrentProfileToFirestore() {
        val profile = UserProfile(
            email = FirebaseRepository.userEmail,
            name = tvName.text.toString().trim(),
            career = tvCareer.text.toString().trim(),
            cycle = tvCycle.text.toString().trim(),
            campus = tvCampus.text.toString().trim()
        )
        UserProfileHelper.saveProfile(this, profile)
    }

    private fun showEditNameDialog() {
        val input = EditText(this)
        input.setText(tvName.text.toString())
        input.setSelection(input.text.length)
        input.hint = "Nombre y apellido del estudiante"

        AlertDialog.Builder(this)
            .setTitle("Editar Nombre de Estudiante")
            .setMessage("Este nombre se mostrará en todo el sistema:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    tvName.text = newName
                    tvInitials.text = UserProfileHelper.computeInitials(newName)
                    saveCurrentProfileToFirestore()
                    Toast.makeText(this, "Nombre actualizado: $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupListeners() {
        btnEditProfileName?.setOnClickListener { showEditNameDialog() }
        tvName.setOnClickListener { showEditNameDialog() }

        rowCareer.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Seleccionar Carrera")
                .setItems(careers.toTypedArray()) { _, which ->
                    val chosen = careers[which]
                    tvCareer.text = chosen
                    saveCurrentProfileToFirestore()
                    Toast.makeText(this, "Carrera guardada: $chosen", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        rowCycle.setOnClickListener {
            val input = EditText(this)
            val currentText = if (tvCycle.text == "Configurar ciclo...") "" else tvCycle.text.toString()
            input.setText(currentText)
            input.hint = "Ej: Ciclo 01"
            AlertDialog.Builder(this)
                .setTitle("Ciclo Académico")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    val chosen = input.text.toString().trim()
                    if (chosen.isNotEmpty()) {
                        tvCycle.text = chosen
                        saveCurrentProfileToFirestore()
                        Toast.makeText(this, "Ciclo guardado: $chosen", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        rowCampus.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Seleccionar Campus UDB")
                .setItems(campuses.toTypedArray()) { _, which ->
                    val chosen = campuses[which]
                    tvCampus.text = chosen
                    saveCurrentProfileToFirestore()
                    Toast.makeText(this, "Campus actualizado: $chosen", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        rowSubjects.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        rowTransactions.setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }

        btnResetPass.setOnClickListener {
            val userEmail = tvEmail.text.toString().trim()
            FirebaseRepository.sendPasswordReset(userEmail) { success, err ->
                if (success) {
                    Toast.makeText(this, "Correo de restablecimiento enviado a $userEmail", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error: ${err ?: "No se pudo enviar el correo"}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnLogout.setOnClickListener {
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

    private fun listenToFirestoreData() {
        // Escuchar perfil del usuario en Firestore (nombre, carrera, ciclo, campus)
        profileListener = UserProfileHelper.listenProfile(this) { profile ->
            applyUserProfile(profile)
        }

        txListener = FirebaseRepository.listenTransactions { transactions ->
            var income = 0.0
            var expense = 0.0
            for (t in transactions) {
                if (t.type.equals("ingreso", ignoreCase = true)) {
                    income += t.amount
                } else {
                    expense += t.amount
                }
            }
            val net = income - expense
            tvNetBalance.text = String.format(Locale.US, "$%.2f", net)
            tvTxCount.text = "${transactions.size} registros"
        }

        schedListener = FirebaseRepository.listenSchedule { list ->
            val uniqueCount = list.map { it.subjectName.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size
            tvSubjectsCount.text = "$uniqueCount materias del ciclo inscritas"
        }
    }
}
