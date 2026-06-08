package com.schedulo.shared.logic

import com.schedulo.shared.model.Job
import com.schedulo.shared.model.Shift

fun calculateEarningsWithOvertime(shifts: List<Shift>, job: Job): Pair<Double, Double> {
    if (job.isGigWork) {
        return Pair(shifts.sumOf { it.totalEarned }, 0.0)
    }
    val totalHours = shifts.sumOf { it.durationHours }
    val threshold = job.overtimeThresholdHours
    val rate = job.defaultHourlyRate
    val multiplier = job.overtimeMultiplier

    return if (totalHours <= threshold) {
        Pair(totalHours * rate, 0.0)
    } else {
        val regularHours = threshold
        val overtimeHours = totalHours - threshold
        val regularEarnings = regularHours * rate
        val overtimeEarnings = overtimeHours * rate * multiplier
        Pair(regularEarnings, overtimeEarnings)
    }
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
