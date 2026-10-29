package sv.edu.udb.finanzasudb.utils

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import sv.edu.udb.finanzasudb.R
import sv.edu.udb.finanzasudb.ui.*

enum class NavTab {
    DASHBOARD, TRANSACTIONS, BUDGETS, SCHEDULE, NEWS, PROFILE
}

object NavigationHelper {

    fun setupBottomNav(activity: Activity, activeTab: NavTab) {
        val tabDashboard = activity.findViewById<View>(R.id.tabDashboard) ?: return
        val tabTransactions = activity.findViewById<View>(R.id.tabTransactions) ?: return
        val tabBudgets = activity.findViewById<View>(R.id.tabBudgets) ?: return
        val tabSchedule = activity.findViewById<View>(R.id.tabSchedule) ?: return
        val tabNews = activity.findViewById<View>(R.id.tabNews) ?: return
        val tabProfile = activity.findViewById<View>(R.id.tabProfile) ?: return

        val activeColor = ContextCompat.getColor(activity, R.color.tab_active)
        val inactiveColor = ContextCompat.getColor(activity, R.color.tab_inactive)

        // Resaltar la pestaña actual
        val pairs = listOf(
            Triple(NavTab.DASHBOARD, R.id.ivTabDashboard, R.id.tvTabDashboard),
            Triple(NavTab.TRANSACTIONS, R.id.ivTabTransactions, R.id.tvTabTransactions),
            Triple(NavTab.BUDGETS, R.id.ivTabBudgets, R.id.tvTabBudgets),
            Triple(NavTab.SCHEDULE, R.id.ivTabSchedule, R.id.tvTabSchedule),
            Triple(NavTab.NEWS, R.id.ivTabNews, R.id.tvTabNews),
            Triple(NavTab.PROFILE, R.id.ivTabProfile, R.id.tvTabProfile)
        )

        for ((tab, iconId, textId) in pairs) {
            val iv = activity.findViewById<ImageView>(iconId)
            val tv = activity.findViewById<TextView>(textId)
            if (tab == activeTab) {
                iv?.setColorFilter(activeColor)
                tv?.setTextColor(activeColor)
            } else {
                iv?.setColorFilter(inactiveColor)
                tv?.setTextColor(inactiveColor)
            }
        }

        // Navegación nativa entre AppCompatActivities sin animaciones bruscas
        @Suppress("DEPRECATION")
        fun navigateTo(targetClass: Class<*>, tab: NavTab) {
            if (activeTab == tab) return
            val intent = Intent(activity, targetClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
        }

        tabDashboard.setOnClickListener { navigateTo(DashboardActivity::class.java, NavTab.DASHBOARD) }
        tabTransactions.setOnClickListener { navigateTo(TransactionsActivity::class.java, NavTab.TRANSACTIONS) }
        tabBudgets.setOnClickListener { navigateTo(BudgetsActivity::class.java, NavTab.BUDGETS) }
        tabSchedule.setOnClickListener { navigateTo(ScheduleActivity::class.java, NavTab.SCHEDULE) }
        tabNews.setOnClickListener { navigateTo(NewsActivity::class.java, NavTab.NEWS) }
        tabProfile.setOnClickListener { navigateTo(ProfileActivity::class.java, NavTab.PROFILE) }
    }
}
