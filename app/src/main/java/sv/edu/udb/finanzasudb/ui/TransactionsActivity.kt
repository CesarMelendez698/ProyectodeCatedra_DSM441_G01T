package sv.edu.udb.finanzasudb.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.adapter.TransactionsAdapter
import sv.edu.udb.finanzasudb.firebase.FirebaseRepository
import sv.edu.udb.finanzasudb.model.Budget
import sv.edu.udb.finanzasudb.model.Transaction
import sv.edu.udb.finanzasudb.utils.AppNotificationHelper
import sv.edu.udb.finanzasudb.utils.NavTab
import sv.edu.udb.finanzasudb.utils.NavigationHelper
import sv.edu.udb.finanzasudb.utils.ThemeHelper
import java.text.SimpleDateFormat
import java.util.*

class TransactionsActivity : AppCompatActivity() {

    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvFilterIncome: TextView
    private lateinit var tvFilterExpense: TextView
    private lateinit var tvFilterCount: TextView
    private lateinit var tvBadgeTotalRegistros: TextView
    private lateinit var btnManageCatalogs: Button
    private lateinit var btnNewTransactionPill: Button

    private lateinit var etSearch: EditText
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterExpense: Button
    private lateinit var btnFilterIncome: Button

    private lateinit var btnPeriodAll: Button
    private lateinit var btnPeriodToday: Button
    private lateinit var btnPeriodMonth: Button
    private lateinit var btnPickFilterDate: Button

    private lateinit var containerAccountFilters: LinearLayout
    private lateinit var containerCategoryFilters: LinearLayout

    private lateinit var adapter: TransactionsAdapter

    private var activeFilterType = "all" // all, expense, income
    private var activeFilterPeriod = "all" // all, today, month, custom
    private var customFilterDate = ""
    private var activeFilterAccount = "all"
    private var activeFilterCategory = "all"
    private var searchQuery = ""

    private val allTransactions = mutableListOf<Transaction>()
    private val availableAccounts = mutableListOf<String>()
    private val availableCategories = mutableListOf<String>()
    private val availableBudgets = mutableListOf<Budget>()

    private var transactionsListener: ListenerRegistration? = null
    private var accountsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var budgetsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyThemeToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        AppNotificationHelper.initChannels(this)
        AppNotificationHelper.requestPermissionIfNecessary(this)

