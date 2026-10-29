package sv.edu.udb.finanzasudb.model

data class CalendarActivity(
    val id: String = "",
    val title: String = "",
    val type: String = "Tarea", // "Tarea", "Examen Parcial", "Laboratorio", "Clase Extra", "Actividad", "Evento", "Entrega"
    val date: String = "", // "dd-MM-yyyy"
    val startTime: String = "08:00 AM",
    val endTime: String = "10:00 AM",
    val location: String = "",
    val notes: String = "",
    val colorHex: String = "#3B82F6",
    val isCompleted: Boolean = false
)
