package sv.edu.udb.finanzasudb.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.adapter.CalendarActivityAdapter
import sv.edu.udb.finanzasudb.adapter.GroupedSubjectAdapter
import sv.edu.udb.finanzasudb.adapter.ScheduleAdapter
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository
import sv.edu.udb.finanzasudb.model.CalendarActivity
import sv.edu.udb.finanzasudb.model.GroupedSubject
import sv.edu.udb.finanzasudb.model.ScheduleItem
import sv.edu.udb.finanzasudb.model.UserProfile
import sv.edu.udb.finanzasudb.utils.AppNotificationHelper
import sv.edu.udb.finanzasudb.utils.NavTab
import sv.edu.udb.finanzasudb.utils.NavigationHelper
import sv.edu.udb.finanzasudb.utils.ThemeHelper
import java.text.SimpleDateFormat
import java.util.*

class ScheduleActivity : AppCompatActivity() {

    private lateinit var tvCareerTitle: TextView
    private lateinit var tvCareerSubtitle: TextView
    private lateinit var btnShare: Button
    private lateinit var btnChooseCareer: Button

    private lateinit var btnTabWeeklySchedule: TextView
    private lateinit var btnTabCalendar: TextView
    private lateinit var btnTabMySubjects: TextView

    private lateinit var scrollDays: HorizontalScrollView
    private lateinit var layoutScheduleListHeader: View
    private lateinit var tvDayClassCount: TextView
    private lateinit var btnAdd: Button
    private lateinit var cardEmptySchedule: MaterialCardView
    private lateinit var rvSchedule: RecyclerView

    // Calendar Section Views
    private lateinit var layoutCalendarSection: View
    private lateinit var calendarViewSchedule: CalendarView
    private lateinit var tvCalendarSelectedDate: TextView
    private lateinit var btnAddCalendarActivity: Button
    private lateinit var cardEmptyCalendar: MaterialCardView
    private lateinit var rvCalendarActivities: RecyclerView

    private lateinit var adapter: ScheduleAdapter
    private lateinit var groupedAdapter: GroupedSubjectAdapter
    private lateinit var calendarAdapter: CalendarActivityAdapter

    private val dayButtons = mutableListOf<TextView>()
    private val daysList = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val dayFullNames = mapOf(
        "Lun" to "Lunes",
        "Mar" to "Martes",
        "Mié" to "Miércoles",
        "Jue" to "Jueves",
        "Vie" to "Viernes",
        "Sáb" to "Sábado",
        "Dom" to "Domingo"
    )
    private var selectedDay = "Lunes"
    private var currentTab = "weekly" // "weekly", "calendar", "subjects"

    private var selectedCalendarDate = ""

    // Listas estrictamente separadas
    private val masterSchedule = mutableListOf<ScheduleItem>() // Solo sesiones de materias del ciclo
    private val calendarActivities = mutableListOf<CalendarActivity>() // Solo actividades de calendario

    private var scheduleListener: ListenerRegistration? = null
    private var calendarListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        AppNotificationHelper.initChannels(this)
        AppNotificationHelper.requestPermissionIfNecessary(this)

