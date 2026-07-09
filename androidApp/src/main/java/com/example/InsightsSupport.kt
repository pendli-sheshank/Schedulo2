package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val shifts by dashboardViewModel.shifts.collectAsState()

    // View customization state
    var viewMode by remember { mutableStateOf("Weekly") } // Weekly | Monthly
    var weekCount by remember { mutableStateOf(8) }
    var monthCount by remember { mutableStateOf(6) }
    var employerFilter by remember { mutableStateOf<String?>(null) }
    var selectedPeriodIndex by remember { mutableStateOf<Int?>(null) }

    val periodSummary = remember(shifts, viewMode, weekCount, monthCount, employerFilter) {
        if (viewMode == "Weekly") dashboardViewModel.getWeeklyPeriodSummary(weekCount, employerFilter)
        else dashboardViewModel.getMonthlyPeriodSummary(monthCount, employerFilter)
    }
    // Default to the latest period; clamp when the period count shrinks.
    val effectiveSelectedIndex = selectedPeriodIndex?.coerceAtMost(periodSummary.lastIndex)
        ?: periodSummary.lastIndex
    val selectedPeriod = periodSummary.getOrNull(effectiveSelectedIndex)
    val selectedPeriodShifts = remember(shifts, selectedPeriod, employerFilter) {
        selectedPeriod?.let {
            dashboardViewModel.getShiftsInPeriod(it.periodStart, it.periodEnd, employerFilter)
                .filter { shift -> shift.startTime < System.currentTimeMillis() }
        } ?: emptyList()
    }

    val earningsByEmployer = remember(shifts) { dashboardViewModel.getEarningsByEmployer() }
    val projection = remember(shifts, employerFilter) { dashboardViewModel.getUpcomingProjection(employerFilter) }
    val upcomingShifts = remember(shifts, employerFilter) {
        val now = System.currentTimeMillis()
        shifts.filter {
            it.startTime >= now &&
                (employerFilter == null || it.company.equals(employerFilter, ignoreCase = true))
        }.sortedBy { it.startTime }.take(5)
    }

    val totalEarnings = periodSummary.sumOf { it.earnings }
    val totalHours = periodSummary.sumOf { it.hours }
    val avgHourlyRate = if (totalHours > 0) totalEarnings / totalHours else 0.0

    val bestPeriod = periodSummary.maxByOrNull { it.earnings }
    val avgPeriodEarnings = if (periodSummary.isNotEmpty()) totalEarnings / periodSummary.size else 0.0
    val periodNoun = if (viewMode == "Weekly") "week" else "month"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earnings Insights") },
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
            // Weekly / Monthly toggle (same pattern as Plan's Month/Week/Day toggle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Weekly", "Monthly").forEach { mode ->
                    val selected = viewMode == mode
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewMode = mode; selectedPeriodIndex = null }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mode, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            // Period count selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val options = if (viewMode == "Weekly") listOf(4, 8, 12) else listOf(3, 6, 12)
                val current = if (viewMode == "Weekly") weekCount else monthCount
                options.forEach { count ->
                    val selected = current == count
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (viewMode == "Weekly") weekCount = count else monthCount = count
                            selectedPeriodIndex = null
                        },
                        label = { Text("$count ${if (viewMode == "Weekly") "wks" else "mos"}", fontSize = 12.sp) }
                    )
                }
            }

            // Employer filter chips
            if (earningsByEmployer.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = employerFilter == null,
                        onClick = { employerFilter = null },
                        label = { Text("All employers", fontSize = 12.sp) }
                    )
                    earningsByEmployer.keys.forEach { employer ->
                        FilterChip(
                            selected = employerFilter == employer,
                            onClick = { employerFilter = if (employerFilter == employer) null else employer },
                            label = { Text(employer, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryChip(Modifier.weight(1f), "Total Earned", "$${"%.0f".format(totalEarnings)}")
                SummaryChip(Modifier.weight(1f), "Total Hours", "${"%.0f".format(totalHours)}h")
                SummaryChip(Modifier.weight(1f), "Avg Rate", "$${"%.2f".format(avgHourlyRate)}/hr")
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${if (viewMode == "Weekly") "Weekly" else "Monthly"} Earnings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap a bar to see that $periodNoun's shifts",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PeriodBarChart(
                        periods = periodSummary,
                        selectedIndex = effectiveSelectedIndex,
                        onBarTap = { index -> selectedPeriodIndex = index }
                    )
                }
            }

            // Selected-period shift list
            if (selectedPeriod != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Shifts · ${selectedPeriod.label}",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp
                            )
                            Text(
                                "$${"%.2f".format(selectedPeriod.earnings)}",
                                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (selectedPeriodShifts.isEmpty()) {
                            Text(
                                "No completed shifts in this $periodNoun.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            selectedPeriodShifts.forEach { shift -> InsightShiftRow(shift) }
                        }
                    }
                }
            }

            // Upcoming projections
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upcoming Projections", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryChip(Modifier.weight(1f), "Projected", "$${"%.0f".format(projection.earnings)}")
                        SummaryChip(Modifier.weight(1f), "Hours", "${"%.0f".format(projection.hours)}h")
                        SummaryChip(Modifier.weight(1f), "Shifts", "${projection.shiftCount}")
                    }
                    if (upcomingShifts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        upcomingShifts.forEach { shift -> InsightShiftRow(shift, isProjection = true) }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "No upcoming shifts scheduled.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        Text("Best ${periodNoun.replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (bestPeriod != null && bestPeriod.earnings > 0) "$${"%.0f".format(bestPeriod.earnings)}" else "--",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PrimaryGreen
                        )
                        if (bestPeriod != null && bestPeriod.earnings > 0) {
                            Text(bestPeriod.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Avg ${periodNoun.replaceFirstChar { it.uppercase() }}ly", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$${"%.0f".format(avgPeriodEarnings)}",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AccentBlue
                        )
                        Text("per $periodNoun", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryChip(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryGreen)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InsightShiftRow(shift: Shift, isProjection: Boolean = false) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM dd · h:mm a", Locale.US) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background((if (shift.isGig) AccentOrange else AccentBlue).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (shift.isGig) Icons.Default.DeliveryDining else Icons.Default.Business,
                null,
                tint = if (shift.isGig) AccentOrange else AccentBlue,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(shift.company, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(dateFormat.format(Date(shift.startTime)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (isProjection) "Est. " else "") + "$${"%.2f".format(shift.totalEarned)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isProjection) AccentBlue else PrimaryGreen
            )
            Text("${"%.1f".format(shift.durationHours)}h", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PeriodBarChart(
    periods: List<DashboardViewModel.PeriodSummary>,
    selectedIndex: Int,
    onBarTap: (Int) -> Unit
) {
    if (periods.isEmpty()) return
    val maxEarnings = periods.maxOfOrNull { it.earnings } ?: 1.0
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val valueStyle = TextStyle(fontSize = 10.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(periods) {
                detectTapGestures { offset ->
                    val spacing = size.width.toFloat() / periods.size
                    val index = (offset.x / spacing).toInt().coerceIn(0, periods.lastIndex)
                    onBarTap(index)
                }
            }
    ) {
        val barWidth = size.width / periods.size * 0.6f
        val spacing = size.width / periods.size
        val topPadding = 20f // room for the selected bar's value label
        val chartHeight = size.height - 30f

        periods.forEachIndexed { index, period ->
            val isSelected = index == selectedIndex
            val barHeight = if (maxEarnings > 0) ((period.earnings / maxEarnings) * (chartHeight - topPadding)).toFloat() else 0f
            val x = index * spacing + (spacing - barWidth) / 2
            val barColor = if (isSelected) AccentBlue else PrimaryGreen

            if (period.earnings > 0) {
                drawRoundRect(
                    color = if (isSelected) barColor else barColor.copy(alpha = 0.75f),
                    topLeft = Offset(x, chartHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            } else {
                // Baseline tick so a zero-earning period reads as loaded data, not a gap
                drawRoundRect(
                    color = (if (isSelected) AccentBlue else Color.Gray).copy(alpha = 0.4f),
                    topLeft = Offset(x, chartHeight - 4f),
                    size = Size(barWidth, 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                )
            }

            if (isSelected) {
                val value = textMeasurer.measure("$${"%.0f".format(period.earnings)}", valueStyle)
                drawText(
                    textLayoutResult = value,
                    topLeft = Offset(
                        (x + barWidth / 2 - value.size.width / 2).coerceIn(0f, size.width - value.size.width),
                        (chartHeight - barHeight - value.size.height - 4f).coerceAtLeast(0f)
                    )
                )
            }

            val label = textMeasurer.measure(period.label, labelStyle)
            drawText(
                textLayoutResult = label,
                topLeft = Offset(x + barWidth / 2 - label.size.width / 2, chartHeight + 8f)
            )
        }
    }
}
