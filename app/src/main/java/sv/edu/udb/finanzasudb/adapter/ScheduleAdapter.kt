package sv.edu.udb.finanzasudb.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.ScheduleItem

class ScheduleAdapter(
    private var scheduleList: List<ScheduleItem>,
    private val onCloneClick: (ScheduleItem) -> Unit,
    private val onEditClick: (ScheduleItem) -> Unit,
    private val onDeleteClick: (ScheduleItem) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    fun updateList(newList: List<ScheduleItem>) {
        scheduleList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(scheduleList[position], onCloneClick, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = scheduleList.size

    class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vColorBar: View = itemView.findViewById(R.id.vColorBar)
        private val tvSubjectHeader: TextView = itemView.findViewById(R.id.tvSubjectHeader)
        private val tvClassroomAndDocente: TextView = itemView.findViewById(R.id.tvClassroomAndDocente)
        private val tvScheduleTime: TextView = itemView.findViewById(R.id.tvScheduleTime)
        private val btnClone: TextView = itemView.findViewById(R.id.btnCopySchedule)
        private val btnEdit: TextView = itemView.findViewById(R.id.btnEditSchedule)
        private val btnDelete: TextView = itemView.findViewById(R.id.btnDeleteSchedule)

        fun bind(
            item: ScheduleItem,
            onCloneClick: (ScheduleItem) -> Unit,
            onEditClick: (ScheduleItem) -> Unit,
            onDeleteClick: (ScheduleItem) -> Unit
        ) {
            val codePrefix = if (item.code.isNotEmpty()) "${item.code} - " else ""
            tvSubjectHeader.text = "$codePrefix${item.subjectName}"

            val roomStr = if (item.classroom.isNotEmpty()) item.classroom else "Campus Virtual"
            val profStr = if (item.professor.isNotEmpty()) item.professor else "Docente Titular"
            tvClassroomAndDocente.text = "Aula: $roomStr • Docente: $profStr"

            tvScheduleTime.text = "🕒 ${item.startTime} - ${item.endTime}"

            val badgeContainer = itemView.findViewById<View?>(R.id.badgeActivityType)
            val tvTypeBadge = itemView.findViewById<TextView?>(R.id.tvScheduleTypeBadge)

            when {
                item.activityType.equals("Laboratorio", ignoreCase = true) -> {
                    badgeContainer?.visibility = View.VISIBLE
                    tvTypeBadge?.text = "🔬 Laboratorio"
                    tvTypeBadge?.setTextColor(0xFF10B981.toInt())
                }
                item.activityType.equals("Teoría", ignoreCase = true) || item.activityType.equals("Teoria", ignoreCase = true) -> {
                    badgeContainer?.visibility = View.VISIBLE
                    tvTypeBadge?.text = "📘 Teoría"
                    tvTypeBadge?.setTextColor(0xFF38BDF8.toInt())
                }
                item.activityType.isNotEmpty() && !item.activityType.equals("Clase", ignoreCase = true) -> {
                    badgeContainer?.visibility = View.VISIBLE
                    tvTypeBadge?.text = "📌 ${item.activityType}"
                    tvTypeBadge?.setTextColor(0xFFF59E0B.toInt())
                }
                else -> {
                    badgeContainer?.visibility = View.GONE
                }
            }

            val colors = listOf("#EC4899", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B")
            val chosenColor = try {
                if (item.colorHex.isNotEmpty()) Color.parseColor(item.colorHex)
                else Color.parseColor(colors[Math.abs(item.subjectName.hashCode()) % colors.size])
            } catch (e: Exception) {
                Color.parseColor("#EC4899")
            }
            vColorBar.setBackgroundColor(chosenColor)

            btnClone.text = "📋 Clonar"
            btnClone.setOnClickListener { onCloneClick(item) }
            btnEdit.setOnClickListener { onEditClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
