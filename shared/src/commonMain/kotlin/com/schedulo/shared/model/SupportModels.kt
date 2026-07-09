package com.schedulo.shared.model

data class WeekSummary(
    val weekStart: Long,
    val label: String,
    val hours: Double,
    val earnings: Double,
    val shiftCount: Int
)

// Generic earnings bucket for the Insights chart — a calendar week or month.
data class PeriodSummary(
    val periodStart: Long,
    val periodEnd: Long,
    val label: String,
    val hours: Double,
    val earnings: Double,
    val shiftCount: Int
)

data class UpcomingProjection(
    val earnings: Double,
    val hours: Double,
    val shiftCount: Int,
    val nextShiftStart: Long? = null
)

data class PayCycleOption(
    val cycleStart: Long,
    val cycleEnd: Long,
    val employer: String,
    val label: String,
    val shiftCount: Int,
    val isCurrent: Boolean
)

data class WeekDayEntry(
    val dayOffset: Int,
    val startH: Int,
    val startM: Int,
    val endH: Int,
    val endM: Int
)

enum class PayCycleStatus { UNPAID, PARTIAL, PAID }

data class WeeklyPayCycle(
    val startDate: Long,
    val endDate: Long,
    val shifts: List<Shift>,
    val totalEarned: Double,
    val status: PayCycleStatus
)
