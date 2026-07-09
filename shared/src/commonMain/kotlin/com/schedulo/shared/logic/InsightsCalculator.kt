package com.schedulo.shared.logic

import com.schedulo.shared.model.PeriodSummary
import com.schedulo.shared.model.Shift
import com.schedulo.shared.model.UpcomingProjection
import com.schedulo.shared.model.WeekSummary
import kotlinx.datetime.*

fun getWeeklyEarningsSummary(
    shifts: List<Shift>,
    weeks: Int = 8,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): List<WeekSummary> {
    val completedShifts = shifts.filter { it.startTime < nowMillis }
    val tz = TimeZone.currentSystemDefault()

    return (0 until weeks).map { offset ->
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
        var weekMonday = now
        while (weekMonday.dayOfWeek != DayOfWeek.MONDAY) {
            weekMonday = weekMonday.minus(1, DateTimeUnit.DAY)
        }
        weekMonday = weekMonday.minus(offset, DateTimeUnit.WEEK)

        val weekStart = weekMonday.atStartOfDayIn(tz).toEpochMilliseconds()
        val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L

        val weekShifts = completedShifts.filter { it.startTime in weekStart until weekEnd }

        val monthDay = weekMonday.month.name.take(3).lowercase()
            .replaceFirstChar { it.uppercase() } + " " +
            weekMonday.dayOfMonth.toString().padStart(2, '0')

        WeekSummary(
            weekStart = weekStart,
            label = monthDay,
            hours = weekShifts.sumOf { it.durationHours },
            earnings = weekShifts.sumOf { it.totalEarned },
            shiftCount = weekShifts.size
        )
    }.reversed()
}

fun getEarningsByEmployer(
    shifts: List<Shift>,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): Map<String, Double> {
    return shifts.filter { it.startTime < nowMillis }
        .groupBy { it.company }
        .mapValues { (_, shifts) -> shifts.sumOf { it.totalEarned } }
        .toList().sortedByDescending { it.second }.toMap()
}

// Calendar-month buckets of completed earnings, oldest first (mirrors the
// weekly summary but for the Insights monthly view).
fun getMonthlyEarningsSummary(
    shifts: List<Shift>,
    months: Int = 6,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): List<PeriodSummary> {
    val completedShifts = shifts.filter { it.startTime < nowMillis }
    val tz = TimeZone.currentSystemDefault()
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date

    return (0 until months).map { offset ->
        val firstOfMonth = LocalDate(today.year, today.month, 1).minus(offset, DateTimeUnit.MONTH)
        val firstOfNext = firstOfMonth.plus(1, DateTimeUnit.MONTH)
        val start = firstOfMonth.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = firstOfNext.atStartOfDayIn(tz).toEpochMilliseconds()

        val monthShifts = completedShifts.filter { it.startTime in start until end }
        val label = firstOfMonth.month.name.take(3).lowercase()
            .replaceFirstChar { it.uppercase() }

        PeriodSummary(
            periodStart = start,
            periodEnd = end,
            label = label,
            hours = monthShifts.sumOf { it.durationHours },
            earnings = monthShifts.sumOf { it.totalEarned },
            shiftCount = monthShifts.size
        )
    }.reversed()
}

// Projected earnings for shifts that haven't started yet.
fun getUpcomingEarningsProjection(
    shifts: List<Shift>,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): UpcomingProjection {
    val upcoming = shifts.filter { it.startTime >= nowMillis }
    return UpcomingProjection(
        earnings = upcoming.sumOf { it.totalEarned },
        hours = upcoming.sumOf { it.durationHours },
        shiftCount = upcoming.size,
        nextShiftStart = upcoming.minOfOrNull { it.startTime }
    )
}

fun getShiftsInPeriod(shifts: List<Shift>, start: Long, end: Long): List<Shift> =
    shifts.filter { it.startTime in start until end }.sortedByDescending { it.startTime }
