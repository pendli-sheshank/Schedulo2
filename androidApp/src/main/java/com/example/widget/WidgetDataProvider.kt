package com.example.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.example.Shift
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

object WidgetDataProvider {

    fun updateWidgetData(context: Context, shifts: List<Shift>) {
        val now = System.currentTimeMillis()

        // Find next upcoming shift (startTime in the future)
        val nextShift = shifts
            .filter { it.startTime > now }
            .minByOrNull { it.startTime }

        // Calculate weekly stats (current calendar week, Monday-based)
        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L

        val weekShifts = shifts.filter { it.startTime in weekStart until weekEnd }
        val weeklyEarnings = weekShifts.sumOf { it.totalEarned }
        val weeklyHours = weekShifts.sumOf { it.durationHours }
        val shiftCount = weekShifts.size

        // Write to SharedPreferences
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
            apply()
        }

        // Trigger widget update
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduloWidget().updateAll(context)
            } catch (_: Exception) {
                // Widget may not be placed yet; ignore
            }
        }
    }
}
