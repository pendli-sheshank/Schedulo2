package com.schedulo.shared.logic

import com.schedulo.shared.model.Job
import com.schedulo.shared.model.Shift
import kotlin.test.Test
import kotlin.test.assertEquals

class ShiftCalculatorTest {

    private fun shift(startHour: Long, hours: Long, rate: Double): Shift =
        Shift(
            id = "s$startHour",
            userId = "u1",
            company = "Cafe",
            startTime = startHour * 3_600_000L,
            endTime = (startHour + hours) * 3_600_000L,
            hourlyRate = rate
        )

    private val job = Job(
        title = "Cafe",
        isGigWork = false,
        defaultHourlyRate = 12.0,
        overtimeThresholdHours = 40.0,
        overtimeMultiplier = 1.5
    )

    @Test
    fun earningsUseShiftStoredRateNotCurrentJobRate() {
        // Shifts were worked at $12/hr; the job's rate has since been raised to
        // $20/hr. Past earnings must stay priced at the stored $12/hr.
        val shifts = listOf(shift(0, 8, 12.0), shift(24, 8, 12.0))
        val raisedJob = job.copy(defaultHourlyRate = 20.0)

        val (regular, overtime) = calculateEarningsWithOvertime(shifts, raisedJob)

        assertEquals(16 * 12.0, regular)
        assertEquals(0.0, overtime)
    }

    @Test
    fun overtimePricedAtShiftStoredRate() {
        // 48 hours at a stored $10/hr with a 40h threshold: 40h regular +
        // 8h × 1.5. The job's current $12 default must not leak in.
        val shifts = (0 until 6).map { day -> shift(day * 24L, 8, 10.0) }

        val (regular, overtime) = calculateEarningsWithOvertime(shifts, job)

        assertEquals(40 * 10.0, regular)
        assertEquals(8 * 10.0 * 1.5, overtime)
    }

    @Test
    fun mixedRatesSplitOvertimeChronologically() {
        // Rate raised mid-cycle: first 40h at $10 fill the regular bucket, the
        // final 8h shift at $14 lands entirely in overtime at its own rate.
        val shifts = (0 until 5).map { day -> shift(day * 24L, 8, 10.0) } +
            shift(5 * 24L, 8, 14.0)

        val (regular, overtime) = calculateEarningsWithOvertime(shifts, job)

        assertEquals(40 * 10.0, regular)
        assertEquals(8 * 14.0 * 1.5, overtime)
    }

    @Test
    fun shiftStraddlingThresholdIsSplit() {
        // 36h worked, then a 8h shift: 4h regular + 4h overtime of that shift.
        val shifts = listOf(
            shift(0, 36, 10.0),
            shift(48, 8, 10.0)
        )

        val (regular, overtime) = calculateEarningsWithOvertime(shifts, job)

        assertEquals(40 * 10.0, regular)
        assertEquals(4 * 10.0 * 1.5, overtime)
    }

    @Test
    fun gigWorkSumsCustomEarningsWithoutOvertime() {
        val gigJob = job.copy(isGigWork = true)
        val gigShift = shift(0, 8, 0.0).copy(isGig = true, customEarned = 95.0)

        val (regular, overtime) = calculateEarningsWithOvertime(listOf(gigShift), gigJob)

        assertEquals(95.0, regular)
        assertEquals(0.0, overtime)
    }
}
