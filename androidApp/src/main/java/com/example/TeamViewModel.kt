package com.example

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.ui.theme.PrimaryGreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.schedulo.shared.model.ShiftTask
import com.schedulo.shared.model.Team
import com.schedulo.shared.model.TeamMember
import com.schedulo.shared.model.SwapRequest
import com.schedulo.shared.model.TaskHistoryEntry
import com.schedulo.shared.model.TeamMessage
import com.schedulo.shared.model.TeamShift
import com.schedulo.shared.model.TeamTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/** Human-readable working-hours summary, e.g. "Open 24 hours" or "9:00 AM – 5:00 PM". */
fun formatWorkHours(open24Hours: Boolean, startMinutes: Int, endMinutes: Int): String {
    if (open24Hours) return "Open 24 hours"
    fun fmt(mins: Int): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, (mins / 60) % 24)
            set(java.util.Calendar.MINUTE, mins % 60)
        }
        return java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(cal.time)
    }
    return "${fmt(startMinutes)} – ${fmt(endMinutes)}"
}

/** All editable team fields captured by the Create / Edit team forms. */
data class TeamFormData(
    val name: String = "",
    val companyName: String = "",
    val weeklyCycleStartDay: String = "Monday",
    val open24Hours: Boolean = true,
    val workStartMinutes: Int = 9 * 60,
    val workEndMinutes: Int = 17 * 60,
    val addressLine: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = ""
) {
    companion object {
        /** Prefill the form from an existing team (for the Edit screen). */
        fun from(team: Team): TeamFormData = TeamFormData(
            name = team.name,
            companyName = team.companyName,
            weeklyCycleStartDay = team.weeklyCycleStartDay,
            open24Hours = team.open24Hours,
            workStartMinutes = if (team.open24Hours) 9 * 60 else team.workStartMinutes,
            workEndMinutes = if (team.open24Hours) 17 * 60 else team.workEndMinutes,
            addressLine = team.addressLine,
            city = team.city,
            region = team.region,
            postalCode = team.postalCode
        )
    }

    /** The non-identity team fields written to Firestore on create and update. */
    fun toFirestoreFields(): HashMap<String, Any> = hashMapOf(
        "weeklyCycleStartDay" to weeklyCycleStartDay,
        "companyName" to companyName.trim(),
        "open24Hours" to open24Hours,
        "workStartMinutes" to workStartMinutes,
        "workEndMinutes" to workEndMinutes,
        "addressLine" to addressLine.trim(),
        "city" to city.trim(),
        "region" to region.trim(),
        "postalCode" to postalCode.trim()
    )
}

