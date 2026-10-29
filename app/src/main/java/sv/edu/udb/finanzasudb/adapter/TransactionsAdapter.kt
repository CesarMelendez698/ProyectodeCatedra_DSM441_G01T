package sv.edu.udb.finanzasudb.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.Transaction
import java.util.Locale

class TransactionsAdapter(
    private var transactions: List<Transaction>,
    private val onEditClick: ((Transaction) -> Unit)? = null,
    private val onDeleteClick: ((Transaction) -> Unit)? = null,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionsAdapter.TransactionViewHolder>() {

    fun updateList(newList: List<Transaction>) {
        transactions = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position], onEditClick, onDeleteClick, onItemClick)
    }

    override fun getItemCount(): Int = transactions.size

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconCircle: FrameLayout = itemView.findViewById(R.id.iconCircle)
        private val tvIconArrow: TextView = itemView.findViewById(R.id.tvIconArrow)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvTxCategory)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvTxDescription)
        private val tvAccountTag: TextView = itemView.findViewById(R.id.tvTxAccountTag)
        private val tvDateTag: TextView = itemView.findViewById(R.id.tvTxDateTag)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvTxAmount)
        private val btnEdit: ImageView = itemView.findViewById(R.id.btnEditTx)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteTx)

        fun bind(
            item: Transaction,
            onEditClick: ((Transaction) -> Unit)?,
            onDeleteClick: ((Transaction) -> Unit)?,
            onItemClick: (Transaction) -> Unit
        ) {
            tvCategory.text = item.category
            tvDesc.text = item.description
            tvAccountTag.text = "💳 ${item.accountName}"
            tvDateTag.text = "•  ${item.date}"

            val isIncome = item.type.equals("ingreso", ignoreCase = true)
            val color = if (isIncome) {
                ContextCompat.getColor(itemView.context, R.color.income_green)
            } else {
                ContextCompat.getColor(itemView.context, R.color.expense_red)
            }
            val sign = if (isIncome) "+" else "-"

            iconCircle.setBackgroundResource(if (isIncome) R.drawable.bg_circle_green else R.drawable.bg_circle_red)
            tvIconArrow.text = if (isIncome) "↓" else "↑"
            tvAmount.setTextColor(color)
            tvAmount.text = String.format(Locale.US, "%s$%.2f", sign, item.amount)

            btnEdit.setOnClickListener { onEditClick?.invoke(item) }
            btnDelete.setOnClickListener { onDeleteClick?.invoke(item) }
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
