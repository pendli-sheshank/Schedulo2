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
    var weeklyCycleStartDay: String? = "Monday" // "Monday", "Tuesday", etc.
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
    var isPaid: Boolean = false
) {
    @get:com.google.firebase.firestore.Exclude
    val durationHours: Double
        get() = if (endTime > startTime) (endTime - startTime) / 3600000.0 else 0.0
        
    @get:com.google.firebase.firestore.Exclude
    val totalEarned: Double
        get() = if (isGig) customEarned else (durationHours * hourlyRate)
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()

    private var loadedForUserId: String? = null
    private var jobsListenerRegistration: ListenerRegistration? = null
    private var shiftsListenerRegistration: ListenerRegistration? = null
    private var profileListenerRegistration: ListenerRegistration? = null
    
    fun clearSyncError() {
        _syncError.value = null
    }

    fun loadSettings() {
        val uid = auth?.currentUser?.uid ?: "local_user"
        _userId.value = uid
        val database = db
        if (database != null && uid != "local_user") {
            database.collection("settings").document(uid).addSnapshotListener { doc, error ->
                if (error != null) {
                    _syncError.value = "Failed to load settings: ${error.message}"
                    return@addSnapshotListener
                }
                if (doc != null && doc.exists()) {
                    _defaultCompany.value = doc.getString("defaultCompany") ?: ""
                    _defaultRate.value = doc.getDouble("defaultRate") ?: 0.0
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
        val uid = auth?.currentUser?.uid ?: "local_user"
        _userId.value = uid
        _defaultCompany.value = company
        _defaultRate.value = rate
        val database = db
        if (database != null && uid != "local_user") {
            val data = hashMapOf(
                "defaultCompany" to company,
                "defaultRate" to rate,
                "userId" to uid,
                "updatedAt" to System.currentTimeMillis()
            )
            database.collection("settings").document(uid).set(data)
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to save settings: ${e.message}"
                }
        }
    }

    fun loadJobs() {
        val uid = auth?.currentUser?.uid ?: "local_user"
        _userId.value = uid
        val database = db
        if (database != null && uid != "local_user") {
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
                        if (list.isEmpty() && !value.metadata.isFromCache) {
                            val defaultJobs = listOf(
                                Job(title = "7-ELEVEN", isGigWork = false, defaultHourlyRate = 15.0, goalHours = 20.0, goalType = "Hours", weeklyCycleStartDay = "Friday"),
                                Job(title = "Walmart", isGigWork = false, defaultHourlyRate = 17.5, goalHours = 25.0, goalType = "Hours", weeklyCycleStartDay = "Monday"),
                                Job(title = "DoorDash", isGigWork = true, defaultHourlyRate = 0.0, goalHours = 200.0, goalType = "Earnings", weeklyCycleStartDay = "Monday")
                            )
                            for (j in defaultJobs) {
                                j.userId = uid
                                database.collection("jobs").document(j.id).set(j)
                            }
                        } else {
                            _jobs.value = list
                        }
                    }
                }
        } else {
            _jobs.value = listOf(
                Job(id = "1", title = "7-ELEVEN", isGigWork = false, defaultHourlyRate = 15.0, goalHours = 20.0, goalType = "Hours", weeklyCycleStartDay = "Friday"),
                Job(id = "2", title = "Walmart", isGigWork = false, defaultHourlyRate = 17.5, goalHours = 25.0, goalType = "Hours", weeklyCycleStartDay = "Monday"),
                Job(id = "3", title = "DoorDash", isGigWork = true, defaultHourlyRate = 0.0, goalHours = 200.0, goalType = "Earnings", weeklyCycleStartDay = "Monday")
            )
        }
    }

    fun addJob(title: String, isGigWork: Boolean, defaultHourlyRate: Double, goalHours: Double, goalType: String, weeklyCycleStartDay: String = "Monday") {
        val uid = auth?.currentUser?.uid ?: "local_user"
        _userId.value = uid
        val job = Job(
            id = java.util.UUID.randomUUID().toString(),
            userId = uid,
            title = title,
            isGigWork = isGigWork,
            defaultHourlyRate = defaultHourlyRate,
            goalHours = goalHours,
            goalType = goalType,
            weeklyCycleStartDay = weeklyCycleStartDay
        )
        val database = db
        if (database != null) {
            database.collection("jobs").document(job.id).set(job)
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to save job: ${e.message}"
                    _jobs.value = _jobs.value + job
                }
        } else {
            _jobs.value = _jobs.value + job
        }
    }

    fun updateJob(jobId: String, title: String, isGigWork: Boolean, defaultHourlyRate: Double, goalHours: Double, goalType: String, weeklyCycleStartDay: String) {
        val job = jobs.value.find { it.id == jobId } ?: return
        val updated = job.copy(
            title = title,
            isGigWork = isGigWork,
            defaultHourlyRate = defaultHourlyRate,
            goalHours = goalHours,
            goalType = goalType,
            weeklyCycleStartDay = weeklyCycleStartDay
        )
        val database = db
        if (database != null) {
            database.collection("jobs").document(jobId).set(updated)
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to update job: ${e.message}"
                }
        } else {
            _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        }
    }

    fun deleteJob(jobId: String) {
        val database = db
        if (database != null) {
            database.collection("jobs").document(jobId).delete()
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to delete job: ${e.message}"
                }
        } else {
            _jobs.value = _jobs.value.filter { it.id != jobId }
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
        _shifts.value = emptyList()
        _jobs.value = emptyList()
        _defaultCompany.value = ""
        _defaultRate.value = 0.0
        _userName.value = ""
        _memberSince.value = ""
        _isLoading.value = false
        _syncError.value = null
    }

    fun loadShifts() {
        val uid = auth?.currentUser?.uid ?: "local_user"
        if (loadedForUserId == uid) return
        loadedForUserId = uid
        _isLoading.value = true
        _syncError.value = null
        loadSettings()
        loadJobs()
        _userId.value = uid
        val database = db
        if (database != null && uid != "local_user") {
            shiftsListenerRegistration?.remove()
            shiftsListenerRegistration = database.collection("shifts")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { value, error ->
                    _isLoading.value = false
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
        } else {
            _isLoading.value = false
            if (uid == "local_user") {
                _syncError.value = "Not signed in. Data will not sync across devices."
            }
        }
    }

    fun addShift(company: String, role: String, startTime: Long, endTime: Long, hourlyRate: Double) {
        addShift(company, startTime, endTime, hourlyRate, false, 0.0, 30, role)
    }

    fun addShift(company: String, startTime: Long, endTime: Long, hourlyRate: Double, isGig: Boolean, customEarned: Double, reminderBeforeMinutes: Int, role: String = "") {
        val uid = auth?.currentUser?.uid ?: "local_user"
        _userId.value = uid
        val shift = Shift(
            id = java.util.UUID.randomUUID().toString(),
            userId = uid,
            company = company,
            role = role,
            startTime = startTime,
            endTime = endTime,
            hourlyRate = hourlyRate,
            isGig = isGig,
            customEarned = customEarned,
            reminderBeforeMinutes = reminderBeforeMinutes
        )
        val database = db
        if (database != null && uid != "local_user") {
            database.collection("shifts").document(shift.id).set(shift)
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to save shift: ${e.message}"
                    _shifts.value = (_shifts.value + shift).sortedByDescending { it.startTime }
                }
        } else {
            _shifts.value = (_shifts.value + shift).sortedByDescending { it.startTime }
        }
    }

    fun updateShift(shiftId: String, company: String, role: String, startTime: Long, endTime: Long, hourlyRate: Double) {
        updateShift(shiftId, company, startTime, endTime, hourlyRate, false, 0.0, 30, role)
    }

    fun updateShift(shiftId: String, company: String, startTime: Long, endTime: Long, hourlyRate: Double, isGig: Boolean, customEarned: Double, reminderBeforeMinutes: Int, role: String = "") {
        val shift = shifts.value.find { it.id == shiftId } ?: return
        val updated = shift.copy(
            company = company,
            role = role,
            startTime = startTime,
            endTime = endTime,
            hourlyRate = hourlyRate,
            isGig = isGig,
            customEarned = customEarned,
            reminderBeforeMinutes = reminderBeforeMinutes
        )
        val database = db
        if (database != null && shift.userId != "local_user") {
            database.collection("shifts").document(shiftId).set(updated)
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to update shift: ${e.message}"
                }
        } else {
            _shifts.value = _shifts.value.map { if (it.id == shiftId) updated else it }.sortedByDescending { it.startTime }
        }
    }

    fun deleteShift(shiftId: String) {
        val database = db
        if (database != null) {
            database.collection("shifts").document(shiftId).delete()
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to delete shift: ${e.message}"
                }
        } else {
            _shifts.value = _shifts.value.filter { it.id != shiftId }
        }
    }

    fun toggleShiftPaidStatus(shiftId: String, isPaid: Boolean) {
        val database = db
        if (database != null) {
            database.collection("shifts").document(shiftId).update("isPaid", isPaid)
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to update payment status: ${e.message}"
                }
        } else {
            _shifts.value = _shifts.value.map { if (it.id == shiftId) it.copy(isPaid = isPaid) else it }
        }
    }

    fun markCycleAsPaid(shiftIds: List<String>, isPaid: Boolean) {
        val database = db
        if (database != null) {
            val batch = database.batch()
            for (id in shiftIds) {
                val docRef = database.collection("shifts").document(id)
                batch.update(docRef, "isPaid", isPaid)
            }
            batch.commit()
                .addOnFailureListener { e ->
                    _syncError.value = "Failed to mark cycle as paid: ${e.message}"
                }
        } else {
            _shifts.value = _shifts.value.map {
                if (it.id in shiftIds) it.copy(isPaid = isPaid) else it
            }
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

    override fun onCleared() {
        super.onCleared()
        jobsListenerRegistration?.remove()
        shiftsListenerRegistration?.remove()
        profileListenerRegistration?.remove()
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
    var reminderMinutes by remember(existingShift) {
        mutableStateOf(existingShift?.reminderBeforeMinutes ?: 30)
    }

    var selectedDateMillis by remember { mutableStateOf(existingShift?.startTime ?: System.currentTimeMillis()) }
    var startHour by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.HOUR_OF_DAY) } ?: 9) }
    var startMinute by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.MINUTE) } ?: 0) }
    var endHour by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.endTime }.get(Calendar.HOUR_OF_DAY) } ?: 17) }
    var endMinute by remember { mutableStateOf(existingShift?.let { Calendar.getInstance().apply { timeInMillis = it.endTime }.get(Calendar.MINUTE) } ?: 0) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var expandedCompany by remember { mutableStateOf(false) }
    var showJobError by remember { mutableStateOf(false) }

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
                        localCal.set(Calendar.MILLISECOND, 0)
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
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
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
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
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
            if (showJobError) {
                Text(
                    text = "Please select a job before saving.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (selectedJob == null && company.isBlank()) {
                        showJobError = true
                        return@Button
                    }
                    showJobError = false
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
                        finalEndTime += 86400000L // Add one day
                    }
                    val hourly = if (isGig) 0.0 else (rate.toDoubleOrNull() ?: 0.0)
                    val earned = if (isGig) (customEarnings.toDoubleOrNull() ?: 0.0) else 0.0

                    if (existingShift != null) {
                        viewModel.updateShift(existingShift.id, company, calStart.timeInMillis, finalEndTime, hourly, isGig, earned, reminderMinutes)
                    } else {
                        viewModel.addShift(company, calStart.timeInMillis, finalEndTime, hourly, isGig, earned, reminderMinutes)
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
