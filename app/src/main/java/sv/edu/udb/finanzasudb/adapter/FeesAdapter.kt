package sv.edu.udb.finanzasudb.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.UdbFee
import java.util.Locale

class FeesAdapter(
    private var fees: List<UdbFee>,
    private val onTogglePaid: (UdbFee) -> Unit,
    private val onEditFee: (UdbFee) -> Unit,
    private val onDeleteFee: (UdbFee) -> Unit
) : RecyclerView.Adapter<FeesAdapter.FeeViewHolder>() {

    fun updateList(newList: List<UdbFee>) {
        fees = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fee, parent, false)
        return FeeViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeeViewHolder, position: Int) {
        holder.bind(fees[position], onTogglePaid, onEditFee, onDeleteFee)
    }

    override fun getItemCount(): Int = fees.size

    class FeeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vColorBar: View = itemView.findViewById(R.id.vFeeColorBar)
        private val ivCheck: ImageView = itemView.findViewById(R.id.ivFeeCheck)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvFeeTitle)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvFeeStatus)
        private val tvDeadline: TextView = itemView.findViewById(R.id.tvFeeDeadline)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvFeeAmount)
        private val btnToggle: TextView = itemView.findViewById(R.id.btnToggleFee)
        private val btnEdit: TextView = itemView.findViewById(R.id.btnEditFee)
        private val btnDelete: TextView = itemView.findViewById(R.id.btnDeleteFee)

        fun bind(
            fee: UdbFee,
            onTogglePaid: (UdbFee) -> Unit,
            onEditFee: (UdbFee) -> Unit,
            onDeleteFee: (UdbFee) -> Unit
        ) {
            tvTitle.text = fee.title
            tvDeadline.text = "🗓️ Límite: ${fee.deadline}"
            tvAmount.text = String.format(Locale.US, "$%.2f", fee.amount)

            if (fee.isPaid) {
                vColorBar.setBackgroundColor(0xFF10B981.toInt())
                ivCheck.setImageResource(android.R.drawable.checkbox_on_background)
                ivCheck.setColorFilter(ContextCompat.getColor(itemView.context, R.color.success))
                tvStatus.text = "✅ Pagada"
                tvStatus.setTextColor(0xFF10B981.toInt())
                tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

                btnToggle.text = "↩ Pendiente"
                btnToggle.setTextColor(0xFFF59E0B.toInt())
            } else {
                vColorBar.setBackgroundColor(0xFFF59E0B.toInt())
                ivCheck.setImageResource(android.R.drawable.checkbox_off_background)
                ivCheck.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                tvStatus.text = "⏳ Pendiente"
                tvStatus.setTextColor(0xFFF59E0B.toInt())
                tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

                btnToggle.text = "✓ Marcar Pagada"
                btnToggle.setTextColor(0xFF10B981.toInt())
            }

            btnToggle.setOnClickListener { onTogglePaid(fee) }
            ivCheck.setOnClickListener { onTogglePaid(fee) }
            btnEdit.setOnClickListener { onEditFee(fee) }
            btnDelete.setOnClickListener { onDeleteFee(fee) }
            itemView.setOnClickListener { onEditFee(fee) }
        }
    }
}
