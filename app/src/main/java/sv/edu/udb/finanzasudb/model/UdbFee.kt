package sv.edu.udb.finanzasudb.model

data class UdbFee(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val deadline: String = "",
    var isPaid: Boolean = false
) {
    val dueDate: String get() = deadline
}
