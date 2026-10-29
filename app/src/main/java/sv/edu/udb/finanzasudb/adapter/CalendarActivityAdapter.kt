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
        }
    }
}
