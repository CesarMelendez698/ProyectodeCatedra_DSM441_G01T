package sv.edu.udb.finanzasudb.model

data class ScheduleItem(
    val id: String = "",
    val subjectName: String = "",
    val code: String = "",
    val classroom: String = "",
    val professor: String = "",
    val dayOfWeek: String = "Lunes", // "Lunes", "Martes", etc.
    val startTime: String = "06:00 PM",
    val endTime: String = "08:00 PM",
    val colorHex: String = "#2563EB",
    val date: String = "", // e.g. "dd-MM-yyyy" for calendar activities
    val activityType: String = "Clase" // "Clase", "Tarea", "Examen", "Laboratorio", "Actividad"
)
