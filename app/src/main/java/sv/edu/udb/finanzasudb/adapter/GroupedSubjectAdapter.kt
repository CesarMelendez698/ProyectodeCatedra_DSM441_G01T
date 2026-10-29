package sv.edu.udb.finanzasudb.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.model.GroupedSubject

class GroupedSubjectAdapter(
    private var subjectList: List<GroupedSubject>,
    private val onCloneClick: (GroupedSubject) -> Unit,
    private val onEditClick: (GroupedSubject) -> Unit,
    private val onDeleteClick: (GroupedSubject) -> Unit
) : RecyclerView.Adapter<GroupedSubjectAdapter.GroupedViewHolder>() {

    fun updateList(newList: List<GroupedSubject>) {
        subjectList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grouped_subject, parent, false)
        return GroupedViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupedViewHolder, position: Int) {
        holder.bind(subjectList[position], onCloneClick, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = subjectList.size

    class GroupedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vColorBar: View = itemView.findViewById(R.id.vGroupedColorBar)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvGroupedSubjectTitle)
        private val tvProfessor: TextView = itemView.findViewById(R.id.tvGroupedProfessor)
        private val tvSessionsBadge: TextView = itemView.findViewById(R.id.tvGroupedSessionsCountBadge)
        private val tvSessionsDetails: TextView = itemView.findViewById(R.id.tvGroupedSessionsDetails)
        private val btnClone: TextView = itemView.findViewById(R.id.btnGroupedCloneDay)
        private val btnEdit: TextView = itemView.findViewById(R.id.btnGroupedEdit)
        private val btnDelete: TextView = itemView.findViewById(R.id.btnGroupedDelete)

        fun bind(
            item: GroupedSubject,
            onCloneClick: (GroupedSubject) -> Unit,
            onEditClick: (GroupedSubject) -> Unit,
            onDeleteClick: (GroupedSubject) -> Unit
        ) {
            val codePrefix = if (item.code.isNotEmpty()) "${item.code} - " else ""
            tvTitle.text = "$codePrefix${item.subjectName}"

            val profStr = if (item.professor.isNotEmpty()) item.professor else "Docente Titular"
            tvProfessor.text = "Docente: $profStr"

            // Analizar sesiones (teoría, laboratorio, etc.)
            val hasLab = item.sessions.any { it.activityType.equals("Laboratorio", ignoreCase = true) }
            val hasTheory = item.sessions.any {
                it.activityType.equals("Teoría", ignoreCase = true) ||
                    it.activityType.equals("Teoria", ignoreCase = true) ||
                    it.activityType.equals("Clase", ignoreCase = true)
            }

            val badgeText = when {
                item.sessions.size > 1 && hasLab && hasTheory -> "🗓️ ${item.sessions.size} sesiones (Teoría + Laboratorio)"
                item.sessions.size > 1 -> "🗓️ ${item.sessions.size} sesiones semanales"
                hasLab -> "🔬 1 sesión semanal (Laboratorio)"
                else -> "📘 1 sesión semanal (Teoría)"
            }
            tvSessionsBadge.text = badgeText

            // Formatear detalles de cada día
            val detailsSb = StringBuilder()
            item.sessions.forEachIndexed { index, session ->
                val typeTag = when {
                    session.activityType.equals("Laboratorio", ignoreCase = true) -> "🔬 Lab"
                    session.activityType.equals("Teoría", ignoreCase = true) || session.activityType.equals("Teoria", ignoreCase = true) -> "📘 Teoría"
                    else -> "📚 Clase"
                }
                val room = if (session.classroom.isNotEmpty()) session.classroom else "Campus Virtual"
                detailsSb.append("• ${session.dayOfWeek}: ${session.startTime} - ${session.endTime} ($typeTag • $room)")
                if (index < item.sessions.size - 1) {
                    detailsSb.append("\n")
                }
            }
            tvSessionsDetails.text = detailsSb.toString()

            val colors = listOf("#EC4899", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B")
            val chosenColor = try {
                if (item.colorHex.isNotEmpty()) Color.parseColor(item.colorHex)
                else Color.parseColor(colors[Math.abs(item.subjectName.hashCode()) % colors.size])
            } catch (e: Exception) {
                Color.parseColor("#3B82F6")
            }
            vColorBar.setBackgroundColor(chosenColor)

            btnClone.setOnClickListener { onCloneClick(item) }
            btnEdit.setOnClickListener { onEditClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
