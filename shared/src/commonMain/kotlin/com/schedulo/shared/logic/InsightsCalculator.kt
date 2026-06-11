package com.schedulo.shared.logic

import com.schedulo.shared.model.Shift
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