        initViews()
        setupTypeFilters()
        setupPeriodFilters()
        setupListeners()
        listenToFirebase()
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.ensureTheme(this)) return
        NavigationHelper.setupBottomNav(this, NavTab.TRANSACTIONS)
    }

    override fun onDestroy() {
        super.onDestroy()
        transactionsListener?.remove()
        accountsListener?.remove()
        categoriesListener?.remove()
        budgetsListener?.remove()
    }

    private fun initViews() {
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        tvFilterIncome = findViewById(R.id.tvFilterIncome)
        tvFilterExpense = findViewById(R.id.tvFilterExpense)
        tvFilterCount = findViewById(R.id.tvFilterCount)
        tvBadgeTotalRegistros = findViewById(R.id.tvBadgeTotalRegistros)
        btnManageCatalogs = findViewById(R.id.btnManageCatalogs)
        btnNewTransactionPill = findViewById(R.id.btnNewTransactionPill)

        etSearch = findViewById(R.id.etTxSearch)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterExpense = findViewById(R.id.btnFilterExpense)
        btnFilterIncome = findViewById(R.id.btnFilterIncome)

        btnPeriodAll = findViewById(R.id.btnPeriodAll)
        btnPeriodToday = findViewById(R.id.btnPeriodToday)
        btnPeriodMonth = findViewById(R.id.btnPeriodMonth)
        btnPickFilterDate = findViewById(R.id.btnPickFilterDate)

        containerAccountFilters = findViewById(R.id.containerAccountFilters)
        containerCategoryFilters = findViewById(R.id.containerCategoryFilters)
        rvTransactions = findViewById(R.id.rvTransactions)

        adapter = TransactionsAdapter(
            transactions = emptyList(),
            onEditClick = { transaction ->
                showAddTransactionDialog(transaction)
            },
            onDeleteClick = { transaction ->
                confirmDeleteTransaction(transaction)
            },
            onItemClick = { transaction ->
                showDigitalTicket(transaction)
            }
        )
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter
    }

    private fun listenToFirebase() {
        // 1. Escuchar Transacciones en tiempo real
        transactionsListener = FirebaseRepository.listenTransactions { liveList ->
            allTransactions.clear()
            allTransactions.addAll(liveList)
            applyFilter()
            AppNotificationHelper.checkBudgetAlerts(this, availableBudgets, allTransactions)
        }

        // 2. Escuchar Presupuestos en tiempo real para verificar alertas de límites
        budgetsListener = FirebaseRepository.listenBudgets { liveBudgets ->
            availableBudgets.clear()
            availableBudgets.addAll(liveBudgets)
            AppNotificationHelper.checkBudgetAlerts(this, availableBudgets, allTransactions)
        }

        // 3. Escuchar Cuentas
        accountsListener = FirebaseRepository.listenAccounts { liveAccounts ->
            availableAccounts.clear()
            availableAccounts.addAll(liveAccounts)
            renderAccountPills()
            applyFilter()
        }

        // 4. Escuchar Categorías
        categoriesListener = FirebaseRepository.listenCategories { liveCategories ->
            availableCategories.clear()
            availableCategories.addAll(liveCategories)
            renderCategoryPills()
            applyFilter()
        }
    }

    private fun setupTypeFilters() {
        btnFilterAll.setOnClickListener {
            activeFilterType = "all"
            updateTypeFilterButtons()
            applyFilter()
        }
        btnFilterExpense.setOnClickListener {
            activeFilterType = "expense"
            updateTypeFilterButtons()
            applyFilter()
        }
        btnFilterIncome.setOnClickListener {
            activeFilterType = "income"
            updateTypeFilterButtons()
            applyFilter()
        }

        etSearch.addTextChangedListener { text ->
            searchQuery = text.toString().trim().lowercase()
            applyFilter()
        }
    }

    private fun updateTypeFilterButtons() {
        btnFilterAll.setBackgroundResource(if (activeFilterType == "all") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnFilterAll.setTextColor(if (activeFilterType == "all") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))

        btnFilterExpense.setBackgroundResource(if (activeFilterType == "expense") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnFilterExpense.setTextColor(if (activeFilterType == "expense") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))

        btnFilterIncome.setBackgroundResource(if (activeFilterType == "income") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnFilterIncome.setTextColor(if (activeFilterType == "income") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun setupPeriodFilters() {
        btnPeriodAll.setOnClickListener {
            activeFilterPeriod = "all"
            updatePeriodButtons()
            applyFilter()
        }
        btnPeriodToday.setOnClickListener {
            activeFilterPeriod = "today"
            updatePeriodButtons()
            applyFilter()
        }
        btnPeriodMonth.setOnClickListener {
            activeFilterPeriod = "month"
            updatePeriodButtons()
            applyFilter()
        }
        btnPickFilterDate.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
                customFilterDate = String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, month + 1, year)
                activeFilterPeriod = "custom"
                btnPickFilterDate.text = "$customFilterDate 📅"
                updatePeriodButtons()
                applyFilter()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun updatePeriodButtons() {
        btnPeriodAll.setBackgroundResource(if (activeFilterPeriod == "all") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnPeriodAll.setTextColor(if (activeFilterPeriod == "all") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))

        btnPeriodToday.setBackgroundResource(if (activeFilterPeriod == "today") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnPeriodToday.setTextColor(if (activeFilterPeriod == "today") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))

        btnPeriodMonth.setBackgroundResource(if (activeFilterPeriod == "month") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnPeriodMonth.setTextColor(if (activeFilterPeriod == "month") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))

        btnPickFilterDate.setBackgroundResource(if (activeFilterPeriod == "custom") R.drawable.bg_primary_button else R.drawable.bg_input_field)
        btnPickFilterDate.setTextColor(if (activeFilterPeriod == "custom") 0xFFFFFFFF.toInt() else ContextCompat.getColor(this, R.color.text_secondary))
        if (activeFilterPeriod != "custom") {
            btnPickFilterDate.text = "Por Fecha 📅"
        }
    }

    private fun renderAccountPills() {
        containerAccountFilters.removeAllViews()
        val allOptions = listOf("all") + availableAccounts
        val btnHeight = (32 * resources.displayMetrics.density).toInt()
        val padH = (12 * resources.displayMetrics.density).toInt()

        for (acc in allOptions) {
            val label = if (acc == "all") "💳 Todas las Cuentas" else "💳 $acc"
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                minHeight = 0
                minWidth = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    btnHeight
                ).apply {
                    marginEnd = 6
                }
                setPadding(padH, 0, padH, 0)

                val isSelected = activeFilterAccount.equals(acc, ignoreCase = true)
                setBackgroundResource(if (isSelected) R.drawable.bg_primary_button else R.drawable.bg_input_field)
                setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else ContextCompat.getColor(this@TransactionsActivity, R.color.text_secondary))

                setOnClickListener {
                    activeFilterAccount = acc
                    renderAccountPills()
                    applyFilter()
                }
            }
            containerAccountFilters.addView(btn)
        }
    }

    private fun renderCategoryPills() {
        containerCategoryFilters.removeAllViews()
        val allOptions = listOf("all") + availableCategories
        val btnHeight = (32 * resources.displayMetrics.density).toInt()
        val padH = (12 * resources.displayMetrics.density).toInt()

        for (cat in allOptions) {
            val label = if (cat == "all") "🏷 Todas las Categorías" else "🏷 $cat"
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                minHeight = 0
                minWidth = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    btnHeight
                ).apply {
                    marginEnd = 6
                }
                setPadding(padH, 0, padH, 0)

                val isSelected = activeFilterCategory.equals(cat, ignoreCase = true)
                setBackgroundResource(if (isSelected) R.drawable.bg_primary_button else R.drawable.bg_input_field)
                setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else ContextCompat.getColor(this@TransactionsActivity, R.color.text_secondary))

                setOnClickListener {
                    activeFilterCategory = cat
                    renderCategoryPills()
                    applyFilter()
                }
            }
            containerCategoryFilters.addView(btn)
        }
    }

    private fun applyFilter() {
        val todayStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val monthStr = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())

        val filtered = allTransactions.filter { item ->
            val matchesType = when (activeFilterType) {
                "income" -> item.type.equals("ingreso", ignoreCase = true)
                "expense" -> item.type.equals("gasto", ignoreCase = true)
                else -> true
            }

            val matchesAccount = activeFilterAccount == "all" ||
                item.accountName.equals(activeFilterAccount, ignoreCase = true)

            val matchesCategory = activeFilterCategory == "all" ||
                item.category.equals(activeFilterCategory, ignoreCase = true)

            val matchesPeriod = when (activeFilterPeriod) {
                "today" -> item.date == todayStr
                "month" -> item.date.contains(monthStr)
                "custom" -> item.date == customFilterDate
                else -> true
            }

            val matchesSearch = searchQuery.isEmpty() ||
                item.description.lowercase().contains(searchQuery) ||
                item.category.lowercase().contains(searchQuery) ||
                item.accountName.lowercase().contains(searchQuery) ||
                String.format(Locale.US, "%.2f", item.amount).contains(searchQuery)

            matchesType && matchesAccount && matchesCategory && matchesPeriod && matchesSearch
        }

        adapter.updateList(filtered)
        calculateBalanceFromFiltered(filtered)
    }

    private fun calculateBalanceFromFiltered(filtered: List<Transaction>) {
        var income = 0.0
        var expense = 0.0

        for (t in filtered) {
            if (t.type.equals("ingreso", ignoreCase = true)) {
                income += t.amount
            } else {
                expense += t.amount
            }
        }

        val net = income - expense
        val prefix = if (net >= 0) "+" else "-"
        tvTotalBalance.text = String.format(Locale.US, "%s$%.2f", prefix, Math.abs(net))
        tvTotalBalance.setTextColor(
            if (net >= 0) ContextCompat.getColor(this, R.color.success)
            else ContextCompat.getColor(this, R.color.danger)
        )

        tvFilterIncome.text = String.format(Locale.US, "+$%.2f", income)
        tvFilterExpense.text = String.format(Locale.US, "-$%.2f", expense)
        tvFilterCount.text = "${filtered.size}"
        tvBadgeTotalRegistros.text = "${filtered.size} registro${if (filtered.size != 1) "s" else ""}"
    }

    private fun setupListeners() {
        btnNewTransactionPill.setOnClickListener {
            showAddTransactionDialog()
        }

        btnManageCatalogs.setOnClickListener {
            showManageCatalogsDialog()
        }
    }

    private fun confirmDeleteTransaction(item: Transaction) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Deseas borrar permanentemente este movimiento de Firebase?")
            .setPositiveButton("Eliminar") { _, _ ->
                FirebaseRepository.deleteTransaction(item.id) { success ->
                    if (success) {
                        Toast.makeText(this, "Transacción eliminada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Modal para Administrar Cuentas y Categorías
    private fun showManageCatalogsDialog() {
        val options = arrayOf("🏦 Administrar Cuentas", "🏷 Administrar Categorías")
        AlertDialog.Builder(this)
            .setTitle("Catálogos de Firebase")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showAccountsManager()
                } else {
                    showCategoriesManager()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showAccountsManager() {
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
        }

        val btnAdd = Button(this).apply {
            text = "+ Agregar Nueva Cuenta"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.bg_primary_button)
            setOnClickListener {
                promptAddNewAccount()
            }
        }
        listLayout.addView(btnAdd)

        val tvTitle = TextView(this).apply {
            text = "Cuentas registradas:"
            textSize = 14f
            setPadding(0, 20, 0, 10)
            setTextColor(ContextCompat.getColor(this@TransactionsActivity, R.color.text_primary))
        }
        listLayout.addView(tvTitle)

        for (acc in availableAccounts) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val tvName = TextView(this).apply {
                text = "• $acc"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(this@TransactionsActivity, R.color.text_primary))
            }
            row.addView(tvName)

            if (!FirebaseRepository.defaultAccounts.contains(acc)) {
                val btnDel = Button(this).apply {
                    text = "Eliminar"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@TransactionsActivity, R.color.danger))
                    setBackgroundResource(R.drawable.bg_input_field)
                    setOnClickListener {
                        FirebaseRepository.deleteAccount(acc) {
                            Toast.makeText(this@TransactionsActivity, "Cuenta '$acc' eliminada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                row.addView(btnDel)
            }
            listLayout.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle("Cuentas de Usuario")
            .setView(listLayout)
            .setPositiveButton("Listo", null)
            .show()
    }

    private fun showCategoriesManager() {
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
        }

        val btnAdd = Button(this).apply {
            text = "+ Agregar Nueva Categoría"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.bg_primary_button)
            setOnClickListener {
                promptAddNewCategory()
            }
        }
        listLayout.addView(btnAdd)

        val tvTitle = TextView(this).apply {
            text = "Categorías registradas:"
            textSize = 14f
            setPadding(0, 20, 0, 10)
            setTextColor(ContextCompat.getColor(this@TransactionsActivity, R.color.text_primary))
        }
        listLayout.addView(tvTitle)

        for (cat in availableCategories) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val tvName = TextView(this).apply {
                text = "• $cat"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ContextCompat.getColor(this@TransactionsActivity, R.color.text_primary))
            }
            row.addView(tvName)

            if (!FirebaseRepository.defaultCategories.contains(cat)) {
                val btnDel = Button(this).apply {
                    text = "Eliminar"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@TransactionsActivity, R.color.danger))
                    setBackgroundResource(R.drawable.bg_input_field)
                    setOnClickListener {
                        FirebaseRepository.deleteCategory(cat) {
                            Toast.makeText(this@TransactionsActivity, "Categoría '$cat' eliminada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                row.addView(btnDel)
            }
            listLayout.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle("Categorías de Usuario")
            .setView(listLayout)
            .setPositiveButton("Listo", null)
            .show()
    }

    private fun promptAddNewAccount(onCreated: ((String) -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "Nombre de la cuenta (ej. Ahorros UDB)"
            setPadding(30, 30, 30, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Nueva Cuenta")
            .setMessage("Se guardará en tu cuenta de Firebase:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    FirebaseRepository.addAccount(name) { success ->
                        if (success) {
                            Toast.makeText(this, "Cuenta '$name' guardada", Toast.LENGTH_SHORT).show()
                            onCreated?.invoke(name)
                        } else {
                            Toast.makeText(this, "Error al guardar cuenta", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptAddNewCategory(onCreated: ((String) -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "Nombre de la categoría (ej. Gimnasio)"
            setPadding(30, 30, 30, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Nueva Categoría")
            .setMessage("Se guardará en tu cuenta de Firebase:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    FirebaseRepository.addCategory(name) { success ->
                        if (success) {
                            Toast.makeText(this, "Categoría '$name' guardada", Toast.LENGTH_SHORT).show()
                            onCreated?.invoke(name)
                        } else {
                            Toast.makeText(this, "Error al guardar categoría", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Modal de Creación / Edición de Transacción (Screenshot 3)
    private fun showAddTransactionDialog(existingItem: Transaction? = null) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null)

        val rbGasto = view.findViewById<RadioButton>(R.id.rbGasto)
        val rbIngreso = view.findViewById<RadioButton>(R.id.rbIngreso)
        val etAmount = view.findViewById<EditText>(R.id.etTxAmount)
        val etDesc = view.findViewById<EditText>(R.id.etTxDescription)
        val spCategory = view.findViewById<Spinner>(R.id.spinnerTxCategory)
        val spAccount = view.findViewById<Spinner>(R.id.spinnerTxAccount)
        val btnAddCatInline = view.findViewById<TextView>(R.id.btnAddCategoryInline)
        val btnAddAccInline = view.findViewById<TextView>(R.id.btnAddAccountInline)
        val btnDate = view.findViewById<Button>(R.id.btnTxDate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelTx)
        val btnSave = view.findViewById<Button>(R.id.btnSaveTx)

        val tvCategoryBudgetHint = view.findViewById<TextView>(R.id.tvCategoryBudgetHint)

        val currentCats = availableCategories.ifEmpty { FirebaseRepository.defaultCategories }.toMutableList()
        val currentAccs = availableAccounts.ifEmpty { FirebaseRepository.defaultAccounts }.toMutableList()

        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currentCats)
        val accAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currentAccs)
        spCategory.adapter = catAdapter
        spAccount.adapter = accAdapter

        fun updateBudgetHint(position: Int) {
            if (position in currentCats.indices) {
                val cat = currentCats[position]
                val b = availableBudgets.firstOrNull { it.category.trim().equals(cat.trim(), true) }
                if (b != null) {
                    val cal = Calendar.getInstance()
                    val spent = allTransactions
                        .filter { it.type.equals("gasto", true) && it.category.trim().equals(cat.trim(), true) && isSameMonth(it.date, cal) }
                        .sumOf { it.amount }
                    val rem = b.limitAmount - spent
                    tvCategoryBudgetHint.text = String.format(Locale.US, "💡 Límite '%s': $%.2f • Gastado: $%.2f (Restante: $%.2f)", cat, b.limitAmount, spent, rem)
                    tvCategoryBudgetHint.setTextColor(
                        if (rem >= 0) ContextCompat.getColor(this, R.color.primary)
                        else ContextCompat.getColor(this, R.color.danger)
                    )
                } else {
                    tvCategoryBudgetHint.text = "💡 Sin límite de gasto asignado para '$cat'"
                    tvCategoryBudgetHint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
            }
        }

        spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                updateBudgetHint(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (existingItem != null) {
            etAmount.setText(existingItem.amount.toString())
            etDesc.setText(existingItem.description)
            if (existingItem.type.equals("ingreso", true)) {
                rbIngreso.isChecked = true
                rbIngreso.setBackgroundResource(R.drawable.bg_success_button)
                rbIngreso.setTextColor(0xFFFFFFFF.toInt())
                rbGasto.setBackgroundResource(0)
                rbGasto.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
            } else {
                rbGasto.isChecked = true
                rbGasto.setBackgroundResource(R.drawable.bg_danger_button)
                rbGasto.setTextColor(0xFFFFFFFF.toInt())
                rbIngreso.setBackgroundResource(0)
                rbIngreso.setTextColor(ContextCompat.getColor(this, R.color.income_green))
            }
            val catIdx = currentCats.indexOf(existingItem.category)
            if (catIdx >= 0) {
                spCategory.setSelection(catIdx)
                updateBudgetHint(catIdx)
            }

            val accIdx = currentAccs.indexOf(existingItem.accountName)
            if (accIdx >= 0) spAccount.setSelection(accIdx)
        } else {
            updateBudgetHint(spCategory.selectedItemPosition)
        }

        rbGasto.setOnClickListener {
            rbGasto.setBackgroundResource(R.drawable.bg_danger_button)
            rbGasto.setTextColor(0xFFFFFFFF.toInt())
            rbIngreso.setBackgroundResource(0)
            rbIngreso.setTextColor(ContextCompat.getColor(this, R.color.income_green))
        }
        rbIngreso.setOnClickListener {
            rbIngreso.setBackgroundResource(R.drawable.bg_success_button)
            rbIngreso.setTextColor(0xFFFFFFFF.toInt())
            rbGasto.setBackgroundResource(0)
            rbGasto.setTextColor(ContextCompat.getColor(this, R.color.expense_red))
        }

        btnAddCatInline.setOnClickListener {
            promptAddNewCategory { newCat ->
                if (!currentCats.contains(newCat)) {
                    currentCats.add(newCat)
                    catAdapter.notifyDataSetChanged()
                }
                spCategory.setSelection(currentCats.indexOf(newCat))
            }
        }

        btnAddAccInline.setOnClickListener {
            promptAddNewAccount { newAcc ->
                if (!currentAccs.contains(newAcc)) {
                    currentAccs.add(newAcc)
                    accAdapter.notifyDataSetChanged()
                }
                spAccount.setSelection(currentAccs.indexOf(newAcc))
            }
        }

        var selectedDateStr = existingItem?.date ?: SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        btnDate.text = "$selectedDateStr 📅"
        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDateStr = String.format(Locale.US, "%02d-%02d-%d", dayOfMonth, month + 1, year)
                btnDate.text = "$selectedDateStr 📅"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val btnTime = view.findViewById<Button>(R.id.btnTxTime)
        var selectedTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        btnTime?.text = "$selectedTimeStr 🕒"
        btnTime?.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.TimePickerDialog(this, { _, hourOfDay, minute ->
                val calTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calTime.time)
                btnTime.text = "$selectedTimeStr 🕒"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }

        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<View>(R.id.btnCloseDialogCross)?.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (amountStr.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this, "Por favor llena monto y descripción", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val type = if (rbGasto.isChecked) "gasto" else "ingreso"
            val category = if (currentCats.isNotEmpty() && spCategory.selectedItemPosition in currentCats.indices) {
                currentCats[spCategory.selectedItemPosition]
            } else "Varios"

            val account = if (currentAccs.isNotEmpty() && spAccount.selectedItemPosition in currentAccs.indices) {
                currentAccs[spAccount.selectedItemPosition]
            } else "Efectivo"

            val newTx = Transaction(
                id = existingItem?.id ?: "",
                description = desc,
                amount = amount,
                type = type,
                category = category,
                date = selectedDateStr,
                accountName = account
            )

            if (existingItem != null) {
                FirebaseRepository.addTransaction(newTx) {
                    Toast.makeText(this, "Transacción actualizada", Toast.LENGTH_SHORT).show()
                }
            } else {
                FirebaseRepository.addTransaction(newTx) { success ->
                    if (success) {
                        Toast.makeText(this, "Transacción guardada (sincronizada en Firebase)", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDigitalTicket(item: Transaction) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_ticket, null)

        val tvDesc = view.findViewById<TextView>(R.id.tvTicketDescription)
        val tvAmount = view.findViewById<TextView>(R.id.tvTicketAmount)
        val tvType = view.findViewById<TextView>(R.id.tvTicketType)
        val tvCategory = view.findViewById<TextView>(R.id.tvTicketCategory)
        val tvAccount = view.findViewById<TextView>(R.id.tvTicketAccount)
        val tvDate = view.findViewById<TextView>(R.id.tvTicketDate)
        val btnShare = view.findViewById<Button>(R.id.btnShareTicket)
        val btnDelete = view.findViewById<Button>(R.id.btnDeleteTicket)
        val btnClose = view.findViewById<Button>(R.id.btnCloseTicket)

        tvDesc.text = item.description
        val isIncome = item.type.equals("ingreso", ignoreCase = true)
        tvAmount.text = String.format(Locale.US, "%s$%.2f", if (isIncome) "+" else "-", item.amount)
        tvAmount.setTextColor(if (isIncome) ContextCompat.getColor(this, R.color.income_green) else ContextCompat.getColor(this, R.color.expense_red))
        tvType.text = if (isIncome) "INGRESO REGISTRADO" else "GASTO REGISTRADO"
        tvCategory.text = item.category
        tvAccount.text = item.accountName
        tvDate.text = item.date

        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            confirmDeleteTransaction(item)
            dialog.dismiss()
        }

        btnShare.setOnClickListener {
            val shareText = """
                🧾 COMPROBANTE DIGITAL UDB
                ━━━━━━━━━━━━━━━━━━━━━
                Concepto: ${item.description}
                Monto: ${if (isIncome) "+" else "-"}$${String.format(Locale.US, "%.2f", item.amount)}
                Tipo: ${if (isIncome) "Ingreso" else "Gasto"}
                Categoría: ${item.category}
                Cuenta: ${item.accountName}
                Fecha: ${item.date}
                ━━━━━━━━━━━━━━━━━━━━━
                Gestionado con Finanzas UDB 📱
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir Comprobante"))
        }

        dialog.show()
    }

    private fun isSameMonth(txDate: String, cal: Calendar): Boolean {
        if (txDate.isEmpty()) return true
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentYear = cal.get(Calendar.YEAR)
        val pattern1 = String.format(Locale.US, "-%02d-%d", currentMonth, currentYear)
        val pattern2 = String.format(Locale.US, "%d-%02d", currentYear, currentMonth)
        return txDate.contains(pattern1) || txDate.contains(pattern2)
    }
}
