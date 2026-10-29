package sv.edu.udb.finanzasudb.model

data class UserProfile(
    val email: String = "",
    val name: String = "",
    val career: String = "Ingeniería en Ciencias de la Computación",
    val cycle: String = "Ciclo 01",
    val campus: String = "Campus Soyapango"
)
