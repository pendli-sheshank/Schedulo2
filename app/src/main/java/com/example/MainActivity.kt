package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import java.util.Calendar
import java.util.Locale
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val navController = rememberNavController()
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val dashboardViewModel: DashboardViewModel = viewModel()
            val themeMode by dashboardViewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                val startDestination = remember {
                    if (authState is AuthState.Authenticated) "dashboard" else "login"
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable("login") {
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToSignup = { navController.navigate("signup") },
                            onNavigateToDashboard = {
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("signup") {
                        SignupScreen(
                            viewModel = authViewModel,
                            onNavigateToLogin = { navController.navigate("login") },
                            onNavigateToDashboard = {
                                navController.navigate("dashboard") {
                                    popUpTo("signup") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("dashboard") { MainLayout(navController, "dashboard", authViewModel, dashboardViewModel) }
                    composable("plan") { MainLayout(navController, "plan", authViewModel, dashboardViewModel) }
                    composable("jobs") { MainLayout(navController, "jobs", authViewModel, dashboardViewModel) }
                    composable("pay") { MainLayout(navController, "pay", authViewModel, dashboardViewModel) }
                    composable("profile") {
                        ProfileScreen(dashboardViewModel = dashboardViewModel, authViewModel = authViewModel, onBack = { navController.popBackStack() })
                    }
                    composable("add_week_plan") {
                        AddWeekPlanScreen(viewModel = dashboardViewModel, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "add_shift?shiftId={shiftId}",
                        arguments = listOf(androidx.navigation.navArgument("shiftId") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        })
                    ) { backStackEntry ->
                        val shiftId = backStackEntry.arguments?.getString("shiftId")
                        AddShiftScreen(
                            shiftId = shiftId,
                            viewModel = dashboardViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

private fun weekRangeLabel(offset: Int): String {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        clear(Calendar.MINUTE)
        clear(Calendar.SECOND)
        clear(Calendar.MILLISECOND)
        add(Calendar.WEEK_OF_YEAR, offset)
    }
    val fmt = java.text.SimpleDateFormat("MMM dd", Locale.US)
    val start = fmt.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, 6)
    val end = fmt.format(cal.time)
    return "$start – $end"
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null,
    dashboardViewModel: DashboardViewModel? = null,
    onNavigateToLogin: (() -> Unit)? = null,
    onEditShift: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPay: () -> Unit = {}
) {
    val shifts by dashboardViewModel?.shifts?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val jobs by dashboardViewModel?.jobs?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val userEmail by authViewModel?.currentUserEmail?.collectAsState() ?: remember { mutableStateOf("") }
    val isLoading by dashboardViewModel?.isLoading?.collectAsState() ?: remember { mutableStateOf(false) }
    val syncError by dashboardViewModel?.syncError?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val userInitials = remember(userEmail) {
        val prefix = userEmail.substringBefore("@")
        if (prefix.length >= 2) prefix.take(2).uppercase() else prefix.uppercase().ifEmpty { "U" }
    }
    val now = System.currentTimeMillis()

    var weekOffset by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }

    val globalWeekStart = remember(weekOffset) {
        Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }.timeInMillis
    }
    val globalWeekEnd = globalWeekStart + 7 * 24 * 60 * 60 * 1000L

    val weekShifts = shifts.filter { it.startTime >= globalWeekStart && it.startTime < globalWeekEnd }
    val completedWeekShifts = weekShifts.filter { it.startTime < now }
    val totalHours = completedWeekShifts.sumOf { it.durationHours }
    val totalEarned = completedWeekShifts.sumOf { it.totalEarned }

    LaunchedEffect(Unit) {
        dashboardViewModel?.loadShifts()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Schedulo",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    dashboardViewModel?.reset()
                    authViewModel?.logout()
                    onNavigateToLogin?.invoke()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SecondaryGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitials,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FILTER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = weekRangeLabel(weekOffset) + if (weekOffset == 0) " (Current)" else "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select Week", tint = PrimaryGreen)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(0, -1, -2, -3, -4).forEach { offset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    weekRangeLabel(offset) + if (offset == 0) " (Current)" else "",
                                    fontWeight = if (offset == weekOffset) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                weekOffset = offset
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (syncError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3E0))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = syncError ?: "", fontSize = 12.sp, color = Color(0xFFE65100), modifier = Modifier.weight(1f))
                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFE65100),
                    modifier = Modifier.size(16.dp).clickable { dashboardViewModel?.clearSyncError() })
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = PrimaryGreen)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { EarningsAndHoursCard(totalEarned, totalHours, onNavigateToPay) }
            item {
                Text(
                    text = "My Employer Goals & Timesheets",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (jobs.isEmpty()) {
                item {
                    Text(
                        text = "Tip: Configure your Employer list in the JOBS tab to track weekly objectives.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(jobs) { job ->
                    JobGoalTrackerCard(job, shifts, weekOffset)
                }
            }
            if (weekOffset == 0) {
                item { UpcomingShiftsSection(shifts, onEditShift) }
            }
        }
    }
}

@Composable
fun EarningsAndHoursCard(totalEarned: Double, totalHours: Double, onNavigateToPay: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = PrimaryGreen)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("TOTAL TRACKED PAY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(2.dp))
            Text("$${"%.2f".format(totalEarned)}", fontSize = 40.sp, fontWeight = FontWeight.Light, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TOTAL COMPLETED HOURS", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                    Text("${"%.1f".format(totalHours)} hrs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onNavigateToPay() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("View Pay Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun JobGoalTrackerCard(job: Job, shifts: List<Shift>, weekOffset: Int = 0) {
    val cycleStart = job.getStartOfCurrentCycle() + weekOffset * 7 * 24 * 60 * 60 * 1000L
    val cycleEnd = cycleStart + 7 * 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()

    val shiftsForJob = shifts.filter {
        it.company.equals(job.title, ignoreCase = true) &&
        it.startTime >= cycleStart && it.startTime < cycleEnd && it.startTime < now
    }

    val hours = shiftsForJob.sumOf { it.durationHours }
    val earnings = shiftsForJob.sumOf { it.totalEarned }

    val (_, overtimeEarnings) = calculateEarningsWithOvertime(shiftsForJob, job)
    val overtimeHours = if (!job.isGigWork && hours > job.overtimeThresholdHours) hours - job.overtimeThresholdHours else 0.0

    val isGig = job.isGigWork
    val isHoursGoal = job.goalType == "Hours"
    val goalValue = job.goalHours
    val progressFraction = if (goalValue > 0) {
        val actualValue = if (isHoursGoal) hours else earnings
        (actualValue / goalValue).coerceIn(0.0, 1.0)
    } else 0.0

    val cycleFormat = remember { java.text.SimpleDateFormat("MMM dd", java.util.Locale.US) }
    val cycleRangeStr = "${cycleFormat.format(java.util.Date(cycleStart))} – ${cycleFormat.format(java.util.Date(cycleEnd - 1000L))}"

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isGig) AccentOrange else AccentBlue))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(job.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                        Text("Cycle: $cycleRangeStr (${job.weeklyCycleStartDay ?: "Monday"} start)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(
                    modifier = Modifier.clip(CircleShape).background(if (isGig) AccentOrange.copy(alpha = 0.1f) else AccentBlue.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (isGig) "Gig Work" else "Hourly", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isGig) AccentOrange else AccentBlue)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MY EARNINGS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    Text("$${"%.2f".format(earnings)}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = PrimaryGreen))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("TOTAL HOURS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    Text("${"%.1f".format(hours)} hrs", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly Target (${job.goalType})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (isHoursGoal) "${"%.1f".format(hours)} / ${"%.0f".format(goalValue)} hrs"
                    else "$${"%.2f".format(earnings)} / $${"%.0f".format(goalValue)}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = progressFraction.toFloat(),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = PrimaryGreen, trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (progressFraction >= 1.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Goal achieved this week!", fontSize = 11.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            }
            if (!isGig && overtimeHours > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Overtime: ${"%.1f".format(overtimeHours)} hrs (+$${"%.2f".format(overtimeEarnings)})",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange
                )
            }
        }
    }
}

