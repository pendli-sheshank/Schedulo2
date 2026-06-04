package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val shifts by dashboardViewModel.shifts.collectAsState()
    val weeklySummary = remember(shifts) { dashboardViewModel.getWeeklyEarningsSummary(8) }
    val earningsByEmployer = remember(shifts) { dashboardViewModel.getEarningsByEmployer() }

    val now = System.currentTimeMillis()
    val completedShifts = shifts.filter { it.startTime < now }
    val totalEarnings = completedShifts.sumOf { it.totalEarned }
    val totalHours = completedShifts.sumOf { it.durationHours }
    val avgHourlyRate = if (totalHours > 0) totalEarnings / totalHours else 0.0

    val bestWeek = weeklySummary.maxByOrNull { it.earnings }
    val avgWeeklyEarnings = if (weeklySummary.isNotEmpty()) weeklySummary.sumOf { it.earnings } / weeklySummary.size else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryChip(Modifier.weight(1f), "Total Earned", "$${"%.0f".format(totalEarnings)}", PrimaryGreen)
                SummaryChip(Modifier.weight(1f), "Total Hours", "${"%.0f".format(totalHours)}h", AccentBlue)
                SummaryChip(Modifier.weight(1f), "Avg Rate", "$${"%.2f".format(avgHourlyRate)}/h", AccentOrange)
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Weekly Earnings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Last 8 weeks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    WeeklyBarChart(weeklySummary)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Earnings by Employer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    val total = earningsByEmployer.values.sum()
                    if (total > 0) {
                        earningsByEmployer.forEach { (employer, earnings) ->
                            val fraction = (earnings / total).toFloat()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(employer, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = PrimaryGreen,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$${"%.0f".format(earnings)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryGreen)
                                    Text("${"%.0f".format(fraction * 100)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        Text("No earnings data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Best Week", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (bestWeek != null && bestWeek.earnings > 0) "$${"%.0f".format(bestWeek.earnings)}" else "--",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PrimaryGreen
                        )
                        if (bestWeek != null && bestWeek.earnings > 0) {
                            Text(bestWeek.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Avg Weekly", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$${"%.0f".format(avgWeeklyEarnings)}",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AccentBlue
                        )
                        Text("per week", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryChip(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WeeklyBarChart(weeks: List<DashboardViewModel.WeekSummary>) {
    val maxEarnings = weeks.maxOfOrNull { it.earnings } ?: 1.0
    val barColor = PrimaryGreen
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val barWidth = size.width / weeks.size * 0.6f
        val spacing = size.width / weeks.size
        val chartHeight = size.height - 30f

        weeks.forEachIndexed { index, week ->
            val barHeight = if (maxEarnings > 0) (week.earnings / maxEarnings * chartHeight).toFloat() else 0f
            val x = index * spacing + (spacing - barWidth) / 2

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )

            val label = textMeasurer.measure(week.label, labelStyle)
            drawText(
                textLayoutResult = label,
                topLeft = Offset(x + barWidth / 2 - label.size.width / 2, chartHeight + 8f)
            )
        }
    }
}
