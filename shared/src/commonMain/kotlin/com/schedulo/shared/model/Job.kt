package com.schedulo.shared.model

import kotlinx.datetime.*

data class Job(
    var id: String = "",
    var userId: String = "",
    var title: String = "",
    var isGigWork: Boolean = false,
    var defaultHourlyRate: Double = 15.0,
    var goalHours: Double = 20.0,
    var goalType: String = "Hours",
    var weeklyCycleStartDay: String? = "Monday",
    var overtimeThresholdHours: Double = 40.0,
    var overtimeMultiplier: Double = 1.5
) {
    fun getStartOfCurrentCycle(targetMillis: Long = Clock.System.now().toEpochMilliseconds()): Long {
        val instant = Instant.fromEpochMilliseconds(targetMillis)
        var date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

        val targetDay = when (weeklyCycleStartDay?.lowercase() ?: "monday") {
            "sunday" -> DayOfWeek.SUNDAY
            "monday" -> DayOfWeek.MONDAY
            "tuesday" -> DayOfWeek.TUESDAY
            "wednesday" -> DayOfWeek.WEDNESDAY
            "thursday" -> DayOfWeek.THURSDAY
            "friday" -> DayOfWeek.FRIDAY
            "saturday" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.MONDAY
        }

        while (date.dayOfWeek != targetDay) {
            date = date.minus(1, DateTimeUnit.DAY)
        }

        val startOfDay = date.atStartOfDayIn(TimeZone.currentSystemDefault())
        return startOfDay.toEpochMilliseconds()
    }
}
