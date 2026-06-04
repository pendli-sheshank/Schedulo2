package com.example

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.SecondaryGreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainLayout(
    navController: NavHostController,
    currentRoute: String,
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        dashboardViewModel.loadShifts()
    }

    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(currentRoute = currentRoute, onNavigate = {
                navController.navigate(it) {
                    popUpTo("dashboard") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        },
        floatingActionButton = {
            Box {
                FabPlaceholder(onClick = { showAddMenu = true })
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add Single Shift") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { showAddMenu = false; navController.navigate("add_shift") }
                    )
                    DropdownMenuItem(
                        text = { Text("Plan Entire Week") },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, null) },
                        onClick = { showAddMenu = false; navController.navigate("add_week_plan") }
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        when (currentRoute) {
            "dashboard" -> DashboardScreen(
                modifier = Modifier.padding(innerPadding),
                authViewModel = authViewModel,
                dashboardViewModel = dashboardViewModel,
                onNavigateToLogin = {
                    navController.navigate("login") { popUpTo("dashboard") { inclusive = true } }
                },
                onEditShift = { id -> navController.navigate("add_shift?shiftId=$id") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToPay = {
                    navController.navigate("pay") {
                        popUpTo("dashboard") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            "plan" -> PlanScreen(
                modifier = Modifier.padding(innerPadding),
                dashboardViewModel = dashboardViewModel,
                onEditShift = { id -> navController.navigate("add_shift?shiftId=$id") }
            )
            "jobs" -> JobsScreen(modifier = Modifier.padding(innerPadding), dashboardViewModel = dashboardViewModel)
            "pay" -> PayScreen(modifier = Modifier.padding(innerPadding), dashboardViewModel = dashboardViewModel)
        }
    }
}

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    dashboardViewModel: DashboardViewModel,
    onEditShift: (String) -> Unit
) {
    val shifts by dashboardViewModel.shifts.collectAsState(initial = emptyList())
    val jobs by dashboardViewModel.jobs.collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()

    var selectedTabIndex by remember { mutableStateOf(0) }

    val weekFormat = remember { SimpleDateFormat("MMM dd", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val dayFormat = remember { SimpleDateFormat("EEE, MMM dd", Locale.US) }

    val upcomingShifts = shifts.filter { it.startTime >= now }.sortedBy { it.startTime }
    val previousShifts = shifts.filter { it.startTime < now }.sortedByDescending { it.startTime }
    val activeShifts = if (selectedTabIndex == 0) upcomingShifts else previousShifts

    val weeklyGroups = remember(activeShifts) {
        activeShifts.groupBy { shift ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = shift.startTime
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedMap(if (selectedTabIndex == 0) compareBy { it } else compareByDescending { it })
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PrimaryGreen,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 },
                text = { Text("Upcoming (${upcomingShifts.size})", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 },
                text = { Text("Previous (${previousShifts.size})", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        if (activeShifts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedTabIndex == 0) Icons.Default.EventAvailable else Icons.Default.History,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (selectedTabIndex == 0) "No upcoming shifts planned." else "No previous shifts recorded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weeklyGroups.forEach { (weekStart, weekShifts) ->
                    val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L
                    val weekRangeStr = "${weekFormat.format(Date(weekStart))} – ${weekFormat.format(Date(weekEnd - 1000L))}"
                    val weekTotalHours = weekShifts.sumOf { it.durationHours }
                    val weekTotalEarned = weekShifts.sumOf { it.totalEarned }
                    val isCurrentWeek = now in weekStart until weekEnd

                    item(key = "header_$weekStart") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentWeek) PrimaryGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, null, tint = if (isCurrentWeek) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(weekRangeStr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                        Text("${weekShifts.size} shift${if (weekShifts.size != 1) "s" else ""} · ${"%.1f".format(weekTotalHours)} hrs",
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$${"%.2f".format(weekTotalEarned)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                    if (isCurrentWeek) { Text("This Week", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen) }
                                }
                            }
                        }
                    }

                    items(weekShifts, key = { it.id }) { shift ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEditShift(shift.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(if (shift.isGig) AccentOrange else AccentBlue))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (shift.isGig) "${shift.company} (Gig)" else shift.company, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(dayFormat.format(Date(shift.startTime)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${timeFormat.format(Date(shift.startTime))} → ${timeFormat.format(Date(shift.endTime))} · ${"%.1f".format(shift.durationHours)} hrs",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$${"%.2f".format(shift.totalEarned)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                    if (shift.reminderBeforeMinutes > 0 && selectedTabIndex == 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryGreen.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("${shift.reminderBeforeMinutes}m", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(modifier: Modifier = Modifier, dashboardViewModel: DashboardViewModel) {
    val jobs by dashboardViewModel.jobs.collectAsState(initial = emptyList())
    val shifts by dashboardViewModel.shifts.collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }
    var editingJobId by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf("") }
    var isGigWork by remember { mutableStateOf(false) }
    var rateStr by remember { mutableStateOf("15.0") }
    var goalHoursStr by remember { mutableStateOf("20.0") }
    var goalType by remember { mutableStateOf("Hours") }
    var weeklyCycleStartDay by remember { mutableStateOf("Monday") }
    var overtimeThresholdStr by remember { mutableStateOf("40.0") }
    var overtimeMultiplierStr by remember { mutableStateOf("1.5") }
    var daysExpanded by remember { mutableStateOf(false) }
    val daysOfWeek = remember { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingJobId == null) "Add Employer Job" else "Edit Employer Job") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Job / Employer Title") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Is Gig Work (e.g. DoorDash)?", modifier = Modifier.weight(1f))
                        Switch(checked = isGigWork, onCheckedChange = { isGigWork = it })
                    }

                    if (!isGigWork) {
                        OutlinedTextField(value = rateStr, onValueChange = { rateStr = it }, label = { Text("Hourly Rate (\$)") },
                            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = weeklyCycleStartDay, onValueChange = {}, label = { Text("Weekly Cycle Start Day") },
                                readOnly = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = daysExpanded) })
                            Box(modifier = Modifier.matchParentSize().clickable { daysExpanded = true })
                            DropdownMenu(expanded = daysExpanded, onDismissRequest = { daysExpanded = false }) {
                                daysOfWeek.forEach { day ->
                                    DropdownMenuItem(text = { Text(day) }, onClick = { weeklyCycleStartDay = day; daysExpanded = false })
                                }
                            }
                        }

                        OutlinedTextField(value = overtimeThresholdStr, onValueChange = { overtimeThresholdStr = it },
                            label = { Text("Overtime After (hrs/week)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = overtimeMultiplierStr, onValueChange = { overtimeMultiplierStr = it },
                            label = { Text("Overtime Rate Multiplier") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Weekly Target Type:", modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Hours", "Earnings").forEach { type ->
                                FilterChip(selected = goalType == type, onClick = { goalType = type }, label = { Text(type) })
                            }
                        }
                    }

                    OutlinedTextField(value = goalHoursStr, onValueChange = { goalHoursStr = it },
                        label = { Text(if (goalType == "Hours") "Weekly Hours Target" else "Weekly Earnings Target (\$)") },
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalRate = if (isGigWork) 0.0 else (rateStr.toDoubleOrNull() ?: 15.0)
                    val finalGoal = goalHoursStr.toDoubleOrNull() ?: 20.0
                    val finalOT = overtimeThresholdStr.toDoubleOrNull() ?: 40.0
                    val finalOTM = overtimeMultiplierStr.toDoubleOrNull() ?: 1.5
                    if (editingJobId == null) dashboardViewModel.addJob(title, isGigWork, finalRate, finalGoal, goalType, weeklyCycleStartDay, finalOT, finalOTM)
                    else dashboardViewModel.updateJob(editingJobId!!, title, isGigWork, finalRate, finalGoal, goalType, weeklyCycleStartDay, finalOT, finalOTM)
                    showDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Employers & Jobs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = {
                editingJobId = null; title = ""; isGigWork = false; rateStr = "15.0"; goalHoursStr = "20.0"
                goalType = "Hours"; weeklyCycleStartDay = "Monday"; overtimeThresholdStr = "40.0"; overtimeMultiplierStr = "1.5"
                showDialog = true
            }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen), shape = RoundedCornerShape(12.dp)) { Text("+ Add Job") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (jobs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No employers added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(jobs) { job ->
                    val cycleStart = job.getStartOfCurrentCycle()
                    val cycleEnd = cycleStart + 7 * 24 * 60 * 60 * 1000L
                    val now = System.currentTimeMillis()
                    val jobShifts = shifts.filter {
                        it.company.equals(job.title, ignoreCase = true) && it.startTime >= cycleStart && it.startTime < cycleEnd && it.startTime < now
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            editingJobId = job.id; title = job.title; isGigWork = job.isGigWork; rateStr = job.defaultHourlyRate.toString()
                            goalHoursStr = job.goalHours.toString(); goalType = job.goalType; weeklyCycleStartDay = job.weeklyCycleStartDay ?: "Monday"
                            overtimeThresholdStr = job.overtimeThresholdHours.toString(); overtimeMultiplierStr = job.overtimeMultiplier.toString()
                            showDialog = true
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(job.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (job.isGigWork) AccentOrange.copy(alpha = 0.12f) else AccentBlue.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(if (job.isGigWork) "Gig" else "Hourly", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (job.isGigWork) AccentOrange else AccentBlue)
                                    }
                                    IconButton(onClick = { dashboardViewModel.deleteJob(job.id) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, "Delete Job", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("SHIFTS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${jobShifts.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                                Column { Text("HOURS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${"%.1f".format(jobShifts.sumOf { it.durationHours })} hrs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
                                Column(horizontalAlignment = Alignment.End) { Text("EARNED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$${"%.2f".format(jobShifts.sumOf { it.totalEarned })}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen) }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Weekly Target: ${if (job.goalType == "Hours") "${job.goalHours} hrs" else "$${job.goalHours}"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!job.isGigWork) Text("Base Rate: $${job.defaultHourlyRate}/hr", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!job.isGigWork) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val cycleStartDay = job.weeklyCycleStartDay ?: "Monday"
                                Text("Pay Cycle: $cycleStartDay to ${
                                    when (cycleStartDay.lowercase(Locale.US)) {
                                        "monday" -> "Sunday"; "tuesday" -> "Monday"; "wednesday" -> "Tuesday"; "thursday" -> "Wednesday"
                                        "friday" -> "Thursday"; "saturday" -> "Friday"; "sunday" -> "Saturday"; else -> "Sunday"
                                    }
                                }", fontSize = 12.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class PayCycleStatus { UPCOMING, PENDING_HOLD, DUE, PAID }

data class WeeklyPayCycle(val startDate: Long, val endDate: Long, val shifts: List<Shift>, val totalEarned: Double, val status: PayCycleStatus)

fun getCycleStartAndEndForShift(shift: Shift, jobs: List<Job>): Pair<Long, Long> {
    val job = jobs.firstOrNull { it.title.lowercase(Locale.US) == shift.company.lowercase(Locale.US) }
    val startDay = job?.weeklyCycleStartDay ?: "Monday"
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = shift.startTime
    calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
    val targetDayOfWeek = when (startDay.lowercase(Locale.US)) {
        "sunday" -> Calendar.SUNDAY; "monday" -> Calendar.MONDAY; "tuesday" -> Calendar.TUESDAY; "wednesday" -> Calendar.WEDNESDAY
        "thursday" -> Calendar.THURSDAY; "friday" -> Calendar.FRIDAY; "saturday" -> Calendar.SATURDAY; else -> Calendar.MONDAY
    }
    while (calendar.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) { calendar.add(Calendar.DAY_OF_YEAR, -1) }
    val cycleStart = calendar.timeInMillis
    return Pair(cycleStart, cycleStart + 7L * 24 * 60 * 60 * 1000L)
}

fun groupShiftsIntoCycles(shifts: List<Shift>, jobs: List<Job>, now: Long): List<WeeklyPayCycle> {
    val nonGigShifts = shifts.filter { !it.isGig }
    val cyclesMap = mutableMapOf<Pair<Long, Long>, MutableList<Shift>>()
    for (shift in nonGigShifts) {
        val key = getCycleStartAndEndForShift(shift, jobs)
        cyclesMap.getOrPut(key) { mutableListOf() }.add(shift)
    }
    val holdDays = 4L * 24 * 60 * 60 * 1000L
    return cyclesMap.map { (key, shiftList) ->
        val (start, end) = key
        val holdEndMillis = end + holdDays
        val status = when {
            now < end -> PayCycleStatus.UPCOMING
            now in end until holdEndMillis -> PayCycleStatus.PENDING_HOLD
            else -> if (shiftList.isNotEmpty() && shiftList.all { it.isPaid }) PayCycleStatus.PAID else PayCycleStatus.DUE
        }
        WeeklyPayCycle(start, end, shiftList.sortedBy { it.startTime }, shiftList.sumOf { it.totalEarned }, status)
    }.sortedByDescending { it.startDate }
}

@Composable
fun PayScreen(modifier: Modifier = Modifier, dashboardViewModel: DashboardViewModel) {
    val shifts by dashboardViewModel.shifts.collectAsState(initial = emptyList())
    val jobs by dashboardViewModel.jobs.collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val cycles = remember(shifts, jobs, now) { groupShiftsIntoCycles(shifts, jobs, now) }

    val gigShifts = remember(shifts) { shifts.filter { it.isGig } }
    val gigTotalEarned = gigShifts.sumOf { it.totalEarned }

    val totalPaid = shifts.filter { it.isPaid }.sumOf { it.totalEarned }
    val totalPendingHold = cycles.filter { it.status == PayCycleStatus.PENDING_HOLD }.sumOf { it.totalEarned }
    val totalDue = cycles.filter { it.status == PayCycleStatus.DUE }.sumOf { cycle -> cycle.shifts.filter { !it.isPaid }.sumOf { it.totalEarned } }
    val upcomingEarned = cycles.filter { it.status == PayCycleStatus.UPCOMING }.sumOf { it.totalEarned }

    val cycleFormat = remember { SimpleDateFormat("MMM dd", Locale.US) }
    val fullDateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val shiftTimeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    var expandedCycleStart by remember { mutableStateOf<Long?>(null) }
    var cycleToConfirmPaid by remember { mutableStateOf<WeeklyPayCycle?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Pay & Earnings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Out-of-Pocket / Due Payout", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$${"%.2f".format(totalDue)}", fontSize = 42.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Received", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Text("$${"%.2f".format(totalPaid)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("On Hold", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Text("$${"%.2f".format(totalPendingHold)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Upcoming", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Text("$${"%.2f".format(upcomingEarned)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (gigShifts.isNotEmpty()) {
            item {
                Text("Gig Earnings (Direct Payout)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.08f)), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${gigShifts.size} Gig Shift${if (gigShifts.size == 1) "" else "s"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Paid daily via direct payout", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$${"%.2f".format(gigTotalEarned)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AccentOrange)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PrimaryGreen.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text("PAID", fontSize = 10.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (cycles.isEmpty() && gigShifts.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No shifts reported yet.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    }
                }
            }
        }

        if (cycles.isNotEmpty()) {
            item { Text("Weekly Payroll Cycles", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Spacer(modifier = Modifier.height(12.dp)) }
            items(cycles) { cycle ->
                val cycleRangeStr = "${cycleFormat.format(Date(cycle.startDate))} – ${cycleFormat.format(Date(cycle.endDate - 1000L))}"
                val holdEndMillis = cycle.endDate + 4L * 24 * 60 * 60 * 1000L
                val payWindowEndMillis = holdEndMillis + 7L * 24 * 60 * 60 * 1000L
                val payWindowStartStr = fullDateFormat.format(Date(holdEndMillis))
                val payWindowEndStr = fullDateFormat.format(Date(payWindowEndMillis - 1000L))
                val isExpanded = expandedCycleStart == cycle.startDate

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { expandedCycleStart = if (isExpanded) null else cycle.startDate },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, when (cycle.status) {
                        PayCycleStatus.DUE -> PrimaryGreen.copy(alpha = 0.5f); PayCycleStatus.PENDING_HOLD -> AccentOrange.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outline
                    })
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cycleRangeStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${cycle.shifts.size} Work Shift${if (cycle.shifts.size == 1) "" else "s"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$${"%.2f".format(cycle.totalEarned)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryGreen)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(when (cycle.status) {
                                    PayCycleStatus.PAID -> PrimaryGreen.copy(alpha = 0.12f); PayCycleStatus.DUE -> PrimaryGreen.copy(alpha = 0.08f)
                                    PayCycleStatus.PENDING_HOLD -> AccentOrange.copy(alpha = 0.12f); PayCycleStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
                                }).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(when (cycle.status) {
                                        PayCycleStatus.PAID -> "RECEIVED"; PayCycleStatus.DUE -> "DUE"; PayCycleStatus.PENDING_HOLD -> "ON HOLD"; PayCycleStatus.UPCOMING -> "ACTIVE"
                                    }, fontSize = 10.sp, fontWeight = FontWeight.Black, color = when (cycle.status) {
                                        PayCycleStatus.PAID -> PrimaryGreen; PayCycleStatus.DUE -> PrimaryGreen; PayCycleStatus.PENDING_HOLD -> AccentOrange
                                        PayCycleStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                                }
                            }
                        }

                        if (cycle.status == PayCycleStatus.PENDING_HOLD) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.08f))) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Payment Hold Active", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Payment window: $payWindowStartStr – $payWindowEndStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                    val daysLeft = ((holdEndMillis - now) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
                                    Text(if (daysLeft > 0) "$daysLeft day${if (daysLeft != 1) "s" else ""} until payout window" else "Payout window is now open",
                                        fontSize = 11.sp, color = if (daysLeft > 0) MaterialTheme.colorScheme.onSurfaceVariant else PrimaryGreen, fontWeight = if (daysLeft == 0) FontWeight.Medium else FontWeight.Normal)
                                }
                            }
                        }

                        if (cycle.status == PayCycleStatus.DUE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.06f))) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Payment window: $payWindowStartStr – $payWindowEndStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { cycleToConfirmPaid = cycle }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(40.dp)) {
                                Text("Mark Entire Week as Paid", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Timesheet Details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                            cycle.shifts.forEach { shift ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(shift.company, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                            Text(SimpleDateFormat("EEE, MMM dd", Locale.US).format(Date(shift.startTime)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                            Text("${shiftTimeFormat.format(Date(shift.startTime))} → ${shiftTimeFormat.format(Date(shift.endTime))} · ${"%.1f".format(shift.durationHours)} hrs",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("$${"%.2f".format(shift.totalEarned)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            if (cycle.status != PayCycleStatus.UPCOMING) {
                                                Checkbox(checked = shift.isPaid, onCheckedChange = { dashboardViewModel.toggleShiftPaidStatus(shift.id, it) }, modifier = Modifier.size(24.dp))
                                            } else {
                                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text("Est", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                                Text("Tap to view timesheet details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (cycleToConfirmPaid != null) {
        val cycle = cycleToConfirmPaid!!
        val cycleRangeStr = "${cycleFormat.format(Date(cycle.startDate))} – ${cycleFormat.format(Date(cycle.endDate - 1000L))}"
        AlertDialog(
            onDismissRequest = { cycleToConfirmPaid = null },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen, modifier = Modifier.size(36.dp)) },
            title = { Text("Confirm Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Mark the week of $cycleRangeStr ($${"%.2f".format(cycle.totalEarned)}) as Paid?", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(onClick = { dashboardViewModel.markCycleAsPaid(cycle.shifts.map { it.id }, true); cycleToConfirmPaid = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) { Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { cycleToConfirmPaid = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    dashboardViewModel: DashboardViewModel,
    authViewModel: AuthViewModel? = null,
    onBack: () -> Unit
) {
    val currentCompany by dashboardViewModel.defaultCompany.collectAsState()
    val currentRate by dashboardViewModel.defaultRate.collectAsState()
    val jobs by dashboardViewModel.jobs.collectAsState()
    val shifts by dashboardViewModel.shifts.collectAsState()
    val userName by dashboardViewModel.userName.collectAsState()
    val memberSince by dashboardViewModel.memberSince.collectAsState()
    val userEmail by authViewModel?.currentUserEmail?.collectAsState() ?: remember { mutableStateOf("") }
    val themeMode by dashboardViewModel.themeMode.collectAsState()

    val displayName = userName.ifBlank { userEmail.substringBefore("@").ifBlank { "User" } }
    val initials = remember(userName, userEmail) {
        if (userName.isNotBlank()) {
            val parts = userName.trim().split(" ")
            if (parts.size >= 2) "${parts.first().first()}${parts.last().first()}".uppercase()
            else userName.take(2).uppercase()
        } else {
            val prefix = userEmail.substringBefore("@")
            if (prefix.length >= 2) prefix.take(2).uppercase() else prefix.uppercase().ifEmpty { "U" }
        }
    }

    val now = System.currentTimeMillis()
    val completedShifts = shifts.filter { it.startTime < now }
    val totalHours = completedShifts.sumOf { it.durationHours }
    val totalEarned = completedShifts.sumOf { it.totalEarned }

    var company by remember(currentCompany) { mutableStateOf(currentCompany) }
    var rate by remember(currentRate) { mutableStateOf(currentRate.toString()) }
    var expanded by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PrimaryGreen)) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(88.dp).clip(CircleShape).background(SecondaryGreen).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Text(initials, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (userEmail.isNotBlank()) Text(userEmail, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                    if (memberSince.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                            Text("Member since $memberSince", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(modifier = Modifier.weight(1f), label = "SHIFTS", value = "${completedShifts.size}")
                StatCard(modifier = Modifier.weight(1f), label = "HOURS", value = "${"%.1f".format(totalHours)}")
                StatCard(modifier = Modifier.weight(1f), label = "EARNED", value = "$${"%.0f".format(totalEarned)}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme toggle
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (mode, label) ->
                            val selected = themeMode == mode
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { dashboardViewModel.setThemeMode(mode) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Employers card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkOutline, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Employers (${jobs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (jobs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(jobs.joinToString(" · ") { it.title }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Export Reports card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showExportDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Export Shift Report", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text("Filter by week & employer", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Default Shift Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Default Job/Company") },
                        readOnly = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
                    Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                    if (jobs.isNotEmpty()) {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            jobs.forEach { job ->
                                DropdownMenuItem(text = { Text(job.title) }, onClick = { company = job.title; rate = job.defaultHourlyRate.toString(); expanded = false })
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Default Hourly Rate (\$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { dashboardViewModel.saveSettings(company, rate.toDoubleOrNull() ?: 0.0); onBack() },
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) {
                    Text("Save Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showExportDialog) {
        ExportFilterDialog(dashboardViewModel = dashboardViewModel, onDismiss = { showExportDialog = false })
    }
}

@Composable
fun ExportFilterDialog(dashboardViewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val jobs by dashboardViewModel.jobs.collectAsState()
    val availableWeeks = remember { dashboardViewModel.getAvailableWeeks() }
    val context = LocalContext.current

    var selectedWeekIndex by remember { mutableStateOf(availableWeeks.indexOfFirst { it.second.contains("Current") }.coerceAtLeast(0)) }
    var selectedEmployer by remember { mutableStateOf("All") }
    var weekExpanded by remember { mutableStateOf(false) }
    var employerExpanded by remember { mutableStateOf(false) }

    val preview = remember(selectedWeekIndex, selectedEmployer) {
        if (availableWeeks.isNotEmpty()) {
            dashboardViewModel.generateFormattedReport(availableWeeks[selectedWeekIndex].first, selectedEmployer)
        } else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Shift Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Week selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (availableWeeks.isNotEmpty()) availableWeeks[selectedWeekIndex].second else "",
                        onValueChange = {}, readOnly = true, label = { Text("Week") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { weekExpanded = true })
                    DropdownMenu(expanded = weekExpanded, onDismissRequest = { weekExpanded = false }) {
                        availableWeeks.forEachIndexed { index, (_, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedWeekIndex = index; weekExpanded = false })
                        }
                    }
                }

                // Employer selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedEmployer, onValueChange = {}, readOnly = true, label = { Text("Employer") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { employerExpanded = true })
                    DropdownMenu(expanded = employerExpanded, onDismissRequest = { employerExpanded = false }) {
                        DropdownMenuItem(text = { Text("All") }, onClick = { selectedEmployer = "All"; employerExpanded = false })
                        jobs.forEach { job ->
                            DropdownMenuItem(text = { Text(job.title) }, onClick = { selectedEmployer = job.title; employerExpanded = false })
                        }
                    }
                }

                // Preview
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Text(preview.ifBlank { "No shifts for this selection." }, fontSize = 13.sp, modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onBackground, style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Schedulo Shift Report")
                    putExtra(Intent.EXTRA_TEXT, preview)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Report"))
                onDismiss()
            }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWeekPlanScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val jobs by viewModel.jobs.collectAsState()
    val shifts by viewModel.shifts.collectAsState()

    var selectedJob by remember(jobs) { mutableStateOf(jobs.firstOrNull()) }
    var expandedCompany by remember { mutableStateOf(false) }

    val daysOfWeek = remember { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") }

    var dayEnabled by remember { mutableStateOf(List(7) { it < 5 }) }
    var dayStartHours by remember { mutableStateOf(List(7) { 9 }) }
    var dayEndHours by remember { mutableStateOf(List(7) { 17 }) }

    val weekStartMillis = remember {
        Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val dayFormat = remember { SimpleDateFormat("M/dd", Locale.US) }
    val duplicateDays = remember(shifts, selectedJob) {
        if (selectedJob == null) emptySet()
        else {
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
            (0..6).filter { dayOffset ->
                val dayMillis = weekStartMillis + dayOffset.toLong() * 24 * 60 * 60 * 1000L
                val dateKey = dateFormat.format(Date(dayMillis))
                shifts.any { it.company.equals(selectedJob!!.title, ignoreCase = true) && dateFormat.format(Date(it.startTime)) == dateKey }
            }.toSet()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Plan Entire Week") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Select Job", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedJob?.title ?: "Select a Job...", onValueChange = {}, readOnly = true,
                    label = { Text("Job") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCompany) }
                )
                Box(modifier = Modifier.matchParentSize().clickable { expandedCompany = true })
                if (jobs.isNotEmpty()) {
                    DropdownMenu(expanded = expandedCompany, onDismissRequest = { expandedCompany = false }) {
                        jobs.forEach { job ->
                            DropdownMenuItem(
                                text = { Text("${job.title} (${if (job.isGigWork) "Gig" else "$${job.defaultHourlyRate}/hr"})") },
                                onClick = { selectedJob = job; expandedCompany = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Week of ${dayFormat.format(Date(weekStartMillis))} – ${dayFormat.format(Date(weekStartMillis + 6L * 24 * 60 * 60 * 1000L))}",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(12.dp))

            daysOfWeek.forEachIndexed { index, dayName ->
                val dayMillis = weekStartMillis + index.toLong() * 24 * 60 * 60 * 1000L
                val dateStr = dayFormat.format(Date(dayMillis))
                val hasDuplicate = index in duplicateDays
                val enabled = dayEnabled[index]

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (hasDuplicate) AccentOrange.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (hasDuplicate) AccentOrange.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = enabled && !hasDuplicate, enabled = !hasDuplicate,
                                onCheckedChange = { dayEnabled = dayEnabled.toMutableList().also { list -> list[index] = it } },
                                modifier = Modifier.padding(end = 8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$dayName ($dateStr)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                                if (hasDuplicate) Text("Shift already exists", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (enabled && !hasDuplicate) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = "${dayStartHours[index]}:00", onValueChange = {
                                        val h = it.replace(":00", "").toIntOrNull()?.coerceIn(0, 23)
                                        if (h != null) dayStartHours = dayStartHours.toMutableList().also { list -> list[index] = h }
                                    },
                                    label = { Text("Start") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = "${dayEndHours[index]}:00", onValueChange = {
                                        val h = it.replace(":00", "").toIntOrNull()?.coerceIn(0, 23)
                                        if (h != null) dayEndHours = dayEndHours.toMutableList().also { list -> list[index] = h }
                                    },
                                    label = { Text("End") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val totalDays = dayEnabled.count { it } - duplicateDays.count { dayEnabled[it] }
            val totalHours = (0..6).filter { dayEnabled[it] && it !in duplicateDays }.sumOf { i ->
                val s = dayStartHours[i]; val e = dayEndHours[i]
                if (e > s) e - s else 24 - s + e
            }
            Text("$totalDays days · $totalHours hours planned", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val job = selectedJob ?: return@Button
                    val entries = (0..6).filter { dayEnabled[it] && it !in duplicateDays }
                        .map { Triple(it, dayStartHours[it], dayEndHours[it]) }
                    viewModel.addWeekPlan(
                        job.title, job.defaultHourlyRate, job.isGigWork,
                        0.0, 30, weekStartMillis, entries
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = selectedJob != null && dayEnabled.any { it }
            ) { Text("Save Week Plan", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