        initDefaultCalendarDate()
        initViews()
        setupDayButtons()
        setupListeners()
        loadScheduleAndActivitiesFromFirestore()
        listenToUserProfile()
    }

    private fun initDefaultCalendarDate() {
        val today = Calendar.getInstance()
        selectedCalendarDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(today.time)
        val dayOfWeekIdx = today.get(Calendar.DAY_OF_WEEK)
        selectedDay = when (dayOfWeekIdx) {
            Calendar.MONDAY -> "Lunes"
            Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"
            Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"
            Calendar.SATURDAY -> "Sábado"
            Calendar.SUNDAY -> "Domingo"
            else -> "Lunes"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleListener?.remove()
        calendarListener?.remove()
        profileListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.ensureTheme(this)) return
        NavigationHelper.setupBottomNav(this, NavTab.SCHEDULE)
        refreshUserProfileUI()
    }

    private fun refreshUserProfileUI() {
        val profile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
        tvCareerTitle.text = profile.career
        tvCareerSubtitle.text = "${profile.cycle} • ${profile.campus}"
    }

    private fun loadScheduleAndActivitiesFromFirestore() {
        // 1. Escuchar solo Materias del Ciclo (Horario regular)
        scheduleListener = FirebaseRepository.listenSchedule { list ->
            masterSchedule.clear()
            masterSchedule.addAll(list)
            updateView()
            AppNotificationHelper.checkClassAlerts(this, masterSchedule)
        }

        // 2. Escuchar solo Actividades de Calendario (Tareas, Parciales, Laboratorios, etc.)
        calendarListener = FirebaseRepository.listenCalendarActivities { list ->
            calendarActivities.clear()
            calendarActivities.addAll(list)
            updateCalendarActivities()
        }
    }

    private fun listenToUserProfile() {
        profileListener = sv.edu.udb.finanzasudb.utils.UserProfileHelper.listenProfile(this) { profile ->
            tvCareerTitle.text = profile.career
            tvCareerSubtitle.text = "${profile.cycle} • ${profile.campus}"
        }
    }

    private fun initViews() {
        tvCareerTitle = findViewById(R.id.tvCareerTitle)
        tvCareerSubtitle = findViewById(R.id.tvCareerSubtitle)
        btnShare = findViewById(R.id.btnShareSchedule)
        btnChooseCareer = findViewById(R.id.btnChooseCareer)

        btnTabWeeklySchedule = findViewById(R.id.btnTabWeeklySchedule)
        btnTabCalendar = findViewById(R.id.btnTabCalendar)
        btnTabMySubjects = findViewById(R.id.btnTabMySubjects)

        scrollDays = findViewById(R.id.scrollDays)
        layoutScheduleListHeader = findViewById(R.id.layoutScheduleListHeader)
        tvDayClassCount = findViewById(R.id.tvDayClassCount)
        btnAdd = findViewById(R.id.btnAddSchedule)
        cardEmptySchedule = findViewById(R.id.cardEmptySchedule)
        rvSchedule = findViewById(R.id.rvSchedule)

        // Calendar views
        layoutCalendarSection = findViewById(R.id.layoutCalendarSection)
        calendarViewSchedule = findViewById(R.id.calendarViewSchedule)
        tvCalendarSelectedDate = findViewById(R.id.tvCalendarSelectedDate)
        btnAddCalendarActivity = findViewById(R.id.btnAddCalendarActivity)
        cardEmptyCalendar = findViewById(R.id.cardEmptyCalendar)
        rvCalendarActivities = findViewById(R.id.rvCalendarActivities)

        // Cargar datos locales iniciales
        val userEmail = FirebaseRepository.userEmail
        val prefs = getSharedPreferences("user_academic_prefs", MODE_PRIVATE)
        val savedCareer = prefs.getString("${userEmail}_career", "Ingeniería en Ciencias de la Computación")
        val savedCycle = prefs.getString("${userEmail}_cycle", "Ciclo 01")
        val savedCampus = prefs.getString("${userEmail}_campus", "Campus Soyapango")

        tvCareerTitle.text = savedCareer
        tvCareerSubtitle.text = "$savedCycle • $savedCampus"

        // Adapter para Vista Semanal (sesiones por día con opción de clonar)
        adapter = ScheduleAdapter(
            scheduleList = emptyList(),
            onCloneClick = { itemToClone -> showCloneScheduleDialog(itemToClone) },
            onEditClick = { itemToEdit -> showEditScheduleDialog(itemToEdit) },
            onDeleteClick = { itemToDelete -> confirmDeleteItem(itemToDelete) }
        )

        // Adapter para Mis Materias (agrupa teoría y lab como 1 sola materia)
        groupedAdapter = GroupedSubjectAdapter(
            subjectList = emptyList(),
            onCloneClick = { grouped -> showCloneFromGroupedDialog(grouped) },
            onEditClick = { grouped -> showEditGroupedSubjectDialog(grouped) },
            onDeleteClick = { grouped -> confirmDeleteGroupedSubject(grouped) }
        )

        rvSchedule.layoutManager = LinearLayoutManager(this)
        rvSchedule.adapter = adapter

        // Adapter dedicado e independiente para Actividades de Calendario
        calendarAdapter = CalendarActivityAdapter(
            activityList = emptyList(),
            onToggleCompleted = { act ->
                FirebaseRepository.toggleCalendarActivityCompleted(act)
            },
            onEditClick = { actToEdit -> showEditCalendarActivityDialog(actToEdit) },
            onDeleteClick = { actToDelete -> confirmDeleteCalendarActivity(actToDelete) }
        )
        rvCalendarActivities.layoutManager = LinearLayoutManager(this)
        rvCalendarActivities.adapter = calendarAdapter
    }

    private fun confirmDeleteItem(itemToDelete: ScheduleItem) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Sesión")
            .setMessage("¿Deseas remover la clase de '${itemToDelete.subjectName}' (${itemToDelete.dayOfWeek})?")
            .setPositiveButton("Eliminar") { _, _ ->
                FirebaseRepository.deleteScheduleItem(itemToDelete.id) { success ->
                    if (success) {
                        Toast.makeText(this, "Sesión eliminada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteGroupedSubject(grouped: GroupedSubject) {
        val count = grouped.sessions.size
        val label = if (count == 1) "1 sesión" else "$count sesiones (Teoría + Laboratorio)"
        AlertDialog.Builder(this)
            .setTitle("Eliminar Asignatura")
            .setMessage("¿Deseas remover la materia '${grouped.subjectName}' y todas sus $label del ciclo?")
            .setPositiveButton("Eliminar Todo") { _, _ ->
                var deletedCount = 0
                for (session in grouped.sessions) {
                    FirebaseRepository.deleteScheduleItem(session.id) {
                        deletedCount++
                    }
                }
                Toast.makeText(this, "Asignatura eliminada del ciclo", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteCalendarActivity(activity: CalendarActivity) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Actividad")
            .setMessage("¿Deseas remover la actividad '${activity.title}' del calendario?")
            .setPositiveButton("Eliminar") { _, _ ->
                FirebaseRepository.deleteCalendarActivity(activity.id) { success ->
                    if (success) {
                        Toast.makeText(this, "Actividad eliminada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupDayButtons() {
        val bLun: TextView = findViewById(R.id.btnDayLun)
        val bMar: TextView = findViewById(R.id.btnDayMar)
        val bMie: TextView = findViewById(R.id.btnDayMie)
        val bJue: TextView = findViewById(R.id.btnDayJue)
        val bVie: TextView = findViewById(R.id.btnDayVie)
        val bSab: TextView = findViewById(R.id.btnDaySab)
        val bDom: TextView = findViewById(R.id.btnDayDom)

        dayButtons.addAll(listOf(bLun, bMar, bMie, bJue, bVie, bSab, bDom))

        for (i in dayButtons.indices) {
            val btn = dayButtons[i]
            val shortCode = daysList[i]
            val fullDay = dayFullNames[shortCode] ?: "Lunes"

            btn.setOnClickListener {
                selectedDay = fullDay
                updateDayButtonsState()
                updateView()
            }
        }
        updateDayButtonsState()
    }

    private fun updateDayButtonsState() {
        for (i in dayButtons.indices) {
            val shortCode = daysList[i]
            val fullDay = dayFullNames[shortCode] ?: "Lunes"
            val isCurrent = fullDay.equals(selectedDay, ignoreCase = true)

            if (isCurrent) {
                dayButtons[i].setBackgroundResource(R.drawable.bg_primary_button)
                dayButtons[i].setTextColor(0xFFFFFFFF.toInt())
            } else {
                dayButtons[i].setBackgroundResource(R.drawable.bg_pill_capsule)
                dayButtons[i].setTextColor(0xFF94A3B8.toInt())
            }
        }
    }

    // Agrupa sesiones de una misma materia (por nombre/código) para que en Mis Materias cuente como 1 sola
    private fun getGroupedSubjects(list: List<ScheduleItem>): List<GroupedSubject> {
        val map = LinkedHashMap<String, MutableList<ScheduleItem>>()
        for (item in list) {
            val key = item.subjectName.trim().lowercase()
            if (key.isEmpty()) continue
            if (!map.containsKey(key)) {
                map[key] = mutableListOf()
            }
            map[key]?.add(item)
        }

        return map.values.map { sessionList ->
            val first = sessionList.first()
            GroupedSubject(
                subjectKey = first.subjectName.trim().lowercase(),
                subjectName = first.subjectName,
                code = first.code,
                professor = first.professor,
                colorHex = first.colorHex,
                sessions = sessionList.sortedWith(
                    compareBy({ getDayOrder(it.dayOfWeek) }, { AppNotificationHelper.parseTimeToMinutes(it.startTime) })
                )
            )
        }.sortedWith(
            compareBy(
                { g -> g.sessions.firstOrNull()?.let { getDayOrder(it.dayOfWeek) } ?: 99 },
                { g -> g.sessions.firstOrNull()?.let { AppNotificationHelper.parseTimeToMinutes(it.startTime) } ?: 9999 }
            )
        )
    }

    private fun getDayOrder(day: String): Int = when (day.trim().lowercase()) {
        "lunes", "lun" -> 1
        "martes", "mar" -> 2
        "miércoles", "miercoles", "mié", "mie" -> 3
        "jueves", "jue" -> 4
        "viernes", "vie" -> 5
        "sábado", "sabado", "sáb", "sab" -> 6
        "domingo", "dom" -> 7
        else -> 8
    }

    private fun updateView() {
        if (currentTab == "calendar") {
            scrollDays.visibility = View.GONE
            layoutScheduleListHeader.visibility = View.GONE
            cardEmptySchedule.visibility = View.GONE
            rvSchedule.visibility = View.GONE
            layoutCalendarSection.visibility = View.VISIBLE
            updateCalendarActivities()
            return
        }

        layoutCalendarSection.visibility = View.GONE

        if (currentTab == "weekly") {
            scrollDays.visibility = View.VISIBLE
            layoutScheduleListHeader.visibility = View.VISIBLE
            btnAdd.text = "＋ Añadir Materia"

            // Filtrar sesiones del día seleccionado ordenadas estrictamente por hora de inicio
            val daySessions = masterSchedule
                .filter { it.dayOfWeek.equals(selectedDay, ignoreCase = true) }
                .sortedBy { AppNotificationHelper.parseTimeToMinutes(it.startTime) }
            tvDayClassCount.text = "$selectedDay (${daySessions.size} sesiones)"

            rvSchedule.adapter = adapter
            if (daySessions.isEmpty()) {
                cardEmptySchedule.visibility = View.VISIBLE
                rvSchedule.visibility = View.GONE
            } else {
                cardEmptySchedule.visibility = View.GONE
                rvSchedule.visibility = View.VISIBLE
                adapter.updateList(daySessions)
            }
        } else {
            // Pestaña "📚 Materias": agrupa teoría y laboratorio como UNA SOLA materia
            scrollDays.visibility = View.GONE
            layoutScheduleListHeader.visibility = View.VISIBLE
            btnAdd.text = "＋ Añadir Materia"

            val grouped = getGroupedSubjects(masterSchedule)
            tvDayClassCount.text = "Mis Materias del Ciclo (${grouped.size})"

            rvSchedule.adapter = groupedAdapter
            if (grouped.isEmpty()) {
                cardEmptySchedule.visibility = View.VISIBLE
                rvSchedule.visibility = View.GONE
            } else {
                cardEmptySchedule.visibility = View.GONE
                rvSchedule.visibility = View.VISIBLE
                groupedAdapter.updateList(grouped)
            }
        }
    }

    private fun updateCalendarActivities() {
        if (selectedCalendarDate.isEmpty()) {
            initDefaultCalendarDate()
        }

        val activitiesForDate = calendarActivities
            .filter { item -> item.date.equals(selectedCalendarDate, ignoreCase = true) }
            .sortedBy { AppNotificationHelper.parseTimeToMinutes(it.startTime) }

        tvCalendarSelectedDate.text = "Actividades para $selectedCalendarDate (${activitiesForDate.size})"

        if (activitiesForDate.isEmpty()) {
            cardEmptyCalendar.visibility = View.VISIBLE
            rvCalendarActivities.visibility = View.GONE
        } else {
            cardEmptyCalendar.visibility = View.GONE
            rvCalendarActivities.visibility = View.VISIBLE
            calendarAdapter.updateList(activitiesForDate)
        }
    }

    private fun setupListeners() {
        btnTabWeeklySchedule.setOnClickListener {
            currentTab = "weekly"
            setActiveTabButton(btnTabWeeklySchedule)
            setInactiveTabButton(btnTabCalendar)
            setInactiveTabButton(btnTabMySubjects)
            updateView()
        }

        btnTabCalendar.setOnClickListener {
            currentTab = "calendar"
            setActiveTabButton(btnTabCalendar)
            setInactiveTabButton(btnTabWeeklySchedule)
            setInactiveTabButton(btnTabMySubjects)
            updateView()
        }

        btnTabMySubjects.setOnClickListener {
            currentTab = "subjects"
            setActiveTabButton(btnTabMySubjects)
            setInactiveTabButton(btnTabWeeklySchedule)
            setInactiveTabButton(btnTabCalendar)
            updateView()
        }

        calendarViewSchedule.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedCalendarDate = String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, month + 1, year)
            updateCalendarActivities()
        }

        btnAddCalendarActivity.setOnClickListener {
            showAddCalendarActivityDialog()
        }

        btnChooseCareer.setOnClickListener {
            showChooseCareerAndCampusDialog()
        }

        btnShare.setOnClickListener {
            shareScheduleText()
        }

        btnAdd.setOnClickListener { showAddScheduleDialog() }
    }

    private fun setActiveTabButton(tv: TextView) {
        tv.setBackgroundResource(R.drawable.bg_pill_subtab_active)
        tv.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun setInactiveTabButton(tv: TextView) {
        tv.setBackgroundResource(R.drawable.bg_pill_subtab_inactive)
        tv.setTextColor(0xFF94A3B8.toInt())
    }

    private fun showChooseCareerAndCampusDialog() {
        val options = arrayOf(
            "Cambiar Carrera Universitaria",
            "Cambiar Ciclo Académico",
            "Cambiar Campus (Soyapango / Cuscatlán / Virtual)"
        )
        AlertDialog.Builder(this)
            .setTitle("Configuración Académica UDB")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickCareerDialog()
                    1 -> pickCycleDialog()
                    2 -> pickCampusDialog()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickCareerDialog() {
        val careers = arrayOf(
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
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Carrera UDB")
            .setItems(careers) { _, which ->
                val chosen = careers[which]
                tvCareerTitle.text = chosen
                val currentProfile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
                val updatedProfile = currentProfile.copy(career = chosen)
                sv.edu.udb.finanzasudb.utils.UserProfileHelper.saveProfile(this, updatedProfile)
                refreshUserProfileUI()
                Toast.makeText(this, "Carrera actualizada: $chosen", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun pickCycleDialog() {
        val input = EditText(this)
        val currentProfile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
        input.setText(currentProfile.cycle)
        input.hint = "Ej: Ciclo 01"

        AlertDialog.Builder(this)
            .setTitle("Ciclo Académico")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val chosen = input.text.toString().trim()
                if (chosen.isNotEmpty()) {
                    val updatedProfile = currentProfile.copy(cycle = chosen)
                    sv.edu.udb.finanzasudb.utils.UserProfileHelper.saveProfile(this, updatedProfile)
                    refreshUserProfileUI()
                    Toast.makeText(this, "Ciclo actualizado: $chosen", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickCampusDialog() {
        val campuses = arrayOf(
            "Campus Soyapango",
            "Campus Antiguo Cuscatlán",
            "Campus Virtual"
        )
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Campus UDB")
            .setItems(campuses) { _, which ->
                val chosen = campuses[which]
                val currentProfile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
                val updatedProfile = currentProfile.copy(campus = chosen)
                sv.edu.udb.finanzasudb.utils.UserProfileHelper.saveProfile(this, updatedProfile)
                refreshUserProfileUI()
                Toast.makeText(this, "Campus actualizado: $chosen", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun shareScheduleText() {
        if (masterSchedule.isEmpty() && calendarActivities.isEmpty()) {
            Toast.makeText(this, "No tienes materias ni actividades para compartir", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("📅 MI HORARIO Y ACTIVIDADES UDB\n")
        sb.append("🎓 ${tvCareerTitle.text}\n")
        sb.append("📍 ${tvCareerSubtitle.text}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n")

        // 1. Materias del ciclo
        val grouped = getGroupedSubjects(masterSchedule)
        if (grouped.isNotEmpty()) {
            sb.append("📚 MATERIAS DEL CICLO (${grouped.size} Asignaturas):\n")
            for (g in grouped) {
                sb.append("📌 ${g.code} - ${g.subjectName}\n")
                sb.append("   👨‍🏫 Docente: ${g.professor}\n")
                for (s in g.sessions) {
                    val tag = if (s.activityType.equals("Laboratorio", true)) "[Laboratorio]" else "[Teoría]"
                    sb.append("   • ${s.dayOfWeek}: ${s.startTime} - ${s.endTime} $tag en ${s.classroom}\n")
                }
                sb.append("\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n")
        }

        // 2. Actividades de Calendario independientes
        if (calendarActivities.isNotEmpty()) {
            sb.append("🗓️ PRÓXIMAS ACTIVIDADES Y EVALUACIONES:\n")
            for (act in calendarActivities) {
                val statusStr = if (act.isCompleted) " [Completada]" else ""
                sb.append("  • [${act.type}] ${act.title}$statusStr\n")
                sb.append("    🗓️ Fecha: ${act.date} | ⏰ ${act.startTime} - ${act.endTime}\n")
                sb.append("    📍 ${act.location}\n")
                if (act.notes.isNotEmpty()) sb.append("    ℹ️ ${act.notes}\n")
                sb.append("\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━━━\n")
        }

        sb.append("Generado con Finanzas UDB 📱")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir Horario y Actividades UDB"))
    }

    // Modal para clonar una materia a otro día (Laboratorio o Teoría)
    private fun showCloneScheduleDialog(item: ScheduleItem) {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val tvInfo = TextView(this).apply {
            text = "Clonando: ${item.subjectName} (${item.code})\nSelecciona el día y si esta sesión será Laboratorio o Teoría:"
            setTextColor(0xFF475569.toInt())
            textSize = 13f
            setPadding(0, 0, 0, 20)
        }

        val types = listOf("Laboratorio", "Teoría", "Clase")
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, types)
            // Si la actual es Teoría, preseleccionar Laboratorio y viceversa
            val defaultIdx = if (item.activityType.equals("Teoría", true) || item.activityType.equals("Teoria", true) || item.activityType.equals("Clase", true)) 0 else 1
            setSelection(defaultIdx)
        }

        val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val spDay = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, days)
            // Seleccionar un día distinto al actual por defecto
            val currentIdx = days.indexOf(item.dayOfWeek)
            val newIdx = if (currentIdx != -1) (currentIdx + 2) % days.size else 0
            setSelection(newIdx)
        }

        var startTimeStr = item.startTime
        var endTimeStr = item.endTime

        val btnStart = Button(this).apply {
            text = "Hora inicio: $startTimeStr ⏰"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    startTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora inicio: $startTimeStr ⏰"
                }, 8, 0, false).show()
            }
        }

        val btnEnd = Button(this).apply {
            text = "Hora fin: $endTimeStr ⏰"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    endTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora fin: $endTimeStr ⏰"
                }, 10, 0, false).show()
            }
        }

        val etClassroom = EditText(this).apply {
            hint = "Aula / Laboratorio (ej. Lab 1)"
            setText(if (item.classroom.contains("Lab", true)) item.classroom else "Laboratorio L-1")
        }

        val etProf = EditText(this).apply {
            hint = "Docente / Instructor"
            setText(item.professor)
        }

        layout.addView(tvInfo)
        layout.addView(spType)
        layout.addView(spDay)
        layout.addView(btnStart)
        layout.addView(btnEnd)
        layout.addView(etClassroom)
        layout.addView(etProf)

        builder.setTitle("Clonar Asignatura a Otro Día")
            .setView(layout)
            .setPositiveButton("Guardar Clon") { _, _ ->
                val chosenType = types[spType.selectedItemPosition]
                val chosenDay = days[spDay.selectedItemPosition]
                val room = etClassroom.text.toString().trim().ifEmpty { "Laboratorio L-1" }
                val prof = etProf.text.toString().trim().ifEmpty { item.professor }

                val clonedItem = ScheduleItem(
                    id = "",
                    subjectName = item.subjectName,
                    code = item.code,
                    classroom = room,
                    professor = prof,
                    dayOfWeek = chosenDay,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    colorHex = item.colorHex,
                    date = "",
                    activityType = chosenType
                )

                FirebaseRepository.addScheduleItem(clonedItem) { success ->
                    if (success) {
                        Toast.makeText(this, "Materia clonada al $chosenDay como $chosenType", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCloneFromGroupedDialog(grouped: GroupedSubject) {
        val template = grouped.sessions.firstOrNull() ?: ScheduleItem(
            subjectName = grouped.subjectName,
            code = grouped.code,
            professor = grouped.professor,
            colorHex = grouped.colorHex
        )
        showCloneScheduleDialog(template)
    }

    private fun showEditGroupedSubjectDialog(grouped: GroupedSubject) {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etSubject = EditText(this).apply {
            hint = "Nombre de Asignatura"
            setText(grouped.subjectName)
        }
        val etCode = EditText(this).apply {
            hint = "Código"
            setText(grouped.code)
        }
        val etProf = EditText(this).apply {
            hint = "Docente Titular"
            setText(grouped.professor)
        }

        layout.addView(etSubject)
        layout.addView(etCode)
        layout.addView(etProf)

        builder.setTitle("Editar Asignatura")
            .setMessage("Este cambio se aplicará a todas las sesiones (${grouped.sessions.size}) de esta materia.")
            .setView(layout)
            .setPositiveButton("Actualizar") { _, _ ->
                val newName = etSubject.text.toString().trim()
                val newCode = etCode.text.toString().trim()
                val newProf = etProf.text.toString().trim()

                if (newName.isNotEmpty()) {
                    for (session in grouped.sessions) {
                        val updated = session.copy(
                            subjectName = newName,
                            code = newCode,
                            professor = newProf
                        )
                        FirebaseRepository.addScheduleItem(updated)
                    }
                    Toast.makeText(this, "Asignatura actualizada en todas sus sesiones", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Modal para agregar actividad independiente de calendario
    private fun showAddCalendarActivityDialog() {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etTitle = EditText(this).apply { hint = "Título (ej. Entrega Proyecto Móvil / Parcial)" }
        val types = listOf("Tarea", "Examen Parcial", "Laboratorio", "Clase Extra", "Actividad", "Evento", "Entrega")
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, types)
        }

        val userCampus = getSharedPreferences("user_academic_prefs", MODE_PRIVATE)
            .getString("${FirebaseRepository.userEmail}_campus", "Campus Soyapango") ?: "Campus Soyapango"

        val etLocation = EditText(this).apply {
            hint = "Aula / Modalidad"
            setText(userCampus)
        }
        val etNotes = EditText(this).apply { hint = "Docente / Detalles / Enlace" }

        var activityDateStr = selectedCalendarDate.ifEmpty {
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        }

        val btnDate = Button(this).apply {
            text = "Fecha: $activityDateStr 📅"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(this@ScheduleActivity, { _, year, month, dayOfMonth ->
                    activityDateStr = String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, month + 1, year)
                    text = "Fecha: $activityDateStr 📅"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        var startTimeStr = "08:00 AM"
        var endTimeStr = "10:00 AM"

        val btnStart = Button(this).apply {
            text = "Hora inicio: $startTimeStr ⏰"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    startTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora inicio: $startTimeStr ⏰"
                }, 8, 0, false).show()
            }
        }

        val btnEnd = Button(this).apply {
            text = "Hora fin: $endTimeStr ⏰"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    endTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora fin: $endTimeStr ⏰"
                }, 10, 0, false).show()
            }
        }

        layout.addView(etTitle)
        layout.addView(spType)
        layout.addView(etLocation)
        layout.addView(etNotes)
        layout.addView(btnDate)
        layout.addView(btnStart)
        layout.addView(btnEnd)

        builder.setTitle("Nueva Actividad en Calendario")
            .setView(layout)
            .setPositiveButton("Guardar en Firebase") { _, _ ->
                val title = etTitle.text.toString().trim()
                val chosenType = types[spType.selectedItemPosition]
                val loc = etLocation.text.toString().trim().ifEmpty { userCampus }
                val notes = etNotes.text.toString().trim().ifEmpty { "Actividad académica" }

                if (title.isNotEmpty()) {
                    val colors = mapOf(
                        "Tarea" to "#F59E0B",
                        "Examen Parcial" to "#EF4444",
                        "Laboratorio" to "#10B981",
                        "Clase Extra" to "#3B82F6",
                        "Actividad" to "#8B5CF6",
                        "Evento" to "#EC4899",
                        "Entrega" to "#38BDF8"
                    )
                    val chosenColor = colors[chosenType] ?: "#3B82F6"

                    val newActivity = CalendarActivity(
                        id = "",
                        title = title,
                        type = chosenType,
                        date = activityDateStr,
                        startTime = startTimeStr,
                        endTime = endTimeStr,
                        location = loc,
                        notes = notes,
                        colorHex = chosenColor,
                        isCompleted = false
                    )

                    FirebaseRepository.addCalendarActivity(newActivity) { success ->
                        if (success) {
                            Toast.makeText(this, "Actividad guardada en Calendario", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Por favor escribe el título de la actividad", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Modal para editar actividad de calendario existente
    private fun showEditCalendarActivityDialog(activity: CalendarActivity) {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etTitle = EditText(this).apply {
            hint = "Título"
            setText(activity.title)
        }
        val types = listOf("Tarea", "Examen Parcial", "Laboratorio", "Clase Extra", "Actividad", "Evento", "Entrega")
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, types)
            setSelection(types.indexOf(activity.type).coerceAtLeast(0))
        }

        val etLocation = EditText(this).apply {
            hint = "Aula / Modalidad"
            setText(activity.location)
        }
        val etNotes = EditText(this).apply {
            hint = "Docente / Detalles / Enlace"
            setText(activity.notes)
        }

        var activityDateStr = activity.date.ifEmpty { selectedCalendarDate }

        val btnDate = Button(this).apply {
            text = "Fecha: $activityDateStr 📅"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(this@ScheduleActivity, { _, year, month, dayOfMonth ->
                    activityDateStr = String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, month + 1, year)
                    text = "Fecha: $activityDateStr 📅"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        var startTimeStr = activity.startTime
        var endTimeStr = activity.endTime

        val btnStart = Button(this).apply {
            text = "Hora inicio: $startTimeStr ⏰"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    startTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora inicio: $startTimeStr ⏰"
                }, 8, 0, false).show()
            }
        }

        val btnEnd = Button(this).apply {
            text = "Hora fin: $endTimeStr ⏰"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    endTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora fin: $endTimeStr ⏰"
                }, 10, 0, false).show()
            }
        }

        layout.addView(etTitle)
        layout.addView(spType)
        layout.addView(etLocation)
        layout.addView(etNotes)
        layout.addView(btnDate)
        layout.addView(btnStart)
        layout.addView(btnEnd)

        builder.setTitle("Editar Actividad")
            .setView(layout)
            .setPositiveButton("Actualizar") { _, _ ->
                val title = etTitle.text.toString().trim()
                val chosenType = types[spType.selectedItemPosition]
                val loc = etLocation.text.toString().trim().ifEmpty { activity.location }
                val notes = etNotes.text.toString().trim()

                if (title.isNotEmpty()) {
                    val updated = activity.copy(
                        title = title,
                        type = chosenType,
                        date = activityDateStr,
                        startTime = startTimeStr,
                        endTime = endTimeStr,
                        location = loc,
                        notes = notes
                    )
                    FirebaseRepository.addCalendarActivity(updated) { success ->
                        if (success) {
                            Toast.makeText(this, "Actividad actualizada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Modal para agregar Asignatura / Materia del Ciclo (Horario regular)
    private fun showAddScheduleDialog() {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etSubject = EditText(this).apply { hint = "Materia (ej. Programación Móvil)" }
        val etCode = EditText(this).apply { hint = "Código (ej. PRN315)" }

        val types = listOf("Teoría", "Laboratorio", "Clase")
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, types)
        }

        val etClassroom = EditText(this).apply { hint = "Aula (ej. Edificio 3 Aula 204)" }
        val etProf = EditText(this).apply { hint = "Docente (ej. Ing. Nelson Rodríguez)" }

        var startTimeStr = "07:00 AM"
        var endTimeStr = "08:40 AM"

        val btnStart = Button(this).apply {
            text = "Hora inicio: $startTimeStr"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    startTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora inicio: $startTimeStr"
                }, 7, 0, false).show()
            }
        }

        val btnEnd = Button(this).apply {
            text = "Hora fin: $endTimeStr"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    endTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora fin: $endTimeStr"
                }, 8, 40, false).show()
            }
        }

        val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val spDay = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, days)
            setSelection(days.indexOf(selectedDay).coerceAtLeast(0))
        }

        // Opción de clonar/agregar sesión complementaria (ej. Laboratorio en otro día)
        val cbAddLab = CheckBox(this).apply {
            text = "🔬 Agregar también sesión de Laboratorio en otro día"
            setTextColor(0xFF10B981.toInt())
            textSize = 12f
            setPadding(0, 10, 0, 10)
        }

        val layoutLabDetails = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(10, 10, 10, 10)
            setBackgroundResource(R.drawable.bg_pill_capsule)
        }

        val spLabDay = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, days)
            setSelection(2) // Miércoles por defecto
        }

        var labStartTimeStr = "10:00 AM"
        var labEndTimeStr = "11:40 AM"

        val btnLabStart = Button(this).apply {
            text = "Hora Lab Inicio: $labStartTimeStr"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    labStartTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora Lab Inicio: $labStartTimeStr"
                }, 10, 0, false).show()
            }
        }

        val btnLabEnd = Button(this).apply {
            text = "Hora Lab Fin: $labEndTimeStr"
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(0xFF333333.toInt())
            setOnClickListener {
                android.app.TimePickerDialog(this@ScheduleActivity, { _, hour, minute ->
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val h = if (hour % 12 == 0) 12 else hour % 12
                    labEndTimeStr = String.format("%02d:%02d %s", h, minute, ampm)
                    text = "Hora Lab Fin: $labEndTimeStr"
                }, 11, 40, false).show()
            }
        }

        val etLabRoom = EditText(this).apply {
            hint = "Aula / Lab (ej. Laboratorio L-1)"
            setText("Laboratorio L-1")
        }

        layoutLabDetails.addView(spLabDay)
        layoutLabDetails.addView(btnLabStart)
        layoutLabDetails.addView(btnLabEnd)
        layoutLabDetails.addView(etLabRoom)

        cbAddLab.setOnCheckedChangeListener { _, isChecked ->
            layoutLabDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        layout.addView(etSubject)
        layout.addView(etCode)
        layout.addView(spType)
        layout.addView(spDay)
        layout.addView(btnStart)
        layout.addView(btnEnd)
        layout.addView(etClassroom)
        layout.addView(etProf)
        layout.addView(cbAddLab)
        layout.addView(layoutLabDetails)

        builder.setTitle("Nueva Asignatura del Ciclo")
            .setView(layout)
            .setPositiveButton("Guardar en Firebase") { _, _ ->
                val subj = etSubject.text.toString().trim()
                val code = etCode.text.toString().trim().ifEmpty { "UDB" }
                val chosenType = types[spType.selectedItemPosition]
                val room = etClassroom.text.toString().trim().ifEmpty { "Campus Virtual" }
                val prof = etProf.text.toString().trim().ifEmpty { "Docente Titular" }
                val chosenDay = days[spDay.selectedItemPosition]

                if (subj.isNotEmpty()) {
                    val colors = listOf("#EC4899", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B")
                    val subjectColor = colors.random()

                    // Sesión principal (guardada por día)
                    val newItem = ScheduleItem(
                        id = "",
                        subjectName = subj,
                        code = code,
                        classroom = room,
                        professor = prof,
                        dayOfWeek = chosenDay,
                        startTime = startTimeStr,
                        endTime = endTimeStr,
                        colorHex = subjectColor,
                        date = "",
                        activityType = chosenType
                    )
                    FirebaseRepository.addScheduleItem(newItem) { success ->
                        if (success) {
                            Toast.makeText(this, "Asignatura guardada", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Si marcó clonar/crear también sesión de laboratorio
                    if (cbAddLab.isChecked) {
                        val labDay = days[spLabDay.selectedItemPosition]
                        val labRoom = etLabRoom.text.toString().trim().ifEmpty { "Laboratorio L-1" }
                        val labItem = ScheduleItem(
                            id = "",
                            subjectName = subj,
                            code = code,
                            classroom = labRoom,
                            professor = prof,
                            dayOfWeek = labDay,
                            startTime = labStartTimeStr,
                            endTime = labEndTimeStr,
                            colorHex = subjectColor,
                            date = "",
                            activityType = "Laboratorio"
                        )
                        FirebaseRepository.addScheduleItem(labItem)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditScheduleDialog(item: ScheduleItem) {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etSubject = EditText(this).apply {
            hint = "Nombre de Asignatura"
            setText(item.subjectName)
        }
        val etCode = EditText(this).apply {
            hint = "Código (ej. PRN315)"
            setText(item.code)
        }

        val types = listOf("Teoría", "Laboratorio", "Clase")
        val spType = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, types)
            val currentIdx = types.indexOfFirst { it.equals(item.activityType, true) }
            setSelection(if (currentIdx != -1) currentIdx else 0)
        }

        val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val spDay = Spinner(this).apply {
            adapter = ArrayAdapter(this@ScheduleActivity, android.R.layout.simple_spinner_dropdown_item, days)
            setSelection(days.indexOf(item.dayOfWeek).coerceAtLeast(0))
        }

        val etClassroom = EditText(this).apply {
            hint = "Aula / Modalidad"
            setText(item.classroom)
        }
        val etProf = EditText(this).apply {
            hint = "Docente"
            setText(item.professor)
        }

        layout.addView(etSubject)
        layout.addView(etCode)
        layout.addView(spType)
        layout.addView(spDay)
        layout.addView(etClassroom)
        layout.addView(etProf)

        builder.setTitle("Editar Sesión de Asignatura")
            .setView(layout)
            .setPositiveButton("Actualizar") { _, _ ->
                val subj = etSubject.text.toString().trim()
                val chosenType = types[spType.selectedItemPosition]
                val chosenDay = days[spDay.selectedItemPosition]
                if (subj.isNotEmpty()) {
                    val updated = item.copy(
                        subjectName = subj,
                        code = etCode.text.toString().trim(),
                        dayOfWeek = chosenDay,
                        classroom = etClassroom.text.toString().trim(),
                        professor = etProf.text.toString().trim(),
                        date = "",
                        activityType = chosenType
                    )
                    FirebaseRepository.addScheduleItem(updated) { success ->
                        if (success) {
                            Toast.makeText(this, "Sesión actualizada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
