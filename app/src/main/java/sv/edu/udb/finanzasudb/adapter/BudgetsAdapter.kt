package sv.edu.udb.finanzasudb.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.Budget
import java.util.Locale

class BudgetsAdapter(
    private var budgets: List<Budget>,
    private val onDeleteClick: (Budget) -> Unit
) : RecyclerView.Adapter<BudgetsAdapter.BudgetViewHolder>() {

    fun updateList(newList: List<Budget>) {
        budgets = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_budget, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        holder.bind(budgets[position], onDeleteClick)
    }

    override fun getItemCount(): Int = budgets.size

    class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategory: TextView = itemView.findViewById(R.id.tvBudgetCategory)
        private val tvValues: TextView = itemView.findViewById(R.id.tvBudgetValues)
        private val pbProgress: ProgressBar = itemView.findViewById(R.id.pbBudgetProgress)
        private val tvRemaining: TextView = itemView.findViewById(R.id.tvRemainingAmount)
        private val tvWarning: TextView = itemView.findViewById(R.id.tvBudgetWarning)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteBudget)

        fun bind(budget: Budget, onDeleteClick: (Budget) -> Unit) {
            tvCategory.text = budget.category
            tvValues.text = String.format(Locale.US, "$%.2f / $%.2f", budget.spentAmount, budget.limitAmount)

            val percentage = if (budget.limitAmount > 0) (budget.spentAmount / budget.limitAmount) * 100 else 0.0
            pbProgress.progress = percentage.toInt().coerceAtMost(100)

            // Colores y alertas exactos de Expo BudgetProgressBar
            val barColor: Int
            if (percentage >= 100) {
                barColor = Color.parseColor("#F44336") // Rojo
                tvWarning.visibility = View.VISIBLE
                tvWarning.text = "🔴 Alerta: Presupuesto Excedido"
                tvWarning.setTextColor(barColor)
            } else if (percentage >= 80) {
                barColor = Color.parseColor("#FF9800") // Naranja
                tvWarning.visibility = View.VISIBLE
                tvWarning.text = "⚠️ Alerta: Has consumido más del 80%"
                tvWarning.setTextColor(barColor)
            } else {
                barColor = Color.parseColor("#4CAF50") // Verde
                tvWarning.visibility = View.GONE
            }

            pbProgress.progressTintList = ColorStateList.valueOf(barColor)

            val rem = budget.limitAmount - budget.spentAmount
            if (rem >= 0) {
                tvRemaining.text = String.format(Locale.US, "Restante: $%.2f", rem)
            } else {
                tvRemaining.text = String.format(Locale.US, "Excedido por: $%.2f", -rem)
            }

            btnDelete.setOnClickListener { onDeleteClick(budget) }
        }
    }
}