@Composable
fun UpcomingShiftsSection(shifts: List<Shift> = emptyList(), onEditShift: (String) -> Unit = {}) {
    val now = System.currentTimeMillis()
    val upcomingShifts = shifts.filter { it.startTime >= now }.sortedBy { it.startTime }.take(5)
    val timeFormat = remember { java.text.SimpleDateFormat("EEE, MMM dd · h:mm a", java.util.Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text("Upcoming Shifts", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))

        if (upcomingShifts.isEmpty()) {
            Text("No upcoming shifts scheduled.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            upcomingShifts.forEach { shift ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEditShift(shift.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (shift.isGig) AccentOrange else AccentBlue)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (shift.isGig) "${shift.company} (Gig)" else shift.company,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = timeFormat.format(java.util.Date(shift.startTime)),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("$${"%.2f".format(shift.totalEarned)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                }
                if (shift != upcomingShifts.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(icon = Icons.Default.Home, label = "HOME", selected = currentRoute == "dashboard", onClick = { onNavigate("dashboard") })
            NavBarItem(icon = Icons.Default.DateRange, label = "PLAN", selected = currentRoute == "plan", onClick = { onNavigate("plan") })
            Spacer(modifier = Modifier.width(56.dp))
            NavBarItem(icon = Icons.Default.WorkOutline, label = "JOBS", selected = currentRoute == "jobs", onClick = { onNavigate("jobs") })
            NavBarItem(icon = Icons.Default.AccountBalanceWallet, label = "PAY", selected = currentRoute == "pay", onClick = { onNavigate("pay") })
        }
    }
}

@Composable
fun NavBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
    }
}

@Composable
fun FabPlaceholder(onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .offset(y = 20.dp)
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryGreen)
            .border(4.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    MyApplicationTheme { DashboardScreen() }
}
