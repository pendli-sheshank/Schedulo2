package com.schedulo.shared.logic

import com.schedulo.shared.model.Shift
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsCalculatorTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun millisAt(year: Int, month: Month, day: Int, hour: Int = 9): Long =
        LocalDateTime(year, month, day, hour, 0).toInstant(tz).toEpochMilliseconds()

    private fun shift(start: Long, hours: Int = 8, rate: Double = 10.0): Shift =
        Shift(
            id = "s$start",
            userId = "u1",
            company = "Cafe",
            startTime = start,
            endTime = start + hours * 3_600_000L,
            hourlyRate = rate
        )

    // --- getMonthlyEarningsSummary ---

    @Test
    fun monthlyBucketsSpanYearBoundary() {
        val now = millisAt(2026, Month.FEBRUARY, 15)
        val shifts = listOf(
            shift(millisAt(2025, Month.DECEMBER, 20)), // 8h * $10 = $80
            shift(millisAt(2026, Month.JANUARY, 5)),
            shift(millisAt(2026, Month.JANUARY, 28)),
            shift(millisAt(2026, Month.FEBRUARY, 10))
        )

        val summary = getMonthlyEarningsSummary(shifts, months = 3, nowMillis = now)

        assertEquals(3, summary.size)
        assertEquals(listOf("Dec", "Jan", "Feb"), summary.map { it.label })
        assertEquals(listOf(1, 2, 1), summary.map { it.shiftCount })
        assertEquals(80.0, summary[0].earnings, 0.001)
        assertEquals(160.0, summary[1].earnings, 0.001)
    }

    @Test
    fun monthlySummaryExcludesUpcomingShifts() {
        val now = millisAt(2026, Month.JULY, 9)
        val shifts = listOf(
            shift(millisAt(2026, Month.JULY, 5)),
            shift(millisAt(2026, Month.JULY, 20)) // in the future
        )

        val summary = getMonthlyEarningsSummary(shifts, months = 1, nowMillis = now)

        assertEquals(1, summary.single().shiftCount)
    }

    @Test
    fun monthlyBucketBoundariesAreCalendarMonths() {
        val now = millisAt(2026, Month.MARCH, 31, hour = 23)
        val summary = getMonthlyEarningsSummary(emptyList(), months = 1, nowMillis = now)
        val bucket = summary.single()

        val expectedStart = LocalDate(2026, Month.MARCH, 1).atStartOfDayIn(tz).toEpochMilliseconds()
        val expectedEnd = LocalDate(2026, Month.MARCH, 1).plus(1, DateTimeUnit.MONTH)
            .atStartOfDayIn(tz).toEpochMilliseconds()
        assertEquals(expectedStart, bucket.periodStart)
        assertEquals(expectedEnd, bucket.periodEnd)
    }

    // --- getUpcomingEarningsProjection ---

    @Test
    fun projectionSumsOnlyUpcomingShifts() {
        val now = millisAt(2026, Month.JULY, 9)
        val next = millisAt(2026, Month.JULY, 10)
        val later = millisAt(2026, Month.JULY, 12)
        val shifts = listOf(
            shift(millisAt(2026, Month.JULY, 1)), // completed — excluded
            shift(next),                          // $80
            shift(later, hours = 4)               // $40
        )

        val projection = getUpcomingEarningsProjection(shifts, nowMillis = now)

        assertEquals(120.0, projection.earnings, 0.001)
        assertEquals(12.0, projection.hours, 0.001)
        assertEquals(2, projection.shiftCount)
        assertEquals(next, projection.nextShiftStart)
    }

    @Test
    fun projectionIncludesGigAndBonusEarnings() {
        val now = millisAt(2026, Month.JULY, 9)
        val gig = shift(millisAt(2026, Month.JULY, 10)).copy(isGig = true, customEarned = 55.0)
        val bonus = shift(millisAt(2026, Month.JULY, 11), hours = 4)
            .copy(bonusApplied = true, bonusAmount = 20.0) // 4h*$10 + $20

        val projection = getUpcomingEarningsProjection(listOf(gig, bonus), nowMillis = now)

        assertEquals(115.0, projection.earnings, 0.001)
    }

    @Test
    fun projectionIsEmptyWithNoUpcomingShifts() {
        val now = millisAt(2026, Month.JULY, 9)
        val projection = getUpcomingEarningsProjection(
            listOf(shift(millisAt(2026, Month.JULY, 1))), nowMillis = now
        )

        assertEquals(0.0, projection.earnings, 0.001)
        assertEquals(0, projection.shiftCount)
        assertNull(projection.nextShiftStart)
    }

    // --- getShiftsInPeriod ---

    @Test
    fun shiftsInPeriodFiltersAndSortsNewestFirst() {
        val start = millisAt(2026, Month.JUNE, 1, hour = 0)
        val end = millisAt(2026, Month.JULY, 1, hour = 0)
        val inside1 = shift(millisAt(2026, Month.JUNE, 5))
        val inside2 = shift(millisAt(2026, Month.JUNE, 20))
        val outside = shift(millisAt(2026, Month.JULY, 2))

        val result = getShiftsInPeriod(listOf(inside1, outside, inside2), start, end)

        assertEquals(listOf(inside2, inside1), result)
        assertTrue(outside !in result)
    }
}
