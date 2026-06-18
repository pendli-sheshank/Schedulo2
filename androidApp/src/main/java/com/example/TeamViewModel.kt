package com.example

import androidx.lifecycle.ViewModel
import com.example.ui.theme.PrimaryGreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.schedulo.shared.model.Team
import com.schedulo.shared.model.TeamMember
import com.schedulo.shared.model.TeamShift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

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

    private var membersListener: ListenerRegistration? = null
    private var shiftsListener: ListenerRegistration? = null

    fun clearError() {
        _errorMessage.value = null
    }

    fun loadTeams() {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        _isLoading.value = true
        _errorMessage.value = null

        database.collection("team_members")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { memberDocs ->
                if (memberDocs.isEmpty) {
                    _teams.value = emptyList()
                    _currentTeam.value = null
                    _isLoading.value = false
                    return@addOnSuccessListener
                }

                val teamIds = memberDocs.documents.mapNotNull { it.getString("teamId") }
                if (teamIds.isEmpty()) {
                    _teams.value = emptyList()
                    _currentTeam.value = null
                    _isLoading.value = false
                    return@addOnSuccessListener
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
                                    memberCount = doc.getLong("memberCount")?.toInt() ?: 0
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
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to load team memberships: ${e.message}"
                _isLoading.value = false
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
                            email = doc.getString("email") ?: ""
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
                            status = doc.getString("status") ?: "assigned"
                        )
                    }.sortedByDescending { it.startTime }
                }
            }
    }

    fun createTeam(name: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        if (name.isBlank()) {
            _errorMessage.value = "Team name cannot be empty."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        val teamId = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        val now = System.currentTimeMillis()

        val teamData = hashMapOf(
            "name" to name.trim(),
            "ownerId" to uid,
            "inviteCode" to inviteCode,
            "createdAt" to now,
            "memberCount" to 1
        )

        val email = auth?.currentUser?.email ?: ""
        val displayName = auth?.currentUser?.displayName ?: ""

        val memberData = hashMapOf(
            "teamId" to teamId,
            "userId" to uid,
            "role" to "manager",
            "joinedAt" to now,
            "displayName" to displayName,
            "email" to email
        )

        val batch = database.batch()
        batch.set(database.collection("teams").document(teamId), teamData)
        batch.set(database.collection("team_members").document(), memberData)

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

    fun joinTeam(inviteCode: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        if (inviteCode.isBlank()) {
            _errorMessage.value = "Invite code cannot be empty."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        database.collection("teams")
            .whereEqualTo("inviteCode", inviteCode.trim().uppercase())
            .get()
            .addOnSuccessListener { teamDocs ->
                if (teamDocs.isEmpty) {
                    _errorMessage.value = "No team found with that invite code."
                    _isLoading.value = false
                    return@addOnSuccessListener
                }

                val teamDoc = teamDocs.documents.first()
                val teamId = teamDoc.id

                // Check if already a member
                database.collection("team_members")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener { existingMember ->
                        if (!existingMember.isEmpty) {
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
                            "email" to email
                        )

                        val batch = database.batch()
                        batch.set(database.collection("team_members").document(), memberData)
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
        notes: String
    ) {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val team = _currentTeam.value ?: return

        if (company.isBlank()) {
            _errorMessage.value = "Company name is required."
            return
        }

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
            "status" to "assigned"
        )

        database.collection("team_shifts").document()
            .set(shiftData)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to assign shift: ${e.message}"
            }
    }

    fun updateShiftStatus(shiftId: String, status: String) {
        val database = db ?: return
        database.collection("team_shifts").document(shiftId)
            .update("status", status)
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to update shift status: ${e.message}"
            }
    }

    fun deleteTeamShift(shiftId: String) {
        val database = db ?: return
        val previousShifts = _teamShifts.value
        _teamShifts.value = _teamShifts.value.filter { it.id != shiftId }
        database.collection("team_shifts").document(shiftId)
            .delete()
            .addOnFailureListener { e ->
                _teamShifts.value = previousShifts
                _errorMessage.value = "Failed to delete shift: ${e.message}"
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
        membersListener?.remove()
        shiftsListener?.remove()
    }
}
