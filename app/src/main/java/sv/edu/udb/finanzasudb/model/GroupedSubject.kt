package sv.edu.udb.finanzasudb.model

data class GroupedSubject(
    val subjectKey: String,
    val subjectName: String,
    val code: String,
    val professor: String,
    val colorHex: String,
    val sessions: List<ScheduleItem>
)
