package com.schedulo.shared.model

data class Team(
    var id: String = "",
    var name: String = "",
    var ownerId: String = "",
    var inviteCode: String = "",
    var createdAt: Long = 0,
    var memberCount: Int = 0,
    var weeklyCycleStartDay: String = "Monday",
    // Company the team works for (used as the company on team shifts).
    var companyName: String = "",
    // Working hours. When open24Hours is true the start/end are ignored.
    var open24Hours: Boolean = true,
    var workStartMinutes: Int = 0,   // minutes from midnight
    var workEndMinutes: Int = 0,
    // Structured location/address.
    var addressLine: String = "",
    var city: String = "",
    var region: String = "",         // state / province / region
    var postalCode: String = ""
)

data class TeamMember(
    var id: String = "",
    var teamId: String = "",
    var userId: String = "",
    var role: String = "member",
    var joinedAt: Long = 0,
    var displayName: String = "",
    var email: String = "",
    // Manager-set default pay rate for this member, prefilled when assigning shifts.
    var defaultHourlyRate: Double = 0.0
)

data class ShiftTask(
    var id: String = "",
    var title: String = "",
    var isCompleted: Boolean = false
)

/**
 * A standalone task assigned to a specific team member (independent of any shift).
 * Managers create these; the assignee (or manager) advances the [status], and every
 * change is appended to [history] so progress can be tracked over time.
 */
data class TeamTask(
    var id: String = "",
    var teamId: String = "",
    var title: String = "",
    var description: String = "",
    var assignedTo: String = "",       // member userId
    var assignedToName: String = "",
    var assignedBy: String = "",       // manager userId who created it
    var status: String = "pending",    // pending | in_progress | completed
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var history: List<TaskHistoryEntry> = emptyList()
)

data class TaskHistoryEntry(
    var status: String = "",
    var changedBy: String = "",
    var changedByName: String = "",
    var timestamp: Long = 0
)

data class TeamMessage(
    var id: String = "",
    var teamId: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var text: String = "",
    var isAnnouncement: Boolean = false,
    var isPinned: Boolean = false,
    var imageUrl: String = "",
    var seenBy: List<String> = emptyList(),
    var createdAt: Long = 0
)

data class SwapRequest(
    var id: String = "",
    var teamId: String = "",
    var requesterId: String = "",
    var requesterName: String = "",
    var requesterShiftId: String = "",
    var targetMemberId: String = "",
    var targetMemberName: String = "",
    var targetShiftId: String = "",
    var status: String = "pending",
    var createdAt: Long = 0,
    var resolvedAt: Long = 0,
    var resolvedBy: String = ""
)

data class TeamShift(
    var id: String = "",
    var teamId: String = "",
    var assignedTo: String = "",
    var assignedBy: String = "",
    var company: String = "",
    var role: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var hourlyRate: Double = 0.0,
    var notes: String = "",
    var status: String = "assigned",
    var tasks: List<ShiftTask> = emptyList()
) {
    val durationHours: Double
        get() = if (endTime > startTime) (endTime - startTime) / 3600000.0 else 0.0
}
