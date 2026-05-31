package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.OnSurfaceVariantLight
import com.example.ui.theme.PrimaryGreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
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
        floatingActionButton = { FabPlaceholder(onClick = { navController.navigate("add_shift") }) },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        when (currentRoute) {
            "dashboard" -> DashboardScreen(
                modifier = Modifier.padding(innerPadding),
                authViewModel = authViewModel,
                dashboardViewModel = dashboardViewModel,
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onEditShift = { id -> navController.navigate("add_shift?shiftId=$id") },
                onNavigateToProfile = { navController.navigate("profile") }
            )
            "plan" -> PlanScreen(
                modifier = Modifier.padding(innerPadding),
                dashboardViewModel = dashboardViewModel,
                onEditShift = { id -> navController.navigate("add_shift?shiftId=$id") }
            )
            "jobs" -> JobsScreen(
                modifier = Modifier.padding(innerPadding),
                dashboardViewModel = dashboardViewModel
            )
            "pay" -> PayScreen(
                modifier = Modifier.padding(innerPadding),
                dashboardViewModel = dashboardViewModel
            )
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
    val now = System.currentTimeMillis()
    val upcomingShifts = shifts.filter { it.startTime >= now }.sortedBy { it.startTime }
    val previousShifts = shifts.filter { it.startTime < now }.sortedByDescending { it.startTime }
    
    var selectedTabIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Upcoming") })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Previous") })
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        val listToDisplay = if (selectedTabIndex == 0) upcomingShifts else previousShifts
        val emptyMessage = if (selectedTabIndex == 0) "No upcoming shifts planned." else "No previous shifts."

        val format = remember { SimpleDateFormat("EEEE, MMM dd, yyyy • hh:mm a", Locale.US) }

        if (listToDisplay.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = OnSurfaceVariantLight)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listToDisplay) { shift ->
                    ShiftItem(
                        modifier = Modifier.clickable { onEditShift(shift.id) },
                        color = if (selectedTabIndex == 0) AccentBlue else OnSurfaceVariantLight,
                        title = if (shift.isGig) "${shift.company} (Gig)" else shift.company,
                        timeStr = "Starts: ${format.format(Date(shift.startTime))}\nEnds: ${format.format(Date(shift.endTime))}",
                        amount = "Est: $${"%.2f".format(shift.totalEarned)}",
                        reminderMinutes = shift.reminderBeforeMinutes
                    )
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
    var daysExpanded by remember { mutableStateOf(false) }
    val daysOfWeek = remember { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingJobId == null) "Add Employer Job" else "Edit Employer Job") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Job / Employer Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Is Gig Work (e.g. DoorDash)?", modifier = Modifier.weight(1f))
                        Switch(
                            checked = isGigWork,
                            onCheckedChange = { isGigWork = it }
                        )
                    }

                    if (!isGigWork) {
                        OutlinedTextField(
                            value = rateStr,
                            onValueChange = { rateStr = it },
                            label = { Text("Hourly Rate ($)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = weeklyCycleStartDay,
                                onValueChange = {},
                                label = { Text("Weekly Cycle Start Day") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = daysExpanded) }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { daysExpanded = true }
                            )
                            DropdownMenu(
                                expanded = daysExpanded,
                                onDismissRequest = { daysExpanded = false }
                            ) {
                                daysOfWeek.forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(day) },
                                        onClick = {
                                            weeklyCycleStartDay = day
                                            daysExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weekly Target Type:", modifier = Modifier.weight(1f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Hours", "Earnings").forEach { type ->
                                FilterChip(
                                    selected = goalType == type,
                                    onClick = { goalType = type },
                                    label = { Text(type) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = goalHoursStr,
                        onValueChange = { goalHoursStr = it },
                        label = { Text(if (goalType == "Hours") "Weekly Hours Target" else "Weekly Earnings Target ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        val finalRate = if (isGigWork) 0.0 else (rateStr.toDoubleOrNull() ?: 15.0)
                        val finalGoal = goalHoursStr.toDoubleOrNull() ?: 20.0

                        val jobId = editingJobId
                        if (jobId == null) {
                            dashboardViewModel.addJob(title, isGigWork, finalRate, finalGoal, goalType, weeklyCycleStartDay)
                        } else {
                            dashboardViewModel.updateJob(jobId, title, isGigWork, finalRate, finalGoal, goalType, weeklyCycleStartDay)
                        }
                        showDialog = false
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Employers & Jobs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Button(
                onClick = {
                    editingJobId = null
                    title = ""
                    isGigWork = false
                    rateStr = "15.0"
                    goalHoursStr = "20.0"
                    goalType = "Hours"
                    weeklyCycleStartDay = "Monday"
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Add Job")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (jobs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No employers added yet.", color = OnSurfaceVariantLight)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(jobs) { job ->
                    val cycleStart = job.getStartOfCurrentCycle()
                    val cycleEnd = cycleStart + 7 * 24 * 60 * 60 * 1000L
                    val now = System.currentTimeMillis()
                    val jobShifts = shifts.filter { 
                        it.company.equals(job.title, ignoreCase = true) &&
                        it.startTime >= cycleStart &&
                        it.startTime < cycleEnd &&
                        it.startTime < now
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingJobId = job.id
                                title = job.title
                                isGigWork = job.isGigWork
                                rateStr = job.defaultHourlyRate.toString()
                                goalHoursStr = job.goalHours.toString()
                                goalType = job.goalType
                                weeklyCycleStartDay = job.weeklyCycleStartDay ?: "Monday"
                                showDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(job.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (job.isGigWork) AccentOrange.copy(alpha = 0.12f) else AccentBlue.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (job.isGigWork) "Gig" else "Hourly",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (job.isGigWork) AccentOrange else AccentBlue
                                        )
                                    }
                                    IconButton(
                                        onClick = { dashboardViewModel.deleteJob(job.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Job",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("SHIFTS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariantLight)
                                    Text("${jobShifts.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("HOURS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariantLight)
                                    Text("${"%.1f".format(jobShifts.sumOf { it.durationHours })} hrs", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("EARNED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariantLight)
                                    Text("$${"%.2f".format(jobShifts.sumOf { it.totalEarned })}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Weekly Target: ${if (job.goalType == "Hours") "${job.goalHours} hrs" else "$${job.goalHours}"}",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariantLight
                                )
                                if (!job.isGigWork) {
                                    Text(
                                        text = "Base Rate: $${job.defaultHourlyRate}/hr",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OnSurfaceVariantLight
                                    )
                                }
                            }
                            if (!job.isGigWork) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val cycleStartDay = job.weeklyCycleStartDay ?: "Monday"
                                Text(
                                    text = "Pay Cycle: $cycleStartDay to ${
                                        when(cycleStartDay.lowercase(java.util.Locale.US)) {
                                            "monday" -> "Sunday"
                                            "tuesday" -> "Monday"
                                            "wednesday" -> "Tuesday"
                                            "thursday" -> "Wednesday"
                                            "friday" -> "Thursday"
                                            "saturday" -> "Friday"
                                            "sunday" -> "Saturday"
                                            else -> "Sunday"
                                        }
                                    }",
                                    fontSize = 12.sp,
                                    color = PrimaryGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class PayCycleStatus {
    UPCOMING,       // Active cycle (current or future). now < endDate
    PENDING_HOLD,   // Completed cycle under 1-week hold (endDate <= now < endDate + 1 week)
    DUE,            // Passed 1-week hold, some or all shifts are unpaid (now >= endDate + 1 week and not all are true)
    PAID            // Passed 1-week hold and all are marked as paid
}

data class WeeklyPayCycle(
    val startDate: Long,
    val endDate: Long,
    val shifts: List<Shift>,
    val totalEarned: Double,
    val status: PayCycleStatus
)

fun getCycleStartAndEndForShift(shift: Shift, jobs: List<Job>): Pair<Long, Long> {
    val job = jobs.firstOrNull { it.title.lowercase(java.util.Locale.US) == shift.company.lowercase(java.util.Locale.US) }
    val startDay = job?.weeklyCycleStartDay ?: "Monday"
    
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = shift.startTime
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    
    val targetDayOfWeek = when (startDay.lowercase(java.util.Locale.US)) {
        "sunday" -> java.util.Calendar.SUNDAY
        "monday" -> java.util.Calendar.MONDAY
        "tuesday" -> java.util.Calendar.TUESDAY
        "wednesday" -> java.util.Calendar.WEDNESDAY
        "thursday" -> java.util.Calendar.THURSDAY
        "friday" -> java.util.Calendar.FRIDAY
        "saturday" -> java.util.Calendar.SATURDAY
        else -> java.util.Calendar.MONDAY
    }
    
    while (calendar.get(java.util.Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    
    val cycleStart = calendar.timeInMillis
    val cycleEnd = cycleStart + 7L * 24 * 60 * 60 * 1000L
    return Pair(cycleStart, cycleEnd)
}

fun groupShiftsIntoCycles(shifts: List<Shift>, jobs: List<Job>, now: Long): List<WeeklyPayCycle> {
    val cyclesMap = mutableMapOf<Pair<Long, Long>, MutableList<Shift>>()
    
    for (shift in shifts) {
        val (start, end) = getCycleStartAndEndForShift(shift, jobs)
        val key = Pair(start, end)
        if (!cyclesMap.containsKey(key)) {
            cyclesMap[key] = mutableListOf()
        }
        cyclesMap[key]?.add(shift)
    }
    
    val oneWeekMillis = 7L * 24 * 60 * 60 * 1000L
    
    return cyclesMap.map { (key, shiftList) ->
        val (start, end) = key
        val totalEarned = shiftList.sumOf { it.totalEarned }
        
        val status = when {
            now < end -> PayCycleStatus.UPCOMING
            now >= end && now < (end + oneWeekMillis) -> PayCycleStatus.PENDING_HOLD
            else -> {
                val allPaid = shiftList.isNotEmpty() && shiftList.all { it.isPaid }
                if (allPaid) PayCycleStatus.PAID else PayCycleStatus.DUE
            }
        }
        
        WeeklyPayCycle(
            startDate = start,
            endDate = end,
            shifts = shiftList.sortedBy { it.startTime },
            totalEarned = totalEarned,
            status = status
        )
    }.sortedByDescending { it.startDate }
}

@Composable
fun PayScreen(modifier: Modifier = Modifier, dashboardViewModel: DashboardViewModel) {
    val shifts by dashboardViewModel.shifts.collectAsState(initial = emptyList())
    val jobs by dashboardViewModel.jobs.collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    
    val cycles = remember(shifts, jobs, now) {
        groupShiftsIntoCycles(shifts, jobs, now)
    }
    
    val totalPaid = shifts.filter { it.isPaid }.sumOf { it.totalEarned }
    val totalPendingHold = cycles.filter { it.status == PayCycleStatus.PENDING_HOLD }.sumOf { it.totalEarned }
    val totalDue = cycles.filter { it.status == PayCycleStatus.DUE }.sumOf { cycle ->
        cycle.shifts.filter { !it.isPaid }.sumOf { it.totalEarned }
    }
    val upcomingEarned = cycles.filter { it.status == PayCycleStatus.UPCOMING }.sumOf { it.totalEarned }
    
    val cycleFormat = remember { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US) }
    val fullDateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US) }
    
    var expandedCycleStart by remember { mutableStateOf<Long?>(null) }
    var cycleToConfirmPaid by remember { mutableStateOf<WeeklyPayCycle?>(null) }
    
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Pay & Earnings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            // Financial Status Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Out-of-Pocket / Due Payout", fontSize = 14.sp, color = OnSurfaceVariantLight)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$${"%.2f".format(totalDue)}", fontSize = 42.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Received / Paid", fontSize = 12.sp, color = OnSurfaceVariantLight, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$${"%.2f".format(totalPaid)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("On Hold (1-Wk)", fontSize = 12.sp, color = OnSurfaceVariantLight, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$${"%.2f".format(totalPendingHold)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Upcoming", fontSize = 12.sp, color = OnSurfaceVariantLight, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$${"%.2f".format(upcomingEarned)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        if (cycles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No shifts reported yet. Work some shifts to track your pay cycles!",
                            fontSize = 15.sp,
                            color = OnSurfaceVariantLight,
                            style = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Weekly Payroll Cycles",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            items(cycles) { cycle ->
                val cycleRangeStr = "${cycleFormat.format(java.util.Date(cycle.startDate))} – ${cycleFormat.format(java.util.Date(cycle.endDate - 1000L))}"
                val payoutDateStr = fullDateFormat.format(java.util.Date(cycle.endDate + 7L * 24 * 60 * 60 * 1000L))
                val isExpanded = expandedCycleStart == cycle.startDate
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable {
                            expandedCycleStart = if (isExpanded) null else cycle.startDate
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = when(cycle.status) {
                            PayCycleStatus.DUE -> PrimaryGreen.copy(alpha = 0.5f)
                            PayCycleStatus.PENDING_HOLD -> AccentOrange.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cycleRangeStr,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${cycle.shifts.size} Work Shift${if (cycle.shifts.size == 1) "" else "s"}",
                                    fontSize = 13.sp,
                                    color = OnSurfaceVariantLight
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${"%.2f".format(cycle.totalEarned)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryGreen
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Beautiful dynamic status badges
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when(cycle.status) {
                                                PayCycleStatus.PAID -> PrimaryGreen.copy(alpha = 0.12f)
                                                PayCycleStatus.DUE -> PrimaryGreen.copy(alpha = 0.08f)
                                                PayCycleStatus.PENDING_HOLD -> AccentOrange.copy(alpha = 0.12f)
                                                PayCycleStatus.UPCOMING -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when(cycle.status) {
                                            PayCycleStatus.PAID -> "RECEIVED / PAID"
                                            PayCycleStatus.DUE -> "DUE FOR PAYOUT"
                                            PayCycleStatus.PENDING_HOLD -> "ON HOLD (1-WK)"
                                            PayCycleStatus.UPCOMING -> "ACTIVE CYCLE"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = when(cycle.status) {
                                            PayCycleStatus.PAID -> PrimaryGreen
                                            PayCycleStatus.DUE -> PrimaryGreen
                                            PayCycleStatus.PENDING_HOLD -> AccentOrange
                                            PayCycleStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                        
                        // If hold is running, display payout date
                        if (cycle.status == PayCycleStatus.PENDING_HOLD) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "🔒 1-Week hold scenario: Payout released on $payoutDateStr",
                                fontSize = 12.sp,
                                color = AccentOrange,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // If due, offer Mark as Paid button
                        if (cycle.status == PayCycleStatus.DUE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    cycleToConfirmPaid = cycle
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Text("Mark Entire Week as Paid", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Timesheet Details",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariantLight,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            cycle.shifts.forEach { shift ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shift.company,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "${SimpleDateFormat("EEE, MMM dd", java.util.Locale.US).format(java.util.Date(shift.startTime))} • ${"%.1f".format(shift.durationHours)} hrs",
                                            fontSize = 12.sp,
                                            color = OnSurfaceVariantLight
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$${"%.2f".format(shift.totalEarned)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryGreen,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        
                                        // Allow marking individual shifts paid if they are not in upcoming
                                        if (cycle.status != PayCycleStatus.UPCOMING) {
                                            Checkbox(
                                                checked = shift.isPaid,
                                                onCheckedChange = { checked ->
                                                    dashboardViewModel.toggleShiftPaidStatus(shift.id, checked)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Est", fontSize = 10.sp, color = OnSurfaceVariantLight)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tap to view timesheet details",
                                    fontSize = 11.sp,
                                    color = OnSurfaceVariantLight.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (cycleToConfirmPaid != null) {
        val cycle = cycleToConfirmPaid!!
        val cycleRangeStr = "${cycleFormat.format(java.util.Date(cycle.startDate))} – ${cycleFormat.format(java.util.Date(cycle.endDate - 1000L))}"
        AlertDialog(
            onDismissRequest = { cycleToConfirmPaid = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Payment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to mark the entire week of $cycleRangeStr ($${"%.2f".format(cycle.totalEarned)}) as Paid?",
                    fontSize = 14.sp,
                    color = OnSurfaceVariantLight
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = cycle.shifts.map { it.id }
                        dashboardViewModel.markCycleAsPaid(ids, true)
                        cycleToConfirmPaid = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { cycleToConfirmPaid = null }
                ) {
                    Text("Cancel", color = OnSurfaceVariantLight)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val currentCompany by dashboardViewModel.defaultCompany.collectAsState()
    val currentRate by dashboardViewModel.defaultRate.collectAsState()
    val jobs by dashboardViewModel.jobs.collectAsState()

    var company by remember(currentCompany) { mutableStateOf(currentCompany) }
    var rate by remember(currentRate) { mutableStateOf(currentRate.toString()) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Default Shift Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Default Job/Company") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true }
                )
                if (jobs.isNotEmpty()) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        jobs.forEach { job ->
                            DropdownMenuItem(
                                text = { Text(job.title) },
                                onClick = {
                                    company = job.title
                                    rate = job.defaultHourlyRate.toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text("Default Hourly Rate ($)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    dashboardViewModel.saveSettings(company, rate.toDoubleOrNull() ?: 0.0)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Save Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
