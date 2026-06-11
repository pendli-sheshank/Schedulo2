package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.ui.theme.PrimaryGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.ListenerRegistration

data class Job(
    var id: String = java.util.UUID.randomUUID().toString(),
    var userId: String = "",
    var title: String = "",
    var isGigWork: Boolean = false,
    var defaultHourlyRate: Double = 15.0,
    var goalHours: Double = 20.0,
    var goalType: String = "Hours", // "Hours" or "Earnings"
    var weeklyCycleStartDay: String? = "Monday", // "Monday", "Tuesday", etc.
    var overtimeThresholdHours: Double = 40.0, // weekly hours after which overtime kicks in
    var overtimeMultiplier: Double = 1.5 // pay multiplier for overtime (e.g., 1.5x)
) {
    fun getStartOfCurrentCycle(targetMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startDay = weeklyCycleStartDay?.lowercase(Locale.US) ?: "monday"
        val targetDayOfWeek = when (startDay) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }

        while (calendar.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return calendar.timeInMillis
    }
}

data class PayAdjustment(
    var id: String = java.util.UUID.randomUUID().toString(),
    var userId: String = "",
    var cycleKey: String = "",
    var employer: String = "",
    var type: String = "Bonus",
    var amount: Double = 0.0,
    var notes: String = "",
    var createdAt: Long = System.currentTimeMillis()
)

data class Shift(
    var id: String = java.util.UUID.randomUUID().toString(),
    var userId: String = "",
    var company: String = "",
    var role: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var hourlyRate: Double = 0.0,
    var isGig: Boolean = false,
    var customEarned: Double = 0.0,
    var reminderBeforeMinutes: Int = 30,
    var isPaid: Boolean = false,
    var notes: String = ""
) {
    @get:com.google.firebase.firestore.Exclude
    val durationHours: Double
        get() = if (endTime > startTime) (endTime - startTime) / 3600000.0 else 0.0
        
    @get:com.google.firebase.firestore.Exclude
    val totalEarned: Double
        get() = if (isGig) customEarned else (durationHours * hourlyRate)
}

fun calculateEarningsWithOvertime(shifts: List<Shift>, job: Job): Pair<Double, Double> {
    // Returns (regularEarnings, overtimeEarnings)
    if (job.isGigWork) {
        // Gig work doesn't have overtime
        return Pair(shifts.sumOf { it.totalEarned }, 0.0)
    }
    val totalHours = shifts.sumOf { it.durationHours }
    val threshold = job.overtimeThresholdHours
    val rate = job.defaultHourlyRate
    val multiplier = job.overtimeMultiplier

    return if (totalHours <= threshold) {
        Pair(totalHours * rate, 0.0)
    } else {
        val regularHours = threshold
        val overtimeHours = totalHours - threshold
        val regularEarnings = regularHours * rate
        val overtimeEarnings = overtimeHours * rate * multiplier
        Pair(regularEarnings, overtimeEarnings)
    }
}

class DashboardViewModel : ViewModel() {
    private val auth by lazy { try { FirebaseAuth.getInstance() } catch(e:Exception){null} }
    private val db by lazy { try { FirebaseFirestore.getInstance() } catch(e:Exception){null} }

    private val _userId = MutableStateFlow(auth?.currentUser?.uid ?: "local_user")
    val userId = _userId.asStateFlow()

    private val _shifts = MutableStateFlow<List<Shift>>(emptyList())
    val shifts = _shifts.asStateFlow()

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs = _jobs.asStateFlow()

    private val _defaultCompany = MutableStateFlow("")
    val defaultCompany = _defaultCompany.asStateFlow()

    private val _defaultRate = MutableStateFlow(0.0)
    val defaultRate = _defaultRate.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName = _userName.asStateFlow()

    private val _memberSince = MutableStateFlow("")
    val memberSince = _memberSince.asStateFlow()

    private val _payAdjustments = MutableStateFlow<List<PayAdjustment>>(emptyList())
    val payAdjustments = _payAdjustments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode = _themeMode.asStateFlow()

    private val _remindersEnabled = MutableStateFlow(true)
    val remindersEnabled = _remindersEnabled.asStateFlow()

