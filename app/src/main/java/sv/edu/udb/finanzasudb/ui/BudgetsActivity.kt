package sv.edu.udb.finanzasudb.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.adapter.BudgetsAdapter
import sv.edu.udb.finanzasudb.adapter.FeesAdapter
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository
import androidx.core.content.ContextCompat
import sv.edu.udb.finanzasudb.model.Budget
import sv.edu.udb.finanzasudb.model.UdbFee
import sv.edu.udb.finanzasudb.utils.AppNotificationHelper
import sv.edu.udb.finanzasudb.utils.NavTab
import sv.edu.udb.finanzasudb.utils.NavigationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetsActivity : AppCompatActivity() {

    // Sub-Tabs
    private lateinit var btnTabPresupuestos: TextView
    private lateinit var btnTabCuotas: TextView
    private lateinit var layoutBudgetsView: LinearLayout
    private lateinit var layoutFeesView: LinearLayout

    // Tab 1: Límites
    private lateinit var etBudgetAmount: EditText
    private lateinit var btnSetBudget: Button
    private lateinit var tvActiveBudgetsCount: TextView
    private lateinit var cardEmptyBudgets: MaterialCardView
    private lateinit var rvBudgetsList: RecyclerView
    private lateinit var chipCategoryGroup: LinearLayout
    private lateinit var tvCategorySelectedInfo: TextView
    private var selectedCategory: String = "Comida"
    private val availableCategories = mutableListOf<String>()

    // Tab 2: Cuotas
    private lateinit var tvFeeProgress: TextView
    private lateinit var tvCycleBadge: TextView
    private lateinit var tvFeeTotalPaid: TextView
    private lateinit var tvFeeTotalPending: TextView
    private lateinit var tvFeeCountRegistered: TextView
    private lateinit var btnAddFeeTop: Button
    private lateinit var btnAddFirstFee: Button
    private lateinit var cardEmptyFees: MaterialCardView
    private lateinit var rvFeesList: RecyclerView

    private lateinit var budgetsAdapter: BudgetsAdapter
    private lateinit var feesAdapter: FeesAdapter

    private var activeSubTab = "budgets"
    private var currentCycle = "Ciclo 01"

    private val rawBudgetsList = mutableListOf<Budget>()
    private val budgetsList = mutableListOf<Budget>()
    private val udbFeesList = mutableListOf<UdbFee>()
    private val userTransactions = mutableListOf<sv.edu.udb.finanzasudb.model.Transaction>()

    private var budgetsListener: ListenerRegistration? = null
    private var feesListener: ListenerRegistration? = null
    private var transactionsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        sv.edu.udb.finanzasudb.utils.ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budgets)

        AppNotificationHelper.initChannels(this)
        AppNotificationHelper.requestPermissionIfNecessary(this)

        initViews()
        setupListeners()
        listenToFirestoreData()
        switchToBudgetsTab()
    }

    override fun onResume() {
        super.onResume()
        if (sv.edu.udb.finanzasudb.utils.ThemeHelper.ensureTheme(this)) return
        NavigationHelper.setupBottomNav(this, NavTab.BUDGETS)
        val profile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
        currentCycle = profile.cycle
        tvCycleBadge.text = "📝 $currentCycle"
    }

    override fun onDestroy() {
        super.onDestroy()
        budgetsListener?.remove()
        feesListener?.remove()
        transactionsListener?.remove()
        categoriesListener?.remove()
    }

    private fun initViews() {
        btnTabPresupuestos = findViewById(R.id.btnTabPresupuestos)
        btnTabCuotas = findViewById(R.id.btnTabCuotas)
        layoutBudgetsView = findViewById(R.id.layoutBudgetsView)
        layoutFeesView = findViewById(R.id.layoutFeesView)

        chipCategoryGroup = findViewById(R.id.chipCategoryGroup)
        tvCategorySelectedInfo = findViewById(R.id.tvCategorySelectedInfo)
        etBudgetAmount = findViewById(R.id.etBudgetAmount)
        btnSetBudget = findViewById(R.id.btnSetBudget)
        tvActiveBudgetsCount = findViewById(R.id.tvActiveBudgetsCount)
        cardEmptyBudgets = findViewById(R.id.cardEmptyBudgets)
        rvBudgetsList = findViewById(R.id.rvBudgetsList)

        tvFeeProgress = findViewById(R.id.tvFeeProgress)
        tvCycleBadge = findViewById(R.id.tvCycleBadge)
        val profile = sv.edu.udb.finanzasudb.utils.UserProfileHelper.getProfile(this)
        currentCycle = profile.cycle
        tvCycleBadge.text = "📝 $currentCycle"
        tvFeeTotalPaid = findViewById(R.id.tvFeeTotalPaid)
        tvFeeTotalPending = findViewById(R.id.tvFeeTotalPending)
        tvFeeCountRegistered = findViewById(R.id.tvFeeCountRegistered)
        btnAddFeeTop = findViewById(R.id.btnAddFeeTop)
        btnAddFirstFee = findViewById(R.id.btnAddFirstFee)
        cardEmptyFees = findViewById(R.id.cardEmptyFees)
        rvFeesList = findViewById(R.id.rvFeesList)

        budgetsAdapter = BudgetsAdapter(budgetsList) { budgetToDelete ->
            AlertDialog.Builder(this)
                .setTitle("Eliminar Presupuesto")
                .setMessage("¿Deseas borrar permanentemente el límite de '${budgetToDelete.category}'?")
                .setPositiveButton("Eliminar") { _, _ ->
                    FirebaseRepository.deleteBudget(budgetToDelete.id) { success ->
                        if (success) {
                            Toast.makeText(this, "Presupuesto eliminado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        feesAdapter = FeesAdapter(
            fees = udbFeesList,
            onTogglePaid = { fee ->
                FirebaseRepository.toggleFeePaid(fee.id, fee.isPaid) { success ->
                    if (success) {
                        val msg = if (!fee.isPaid) "Marcada como pagada ✅" else "Marcada como pendiente ⏳"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEditFee = { feeToEdit ->
                showFeeDialog(feeToEdit)
            },
            onDeleteFee = { feeToDelete ->
                confirmDeleteFee(feeToDelete)
            }
        )

        rvBudgetsList.layoutManager = LinearLayoutManager(this)
        rvBudgetsList.adapter = budgetsAdapter

        rvFeesList.layoutManager = LinearLayoutManager(this)
        rvFeesList.adapter = feesAdapter
    }

    private fun renderCategoryChips() {
        chipCategoryGroup.removeAllViews()
        val cats = availableCategories.ifEmpty { FirebaseRepository.defaultCategories }

        for (cat in cats) {
            val isSelected = cat.trim().equals(selectedCategory.trim(), ignoreCase = true)
            val chip = TextView(this).apply {
                text = cat
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(32, 12, 32, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size).coerceAtMost(80)
                ).apply {
                    marginEnd = 16
                }
                setBackgroundResource(if (isSelected) R.drawable.bg_primary_button else R.drawable.bg_input_field)
                setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else ContextCompat.getColor(this@BudgetsActivity, R.color.text_secondary))
                setOnClickListener {
                    selectedCategory = cat
                    renderCategoryChips()
                }
            }
            chipCategoryGroup.addView(chip)
        }

        // Botón "+ Nueva" para crear categorías directamente desde Presupuestos
        val btnAddCat = TextView(this).apply {
            text = "+ Nueva"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(28, 12, 28, 12)
            setBackgroundResource(R.drawable.bg_input_field)
            setTextColor(ContextCompat.getColor(this@BudgetsActivity, R.color.primary))
            setOnClickListener {
                promptAddNewCategory()
            }
        }
        chipCategoryGroup.addView(btnAddCat)

        updateSelectedCategoryDisplay()
    }

    private fun updateSelectedCategoryDisplay() {
        val cal = Calendar.getInstance()
        val currentMonthSpent = userTransactions
            .filter { t ->
                t.type.equals("gasto", ignoreCase = true) &&
                t.category.trim().equals(selectedCategory.trim(), ignoreCase = true) &&
                isSameMonth(t.date, cal)
            }
            .sumOf { it.amount }

        val existingBudget = rawBudgetsList.firstOrNull {
            it.category.trim().equals(selectedCategory.trim(), ignoreCase = true)
        }

        val limitMsg = if (existingBudget != null) {
            " • Límite activo: $${String.format(Locale.US, "%.2f", existingBudget.limitAmount)}"
        } else {
            " • Sin límite configurado"
        }

        tvCategorySelectedInfo.text = "🏷 Categoría: $selectedCategory • Gastado este mes: $${String.format(Locale.US, "%.2f", currentMonthSpent)}$limitMsg"

        if (existingBudget != null && etBudgetAmount.text.isEmpty()) {
            etBudgetAmount.setText(String.format(Locale.US, "%.2f", existingBudget.limitAmount))
        }
    }

    private fun promptAddNewCategory() {
        val input = EditText(this)
        input.hint = "Ej: Libros, Gimnasio, etc."
        AlertDialog.Builder(this)
            .setTitle("Nueva Categoría de Gasto")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val newCat = input.text.toString().trim()
                if (newCat.isNotEmpty()) {
                    FirebaseRepository.addCategory(newCat) { success ->
                        if (success) {
                            selectedCategory = newCat
                            Toast.makeText(this, "Categoría '$newCat' creada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupListeners() {
        btnTabPresupuestos.setOnClickListener { switchToBudgetsTab() }
        btnTabCuotas.setOnClickListener { switchToFeesTab() }

        btnSetBudget.setOnClickListener {
            val amountStr = etBudgetAmount.text.toString().trim().replace("$", "").trim()
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0) {
                Toast.makeText(this, "Ingresa un monto válido para el presupuesto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val budget = Budget(
                category = selectedCategory,
                limitAmount = amount
            )

            FirebaseRepository.addBudget(budget) { success ->
                if (success) {
                    Toast.makeText(this, "Límite guardado para $selectedCategory", Toast.LENGTH_SHORT).show()
                    etBudgetAmount.setText("")
                } else {
                    Toast.makeText(this, "Error al guardar presupuesto en Firebase", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val openAddFeeDialog = { showFeeDialog() }
        btnAddFeeTop.setOnClickListener { openAddFeeDialog() }
        btnAddFirstFee.setOnClickListener { openAddFeeDialog() }

        tvCycleBadge.setOnClickListener {
            val input = EditText(this)
            input.setText(currentCycle)
            AlertDialog.Builder(this)
                .setTitle("Ciclo Académico UDB")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    currentCycle = input.text.toString().trim().ifEmpty { "Ciclo 01" }
                    tvCycleBadge.text = "📝 $currentCycle"
                    val userEmail = FirebaseRepository.userEmail
                    getSharedPreferences("user_academic_prefs", MODE_PRIVATE)
                        .edit().putString("${userEmail}_cycle", currentCycle).apply()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun switchToBudgetsTab() {
        activeSubTab = "budgets"
        btnTabPresupuestos.setBackgroundResource(R.drawable.bg_pill_subtab_active)
        btnTabPresupuestos.setTextColor(0xFFFFFFFF.toInt())
        btnTabCuotas.setBackgroundResource(R.drawable.bg_pill_subtab_inactive)
        btnTabCuotas.setTextColor(0xFF94A3B8.toInt())

        layoutBudgetsView.visibility = View.VISIBLE
        layoutFeesView.visibility = View.GONE
    }

    private fun switchToFeesTab() {
        activeSubTab = "fees"
        btnTabCuotas.setBackgroundResource(R.drawable.bg_pill_subtab_active)
        btnTabCuotas.setTextColor(0xFFFFFFFF.toInt())
        btnTabPresupuestos.setBackgroundResource(R.drawable.bg_pill_subtab_inactive)
        btnTabPresupuestos.setTextColor(0xFF94A3B8.toInt())

        layoutBudgetsView.visibility = View.GONE
        layoutFeesView.visibility = View.VISIBLE
        updateFeeMetrics()
    }

    private fun listenToFirestoreData() {
        // 1. Transacciones en tiempo real
        transactionsListener = FirebaseRepository.listenTransactions { liveTx ->
            userTransactions.clear()
            userTransactions.addAll(liveTx)
            recalculateBudgets()
        }

        // 2. Presupuestos y límites en tiempo real
        budgetsListener = FirebaseRepository.listenBudgets { liveBudgets ->
            rawBudgetsList.clear()
            rawBudgetsList.addAll(liveBudgets)
            recalculateBudgets()
        }

        // 3. Categorías compartidas de Movimientos
        categoriesListener = FirebaseRepository.listenCategories { liveCats ->
            availableCategories.clear()
            availableCategories.addAll(liveCats)
            if (selectedCategory !in availableCategories && availableCategories.isNotEmpty()) {
                selectedCategory = availableCategories.first()
            }
            renderCategoryChips()
        }

        // 4. Cuotas UDB (Ordenadas: pendientes primero, luego por fecha límite)
        feesListener = FirebaseRepository.listenFees { liveFees ->
            udbFeesList.clear()
            udbFeesList.addAll(liveFees.sortedWith(compareBy({ it.isPaid }, { it.deadline })))
            feesAdapter.updateList(udbFeesList)
            updateFeeMetrics()
        }
    }

    private fun recalculateBudgets() {
        val cal = Calendar.getInstance()
        budgetsList.clear()
        for (b in rawBudgetsList) {
            val spent = userTransactions
                .filter { t ->
                    t.type.equals("gasto", ignoreCase = true) &&
                    t.category.trim().equals(b.category.trim(), ignoreCase = true) &&
                    isSameMonth(t.date, cal)
                }
                .sumOf { it.amount }
            budgetsList.add(b.copy(spentAmount = spent))
        }

        tvActiveBudgetsCount.text = "Límites Activos (${budgetsList.size})"

        if (budgetsList.isEmpty()) {
            cardEmptyBudgets.visibility = View.VISIBLE
            rvBudgetsList.visibility = View.GONE
        } else {
            cardEmptyBudgets.visibility = View.GONE
            rvBudgetsList.visibility = View.VISIBLE
            budgetsAdapter.updateList(budgetsList)
        }

        updateSelectedCategoryDisplay()
        AppNotificationHelper.checkBudgetAlerts(this, rawBudgetsList, userTransactions)
    }

    private fun isSameMonth(txDate: String, cal: Calendar): Boolean {
        if (txDate.isEmpty()) return true
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentYear = cal.get(Calendar.YEAR)
        val pattern1 = String.format(Locale.US, "-%02d-%d", currentMonth, currentYear)
        val pattern2 = String.format(Locale.US, "%d-%02d", currentYear, currentMonth)
        return txDate.contains(pattern1) || txDate.contains(pattern2)
    }

    private fun updateFeeMetrics() {
        var paid = 0.0
        var pending = 0.0
        var paidCount = 0
        for (f in udbFeesList) {
            if (f.isPaid) {
                paid += f.amount
                paidCount++
            } else {
                pending += f.amount
            }
        }
        tvFeeTotalPaid.text = String.format(Locale.US, "$%.2f", paid)
        tvFeeTotalPending.text = String.format(Locale.US, "$%.2f", pending)
        tvFeeProgress.text = "Progreso: $paidCount de ${udbFeesList.size} pagados"
        tvFeeCountRegistered.text = "${udbFeesList.size} cuotas registradas"

        if (udbFeesList.isEmpty()) {
            cardEmptyFees.visibility = View.VISIBLE
            rvFeesList.visibility = View.GONE
        } else {
            cardEmptyFees.visibility = View.GONE
            rvFeesList.visibility = View.VISIBLE
        }
    }

    private fun confirmDeleteFee(feeToDelete: UdbFee) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Eliminar Cuota")
            .setMessage("¿Deseas remover permanentemente la cuota '${feeToDelete.title}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                FirebaseRepository.deleteFee(feeToDelete.id) { success ->
                    if (success) {
                        Toast.makeText(this, "Cuota eliminada de Firebase 🗑️", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFeeDialog(feeToEdit: UdbFee? = null) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_fee_form, null)

        val tvDialogTitle = view.findViewById<TextView>(R.id.tvFeeDialogTitle)
        val tvDialogSubtitle = view.findViewById<TextView>(R.id.tvFeeDialogSubtitle)
        val btnClose = view.findViewById<TextView>(R.id.btnCloseFeeDialog)

        val chipFee01 = view.findViewById<TextView>(R.id.chipFee01)
        val chipFee02 = view.findViewById<TextView>(R.id.chipFee02)
        val chipFee03 = view.findViewById<TextView>(R.id.chipFee03)
        val chipFee04 = view.findViewById<TextView>(R.id.chipFee04)
        val chipFee05 = view.findViewById<TextView>(R.id.chipFee05)
        val chipFeeMatricula = view.findViewById<TextView>(R.id.chipFeeMatricula)

        val etTitle = view.findViewById<EditText>(R.id.etFeeTitle)
        val etAmount = view.findViewById<EditText>(R.id.etFeeAmount)
        val btnPickDeadline = view.findViewById<LinearLayout>(R.id.btnPickFeeDeadline)
        val tvDeadlineSelected = view.findViewById<TextView>(R.id.tvFeeDeadlineSelected)
        val cbPaid = view.findViewById<CheckBox>(R.id.cbFeePaid)

        val btnCancel = view.findViewById<Button>(R.id.btnCancelFee)
        val btnSave = view.findViewById<Button>(R.id.btnSaveFee)

        val isEditing = feeToEdit != null
        if (isEditing) {
            tvDialogTitle.text = "✏️ Editar Cuota UDB"
            tvDialogSubtitle.text = "Modifica los datos, fecha límite o estado del arancel"
            btnSave.text = "💾 Actualizar Cuota"
            etTitle.setText(feeToEdit!!.title)
            etAmount.setText(if (feeToEdit.amount > 0) String.format(Locale.US, "%.2f", feeToEdit.amount) else "")
            cbPaid.isChecked = feeToEdit.isPaid
        } else {
            tvDialogTitle.text = "🎓 Anotar Cuota UDB"
            tvDialogSubtitle.text = "Elige la fecha en el calendario y define el arancel"
            btnSave.text = "💾 Guardar Cuota"
            cbPaid.isChecked = false
        }

        // Sugerencias rápidas de aranceles UDB
        val setChipText = { txt: String ->
            etTitle.setText(txt)
            etTitle.setSelection(txt.length)
        }
        chipFee01.setOnClickListener { setChipText("Cuota 01 (Febrero)") }
        chipFee02.setOnClickListener { setChipText("Cuota 02 (Marzo)") }
        chipFee03.setOnClickListener { setChipText("Cuota 03 (Abril)") }
        chipFee04.setOnClickListener { setChipText("Cuota 04 (Mayo)") }
        chipFee05.setOnClickListener { setChipText("Cuota 05 (Junio)") }
        chipFeeMatricula.setOnClickListener { setChipText("Matrícula de Ciclo") }

        // Fecha seleccionada en el calendario
        var selectedDeadline = feeToEdit?.deadline?.ifEmpty { null }
            ?: SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        tvDeadlineSelected.text = selectedDeadline

        btnPickDeadline.setOnClickListener {
            val cal = Calendar.getInstance()
            try {
                val parts = selectedDeadline.split("-")
                if (parts.size == 3) {
                    val d = parts[0].toInt()
                    val m = parts[1].toInt() - 1
                    val y = parts[2].toInt()
                    cal.set(y, m, d)
                }
            } catch (_: Exception) {}

            android.app.DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedDeadline = String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, month + 1, year)
                    tvDeadlineSelected.text = selectedDeadline
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amount = etAmount.text.toString().trim().replace("$", "").toDoubleOrNull() ?: 0.0

            if (title.isEmpty()) {
                Toast.makeText(this, "Ingresa el nombre o concepto de la cuota", Toast.LENGTH_SHORT).show()
                etTitle.requestFocus()
                return@setOnClickListener
            }
            if (amount <= 0) {
                Toast.makeText(this, "Ingresa un monto válido mayor a $0.00", Toast.LENGTH_SHORT).show()
                etAmount.requestFocus()
                return@setOnClickListener
            }

            val fee = UdbFee(
                id = feeToEdit?.id ?: "",
                title = title,
                amount = amount,
                deadline = selectedDeadline,
                isPaid = cbPaid.isChecked
            )

            FirebaseRepository.addFee(fee) { success ->
                if (success) {
                    val msg = if (isEditing) "Cuota actualizada con éxito ✏️" else "Cuota anotada con éxito 🎓"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error al sincronizar con Firebase", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}
