package sv.edu.udb.finanzasudb.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import sv.edu.udb.finanzasudb.model.Budget
import sv.edu.udb.finanzasudb.model.CalendarActivity
import sv.edu.udb.finanzasudb.model.ScheduleItem
import sv.edu.udb.finanzasudb.model.Transaction
import sv.edu.udb.finanzasudb.model.UdbFee

object FirebaseRepository {

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy {
        val firestore = FirebaseFirestore.getInstance()
        try {
            val cacheSettings = com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build()
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) {}
        firestore
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val userEmail: String
        get() = currentUser?.email ?: "usuario_anonimo"

    fun signOut() {
        auth.signOut()
    }

    fun sendPasswordReset(email: String, onComplete: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.localizedMessage) }
    }

    // ==========================================
    // 0. PERFIL DE USUARIO (Firestore + Offline)
    // ==========================================
    fun listenUserProfile(onUpdate: (sv.edu.udb.finanzasudb.model.UserProfile) -> Unit): ListenerRegistration {
        return db.collection("users").document(userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }
                val name = snapshot.getString("name") ?: ""
                val email = snapshot.getString("email") ?: userEmail
                val career = snapshot.getString("career") ?: "Ingeniería en Ciencias de la Computación"
                val cycle = snapshot.getString("cycle") ?: "Ciclo 01"
                val campus = snapshot.getString("campus") ?: "Campus Soyapango"
                onUpdate(sv.edu.udb.finanzasudb.model.UserProfile(email, name, career, cycle, campus))
            }
    }

    fun saveUserProfile(profile: sv.edu.udb.finanzasudb.model.UserProfile, onComplete: ((Boolean) -> Unit)? = null) {
        val data = hashMapOf<String, Any>(
            "userId" to userEmail,
            "email" to profile.email.ifEmpty { userEmail },
            "career" to profile.career,
            "cycle" to profile.cycle,
            "campus" to profile.campus,
            "updatedAt" to System.currentTimeMillis()
        )
        if (profile.name.isNotBlank()) {
            data["name"] = profile.name
        }
        db.collection("users").document(userEmail)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    fun getUserProfile(onResult: (sv.edu.udb.finanzasudb.model.UserProfile?) -> Unit) {
        db.collection("users").document(userEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: ""
                    val email = snapshot.getString("email") ?: userEmail
                    val career = snapshot.getString("career") ?: "Ingeniería en Ciencias de la Computación"
                    val cycle = snapshot.getString("cycle") ?: "Ciclo 01"
                    val campus = snapshot.getString("campus") ?: "Campus Soyapango"
                    onResult(sv.edu.udb.finanzasudb.model.UserProfile(email, name, career, cycle, campus))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // ==========================================
    // 1. TRANSACCIONES (Firestore en Tiempo Real + Offline)
    // ==========================================
    fun listenTransactions(onUpdate: (List<Transaction>) -> Unit): ListenerRegistration {
        return db.collection("transactions")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val desc = doc.getString("description") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: "gasto"
                    val cat = doc.getString("category") ?: "Varios"
                    val date = doc.getString("date") ?: ""
                    val account = doc.getString("accountName") ?: "Efectivo"
                    Transaction(doc.id, desc, amount, type, cat, date, account)
                }
                onUpdate(list)
            }
    }

    fun addTransaction(tx: Transaction, onComplete: ((Boolean) -> Unit)? = null) {
        val docRef = if (tx.id.isNotEmpty()) {
            db.collection("transactions").document(tx.id)
        } else {
            db.collection("transactions").document()
        }
        val data = hashMapOf(
            "userId" to userEmail,
            "description" to tx.description,
            "amount" to tx.amount,
            "type" to tx.type,
            "category" to tx.category,
            "accountName" to tx.accountName,
            "date" to tx.date,
            "createdAt" to System.currentTimeMillis()
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteTransaction(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("transactions").document(id).delete()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    // ==========================================
    // 2. PRESUPUESTOS (Firestore)
    // ==========================================
    fun listenBudgets(onUpdate: (List<Budget>) -> Unit): ListenerRegistration {
        return db.collection("budgets")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val cat = doc.getString("category") ?: ""
                    val limit = doc.getDouble("limitAmount") ?: 0.0
                    val spent = doc.getDouble("spentAmount") ?: 0.0
                    Budget(doc.id, cat, limit, spent)
                }
                onUpdate(list)
            }
    }

    fun addBudget(budget: Budget, onComplete: ((Boolean) -> Unit)? = null) {
        val catClean = budget.category.trim()
        val docRef = if (budget.id.isNotEmpty()) {
            db.collection("budgets").document(budget.id)
        } else {
            db.collection("budgets").document()
        }
        val data = hashMapOf(
            "userId" to userEmail,
            "category" to catClean,
            "limitAmount" to budget.limitAmount,
            "updatedAt" to System.currentTimeMillis()
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteBudget(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("budgets").document(id).delete()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    // ==========================================
    // 3. CUOTAS UDB (Firestore + Offline)
    // ==========================================
    fun listenFees(onUpdate: (List<UdbFee>) -> Unit): ListenerRegistration {
        return db.collection("udb_fees")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val deadline = doc.getString("deadline") ?: ""
                    val isPaid = doc.getBoolean("isPaid") ?: false
                    UdbFee(doc.id, title, amount, deadline, isPaid)
                }
                onUpdate(list)
            }
    }

    fun addFee(fee: UdbFee, onComplete: ((Boolean) -> Unit)? = null) {
        val docRef = if (fee.id.isNotEmpty()) db.collection("udb_fees").document(fee.id) else db.collection("udb_fees").document()
        val data = hashMapOf(
            "userId" to userEmail,
            "title" to fee.title,
            "amount" to fee.amount,
            "deadline" to fee.deadline,
            "isPaid" to fee.isPaid
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteFee(feeId: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("udb_fees").document(feeId).delete()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun toggleFeePaid(feeId: String, currentPaid: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("udb_fees").document(feeId).update("isPaid", !currentPaid)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    // ==========================================
    // 4. HORARIO INSTITUCIONAL (Materias del Ciclo)
    // ==========================================
    fun listenSchedule(onUpdate: (List<ScheduleItem>) -> Unit): ListenerRegistration {
        return db.collection("schedule")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val materiasList = mutableListOf<ScheduleItem>()
                for (doc in snapshot.documents) {
                    val actType = doc.getString("activityType") ?: "Clase"
                    val date = doc.getString("date") ?: ""

                    // Actividades de calendario tienen fecha o son tipos de evaluación/tarea
                    val isCalendarAct = date.isNotEmpty() || (
                        actType.equals("Tarea", true) ||
                        actType.equals("Examen Parcial", true) ||
                        actType.equals("Examen", true) ||
                        actType.equals("Parcial", true) ||
                        actType.equals("Evento", true) ||
                        actType.equals("Entrega", true) ||
                        actType.equals("Actividad", true)
                    )

                    if (isCalendarAct) {
                        val title = doc.getString("subjectName") ?: ""
                        val type = if (actType.isNotEmpty() && !actType.equals("Clase", true)) actType else "Tarea"
                        val start = doc.getString("startTime") ?: "08:00 AM"
                        val end = doc.getString("endTime") ?: "10:00 AM"
                        val loc = doc.getString("classroom") ?: ""
                        val notes = doc.getString("professor") ?: ""
                        val color = doc.getString("colorHex") ?: "#3B82F6"
                        val activity = CalendarActivity("", title, type, date, start, end, loc, notes, color, false)
                        addCalendarActivity(activity)
                        doc.reference.delete()
                    } else {
                        val subj = doc.getString("subjectName") ?: ""
                        val code = doc.getString("code") ?: ""
                        val room = doc.getString("classroom") ?: ""
                        val prof = doc.getString("professor") ?: ""
                        val day = doc.getString("dayOfWeek") ?: "Lunes"
                        val start = doc.getString("startTime") ?: "06:00 PM"
                        val end = doc.getString("endTime") ?: "08:00 PM"
                        val color = doc.getString("colorHex") ?: "#2563EB"
                        val cleanType = if (actType.equals("Laboratorio", true)) "Laboratorio" else if (actType.equals("Teoría", true) || actType.equals("Teoria", true)) "Teoría" else "Clase"
                        materiasList.add(ScheduleItem(doc.id, subj, code, room, prof, day, start, end, color, "", cleanType))
                    }
                }
                onUpdate(materiasList)
            }
    }

    fun addScheduleItem(item: ScheduleItem, onComplete: ((Boolean) -> Unit)? = null) {
        val docRef = if (item.id.isNotEmpty()) {
            db.collection("schedule").document(item.id)
        } else {
            db.collection("schedule").document()
        }
        val cleanType = if (item.activityType.equals("Laboratorio", true)) "Laboratorio" else if (item.activityType.equals("Teoría", true) || item.activityType.equals("Teoria", true)) "Teoría" else "Clase"
        val data = hashMapOf(
            "userId" to userEmail,
            "subjectName" to item.subjectName,
            "code" to item.code,
            "classroom" to item.classroom,
            "professor" to item.professor,
            "dayOfWeek" to item.dayOfWeek,
            "startTime" to item.startTime,
            "endTime" to item.endTime,
            "colorHex" to item.colorHex,
            "date" to "",
            "activityType" to cleanType,
            "updatedAt" to System.currentTimeMillis()
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteScheduleItem(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("schedule").document(id).delete()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    // ==========================================
    // 4.1 ACTIVIDADES DE CALENDARIO INDEPENDIENTES (Tareas, Parciales, Laboratorios, etc.)
    // ==========================================
    fun listenCalendarActivities(onUpdate: (List<CalendarActivity>) -> Unit): ListenerRegistration {
        return db.collection("calendar_activities")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: doc.getString("subjectName") ?: ""
                    val type = doc.getString("type") ?: doc.getString("activityType") ?: "Tarea"
                    val date = doc.getString("date") ?: ""
                    val start = doc.getString("startTime") ?: "08:00 AM"
                    val end = doc.getString("endTime") ?: "10:00 AM"
                    val loc = doc.getString("location") ?: doc.getString("classroom") ?: ""
                    val notes = doc.getString("notes") ?: doc.getString("professor") ?: ""
                    val color = doc.getString("colorHex") ?: "#3B82F6"
                    val completed = doc.getBoolean("isCompleted") ?: false
                    CalendarActivity(doc.id, title, type, date, start, end, loc, notes, color, completed)
                }
                onUpdate(list)
            }
    }

    fun addCalendarActivity(item: CalendarActivity, onComplete: ((Boolean) -> Unit)? = null) {
        val docRef = if (item.id.isNotEmpty()) {
            db.collection("calendar_activities").document(item.id)
        } else {
            db.collection("calendar_activities").document()
        }
        val data = hashMapOf(
            "userId" to userEmail,
            "title" to item.title,
            "type" to item.type,
            "date" to item.date,
            "startTime" to item.startTime,
            "endTime" to item.endTime,
            "location" to item.location,
            "notes" to item.notes,
            "colorHex" to item.colorHex,
            "isCompleted" to item.isCompleted,
            "updatedAt" to System.currentTimeMillis()
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteCalendarActivity(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("calendar_activities").document(id).delete()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun toggleCalendarActivityCompleted(item: CalendarActivity, onComplete: ((Boolean) -> Unit)? = null) {
        if (item.id.isEmpty()) return
        val newStatus = !item.isCompleted
        db.collection("calendar_activities").document(item.id)
            .update("isCompleted", newStatus)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    // ==========================================
    // 5. CUENTAS (Firestore + Predeterminadas)
    // ==========================================
    val defaultAccounts = listOf("Efectivo", "Tarjeta", "Cuenta banco")

    fun listenAccounts(onUpdate: (List<String>) -> Unit): ListenerRegistration {
        return db.collection("accounts")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onUpdate(defaultAccounts)
                    return@addSnapshotListener
                }
                val customAccs = snapshot.documents.mapNotNull { it.getString("name") }
                val merged = (defaultAccounts + customAccs).distinct()
                onUpdate(merged)
            }
    }

    fun addAccount(name: String, onComplete: ((Boolean) -> Unit)? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onComplete?.invoke(false)
            return
        }
        val docRef = db.collection("accounts").document()
        val data = hashMapOf(
            "userId" to userEmail,
            "name" to trimmed,
            "createdAt" to System.currentTimeMillis()
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteAccount(name: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("accounts")
            .whereEqualTo("userId", userEmail)
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                onComplete?.invoke(true)
            }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    // ==========================================
    // 6. CATEGORÍAS (Firestore + Predeterminadas)
    // ==========================================
    val defaultCategories = listOf(
        "Alimentación",
        "Transporte",
        "Estudios UDB",
        "Materiales UDB",
        "Servicios",
        "Entretenimiento",
        "Salud",
        "Varios"
    )

    fun listenCategories(onUpdate: (List<String>) -> Unit): ListenerRegistration {
        return db.collection("categories")
            .whereEqualTo("userId", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onUpdate(defaultCategories)
                    return@addSnapshotListener
                }
                val customCats = snapshot.documents.mapNotNull { it.getString("name") }
                val merged = (defaultCategories + customCats).distinct()
                onUpdate(merged)
            }
    }

    fun addCategory(name: String, onComplete: ((Boolean) -> Unit)? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onComplete?.invoke(false)
            return
        }
        val docRef = db.collection("categories").document()
        val data = hashMapOf(
            "userId" to userEmail,
            "name" to trimmed,
            "createdAt" to System.currentTimeMillis()
        )
        docRef.set(data)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    fun deleteCategory(name: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("categories")
            .whereEqualTo("userId", userEmail)
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                onComplete?.invoke(true)
            }
            .addOnFailureListener { onComplete?.invoke(false) }
        onComplete?.invoke(true)
    }

    // ==========================================
    // 7. PURGAR DATOS AL BORRAR/RECREAR USUARIO
    // ==========================================
    fun purgeUserData(email: String, onComplete: () -> Unit) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) {
            onComplete()
            return
        }

        db.collection("users").document(cleanEmail).delete()
        val collections = listOf("transactions", "budgets", "udb_fees", "schedule", "accounts", "categories")
        var pending = collections.size

        for (col in collections) {
            db.collection(col)
                .whereEqualTo("userId", cleanEmail)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val batch = db.batch()
                        for (doc in snapshot.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().addOnCompleteListener {
                            pending--
                            if (pending <= 0) onComplete()
                        }
                    } else {
                        pending--
                        if (pending <= 0) onComplete()
                    }
                }
                .addOnFailureListener {
                    pending--
                    if (pending <= 0) onComplete()
                }
        }
    }

    fun clearLocalUserData(context: android.content.Context, email: String) {
        val cleanEmail = email.trim()
        val prefs = context.getSharedPreferences("user_academic_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${cleanEmail}_career")
            .remove("${cleanEmail}_cycle")
            .remove("${cleanEmail}_campus")
            .apply()
    }

    fun deleteUserAccount(context: android.content.Context, onComplete: (Boolean, String?) -> Unit) {
        val user = currentUser
        if (user == null) {
            onComplete(false, "No hay usuario autenticado")
            return
        }
        val email = user.email ?: ""
        purgeUserData(email) {
            clearLocalUserData(context, email)
            user.delete()
                .addOnSuccessListener {
                    signOut()
                    onComplete(true, null)
                }
                .addOnFailureListener { e ->
                    onComplete(false, e.localizedMessage)
                }
        }
    }
}
