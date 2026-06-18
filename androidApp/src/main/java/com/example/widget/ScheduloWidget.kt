package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import android.graphics.Color as AndroidColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WidgetData(
    val nextShiftCompany: String = "",
    val nextShiftRole: String = "",
    val nextShiftStartMillis: Long = 0L,
    val nextShiftEndMillis: Long = 0L,
    val weeklyEarnings: Double = 0.0,
    val weeklyHours: Double = 0.0,
    val shiftCount: Int = 0
)

class ScheduloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduloWidget()
}

class ScheduloWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = readWidgetData(context)
        provideContent {
            GlanceTheme {
                WidgetContent(data)
            }
        }
    }

    private fun readWidgetData(context: Context): WidgetData {
        val prefs = context.getSharedPreferences("schedulo_widget_data", Context.MODE_PRIVATE)
        return WidgetData(
            nextShiftCompany = prefs.getString("next_shift_company", "") ?: "",
            nextShiftRole = prefs.getString("next_shift_role", "") ?: "",
            nextShiftStartMillis = prefs.getLong("next_shift_start", 0L),
            nextShiftEndMillis = prefs.getLong("next_shift_end", 0L),
            weeklyEarnings = java.lang.Double.longBitsToDouble(
                prefs.getLong("weekly_earnings_bits", java.lang.Double.doubleToLongBits(0.0))
            ),
            weeklyHours = java.lang.Double.longBitsToDouble(
                prefs.getLong("weekly_hours_bits", java.lang.Double.doubleToLongBits(0.0))
            ),
            shiftCount = prefs.getInt("shift_count", 0)
        )
    }

    @Composable
    private fun WidgetContent(data: WidgetData) {
        val primaryGreen = ColorProvider(AndroidColor.parseColor("#2D6A4F"))
        val textPrimary = ColorProvider(AndroidColor.parseColor("#1B1B1B"))
        val textSecondary = ColorProvider(AndroidColor.parseColor("#666666"))
        val textTertiary = ColorProvider(AndroidColor.parseColor("#888888"))
        val bgColor = ColorProvider(AndroidColor.WHITE)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(bgColor)
                .padding(14.dp)
        ) {
            // Header
            Text(
                text = "Next Shift",
                style = TextStyle(
                    color = primaryGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (data.nextShiftCompany.isEmpty() || data.nextShiftStartMillis == 0L) {
                // No upcoming shifts
                Text(
                    text = "No upcoming shifts",
                    style = TextStyle(
                        color = textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            } else {
                // Company name
                Text(
                    text = data.nextShiftCompany,
                    style = TextStyle(
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Role (if present)
                if (data.nextShiftRole.isNotEmpty()) {
                    Text(
                        text = data.nextShiftRole,
                        style = TextStyle(
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(2.dp))

                // Formatted start time
                Text(
                    text = formatShiftTime(data.nextShiftStartMillis, data.nextShiftEndMillis),
                    style = TextStyle(
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Weekly summary divider line (using a thin spacer with background)
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(AndroidColor.parseColor("#E0E0E0")))
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Weekly stats
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$${String.format(Locale.US, "%.2f", data.weeklyEarnings)}",
                    style = TextStyle(
                        color = primaryGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "${String.format(Locale.US, "%.1f", data.weeklyHours)} hrs",
                    style = TextStyle(
                        color = textTertiary,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "${data.shiftCount} shifts",
                    style = TextStyle(
                        color = textTertiary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }

    private fun formatShiftTime(startMillis: Long, endMillis: Long): String {
        val now = System.currentTimeMillis()
        val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
        val todayCal = Calendar.getInstance().apply { timeInMillis = now }
        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

        val dayPrefix = when {
            isSameDay(startCal, todayCal) -> "Today"
            isSameDay(startCal, Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1) }) -> "Tomorrow"
            else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(startMillis))
        }

        return "$dayPrefix ${timeFormat.format(Date(startMillis))} - ${timeFormat.format(Date(endMillis))}"
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
