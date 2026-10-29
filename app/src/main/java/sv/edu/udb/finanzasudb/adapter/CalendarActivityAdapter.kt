package sv.edu.udb.finanzasudb.adapter

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.CalendarActivity

class CalendarActivityAdapter(
    private var activityList: List<CalendarActivity>,
    private val onToggleCompleted: (CalendarActivity) -> Unit,
    private val onEditClick: (CalendarActivity) -> Unit,
    private val onDeleteClick: (CalendarActivity) -> Unit
) : RecyclerView.Adapter<CalendarActivityAdapter.ActivityViewHolder>() {

    fun updateList(newList: List<CalendarActivity>) {
        activityList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_activity, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(activityList[position], onToggleCompleted, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = activityList.size

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vColorBar: View = itemView.findViewById(R.id.vActivityColorBar)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvActivityTitle)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvActivityStatus)
        private val tvLocationAndNotes: TextView = itemView.findViewById(R.id.tvActivityLocationAndNotes)
        private val tvTypeBadge: TextView = itemView.findViewById(R.id.tvActivityTypeBadge)
        private val tvTime: TextView = itemView.findViewById(R.id.tvActivityTime)
        private val tvDate: TextView = itemView.findViewById(R.id.tvActivityDate)
        private val btnToggle: TextView = itemView.findViewById(R.id.btnToggleCompleted)
        private val btnEdit: TextView = itemView.findViewById(R.id.btnEditActivity)
        private val btnDelete: TextView = itemView.findViewById(R.id.btnDeleteActivity)

        fun bind(
            item: CalendarActivity,
            onToggleCompleted: (CalendarActivity) -> Unit,
            onEditClick: (CalendarActivity) -> Unit,
            onDeleteClick: (CalendarActivity) -> Unit
        ) {
            tvTitle.text = item.title

            val icon = when (item.type.lowercase()) {
                "tarea" -> "📝"
                "examen parcial", "examen", "parcial" -> "📋"
                "laboratorio" -> "🔬"
                "clase extra" -> "📘"
                "evento" -> "🎉"
                "entrega" -> "🚀"
                else -> "📌"
            }
            tvTypeBadge.text = "$icon ${item.type}"

            val locStr = if (item.location.isNotEmpty()) item.location else "Campus"
            val notesStr = if (item.notes.isNotEmpty()) item.notes else "Sin notas adicionales"
            tvLocationAndNotes.text = "📍 $locStr • ℹ️ $notesStr"

            tvTime.text = "🕒 ${item.startTime} - ${item.endTime}"
            tvDate.text = "🗓️ ${item.date}"

            if (item.isCompleted) {
                tvStatus.text = "✅ Completada"
                tvStatus.setTextColor(0xFF10B981.toInt())
                btnToggle.text = "↩ Pendiente"
                btnToggle.setTextColor(0xFFF59E0B.toInt())
                tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                tvStatus.text = "⏳ Pendiente"
                tvStatus.setTextColor(0xFFF59E0B.toInt())
                btnToggle.text = "✓ Completar"
                btnToggle.setTextColor(0xFF10B981.toInt())
                tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            val defaultColors = listOf("#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899")
            val chosenColor = try {
                if (item.colorHex.isNotEmpty()) Color.parseColor(item.colorHex)
                else Color.parseColor(defaultColors[Math.abs(item.title.hashCode()) % defaultColors.size])
            } catch (e: Exception) {
                Color.parseColor("#3B82F6")
            }
            vColorBar.setBackgroundColor(chosenColor)

            btnToggle.setOnClickListener { onToggleCompleted(item) }
            btnEdit.setOnClickListener { onEditClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