class TeamViewModel : ViewModel() {
    private val auth by lazy { try { FirebaseAuth.getInstance() } catch (e: Exception) { null } }
    private val db by lazy { try { FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DB_NAME) } catch (e: Exception) { null } }

    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams = _teams.asStateFlow()

    private val _currentTeam = MutableStateFlow<Team?>(null)
    val currentTeam = _currentTeam.asStateFlow()

    private val _members = MutableStateFlow<List<TeamMember>>(emptyList())
    val members = _members.asStateFlow()

    private val _teamShifts = MutableStateFlow<List<TeamShift>>(emptyList())
    val teamShifts = _teamShifts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _userRole = MutableStateFlow("member")
    val userRole = _userRole.asStateFlow()

    private val _memberJobs = MutableStateFlow<List<Job>>(emptyList())
    val memberJobs = _memberJobs.asStateFlow()

    private val _teamMessages = MutableStateFlow<List<TeamMessage>>(emptyList())
    val teamMessages = _teamMessages.asStateFlow()

    private val _swapRequests = MutableStateFlow<List<SwapRequest>>(emptyList())
    val swapRequests = _swapRequests.asStateFlow()

    private val _teamTasks = MutableStateFlow<List<TeamTask>>(emptyList())
    val teamTasks = _teamTasks.asStateFlow()

    private var teamsListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private var shiftsListener: ListenerRegistration? = null
    private var memberJobsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var swapRequestsListener: ListenerRegistration? = null
    private var teamTasksListener: ListenerRegistration? = null
    private var scheduleNotificationsListener: ListenerRegistration? = null

    private val storage by lazy { try { FirebaseStorage.getInstance() } catch (e: Exception) { null } }
    private val _isUploadingImage = MutableStateFlow(false)
    val isUploadingImage = _isUploadingImage.asStateFlow()

    var chatNotificationCallback: ((String, String) -> Unit)? = null

    /** (teamName, company, startTime, endTime) for a newly assigned schedule. */
    var scheduleNotificationCallback: ((String, String, Long, Long) -> Unit)? = null

    /**
     * Global listener for schedule-assignment notifications addressed to the
     * current user. Unlike the team listeners (which only run while a team is
     * selected), this runs for the whole signed-in session, so assignments land
     * as a device notification no matter which screen is open. A persisted
     * createdAt watermark stops docs from re-firing on every cold start.
     */
    fun startScheduleNotificationsListener(context: android.content.Context) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val prefs = context.getSharedPreferences("schedule_notifications", android.content.Context.MODE_PRIVATE)
        val watermarkKey = "last_seen_$uid"

        scheduleNotificationsListener?.remove()
        scheduleNotificationsListener = database.collection("notifications")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { value, error ->
                if (error != null || value == null) return@addSnapshotListener
                // Default the watermark to "now" on first run so a fresh install
                // doesn't replay the full history as banners.
                var watermark = prefs.getLong(watermarkKey, System.currentTimeMillis())
                if (!prefs.contains(watermarkKey)) {
                    prefs.edit().putLong(watermarkKey, watermark).apply()
                }
                val newDocs = value.documents
                    .filter { (it.getLong("createdAt") ?: 0L) > watermark }
                    .sortedBy { it.getLong("createdAt") ?: 0L }
                newDocs.forEach { doc ->
                    scheduleNotificationCallback?.invoke(
                        doc.getString("teamName") ?: "",
                        doc.getString("company") ?: "",
                        doc.getLong("startTime") ?: 0L,
                        doc.getLong("endTime") ?: 0L
                    )
                    watermark = maxOf(watermark, doc.getLong("createdAt") ?: 0L)
                }
                if (newDocs.isNotEmpty()) {
                    prefs.edit().putLong(watermarkKey, watermark).apply()
                }
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    fun fetchMemberJobs(userId: String) {
        val database = db ?: return
        memberJobsListener?.remove()
        memberJobsListener = database.collection("jobs")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                _memberJobs.value = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Job::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
    }

    fun loadTeams() {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _isLoading.value = true
        _errorMessage.value = null

        teamsListener?.remove()
        teamsListener = database.collection("team_members")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { memberDocs, error ->
                if (error != null) {
                    _errorMessage.value = "Failed to load team memberships: ${error.message}"
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                if (memberDocs == null || memberDocs.isEmpty) {
                    _teams.value = emptyList()
                    _currentTeam.value = null
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val teamIds = memberDocs.documents.mapNotNull { it.getString("teamId") }
                if (teamIds.isEmpty()) {
                    _teams.value = emptyList()
                    _currentTeam.value = null
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                // Firestore whereIn supports max 30 items
                val chunks = teamIds.chunked(30)
                val allTeams = mutableListOf<Team>()
                var completedChunks = 0

                for (chunk in chunks) {
                    database.collection("teams")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get()
                        .addOnSuccessListener { teamDocs ->
                            for (doc in teamDocs.documents) {
                                val team = Team(
                                    id = doc.id,
                                    name = doc.getString("name") ?: "",
                                    ownerId = doc.getString("ownerId") ?: "",
                                    inviteCode = doc.getString("inviteCode") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: 0,
                                    memberCount = doc.getLong("memberCount")?.toInt() ?: 0,
                                    weeklyCycleStartDay = doc.getString("weeklyCycleStartDay") ?: "Monday",
                                    companyName = doc.getString("companyName") ?: "",
                                    open24Hours = doc.getBoolean("open24Hours") ?: true,
                                    workStartMinutes = doc.getLong("workStartMinutes")?.toInt() ?: 0,
                                    workEndMinutes = doc.getLong("workEndMinutes")?.toInt() ?: 0,
                                    addressLine = doc.getString("addressLine") ?: "",
                                    city = doc.getString("city") ?: "",
                                    region = doc.getString("region") ?: "",
                                    postalCode = doc.getString("postalCode") ?: ""
                                )
                                allTeams.add(team)
                            }
                            completedChunks++
                            if (completedChunks == chunks.size) {
                                _teams.value = allTeams.sortedByDescending { it.createdAt }
                                if (_currentTeam.value == null && allTeams.isNotEmpty()) {
                                    selectTeam(allTeams.first())
                                } else {
                                    // Refresh current team data
                                    val current = _currentTeam.value
                                    if (current != null) {
                                        val updated = allTeams.find { it.id == current.id }
                                        if (updated != null) _currentTeam.value = updated
                                    }
                                }
                                _isLoading.value = false
                            }
                        }
                        .addOnFailureListener { e ->
                            _errorMessage.value = "Failed to load teams: ${e.message}"
                            _isLoading.value = false
                        }
                }
            }
    }

    fun selectTeam(team: Team) {
        _currentTeam.value = team
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return

        // Determine user role in this team
        database.collection("team_members")
            .whereEqualTo("teamId", team.id)
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { docs ->
                val memberDoc = docs.documents.firstOrNull()
                _userRole.value = memberDoc?.getString("role") ?: "member"
            }

        // Listen for members
        membersListener?.remove()
        membersListener = database.collection("team_members")
            .whereEqualTo("teamId", team.id)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Failed to load members: ${error.message}"
                    return@addSnapshotListener
                }
                if (value != null) {
                    _members.value = value.documents.map { doc ->
                        TeamMember(
                            id = doc.id,
                            teamId = doc.getString("teamId") ?: "",
                            userId = doc.getString("userId") ?: "",
                            role = doc.getString("role") ?: "member",
                            joinedAt = doc.getLong("joinedAt") ?: 0,
                            displayName = doc.getString("displayName") ?: "",
                            email = doc.getString("email") ?: "",
                            defaultHourlyRate = doc.getDouble("defaultHourlyRate") ?: 0.0
                        )
                    }
                }
            }

        // Listen for team shifts
        shiftsListener?.remove()
        shiftsListener = database.collection("team_shifts")
            .whereEqualTo("teamId", team.id)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Failed to load team shifts: ${error.message}"
                    return@addSnapshotListener
                }
                if (value != null) {
                    _teamShifts.value = value.documents.map { doc ->
                        val tasksRaw = doc.get("tasks") as? List<*> ?: emptyList<Any>()
                        val tasks = tasksRaw.mapNotNull { item ->
                            val map = item as? Map<*, *> ?: return@mapNotNull null
                            ShiftTask(
                                id = map["id"] as? String ?: "",
                                title = map["title"] as? String ?: "",
                                isCompleted = map["isCompleted"] as? Boolean ?: false
                            )
                        }
                        TeamShift(
                            id = doc.id,
                            teamId = doc.getString("teamId") ?: "",
                            assignedTo = doc.getString("assignedTo") ?: "",
                            assignedBy = doc.getString("assignedBy") ?: "",
                            company = doc.getString("company") ?: "",
                            role = doc.getString("role") ?: "",
                            startTime = doc.getLong("startTime") ?: 0,
                            endTime = doc.getLong("endTime") ?: 0,
                            hourlyRate = doc.getDouble("hourlyRate") ?: 0.0,
                            notes = doc.getString("notes") ?: "",
                            // Schedules are approved on assignment now; treat legacy
                            // "assigned" docs from older builds as accepted.
                            status = (doc.getString("status") ?: "accepted")
                                .let { if (it == "assigned") "accepted" else it },
                            tasks = tasks
                        )
                    }.sortedByDescending { it.startTime }
                    reconcilePersonalMirrors(_teamShifts.value, team.id)
                }
            }

        // Listen for team messages.
        // Note: we intentionally do NOT add an .orderBy() server-side. Combining
        // whereEqualTo("teamId") with orderBy("createdAt") requires a composite index;
        // if that index is missing the listener fails and silently stops delivering
        // updates (causing "messages arrive late / no notification"). Sorting locally
        // keeps real-time delivery reliable.
        messagesListener?.remove()
        messagesListener = database.collection("team_messages")
            .whereEqualTo("teamId", team.id)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Failed to load messages: ${error.message}"
                    return@addSnapshotListener
                }
                if (value != null) {
                    val uid = auth?.currentUser?.uid
                    val oldIds = _teamMessages.value.map { it.id }.toSet()
                    val newMessages = value.documents.map { doc ->
                        TeamMessage(
                            id = doc.id,
                            teamId = doc.getString("teamId") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            senderName = doc.getString("senderName") ?: "",
                            text = doc.getString("text") ?: "",
                            isAnnouncement = doc.getBoolean("isAnnouncement") ?: false,
                            isPinned = doc.getBoolean("isPinned") ?: false,
                            imageUrl = doc.getString("imageUrl") ?: "",
                            seenBy = (doc.get("seenBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            createdAt = doc.getLong("createdAt") ?: 0
                        )
                    }
                    if (oldIds.isNotEmpty()) {
                        newMessages.filter { it.id !in oldIds && it.senderId != uid }
                            .sortedBy { it.createdAt }
                            .forEach { msg ->
                                val preview = if (msg.imageUrl.isNotEmpty()) "Sent a photo" else msg.text
                                chatNotificationCallback?.invoke(msg.senderName, preview)
                            }
                    }
                    _teamMessages.value = newMessages.sortedByDescending { it.createdAt }
                }
            }

        // Listen for swap requests
        swapRequestsListener?.remove()
        swapRequestsListener = database.collection("swap_requests")
            .whereEqualTo("teamId", team.id)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                if (value != null) {
                    _swapRequests.value = value.documents.map { doc ->
                        SwapRequest(
                            id = doc.id,
                            teamId = doc.getString("teamId") ?: "",
                            requesterId = doc.getString("requesterId") ?: "",
                            requesterName = doc.getString("requesterName") ?: "",
                            requesterShiftId = doc.getString("requesterShiftId") ?: "",
                            targetMemberId = doc.getString("targetMemberId") ?: "",
                            targetMemberName = doc.getString("targetMemberName") ?: "",
                            targetShiftId = doc.getString("targetShiftId") ?: "",
                            status = doc.getString("status") ?: "pending",
                            createdAt = doc.getLong("createdAt") ?: 0,
                            resolvedAt = doc.getLong("resolvedAt") ?: 0,
                            resolvedBy = doc.getString("resolvedBy") ?: ""
                        )
                    }.sortedByDescending { it.createdAt }
                }
            }

        // Listen for standalone team tasks
        teamTasksListener?.remove()
        teamTasksListener = database.collection("team_tasks")
            .whereEqualTo("teamId", team.id)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Failed to load tasks: ${error.message}"
                    return@addSnapshotListener
                }
                if (value != null) {
                    _teamTasks.value = value.documents.map { doc ->
                        val historyRaw = doc.get("history") as? List<*> ?: emptyList<Any>()
                        val history = historyRaw.mapNotNull { item ->
                            val map = item as? Map<*, *> ?: return@mapNotNull null
                            TaskHistoryEntry(
                                status = map["status"] as? String ?: "",
                                changedBy = map["changedBy"] as? String ?: "",
                                changedByName = map["changedByName"] as? String ?: "",
                                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0
                            )
                        }
                        TeamTask(
                            id = doc.id,
                            teamId = doc.getString("teamId") ?: "",
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            assignedTo = doc.getString("assignedTo") ?: "",
                            assignedToName = doc.getString("assignedToName") ?: "",
                            assignedBy = doc.getString("assignedBy") ?: "",
                            status = doc.getString("status") ?: "pending",
                            createdAt = doc.getLong("createdAt") ?: 0,
                            updatedAt = doc.getLong("updatedAt") ?: 0,
                            history = history.sortedByDescending { it.timestamp }
                        )
                    }.sortedByDescending { it.createdAt }
                }
            }
    }

    /**
     * Team features require a verified email. FirebaseUser.isEmailVerified is a
     * cached value that only refreshes after reload(), so a user who has already
     * verified (here or on another device) could be wrongly blocked by a stale
     * token. Reload first, then run [onVerified] if verified; otherwise (re)send a
     * verification email — covering the case where the signup email never arrived —
     * and surface a clear message.
     */
    private fun withVerifiedEmail(actionLabel: String, onVerified: () -> Unit) {
        val user = auth?.currentUser ?: return
        user.reload().addOnCompleteListener {
            val refreshed = auth?.currentUser
            if (refreshed?.isEmailVerified == true) {
                onVerified()
            } else {
                try { refreshed?.sendEmailVerification() } catch (_: Exception) { }
                val address = refreshed?.email ?: "your inbox"
                _errorMessage.value = "Please verify your email before $actionLabel a team. We've sent a verification link to $address — open it, then try again."
            }
        }
    }

    fun createTeam(form: TeamFormData) {
        if (form.name.isBlank()) {
            _errorMessage.value = "Team name cannot be empty."
            return
        }
        if (form.companyName.isBlank()) {
            _errorMessage.value = "Company name cannot be empty."
            return
        }
        withVerifiedEmail("creating") { performCreateTeam(form) }
    }

    private fun performCreateTeam(form: TeamFormData) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _isLoading.value = true
        _errorMessage.value = null

        val teamId = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        val now = System.currentTimeMillis()

        val teamData = hashMapOf(
            "name" to form.name.trim(),
            "ownerId" to uid,
            "inviteCode" to inviteCode,
            "createdAt" to now,
            "memberCount" to 1
        ) + form.toFirestoreFields()

        val email = auth?.currentUser?.email ?: ""
        val displayName = auth?.currentUser?.displayName ?: ""

        val memberData = hashMapOf(
            "teamId" to teamId,
            "userId" to uid,
            "role" to "manager",
            "joinedAt" to now,
            "displayName" to displayName,
            "email" to email,
            "defaultHourlyRate" to 0.0
        )

        val batch = database.batch()
        batch.set(database.collection("teams").document(teamId), teamData)
        // Deterministic membership id "{teamId}_{userId}" lets security rules prove
        // membership with a single exists() check.
        batch.set(database.collection("team_members").document("${teamId}_$uid"), memberData)
        // Public-by-secret lookup so joiners can resolve a code -> teamId without
        // the teams collection being world-readable.
        batch.set(database.collection("invite_codes").document(inviteCode), hashMapOf("teamId" to teamId))

        batch.commit()
            .addOnSuccessListener {
                _isLoading.value = false
                loadTeams()
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to create team: ${e.message}"
                _isLoading.value = false
            }
    }

    /** Update the editable team fields (manager/owner only, enforced by rules). */
    fun updateTeam(teamId: String, form: TeamFormData) {
        val database = db ?: return
        if (form.name.isBlank() || form.companyName.isBlank()) {
            _errorMessage.value = "Team and company name cannot be empty."
            return
        }
        val updates = hashMapOf<String, Any>("name" to form.name.trim()) + form.toFirestoreFields()
        database.collection("teams").document(teamId)
            .update(updates)
            .addOnSuccessListener {
                val current = _currentTeam.value
                if (current?.id == teamId) {
                    _currentTeam.value = current.copy(
                        name = form.name.trim(),
                        weeklyCycleStartDay = form.weeklyCycleStartDay,
                        companyName = form.companyName.trim(),
                        open24Hours = form.open24Hours,
                        workStartMinutes = form.workStartMinutes,
                        workEndMinutes = form.workEndMinutes,
                        addressLine = form.addressLine.trim(),
                        city = form.city.trim(),
                        region = form.region.trim(),
                        postalCode = form.postalCode.trim()
                    )
                }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to update team: ${e.message}"
            }
    }

    /** Set a member's default pay rate (manager/owner only). */
    fun updateMemberRate(memberDocId: String, rate: Double) {
        val database = db ?: return
        val safeRate = rate.coerceAtLeast(0.0)
        val previous = _members.value
        _members.value = _members.value.map {
            if (it.id == memberDocId) it.copy(defaultHourlyRate = safeRate) else it
        }
        database.collection("team_members").document(memberDocId)
            .update("defaultHourlyRate", safeRate)
            .addOnFailureListener { e ->
                _members.value = previous
                _errorMessage.value = "Failed to update pay rate: ${e.message}"
            }
    }

    fun joinTeam(inviteCode: String) {
        if (inviteCode.isBlank()) {
            _errorMessage.value = "Invite code cannot be empty."
            return
        }
        withVerifiedEmail("joining") { performJoinTeam(inviteCode) }
    }

    private fun performJoinTeam(inviteCode: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _isLoading.value = true
        _errorMessage.value = null

        val code = inviteCode.trim().uppercase()

        // Resolve the code -> teamId via the public-by-secret lookup collection
        // (a direct get() by document id; the teams collection is not queryable
        // by non-members).
        database.collection("invite_codes").document(code)
            .get()
            .addOnSuccessListener { codeDoc ->
                val teamId = codeDoc.getString("teamId")
                if (!codeDoc.exists() || teamId.isNullOrBlank()) {
                    _errorMessage.value = "No team found with that invite code."
                    _isLoading.value = false
                    return@addOnSuccessListener
                }

                val memberRef = database.collection("team_members").document("${teamId}_$uid")

                // Check if already a member (direct read of the deterministic doc).
                memberRef.get()
                    .addOnSuccessListener { existingMember ->
                        if (existingMember.exists()) {
                            _errorMessage.value = "You are already a member of this team."
                            _isLoading.value = false
                            return@addOnSuccessListener
                        }

                        val email = auth?.currentUser?.email ?: ""
                        val displayName = auth?.currentUser?.displayName ?: ""
                        val now = System.currentTimeMillis()

                        val memberData = hashMapOf(
                            "teamId" to teamId,
                            "userId" to uid,
                            "role" to "member",
                            "joinedAt" to now,
                            "displayName" to displayName,
                            "email" to email,
                            // Included so the security rule can verify the joiner
                            // presented the correct invite code.
                            "inviteCode" to code
                        )

                        val batch = database.batch()
                        batch.set(memberRef, memberData)
                        batch.update(database.collection("teams").document(teamId), "memberCount", FieldValue.increment(1))

                        batch.commit()
                            .addOnSuccessListener {
                                _isLoading.value = false
                                loadTeams()
                            }
                            .addOnFailureListener { e ->
                                _errorMessage.value = "Failed to join team: ${e.message}"
                                _isLoading.value = false
                            }
                    }
                    .addOnFailureListener { e ->
                        _errorMessage.value = "Failed to check membership: ${e.message}"
                        _isLoading.value = false
                    }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to find team: ${e.message}"
                _isLoading.value = false
            }
    }

    fun assignShift(
        memberId: String,
        company: String,
        role: String,
        startTime: Long,
        endTime: Long,
        hourlyRate: Double,
        notes: String,
        tasks: List<ShiftTask> = emptyList()
    ) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val team = _currentTeam.value ?: return

        if (company.isBlank()) {
            _errorMessage.value = "Company name is required."
            return
        }

        val tasksList = tasks.map { task ->
            hashMapOf(
                "id" to task.id,
                "title" to task.title,
                "isCompleted" to task.isCompleted
            )
        }

        val shiftId = UUID.randomUUID().toString()
        val shiftData = hashMapOf(
            "teamId" to team.id,
            "assignedTo" to memberId,
            "assignedBy" to uid,
            "company" to company.trim(),
            "role" to role.trim(),
            "startTime" to startTime,
            "endTime" to endTime,
            "hourlyRate" to hourlyRate,
            "notes" to notes.trim(),
            "status" to "accepted",
            "tasks" to tasksList
        )

        database.collection("team_shifts").document(shiftId)
            .set(shiftData)
            .addOnSuccessListener {
                val teamShift = TeamShift(
                    id = shiftId,
                    teamId = team.id,
                    assignedTo = memberId,
                    assignedBy = uid,
                    company = company.trim(),
                    role = role.trim(),
                    startTime = startTime,
                    endTime = endTime,
                    hourlyRate = hourlyRate,
                    notes = notes.trim(),
                    status = "accepted",
                    tasks = tasks
                )
                createPersonalShiftFromTeam(teamShift, memberId)
                notifyScheduleAssigned(teamShift, memberId, team.name)
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to assign shift: ${e.message}"
            }
    }

    /** Writes a notifications doc so the assignee's device can surface the new schedule. */
    private fun notifyScheduleAssigned(teamShift: TeamShift, targetUserId: String, teamName: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        if (targetUserId == uid) return // no point notifying yourself
        val notificationData = hashMapOf(
            "userId" to targetUserId,
            "type" to "shift_assigned",
            "teamId" to teamShift.teamId,
            "teamShiftId" to teamShift.id,
            "teamName" to teamName,
            "company" to teamShift.company,
            "startTime" to teamShift.startTime,
            "endTime" to teamShift.endTime,
            "createdAt" to System.currentTimeMillis(),
            "read" to false
        )
        // Best-effort: the schedule itself is already saved; a failed notification
        // write must not surface as an assignment error.
        database.collection("notifications").document().set(notificationData)
    }

    private fun createPersonalShiftFromTeam(teamShift: TeamShift, targetUserId: String) {
        val database = db ?: return
        val shiftData = hashMapOf(
            "userId" to targetUserId,
            "company" to teamShift.company,
            "role" to teamShift.role,
            "startTime" to teamShift.startTime,
            "endTime" to teamShift.endTime,
            "hourlyRate" to teamShift.hourlyRate,
            "isGig" to false,
            "customEarned" to 0.0,
            "reminderBeforeMinutes" to 30,
            "isPaid" to false,
            "notes" to "Team shift: ${teamShift.notes}".trim(),
            "bonusApplied" to false,
            "bonusAmount" to 0.0,
            "teamShiftId" to teamShift.id,
            "teamId" to teamShift.teamId
        )
        // Deterministic doc id so concurrent mirror creation (assign + reconcile,
        // or two devices) collapses into one document instead of duplicates.
        database.collection("shifts").document("team_${teamShift.id}")
            .set(shiftData)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to create personal shift: ${e.message}"
            }
    }

    /**
     * Keeps the current user's personal mirror copies in sync with the selected
     * team's shifts. Swaps only update team_shifts (assignedTo/hourlyRate), so
     * without this the old assignee keeps a stale mirror and the new assignee
     * never gets one. Operates only on the user's own docs (rules-compatible)
     * and only on mirrors tied to this team, so other teams' mirrors are untouched.
     */
    private fun reconcilePersonalMirrors(teamShifts: List<TeamShift>, teamId: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val shiftsById = teamShifts.associateBy { it.id }

        database.collection("shifts").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { snapshot ->
                val mirrors = snapshot.documents.filter {
                    !it.getString("teamShiftId").isNullOrEmpty()
                }
                val mirroredTeamShiftIds = mirrors.mapNotNull { it.getString("teamShiftId") }.toSet()

                mirrors.forEach { doc ->
                    val teamShiftId = doc.getString("teamShiftId") ?: return@forEach
                    val teamShift = shiftsById[teamShiftId]
                    val mirrorTeamId = doc.getString("teamId") ?: ""
                    val orphaned = teamShift == null && mirrorTeamId == teamId
                    val reassigned = teamShift != null && teamShift.assignedTo != uid
                    if (orphaned || reassigned) {
                        doc.reference.delete()
                        val ctx = try { FirebaseApp.getInstance().applicationContext } catch (_: Exception) { null }
                        ctx?.let { try { NotificationHelper.cancelReminder(it, doc.id) } catch (_: Exception) {} }
                    }
                }

                teamShifts
                    .filter { it.assignedTo == uid && it.status != "declined" && it.id !in mirroredTeamShiftIds }
                    .forEach { createPersonalShiftFromTeam(it, uid) }
            }
    }

    fun updateTeamName(teamId: String, newName: String) {
        val database = db ?: return
        if (newName.isBlank()) return
        database.collection("teams").document(teamId)
            .update("name", newName.trim())
            .addOnSuccessListener { loadTeams() }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to update team name: ${e.message}"
            }
    }

    fun updateWeeklyCycleStartDay(teamId: String, day: String) {
        val database = db ?: return
        database.collection("teams").document(teamId)
            .update("weeklyCycleStartDay", day)
            .addOnSuccessListener {
                val current = _currentTeam.value
                if (current?.id == teamId) {
                    _currentTeam.value = current.copy(weeklyCycleStartDay = day)
                }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to update week start day: ${e.message}"
            }
    }

    fun deleteTeam(teamId: String) {
        val database = db ?: return
        _isLoading.value = true

        database.collection("team_members")
            .whereEqualTo("teamId", teamId)
            .get()
            .addOnSuccessListener { memberDocs ->
                database.collection("team_shifts")
                    .whereEqualTo("teamId", teamId)
                    .get()
                    .addOnSuccessListener { shiftDocs ->
                        val batch = database.batch()
                        for (doc in memberDocs.documents) batch.delete(doc.reference)
                        for (doc in shiftDocs.documents) batch.delete(doc.reference)
                        // Remove the invite-code lookup entry alongside the team.
                        val inviteCode = _currentTeam.value?.takeIf { it.id == teamId }?.inviteCode
                        if (!inviteCode.isNullOrBlank()) {
                            batch.delete(database.collection("invite_codes").document(inviteCode))
                        }
                        batch.delete(database.collection("teams").document(teamId))
                        batch.commit()
                            .addOnSuccessListener {
                                _currentTeam.value = null
                                _members.value = emptyList()
                                _teamShifts.value = emptyList()
                                membersListener?.remove()
                                shiftsListener?.remove()
                                _isLoading.value = false
                                loadTeams()
                            }
                            .addOnFailureListener { e ->
                                _errorMessage.value = "Failed to delete team: ${e.message}"
                                _isLoading.value = false
                            }
                    }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to delete team: ${e.message}"
                _isLoading.value = false
            }
    }

    fun deleteTeamShift(shiftId: String) {
        val database = db ?: return
        val previousShifts = _teamShifts.value
        _teamShifts.value = _teamShifts.value.filter { it.id != shiftId }
        database.collection("shifts").whereEqualTo("teamShiftId", shiftId).get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { it.reference.delete() }
            }
        database.collection("team_shifts").document(shiftId)
            .delete()
            .addOnFailureListener { e ->
                _teamShifts.value = previousShifts
                _errorMessage.value = "Failed to delete shift: ${e.message}"
            }
    }

    fun toggleTaskCompletion(shiftId: String, taskId: String) {
        val database = db ?: return
        val shift = _teamShifts.value.find { it.id == shiftId } ?: return
        val updatedTasks = shift.tasks.map { task ->
            if (task.id == taskId) ShiftTask(id = task.id, title = task.title, isCompleted = !task.isCompleted)
            else task
        }
        val tasksList = updatedTasks.map { task ->
            hashMapOf(
                "id" to task.id,
                "title" to task.title,
                "isCompleted" to task.isCompleted
            )
        }
        database.collection("team_shifts").document(shiftId)
            .update("tasks", tasksList)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to update task: ${e.message}"
            }
    }

    // ---- Standalone team tasks (assigned to a member, with progress + history) ----

    fun createTeamTask(memberId: String, memberName: String, title: String, description: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val team = _currentTeam.value ?: return
        if (title.isBlank()) {
            _errorMessage.value = "Task title is required."
            return
        }
        if (memberId.isBlank()) {
            _errorMessage.value = "Select a member to assign the task to."
            return
        }
        val now = System.currentTimeMillis()
        val actorName = currentUserDisplayName()
        val taskData = hashMapOf(
            "teamId" to team.id,
            "title" to title.trim(),
            "description" to description.trim(),
            "assignedTo" to memberId,
            "assignedToName" to memberName,
            "assignedBy" to uid,
            "status" to "pending",
            "createdAt" to now,
            "updatedAt" to now,
            "history" to listOf(
                hashMapOf(
                    "status" to "pending",
                    "changedBy" to uid,
                    "changedByName" to actorName,
                    "timestamp" to now
                )
            )
        )
        database.collection("team_tasks").document()
            .set(taskData)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to create task: ${e.message}"
            }
    }

    fun updateTeamTaskStatus(taskId: String, newStatus: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val task = _teamTasks.value.find { it.id == taskId } ?: return
        if (task.status == newStatus) return
        val now = System.currentTimeMillis()
        val actorName = currentUserDisplayName()

        // Optimistic local update so the UI reflects the change immediately.
        val previous = _teamTasks.value
        val newEntry = TaskHistoryEntry(status = newStatus, changedBy = uid, changedByName = actorName, timestamp = now)
        _teamTasks.value = _teamTasks.value.map {
            if (it.id == taskId) it.copy(status = newStatus, updatedAt = now, history = listOf(newEntry) + it.history) else it
        }

        val historyEntry = hashMapOf(
            "status" to newStatus,
            "changedBy" to uid,
            "changedByName" to actorName,
            "timestamp" to now
        )
        database.collection("team_tasks").document(taskId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to now,
                    "history" to FieldValue.arrayUnion(historyEntry)
                )
            )
            .addOnFailureListener { e ->
                _teamTasks.value = previous
                _errorMessage.value = "Failed to update task: ${e.message}"
            }
    }

    fun deleteTeamTask(taskId: String) {
        val database = db ?: return
        val previous = _teamTasks.value
        _teamTasks.value = _teamTasks.value.filter { it.id != taskId }
        database.collection("team_tasks").document(taskId)
            .delete()
            .addOnFailureListener { e ->
                _teamTasks.value = previous
                _errorMessage.value = "Failed to delete task: ${e.message}"
            }
    }

    private fun currentUserDisplayName(): String =
        auth?.currentUser?.displayName?.ifBlank { auth?.currentUser?.email?.substringBefore("@") } ?: ""

    fun promoteMember(memberDocId: String) {
        val database = db ?: return
        val previousMembers = _members.value
        _members.value = _members.value.map {
            if (it.id == memberDocId) it.copy(role = "manager") else it
        }
        database.collection("team_members").document(memberDocId)
            .update("role", "manager")
            .addOnFailureListener { e ->
                _members.value = previousMembers
                _errorMessage.value = "Failed to promote member: ${e.message}"
            }
    }

    fun demoteMember(memberDocId: String) {
        val database = db ?: return
        val previousMembers = _members.value
        _members.value = _members.value.map {
            if (it.id == memberDocId) it.copy(role = "member") else it
        }
        database.collection("team_members").document(memberDocId)
            .update("role", "member")
            .addOnFailureListener { e ->
                _members.value = previousMembers
                _errorMessage.value = "Failed to demote member: ${e.message}"
            }
    }

    fun removeMember(memberDocId: String, teamId: String) {
        val database = db ?: return
        val previousMembers = _members.value
        _members.value = _members.value.filter { it.id != memberDocId }

        val batch = database.batch()
        batch.delete(database.collection("team_members").document(memberDocId))
        batch.update(database.collection("teams").document(teamId), "memberCount", FieldValue.increment(-1))

        batch.commit()
            .addOnFailureListener { e ->
                _members.value = previousMembers
                _errorMessage.value = "Failed to remove member: ${e.message}"
            }
    }

    fun sendMessage(text: String, isAnnouncement: Boolean = false) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val team = _currentTeam.value ?: return
        if (text.isBlank()) return

        val senderName = auth?.currentUser?.displayName?.ifBlank { auth?.currentUser?.email?.substringBefore("@") } ?: ""
        val messageData = hashMapOf(
            "teamId" to team.id,
            "senderId" to uid,
            "senderName" to senderName,
            "text" to text.trim(),
            "isAnnouncement" to isAnnouncement,
            "isPinned" to false,
            "createdAt" to System.currentTimeMillis()
        )

        database.collection("team_messages").document()
            .set(messageData)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to send message: ${e.message}"
            }
    }

    fun sendImage(imageUri: Uri, context: android.content.Context) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val storageRef = storage ?: return
        val team = _currentTeam.value ?: return
        _isUploadingImage.value = true

        val senderName = auth?.currentUser?.displayName?.ifBlank { auth?.currentUser?.email?.substringBefore("@") } ?: ""
        val messageId = UUID.randomUUID().toString()
        val ref = storageRef.reference.child("chat_images/${team.id}/$messageId.jpg")
        val appContext = context.applicationContext

        // Decode/compress on a background thread: a large bitmap on the main thread
        // can ANR. Catch Throwable here (incl. OutOfMemoryError) so a problem photo
        // surfaces a real reason instead of silently failing.
        Thread {
            val compressed = try {
                compressImage(appContext, imageUri)
            } catch (t: Throwable) {
                _isUploadingImage.value = false
                _errorMessage.value = "Failed to process image: ${t.message ?: t.javaClass.simpleName}"
                return@Thread
            }
            if (compressed == null) {
                _isUploadingImage.value = false
                _errorMessage.value = "Failed to process image (unsupported or empty file)"
                return@Thread
            }
            ref.putBytes(compressed)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val messageData = hashMapOf(
                        "teamId" to team.id,
                        "senderId" to uid,
                        "senderName" to senderName,
                        "text" to "",
                        "isAnnouncement" to false,
                        "isPinned" to false,
                        "imageUrl" to downloadUrl.toString(),
                        "seenBy" to listOf(uid),
                        "createdAt" to System.currentTimeMillis()
                    )
                    database.collection("team_messages").document(messageId)
                        .set(messageData)
                        .addOnFailureListener { e -> _errorMessage.value = "Failed to send image: ${e.message}" }
                    _isUploadingImage.value = false
                }
            }
            .addOnFailureListener { e ->
                _isUploadingImage.value = false
                _errorMessage.value = "Failed to upload image: ${e.message}"
            }
        }.start()
    }

    // Throws on hard failure (caller reports the reason); returns null only when the
    // source can't be decoded into a bitmap (e.g. corrupt or non-image content).
    private fun compressImage(context: android.content.Context, uri: Uri): ByteArray? {
        val maxDim = 800
        val decoded = decodeBitmap(context, uri, maxDim) ?: return null

        val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
        val w = maxOf(1, (decoded.width * scale).toInt())
        val h = maxOf(1, (decoded.height * scale).toInt())
        val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, w, h, true)
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    // Decode a downsampled bitmap. On API 28+ ImageDecoder is used because it
    // handles modern camera formats (HEIC/HEIF, WebP) that BitmapFactory often
    // can't, which is the usual cause of "failed to process image". A software
    // allocator is required so the result can be re-compressed (hardware bitmaps
    // can't be read back). BitmapFactory's two-pass decode is the fallback.
    private fun decodeBitmap(context: android.content.Context, uri: Uri, maxDim: Int): android.graphics.Bitmap? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val srcW = info.size.width
                val srcH = info.size.height
                var sample = 1
                while (srcW / sample > maxDim * 2 || srcH / sample > maxDim * 2) sample *= 2
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        }

        // Pass 1: read only the bounds so we never load the full-res bitmap into memory.
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Pass 2: decode downsampled so memory usage stays bounded regardless of source size.
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
            sample *= 2
        }
        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }

    fun markMessageSeen(messageId: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val message = _teamMessages.value.find { it.id == messageId } ?: return
        if (uid in message.seenBy) return
        database.collection("team_messages").document(messageId)
            .update("seenBy", FieldValue.arrayUnion(uid))
            .addOnSuccessListener { checkAutoDelete(messageId) }
    }

    private fun checkAutoDelete(messageId: String) {
        val database = db ?: return
        val memberCount = _members.value.size
        if (memberCount == 0) return
        database.collection("team_messages").document(messageId).get()
            .addOnSuccessListener { doc ->
                val seenBy = (doc.get("seenBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val imageUrl = doc.getString("imageUrl") ?: ""
                if (imageUrl.isNotEmpty() && seenBy.size >= memberCount) {
                    try { storage?.getReferenceFromUrl(imageUrl)?.delete() } catch (_: Exception) {}
                    database.collection("team_messages").document(messageId)
                        .update(mapOf("imageUrl" to "", "text" to "Image expired"))
                }
            }
    }

    fun deleteMessage(messageId: String) {
        val database = db ?: return
        val previous = _teamMessages.value
        val deleted = previous.find { it.id == messageId }
        if (deleted != null && deleted.imageUrl.isNotEmpty()) {
            try { storage?.getReferenceFromUrl(deleted.imageUrl)?.delete() } catch (_: Exception) {}
        }
        _teamMessages.value = _teamMessages.value.filter { it.id != messageId }
        database.collection("team_messages").document(messageId)
            .delete()
            .addOnFailureListener { e ->
                _teamMessages.value = previous
                _errorMessage.value = "Failed to delete message: ${e.message}"
            }
    }

    fun togglePin(messageId: String) {
        val database = db ?: return
        val message = _teamMessages.value.find { it.id == messageId } ?: return
        val newPinned = !message.isPinned
        val previous = _teamMessages.value
        _teamMessages.value = _teamMessages.value.map {
            if (it.id == messageId) it.copy(isPinned = newPinned) else it
        }
        database.collection("team_messages").document(messageId)
            .update("isPinned", newPinned)
            .addOnFailureListener { e ->
                _teamMessages.value = previous
                _errorMessage.value = "Failed to update pin: ${e.message}"
            }
    }

    fun requestSwap(myShiftId: String, targetMemberId: String, targetShiftId: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val team = _currentTeam.value ?: return

        val requesterName = auth?.currentUser?.displayName?.ifBlank { auth?.currentUser?.email?.substringBefore("@") } ?: ""
        val targetMember = _members.value.find { it.userId == targetMemberId }
        val targetName = targetMember?.displayName?.ifBlank { targetMember.email.substringBefore("@") } ?: ""

        val data = hashMapOf(
            "teamId" to team.id,
            "requesterId" to uid,
            "requesterName" to requesterName,
            "requesterShiftId" to myShiftId,
            "targetMemberId" to targetMemberId,
            "targetMemberName" to targetName,
            "targetShiftId" to targetShiftId,
            "status" to "pending",
            "createdAt" to System.currentTimeMillis(),
            "resolvedAt" to 0L,
            "resolvedBy" to ""
        )

        database.collection("swap_requests").document()
            .set(data)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to request swap: ${e.message}"
            }
    }

    fun respondToSwap(requestId: String, accept: Boolean) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val newStatus = if (accept) "target_accepted" else "declined"
        val previous = _swapRequests.value
        _swapRequests.value = _swapRequests.value.map {
            if (it.id == requestId) it.copy(status = newStatus, resolvedBy = if (!accept) uid else "", resolvedAt = if (!accept) System.currentTimeMillis() else 0) else it
        }
        val updates = hashMapOf<String, Any>("status" to newStatus)
        if (!accept) {
            updates["resolvedBy"] = uid
            updates["resolvedAt"] = System.currentTimeMillis()
        }
        database.collection("swap_requests").document(requestId)
            .update(updates)
            .addOnFailureListener { e ->
                _swapRequests.value = previous
                _errorMessage.value = "Failed to respond to swap: ${e.message}"
            }
    }

    fun approveSwap(requestId: String, approve: Boolean) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return

        if (!approve) {
            val previous = _swapRequests.value
            _swapRequests.value = _swapRequests.value.map {
                if (it.id == requestId) it.copy(status = "declined", resolvedBy = uid, resolvedAt = System.currentTimeMillis()) else it
            }
            database.collection("swap_requests").document(requestId)
                .update(mapOf("status" to "declined", "resolvedBy" to uid, "resolvedAt" to System.currentTimeMillis()))
                .addOnFailureListener { e ->
                    _swapRequests.value = previous
                    _errorMessage.value = "Failed to decline swap: ${e.message}"
                }
            return
        }

        val request = _swapRequests.value.find { it.id == requestId } ?: return
        executeSwap(request)
    }

    private fun executeSwap(request: SwapRequest) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return

        // A swap should trade only the time-slots, never the pay rate. Each person's
        // hourly rate must move with them, otherwise whoever takes a shift would be
        // paid the other person's rate. We read both shifts' current rates and swap
        // them alongside assignedTo so nobody's pay rate changes because of the swap.
        val requesterShift = _teamShifts.value.find { it.id == request.requesterShiftId }
        val targetShift = _teamShifts.value.find { it.id == request.targetShiftId }
        if (requesterShift == null || targetShift == null) {
            _errorMessage.value = "Couldn't load the shift details to swap. Please try again."
            return
        }
        val requesterRate = requesterShift.hourlyRate
        val targetRate = targetShift.hourlyRate

        val batch = database.batch()
        batch.update(
            database.collection("team_shifts").document(request.requesterShiftId),
            mapOf(
                "assignedTo" to request.targetMemberId,
                "hourlyRate" to targetRate
            )
        )
        batch.update(
            database.collection("team_shifts").document(request.targetShiftId),
            mapOf(
                "assignedTo" to request.requesterId,
                "hourlyRate" to requesterRate
            )
        )
        batch.update(
            database.collection("swap_requests").document(request.id),
            mapOf("status" to "approved", "resolvedBy" to uid, "resolvedAt" to System.currentTimeMillis())
        )

        batch.commit()
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to execute swap: ${e.message}"
            }
    }

    fun cancelSwapRequest(requestId: String) {
        val database = db ?: return
        val previous = _swapRequests.value
        _swapRequests.value = _swapRequests.value.filter { it.id != requestId }
        database.collection("swap_requests").document(requestId)
            .delete()
            .addOnFailureListener { e ->
                _swapRequests.value = previous
                _errorMessage.value = "Failed to cancel swap: ${e.message}"
            }
    }

    fun leaveTeam(teamId: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _isLoading.value = true

        database.collection("team_members")
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    _isLoading.value = false
                    return@addOnSuccessListener
                }

                val batch = database.batch()
                for (doc in docs.documents) {
                    batch.delete(doc.reference)
                }
                batch.update(database.collection("teams").document(teamId), "memberCount", FieldValue.increment(-1))

                batch.commit()
                    .addOnSuccessListener {
                        if (_currentTeam.value?.id == teamId) {
                            _currentTeam.value = null
                            _members.value = emptyList()
                            _teamShifts.value = emptyList()
                            membersListener?.remove()
                            shiftsListener?.remove()
                        }
                        _isLoading.value = false
                        loadTeams()
                    }
                    .addOnFailureListener { e ->
                        _errorMessage.value = "Failed to leave team: ${e.message}"
                        _isLoading.value = false
                    }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to leave team: ${e.message}"
                _isLoading.value = false
            }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    override fun onCleared() {
        super.onCleared()
        teamsListener?.remove()
        membersListener?.remove()
        shiftsListener?.remove()
        memberJobsListener?.remove()
        messagesListener?.remove()
        swapRequestsListener?.remove()
        teamTasksListener?.remove()
        scheduleNotificationsListener?.remove()
    }
}
