package com.schedulo.shared.model

data class Shift(
    var id: String = "",
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
    val durationHours: Double
        get() = if (endTime > startTime) (endTime - startTime) / 3600000.0 else 0.0

    val totalEarned: Double
        get() = if (isGig) customEarned else (durationHours * hourlyRate)
}
