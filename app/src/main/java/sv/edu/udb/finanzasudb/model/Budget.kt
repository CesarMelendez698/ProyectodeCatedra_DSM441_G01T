package sv.edu.udb.finanzasudb.model

data class Budget(
    val id: String = "",
    val category: String = "",
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0
) {
    val remaining: Double get() = (limitAmount - spentAmount).coerceAtLeast(0.0)
    val percentage: Int get() = if (limitAmount > 0) ((spentAmount / limitAmount) * 100).toInt().coerceAtMost(100) else 0
}
