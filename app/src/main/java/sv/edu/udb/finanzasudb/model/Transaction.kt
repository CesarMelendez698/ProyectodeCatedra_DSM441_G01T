package sv.edu.udb.finanzasudb.model

data class Transaction(
    val id: String,
    val description: String,
    val amount: Double,
    val type: String, // "ingreso" o "gasto"
    val category: String,
    val date: String,
    val accountName: String
)
