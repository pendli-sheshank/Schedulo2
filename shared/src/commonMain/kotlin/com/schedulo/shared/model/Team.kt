package com.schedulo.shared.model

data class Team(
    var id: String = "",
    var name: String = "",
    var ownerId: String = "",
    var inviteCode: String = "",
    var createdAt: Long = 0,
    var memberCount: Int = 0
)

data class TeamMember(
    var id: String = "",
    var teamId: String = "",
    var userId: String = "",
    var role: String = "member",
    var joinedAt: Long = 0,
    var displayName: String = "",
    var email: String = ""
)

data class ShiftTask(
    var id: String = "",
    var title: String = "",
    var isCompleted: Boolean = false
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
