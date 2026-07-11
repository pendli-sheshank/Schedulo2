package com.example.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.Shift
import com.example.startOfWeekContaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object WidgetDataProvider {

    private const val TAG = "WidgetDataProvider"

    fun updateWidgetData(context: Context, shifts: List<Shift>, weekStartDay: String = "Monday") {
        val now = System.currentTimeMillis()

        val nextShift = shifts
            .filter { it.startTime > now }
            .minByOrNull { it.startTime }

        val weekStart = startOfWeekContaining(now, weekStartDay)
        val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L

        val weekShifts = shifts.filter { it.startTime in weekStart until weekEnd }
        val weeklyEarnings = weekShifts.sumOf { it.totalEarned }
        val weeklyHours = weekShifts.sumOf { it.durationHours }
        val shiftCount = weekShifts.size

        val prefs = context.getSharedPreferences("schedulo_widget_data", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("next_shift_company", nextShift?.company ?: "")
            putString("next_shift_role", nextShift?.role ?: "")
            putLong("next_shift_start", nextShift?.startTime ?: 0L)
            putLong("next_shift_end", nextShift?.endTime ?: 0L)
            putLong(
                "weekly_earnings_bits",
                java.lang.Double.doubleToLongBits(weeklyEarnings)
            )
            putLong(
                "weekly_hours_bits",
                java.lang.Double.doubleToLongBits(weeklyHours)
            )
            putInt("shift_count", shiftCount)
            commit()
        }

        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                ScheduloWidget().updateAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget", e)
            }
        }
    }
}
