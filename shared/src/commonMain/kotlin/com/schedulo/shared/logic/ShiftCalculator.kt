package com.schedulo.shared.logic

import com.schedulo.shared.model.Job
import com.schedulo.shared.model.Shift

fun calculateEarningsWithOvertime(shifts: List<Shift>, job: Job): Pair<Double, Double> {
    if (job.isGigWork) {
        return Pair(shifts.sumOf { it.totalEarned }, 0.0)
    }
    // Each shift is priced at its own stored hourlyRate (the rate in effect when
    // it was worked) so editing the job's defaultHourlyRate never re-prices past
    // cycles. Hours past the overtime threshold are split chronologically.
    val threshold = job.overtimeThresholdHours
    val multiplier = job.overtimeMultiplier
    var hoursSoFar = 0.0
    var regularEarnings = 0.0
    var overtimeEarnings = 0.0
    for (shift in shifts.sortedBy { it.startTime }) {
        val hours = shift.durationHours
        val regularPortion = (threshold - hoursSoFar).coerceIn(0.0, hours)
        regularEarnings += regularPortion * shift.hourlyRate
        overtimeEarnings += (hours - regularPortion) * shift.hourlyRate * multiplier
        hoursSoFar += hours
    }
    return Pair(regularEarnings, overtimeEarnings)
}

fun detectConflicts(
    shifts: List<Shift>,
    startTime: Long,
    endTime: Long,
    excludeShiftId: String? = null
): List<Shift> {
    return shifts.filter { shift ->
        shift.id != excludeShiftId &&
            shift.startTime < endTime && shift.endTime > startTime
    }
}