    private val _defaultReminderMinutes = MutableStateFlow(30)
    val defaultReminderMinutes = _defaultReminderMinutes.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        database.collection("settings").document(uid).update("themeMode", mode)
            .addOnFailureListener {
                database.collection("settings").document(uid)
                    .set(mapOf("themeMode" to mode, "userId" to uid), com.google.firebase.firestore.SetOptions.merge())
            }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        _remindersEnabled.value = enabled
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        database.collection("settings").document(uid)
            .set(mapOf("remindersEnabled" to enabled, "userId" to uid), com.google.firebase.firestore.SetOptions.merge())
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        _defaultReminderMinutes.value = minutes
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        database.collection("settings").document(uid)
            .set(mapOf("defaultReminderMinutes" to minutes, "userId" to uid), com.google.firebase.firestore.SetOptions.merge())
    }

    private var loadedForUserId: String? = null
    private var jobsListenerRegistration: ListenerRegistration? = null
    private var shiftsListenerRegistration: ListenerRegistration? = null
    private var profileListenerRegistration: ListenerRegistration? = null
    private var settingsListenerRegistration: ListenerRegistration? = null
    private var adjustmentsListenerRegistration: ListenerRegistration? = null
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refreshData() {
        _isRefreshing.value = true
        loadedForUserId = null
        jobsListenerRegistration?.remove()
        jobsListenerRegistration = null
        shiftsListenerRegistration?.remove()
        shiftsListenerRegistration = null
        profileListenerRegistration?.remove()
        profileListenerRegistration = null
        settingsListenerRegistration?.remove()
        settingsListenerRegistration = null
        adjustmentsListenerRegistration?.remove()
        adjustmentsListenerRegistration = null
        loadShifts()
    }

    fun detectConflicts(startTime: Long, endTime: Long, excludeShiftId: String? = null): List<Shift> {
        return _shifts.value.filter { shift ->
            shift.id != excludeShiftId &&
                shift.startTime < endTime && shift.endTime > startTime
        }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    fun loadSettings() {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _userId.value = uid
        settingsListenerRegistration?.remove()
        settingsListenerRegistration = database.collection("settings").document(uid).addSnapshotListener { doc, error ->
            if (error != null) {
                _syncError.value = "Failed to load settings: ${error.message}"
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                _defaultCompany.value = doc.getString("defaultCompany") ?: ""
                _defaultRate.value = doc.getDouble("defaultRate") ?: 0.0
                _themeMode.value = doc.getString("themeMode") ?: "system"
                _remindersEnabled.value = doc.getBoolean("remindersEnabled") ?: true
                _defaultReminderMinutes.value = doc.getLong("defaultReminderMinutes")?.toInt() ?: 30
            }
        }
        profileListenerRegistration?.remove()
        profileListenerRegistration = database.collection("profiles").document(uid).addSnapshotListener { doc, error ->
            if (error != null) {
                _syncError.value = "Failed to load profile: ${error.message}"
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                _userName.value = doc.getString("full_name") ?: ""
                val createdAt = doc.getLong("created_at")
                if (createdAt != null) {
                    val fmt = java.text.SimpleDateFormat("MMMM yyyy", Locale.US)
                    _memberSince.value = fmt.format(Date(createdAt))
                }
            } else {
                ensureProfileExists(uid, database)
            }
        }
    }

    private fun ensureProfileExists(uid: String, database: FirebaseFirestore) {
        val email = auth?.currentUser?.email ?: ""
        val displayName = auth?.currentUser?.displayName ?: ""
        val profile = hashMapOf(
            "id" to uid,
            "email" to email,
            "full_name" to displayName,
            "created_at" to System.currentTimeMillis()
        )
        database.collection("profiles").document(uid).set(profile)
    }

    fun saveSettings(company: String, rate: Double) {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            _syncError.value = "Please sign in to save settings."
            return
        }
        _userId.value = uid
        _defaultCompany.value = company
        _defaultRate.value = rate
        val data = hashMapOf(
            "defaultCompany" to company,
            "defaultRate" to rate,
            "userId" to uid,
            "updatedAt" to System.currentTimeMillis()
        )
        database.collection("settings").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { e ->
                _syncError.value = "Failed to save settings: ${e.message}"
            }
    }

    fun loadJobs() {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _userId.value = uid
        jobsListenerRegistration?.remove()
        jobsListenerRegistration = database.collection("jobs")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _syncError.value = "Failed to load jobs: ${error.message}"
                    return@addSnapshotListener
                }
                if (value != null) {
                    val list = value.documents.mapNotNull { doc ->
                        doc.toObject(Job::class.java)?.copy(id = doc.id)
                    }
                    _jobs.value = list
                    if (list.isEmpty() && !value.metadata.isFromCache && !value.metadata.hasPendingWrites()) {
                        val defaultJobs = listOf(
                            Job(title = "7-ELEVEN", isGigWork = false, defaultHourlyRate = 15.0, goalHours = 20.0, goalType = "Hours", weeklyCycleStartDay = "Friday"),
                            Job(title = "Walmart", isGigWork = false, defaultHourlyRate = 17.5, goalHours = 25.0, goalType = "Hours", weeklyCycleStartDay = "Monday"),
                            Job(title = "DoorDash", isGigWork = true, defaultHourlyRate = 0.0, goalHours = 200.0, goalType = "Earnings", weeklyCycleStartDay = "Monday")
                        )
                        for (j in defaultJobs) {
                            j.userId = uid
                            database.collection("jobs").document(j.id).set(j)
                        }
                    }
                }
            }
    }

    fun addJob(title: String, isGigWork: Boolean, defaultHourlyRate: Double, goalHours: Double, goalType: String, weeklyCycleStartDay: String = "Monday", overtimeThresholdHours: Double = 40.0, overtimeMultiplier: Double = 1.5) {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            _syncError.value = "Please sign in to add employers."
            return
        }
        _userId.value = uid
        val job = Job(
            id = java.util.UUID.randomUUID().toString(),
            userId = uid,
            title = title,
            isGigWork = isGigWork,
            defaultHourlyRate = defaultHourlyRate,
            goalHours = goalHours,
            goalType = goalType,
            weeklyCycleStartDay = weeklyCycleStartDay,
            overtimeThresholdHours = overtimeThresholdHours,
            overtimeMultiplier = overtimeMultiplier
        )
        _jobs.value = _jobs.value + job
        database.collection("jobs").document(job.id).set(job)
            .addOnFailureListener { e ->
                _syncError.value = "Failed to save job: ${e.message}"
            }
    }

    fun updateJob(jobId: String, title: String, isGigWork: Boolean, defaultHourlyRate: Double, goalHours: Double, goalType: String, weeklyCycleStartDay: String, overtimeThresholdHours: Double = 40.0, overtimeMultiplier: Double = 1.5) {
        val job = jobs.value.find { it.id == jobId } ?: return
        val database = db ?: run {
            _syncError.value = "Please sign in to update employers."
            return
        }
        val updated = job.copy(
            title = title,
            isGigWork = isGigWork,
            defaultHourlyRate = defaultHourlyRate,
            goalHours = goalHours,
            goalType = goalType,
            weeklyCycleStartDay = weeklyCycleStartDay,
            overtimeThresholdHours = overtimeThresholdHours,
            overtimeMultiplier = overtimeMultiplier
        )
        _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        database.collection("jobs").document(jobId).set(updated)
            .addOnFailureListener { e ->
                _syncError.value = "Failed to update job: ${e.message}"
            }
    }

    fun deleteJob(jobId: String) {
        val database = db ?: run {
            _syncError.value = "Please sign in to delete employers."
            return
        }
        _jobs.value = _jobs.value.filter { it.id != jobId }
        database.collection("jobs").document(jobId).delete()
            .addOnFailureListener { e ->
                _syncError.value = "Failed to delete job: ${e.message}"
            }
    }

    fun reset() {
        loadedForUserId = null
        jobsListenerRegistration?.remove()
        jobsListenerRegistration = null
        shiftsListenerRegistration?.remove()
        shiftsListenerRegistration = null
        profileListenerRegistration?.remove()
        profileListenerRegistration = null
        settingsListenerRegistration?.remove()
        settingsListenerRegistration = null
        adjustmentsListenerRegistration?.remove()
        adjustmentsListenerRegistration = null
        _shifts.value = emptyList()
        _jobs.value = emptyList()
        _payAdjustments.value = emptyList()
        _defaultCompany.value = ""
        _defaultRate.value = 0.0
        _userName.value = ""
        _memberSince.value = ""
        _isLoading.value = false
        _syncError.value = null
        _themeMode.value = "system"
    }

    fun loadShifts() {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            _isLoading.value = false
            _syncError.value = "Please sign in to access your data."
            return
        }
        if (loadedForUserId == uid) return
        loadedForUserId = uid
        _isLoading.value = true
        _syncError.value = null
        loadSettings()
        loadJobs()
        loadPayAdjustments()
        _userId.value = uid
        shiftsListenerRegistration?.remove()
        shiftsListenerRegistration = database.collection("shifts")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { value, error ->
                _isLoading.value = false
                _isRefreshing.value = false
                if (error != null) {
                    _syncError.value = "Failed to load shifts: ${error.message}"
                    return@addSnapshotListener
                }
                if (value != null) {
                    val list = value.documents.mapNotNull { doc ->
                        doc.toObject(Shift::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.startTime }
                    _shifts.value = list
                }
            }
    }

    fun addShift(company: String, role: String, startTime: Long, endTime: Long, hourlyRate: Double) {
        addShift(company, startTime, endTime, hourlyRate, false, 0.0, 30, "")
    }

    fun addShift(company: String, startTime: Long, endTime: Long, hourlyRate: Double, isGig: Boolean, customEarned: Double, reminderBeforeMinutes: Int, notes: String = "") {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            _syncError.value = "Please sign in to save shifts."
            return
        }
        _userId.value = uid
        val shift = Shift(
            id = java.util.UUID.randomUUID().toString(),
            userId = uid,
            company = company,
            role = "",
            startTime = startTime,
            endTime = endTime,
            hourlyRate = hourlyRate,
            isGig = isGig,
            customEarned = customEarned,
            reminderBeforeMinutes = reminderBeforeMinutes,
            isPaid = isGig,
            notes = notes
        )
        _shifts.value = (_shifts.value + shift).sortedByDescending { it.startTime }
        database.collection("shifts").document(shift.id).set(shift)
            .addOnFailureListener { e ->
                _syncError.value = "Failed to save shift: ${e.message}"
            }
    }

    fun updateShift(shiftId: String, company: String, role: String, startTime: Long, endTime: Long, hourlyRate: Double) {
        updateShift(shiftId, company, startTime, endTime, hourlyRate, false, 0.0, 30, "")
    }

    fun updateShift(shiftId: String, company: String, startTime: Long, endTime: Long, hourlyRate: Double, isGig: Boolean, customEarned: Double, reminderBeforeMinutes: Int, notes: String = "") {
        val shift = shifts.value.find { it.id == shiftId } ?: return
        val database = db
        if (database == null) {
            _syncError.value = "Please sign in to update shifts."
            return
        }
        val updated = shift.copy(
            company = company,
            role = "",
            startTime = startTime,
            endTime = endTime,
            hourlyRate = hourlyRate,
            isGig = isGig,
            customEarned = customEarned,
            reminderBeforeMinutes = reminderBeforeMinutes,
            isPaid = if (isGig) true else shift.isPaid,
            notes = notes
        )
        _shifts.value = _shifts.value.map { if (it.id == shiftId) updated else it }.sortedByDescending { it.startTime }
        database.collection("shifts").document(shiftId).set(updated)
            .addOnFailureListener { e ->
                _syncError.value = "Failed to update shift: ${e.message}"
            }
    }

    fun deleteShift(shiftId: String) {
        val database = db
        if (database == null) {
            _syncError.value = "Please sign in to delete shifts."
            return
        }
        _shifts.value = _shifts.value.filter { it.id != shiftId }
        database.collection("shifts").document(shiftId).delete()
            .addOnFailureListener { e ->
                _syncError.value = "Failed to delete shift: ${e.message}"
            }
    }

    fun toggleShiftPaidStatus(shiftId: String, isPaid: Boolean) {
        val database = db ?: run {
            _syncError.value = "Please sign in to update payment status."
            return
        }
        val updatedShifts = _shifts.value.map { if (it.id == shiftId) it.copy(isPaid = isPaid) else it }
        _shifts.value = updatedShifts
        val updatedShift = updatedShifts.firstOrNull { it.id == shiftId } ?: return
        database.collection("shifts").document(shiftId).set(updatedShift)
            .addOnFailureListener { e ->
                _syncError.value = "Failed to update payment status: ${e.message}"
                _shifts.value = _shifts.value.map { if (it.id == shiftId) it.copy(isPaid = !isPaid) else it }
            }
    }

    fun markCycleAsPaid(shiftIds: List<String>, isPaid: Boolean) {
        val database = db ?: run {
            _syncError.value = "Please sign in to update payment status."
            return
        }
        val shiftIdSet = shiftIds.toSet()
        val updatedShifts = _shifts.value.map {
            if (it.id in shiftIdSet) it.copy(isPaid = isPaid) else it
        }
        _shifts.value = updatedShifts
        val batch = database.batch()
        val shiftsToWrite = updatedShifts.filter { it.id in shiftIdSet }
        for (shift in shiftsToWrite) {
            val docRef = database.collection("shifts").document(shift.id)
            batch.set(docRef, shift)
        }
        batch.commit()
            .addOnFailureListener { e ->
                _syncError.value = "Failed to mark cycle as paid: ${e.message}"
                _shifts.value = _shifts.value.map {
                    if (it.id in shiftIdSet) it.copy(isPaid = !isPaid) else it
                }
            }
    }

    fun generateFormattedReport(weekStartMillis: Long, employer: String?): String {
        val weekEndMillis = weekStartMillis + 7L * 24 * 60 * 60 * 1000L
        val filtered = _shifts.value.filter { shift ->
            shift.startTime >= weekStartMillis && shift.startTime < weekEndMillis &&
            (employer == null || employer == "All" || shift.company.equals(employer, ignoreCase = true))
        }.sortedBy { it.startTime }

        val sb = StringBuilder()
        val weekFormat = SimpleDateFormat("MMM dd", Locale.US)
        val dayFormat = SimpleDateFormat("EEEE (M/dd)", Locale.US)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        sb.appendLine("Schedule: ${weekFormat.format(Date(weekStartMillis))} – ${weekFormat.format(Date(weekEndMillis - 1000L))}")
        if (employer != null && employer != "All") sb.appendLine("Employer: $employer")
        sb.appendLine()

        var totalHours = 0.0
        var totalEarnings = 0.0
        for (shift in filtered) {
            val day = dayFormat.format(Date(shift.startTime))
            val start = timeFormat.format(Date(shift.startTime))
            val end = timeFormat.format(Date(shift.endTime))
            val hrs = shift.durationHours
            totalHours += hrs
            totalEarnings += shift.totalEarned
            val line = "$day: $start – $end (${"%.1f".format(hrs)} hrs) $${"%.2f".format(shift.totalEarned)}"
            sb.appendLine(line)
            if (shift.notes.isNotBlank()) sb.appendLine("  Notes: ${shift.notes}")
        }
        sb.appendLine()
        sb.appendLine("Total ${"%.1f".format(totalHours)} hours · $${"%.2f".format(totalEarnings)}")
        if (filtered.any { it.isPaid }) {
            val paidCount = filtered.count { it.isPaid }
            sb.appendLine("Paid: $paidCount/${filtered.size} shifts")
        }
        return sb.toString()
    }

    fun generateCycleReport(cycleStart: Long, cycleEnd: Long, employer: String, job: Job?): String {
        val filtered = _shifts.value.filter { shift ->
            shift.startTime >= cycleStart && shift.startTime < cycleEnd &&
            shift.company.equals(employer, ignoreCase = true)
        }.sortedBy { it.startTime }

        val sb = StringBuilder()
        val weekFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val dayFormat = SimpleDateFormat("EEEE (M/dd)", Locale.US)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        sb.appendLine("TIMESHEET REPORT")
        sb.appendLine("Employer: $employer")
        sb.appendLine("Pay Period: ${weekFormat.format(Date(cycleStart))} – ${weekFormat.format(Date(cycleEnd - 1000L))}")
        if (job != null) sb.appendLine("Cycle Start Day: ${job.weeklyCycleStartDay ?: "Monday"}")
        sb.appendLine("─".repeat(40))

        var totalHours = 0.0
        var totalEarnings = 0.0
        for (shift in filtered) {
            val day = dayFormat.format(Date(shift.startTime))
            val start = timeFormat.format(Date(shift.startTime))
            val end = timeFormat.format(Date(shift.endTime))
            val hrs = shift.durationHours
            totalHours += hrs
            totalEarnings += shift.totalEarned
            val status = if (shift.isPaid) " [PAID]" else ""
            sb.appendLine("$day: $start – $end (${"%.1f".format(hrs)} hrs)$status")
            if (shift.notes.isNotBlank()) sb.appendLine("  Notes: ${shift.notes}")
        }

        sb.appendLine("─".repeat(40))

        if (job != null && !job.isGigWork) {
            val (regular, overtime) = calculateEarningsWithOvertime(filtered, job)
            val regularHours = totalHours.coerceAtMost(job.overtimeThresholdHours)
            val overtimeHours = (totalHours - regularHours).coerceAtLeast(0.0)
            sb.appendLine("Regular: ${"%.1f".format(regularHours)} hrs × $${"%.2f".format(job.defaultHourlyRate)} = $${"%.2f".format(regular)}")
            if (overtimeHours > 0) {
                sb.appendLine("Overtime: ${"%.1f".format(overtimeHours)} hrs × $${"%.2f".format(job.defaultHourlyRate * job.overtimeMultiplier)} = $${"%.2f".format(overtime)}")
            }
            sb.appendLine("TOTAL: ${"%.1f".format(totalHours)} hours · $${"%.2f".format(regular + overtime)}")
        } else {
            sb.appendLine("TOTAL: ${"%.1f".format(totalHours)} hours · $${"%.2f".format(totalEarnings)}")
        }

        val paidCount = filtered.count { it.isPaid }
        sb.appendLine("Payment Status: $paidCount/${filtered.size} shifts paid")
        return sb.toString()
    }

    fun generateCycleCsvReport(cycleStart: Long, cycleEnd: Long, employer: String, job: Job?): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val filtered = _shifts.value.filter { shift ->
            shift.startTime >= cycleStart && shift.startTime < cycleEnd &&
            shift.company.equals(employer, ignoreCase = true)
        }.sortedBy { it.startTime }

        val sb = StringBuilder()
        sb.appendLine("Date,Company,Start,End,Hours,Rate,Earned,Gig,Paid,Notes")
        filtered.forEach { s ->
            val notes = s.notes.replace(",", ";").replace("\n", " ")
            sb.appendLine("${dateFormat.format(Date(s.startTime))},${s.company},${timeFormat.format(Date(s.startTime))},${timeFormat.format(Date(s.endTime))},${"%.2f".format(s.durationHours)},${s.hourlyRate},${"%.2f".format(s.totalEarned)},${s.isGig},${s.isPaid},${notes}")
        }
        return sb.toString()
    }

    data class PayCycleOption(val cycleStart: Long, val cycleEnd: Long, val employer: String, val label: String, val shiftCount: Int, val isCurrent: Boolean)

    fun getAvailablePayCycles(): List<PayCycleOption> {
        val now = System.currentTimeMillis()
        val weekFormat = SimpleDateFormat("MMM dd", Locale.US)
        val allJobs = _jobs.value
        val allShifts = _shifts.value

        val cycles = mutableListOf<PayCycleOption>()

        for (job in allJobs) {
            val jobShifts = allShifts.filter { it.company.equals(job.title, ignoreCase = true) && !it.isGig }
            if (jobShifts.isEmpty()) continue

            val seenCycles = mutableSetOf<Long>()
            for (shift in jobShifts) {
                val (start, end) = getCycleStartAndEndForShift(shift, allJobs)
                if (seenCycles.add(start)) {
                    val shiftsInCycle = jobShifts.count { it.startTime >= start && it.startTime < end }
                    val isCurrent = now in start until end
                    val label = "${job.title}: ${weekFormat.format(Date(start))} – ${weekFormat.format(Date(end - 1000L))}" +
                        if (isCurrent) " (Current)" else ""
                    cycles.add(PayCycleOption(start, end, job.title, label, shiftsInCycle, isCurrent))
                }
            }

            val currentCycleStart = job.getStartOfCurrentCycle(now)
            val currentCycleEnd = currentCycleStart + 7L * 24 * 60 * 60 * 1000L
            if (seenCycles.add(currentCycleStart)) {
                val label = "${job.title}: ${weekFormat.format(Date(currentCycleStart))} – ${weekFormat.format(Date(currentCycleEnd - 1000L))} (Current)"
                cycles.add(PayCycleOption(currentCycleStart, currentCycleEnd, job.title, label, 0, true))
            }
        }

        return cycles.sortedWith(compareByDescending<PayCycleOption> { it.cycleStart }.thenBy { it.employer })
    }

    fun getAvailableWeeks(): List<Pair<Long, String>> {
        val weekFormat = SimpleDateFormat("MMM dd", Locale.US)
        val weeks = mutableSetOf<Long>()
        val now = System.currentTimeMillis()
        for (offset in -8..4) {
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.WEEK_OF_YEAR, offset)
            }
            weeks.add(cal.timeInMillis)
        }
        return weeks.sorted().map { start ->
            val end = start + 7L * 24 * 60 * 60 * 1000L
            val label = "${weekFormat.format(Date(start))} – ${weekFormat.format(Date(end - 1000L))}" +
                if (now in start until end) " (Current)" else ""
            Pair(start, label)
        }.reversed()
    }

    data class WeekDayEntry(val dayOffset: Int, val startH: Int, val startM: Int, val endH: Int, val endM: Int)

    fun addWeekPlan(company: String, hourlyRate: Double, isGig: Boolean, customEarned: Double, reminderMinutes: Int, weekStartMillis: Long, dayEntries: List<Triple<Int, Int, Int>>) {
        addWeekPlanWithMinutes(company, hourlyRate, isGig, customEarned, reminderMinutes, weekStartMillis,
            dayEntries.map { (d, s, e) -> WeekDayEntry(d, s, 0, e, 0) })
    }

    fun addWeekPlanWithMinutes(company: String, hourlyRate: Double, isGig: Boolean, customEarned: Double, reminderMinutes: Int, weekStartMillis: Long, dayEntries: List<WeekDayEntry>) {
        val existingShifts = _shifts.value
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        for (entry in dayEntries) {
            val dayMillis = weekStartMillis + entry.dayOffset.toLong() * 24 * 60 * 60 * 1000L
            val dateKey = dayFormat.format(Date(dayMillis))
            val alreadyExists = existingShifts.any {
                it.company.equals(company, ignoreCase = true) &&
                dayFormat.format(Date(it.startTime)) == dateKey
            }
            if (alreadyExists) continue

            val calStart = Calendar.getInstance().apply {
                timeInMillis = dayMillis
                set(Calendar.HOUR_OF_DAY, entry.startH)
                set(Calendar.MINUTE, entry.startM)
                set(Calendar.SECOND, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                timeInMillis = dayMillis
                set(Calendar.HOUR_OF_DAY, entry.endH)
                set(Calendar.MINUTE, entry.endM)
                set(Calendar.SECOND, 0)
            }
            var endTime = calEnd.timeInMillis
            if (endTime <= calStart.timeInMillis) endTime += 86400000L

            addShift(company, calStart.timeInMillis, endTime, hourlyRate, isGig, customEarned, reminderMinutes)
        }
    }

    fun updateUserName(newName: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        database.collection("profiles").document(uid)
            .update("full_name", newName)
            .addOnSuccessListener { _userName.value = newName }
            .addOnFailureListener { e ->
                _syncError.value = "Failed to update name: ${e.message}"
            }
    }

    data class WeekSummary(val weekStart: Long, val label: String, val hours: Double, val earnings: Double, val shiftCount: Int)

    fun getWeeklyEarningsSummary(weeks: Int = 8): List<WeekSummary> {
        val weekFormat = SimpleDateFormat("MMM dd", Locale.US)
        val now = System.currentTimeMillis()
        val completedShifts = _shifts.value.filter { it.startTime < now }
        return (0 until weeks).map { offset ->
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.WEEK_OF_YEAR, -offset)
            }
            val weekStart = cal.timeInMillis
            val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L
            val weekShifts = completedShifts.filter { it.startTime in weekStart until weekEnd }
            WeekSummary(
                weekStart = weekStart,
                label = weekFormat.format(Date(weekStart)),
                hours = weekShifts.sumOf { it.durationHours },
                earnings = weekShifts.sumOf { it.totalEarned },
                shiftCount = weekShifts.size
            )
        }.reversed()
    }

    fun getEarningsByEmployer(): Map<String, Double> {
        val now = System.currentTimeMillis()
        return _shifts.value.filter { it.startTime < now }
            .groupBy { it.company }
            .mapValues { (_, shifts) -> shifts.sumOf { it.totalEarned } }
            .toList().sortedByDescending { it.second }.toMap()
    }

    fun generateCsvReport(weekStart: Long, employer: String): String {
        val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val filtered = _shifts.value.filter { shift ->
            shift.startTime in weekStart until weekEnd &&
                (employer == "All" || shift.company.equals(employer, ignoreCase = true))
        }.sortedBy { it.startTime }

        val sb = StringBuilder()
        sb.appendLine("Date,Company,Start,End,Hours,Rate,Earned,Gig,Paid,Notes")
        filtered.forEach { s ->
            val notes = s.notes.replace(",", ";").replace("\n", " ")
            sb.appendLine("${dateFormat.format(Date(s.startTime))},${s.company},${timeFormat.format(Date(s.startTime))},${timeFormat.format(Date(s.endTime))},${"%.2f".format(s.durationHours)},${s.hourlyRate},${"%.2f".format(s.totalEarned)},${s.isGig},${s.isPaid},${notes}")
        }
        return sb.toString()
    }

    private fun loadPayAdjustments() {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        adjustmentsListenerRegistration?.remove()
        adjustmentsListenerRegistration = database.collection("pay_adjustments")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                if (value != null) {
                    _payAdjustments.value = value.documents.mapNotNull { doc ->
                        doc.toObject(PayAdjustment::class.java)?.copy(id = doc.id)
                    }
                }
            }
    }

    fun addPayAdjustment(cycleKey: String, employer: String, type: String, amount: Double, notes: String) {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            _syncError.value = "Please sign in to add adjustments."
            return
        }
        val adjustment = PayAdjustment(
            userId = uid,
            cycleKey = cycleKey,
            employer = employer,
            type = type,
            amount = amount,
            notes = notes
        )
        _payAdjustments.value = _payAdjustments.value + adjustment
        database.collection("pay_adjustments").document(adjustment.id).set(adjustment)
            .addOnFailureListener { e ->
                _syncError.value = "Failed to save adjustment: ${e.message}"
            }
    }

    fun deletePayAdjustment(adjustmentId: String) {
        val database = db ?: return
        _payAdjustments.value = _payAdjustments.value.filter { it.id != adjustmentId }
        database.collection("pay_adjustments").document(adjustmentId).delete()
            .addOnFailureListener { e ->
                _syncError.value = "Failed to delete adjustment: ${e.message}"
            }
    }

    fun getAdjustmentsForCycle(cycleKey: String): List<PayAdjustment> {
        return _payAdjustments.value.filter { it.cycleKey == cycleKey }
    }

    override fun onCleared() {
        super.onCleared()
        jobsListenerRegistration?.remove()
        shiftsListenerRegistration?.remove()
        profileListenerRegistration?.remove()
        settingsListenerRegistration?.remove()
        adjustmentsListenerRegistration?.remove()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftScreen(
    shiftId: String? = null,
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val jobs by viewModel.jobs.collectAsState()
    val shifts by viewModel.shifts.collectAsState()
    val existingShift = shiftId?.let { id -> shifts.find { it.id == id } }

    var selectedJob by remember(jobs, existingShift) {
        mutableStateOf(
            if (existingShift != null) {
                jobs.find { it.title.equals(existingShift.company, ignoreCase = true) }
            } else if (jobs.isNotEmpty()) {
                jobs.first()
            } else {
                null
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    var company by remember(selectedJob) { mutableStateOf(selectedJob?.title ?: existingShift?.company ?: "") }
    var isGig by remember(selectedJob) { mutableStateOf(selectedJob?.isGigWork ?: existingShift?.isGig ?: false) }
    var rate by remember(selectedJob, existingShift) {
        mutableStateOf(
            existingShift?.hourlyRate?.toString() 
                ?: selectedJob?.defaultHourlyRate?.toString() 
                ?: "0.0"
        )
    }
    var customEarnings by remember(existingShift) {
        mutableStateOf(existingShift?.customEarned?.toString() ?: "")
    }
    val defaultReminderMin by viewModel.defaultReminderMinutes.collectAsState()
    var reminderMinutes by remember(existingShift, defaultReminderMin) {
        mutableStateOf(existingShift?.reminderBeforeMinutes ?: defaultReminderMin)
    }
    var shiftNotes by remember(existingShift) {
        mutableStateOf(existingShift?.notes ?: "")
    }

    var selectedDateMillis by remember { mutableStateOf(existingShift?.startTime ?: System.currentTimeMillis()) }
    var startHour by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.HOUR_OF_DAY) } ?: 9) }
    var startMinute by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.MINUTE) } ?: 0) }
    var endHour by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.endTime }.get(Calendar.HOUR_OF_DAY) } ?: 17) }
    var endMinute by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.endTime }.get(Calendar.MINUTE) } ?: 0) }

    var recurrence by remember { mutableStateOf("None") }
    var recurrenceWeeks by remember { mutableStateOf("4") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var expandedCompany by remember { mutableStateOf(false) }

    val initialUtcMillis = remember {
        val localCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(localCal.get(Calendar.YEAR), localCal.get(Calendar.MONTH), localCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }
        utcCal.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
    val startTimePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)
    val endTimePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMs ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = utcMs
                        val localCal = Calendar.getInstance()
                        localCal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                        selectedDateMillis = localCal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startHour = startTimePickerState.hour
                    startMinute = startTimePickerState.minute
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            text = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = startTimePickerState) } }
        )
    }

    if (showEndTimePicker) {
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endHour = endTimePickerState.hour
                    endMinute = endTimePickerState.minute
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            text = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = endTimePickerState) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingShift != null) "Edit Shift" else "Add Shift") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (existingShift != null) {
                        IconButton(onClick = { 
                            viewModel.deleteShift(existingShift.id)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Select Job", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = company.ifEmpty { "Select a Job..." },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Job") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCompany) }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedCompany = true }
                )
                if (jobs.isNotEmpty()) {
                    DropdownMenu(
                        expanded = expandedCompany,
                        onDismissRequest = { expandedCompany = false }
                    ) {
                        jobs.forEach { job ->
                            DropdownMenuItem(
                                text = { Text("${job.title} (${if (job.isGigWork) "Gig" else "$${job.defaultHourlyRate}/hr"})") },
                                onClick = {
                                    selectedJob = job
                                    company = job.title
                                    isGig = job.isGigWork
                                    rate = job.defaultHourlyRate.toString()
                                    expandedCompany = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Pay representation
            if (isGig) {
                OutlinedTextField(
                    value = customEarnings,
                    onValueChange = { customEarnings = it },
                    label = { Text("Shift Earnings ($) [Gig Work]") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Hourly Rate ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Date Picker Field
            OutlinedTextField(
                value = dateFormat.format(Date(selectedDateMillis)),
                onValueChange = { },
                label = { Text("Date") },
                enabled = false,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Start Time Field
                OutlinedTextField(
                    value = String.format(Locale.US, "%02d:%02d", startHour, startMinute),
                    onValueChange = { },
                    label = { Text("Start Time") },
                    enabled = false,
                    modifier = Modifier.weight(1f).clickable { showStartTimePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                // End Time Field
                OutlinedTextField(
                    value = String.format(Locale.US, "%02d:%02d", endHour, endMinute),
                    onValueChange = { },
                    label = { Text("End Time") },
                    enabled = false,
                    modifier = Modifier.weight(1f).clickable { showEndTimePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Reminder selector
            Text("Remind Me", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "None", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (minutes, label) ->
                    val selected = reminderMinutes == minutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { reminderMinutes = minutes }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (existingShift == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Repeat Shift", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("None", "Daily", "Weekly", "Biweekly").forEach { option ->
                        val selected = recurrence == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { recurrence = option }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                if (recurrence != "None") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recurrenceWeeks,
                        onValueChange = { recurrenceWeeks = it },
                        label = { Text("Repeat for (weeks)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = shiftNotes,
                onValueChange = { if (it.length <= 1000) shiftNotes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Parking instructions, door code, etc.") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 4
            )

            val previewCalStart = remember(selectedDateMillis, startHour, startMinute) {
                Calendar.getInstance().apply { timeInMillis = selectedDateMillis; set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, startMinute); set(Calendar.SECOND, 0) }.timeInMillis
            }
            val previewCalEnd = remember(selectedDateMillis, endHour, endMinute, previewCalStart) {
                val end = Calendar.getInstance().apply { timeInMillis = selectedDateMillis; set(Calendar.HOUR_OF_DAY, endHour); set(Calendar.MINUTE, endMinute); set(Calendar.SECOND, 0) }.timeInMillis
                if (end < previewCalStart) end + 86400000L else end
            }
            val conflicts = remember(previewCalStart, previewCalEnd, shifts) {
                viewModel.detectConflicts(previewCalStart, previewCalEnd, existingShift?.id)
            }
            if (conflicts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Shift Overlap Detected", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF92400E))
                            conflicts.forEach { conflict ->
                                val fmt = SimpleDateFormat("MMM dd h:mm a", Locale.US)
                                Text("${conflict.company}: ${fmt.format(Date(conflict.startTime))} - ${fmt.format(Date(conflict.endTime))}", fontSize = 12.sp, color = Color(0xFF92400E))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val calStart = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                    }
                    val calEnd = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, endHour)
                        set(Calendar.MINUTE, endMinute)
                        set(Calendar.SECOND, 0)
                    }
                    var finalEndTime = calEnd.timeInMillis
                    if (finalEndTime < calStart.timeInMillis) {
                        finalEndTime += 86400000L
                    }
                    val hourly = if (isGig) 0.0 else (rate.toDoubleOrNull() ?: 0.0)
                    val earned = if (isGig) (customEarnings.toDoubleOrNull() ?: 0.0) else 0.0
                    val trimmedNotes = shiftNotes.trim()
                    val remindersOn = viewModel.remindersEnabled.value
                    val effectiveReminder = if (remindersOn) reminderMinutes else 0

                    if (existingShift != null) {
                        viewModel.updateShift(existingShift.id, company, calStart.timeInMillis, finalEndTime, hourly, isGig, earned, effectiveReminder, trimmedNotes)
                        if (effectiveReminder > 0) {
                            NotificationHelper.scheduleReminder(context, Shift(id = existingShift.id, company = company, startTime = calStart.timeInMillis, endTime = finalEndTime, reminderBeforeMinutes = effectiveReminder))
                        } else {
                            NotificationHelper.cancelReminder(context, existingShift.id)
                        }
                    } else {
                        viewModel.addShift(company, calStart.timeInMillis, finalEndTime, hourly, isGig, earned, effectiveReminder, trimmedNotes)
                        if (effectiveReminder > 0) {
                            NotificationHelper.scheduleReminder(context, Shift(company = company, startTime = calStart.timeInMillis, endTime = finalEndTime, reminderBeforeMinutes = effectiveReminder))
                        }
                        if (recurrence != "None") {
                            val weeks = recurrenceWeeks.toIntOrNull()?.coerceIn(1, 52) ?: 4
                            val dayIncrement = when (recurrence) {
                                "Daily" -> 1
                                "Weekly" -> 7
                                "Biweekly" -> 14
                                else -> 0
                            }
                            val totalOccurrences = if (recurrence == "Daily") weeks * 7 else weeks
                            for (i in 1..totalOccurrences) {
                                val offsetMs = i.toLong() * dayIncrement * 24 * 60 * 60 * 1000L
                                val recurShift = Shift(company = company, startTime = calStart.timeInMillis + offsetMs, endTime = finalEndTime + offsetMs, reminderBeforeMinutes = effectiveReminder)
                                viewModel.addShift(company, calStart.timeInMillis + offsetMs, finalEndTime + offsetMs, hourly, isGig, earned, effectiveReminder, trimmedNotes)
                                if (effectiveReminder > 0) NotificationHelper.scheduleReminder(context, recurShift)
                            }
                        }
                    }
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text(if (existingShift != null) "Update Shift" else "Save Shift", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
