package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }

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
    val userName by dashboardViewModel?.userName?.collectAsState() ?: remember { mutableStateOf("") }
    val isLoading by dashboardViewModel?.isLoading?.collectAsState() ?: remember { mutableStateOf(false) }
    val syncError by dashboardViewModel?.syncError?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val displayInitials = remember(userName, userEmail) {
        if (userName.isNotBlank()) {
            val parts = userName.trim().split(" ")
            if (parts.size >= 2) "${parts.first().first()}${parts.last().first()}".uppercase()
            else userName.take(2).uppercase()
        } else {
            val prefix = userEmail.substringBefore("@")
            if (prefix.length >= 2) prefix.take(2).uppercase() else prefix.uppercase().ifEmpty { "U" }
        }
    }
    val greetingName = remember(userName, userEmail) {
        if (userName.isNotBlank()) userName.split(" ").firstOrNull() ?: "there"
        else userEmail.substringBefore("@").ifEmpty { "there" }
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
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Hi, $greetingName",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = weekRangeLabel(weekOffset) + if (weekOffset == 0) " · This Week" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = {
                    dashboardViewModel?.reset()
                    authViewModel?.logout()
                    onNavigateToLogin?.invoke()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(PrimaryGreen, SecondaryGreen))
                        )
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayInitials,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        // Week filter chip
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                onClick = { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = weekRangeLabel(weekOffset) + if (weekOffset == 0) " (Current)" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = "Select Week",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                        },
                        leadingIcon = if (offset == weekOffset) {
                            { Icon(Icons.Default.Check, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }

        // Error banner
        if (syncError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = syncError ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { dashboardViewModel?.clearSyncError() }
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { EarningsAndHoursCard(totalEarned, totalHours, weekShifts.size, onNavigateToPay) }

            if (jobs.isNotEmpty()) {
                item {
                    Text(
                        text = "Employer Goals",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
fun EarningsAndHoursCard(totalEarned: Double, totalHours: Double, shiftCount: Int, onNavigateToPay: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryGreen, Color(0xFF1B4332))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Weekly Earnings",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$${"%.2f".format(totalEarned)}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        onClick = { onNavigateToPay() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pay Details",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatPill(
                        label = "Hours",
                        value = "${"%.1f".format(totalHours)}h",
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Shifts",
                        value = "$shiftCount",
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Avg/Shift",
                        value = if (shiftCount > 0) "$${"%.0f".format(totalEarned / shiftCount)}" else "$0",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
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

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.toFloat(),
        animationSpec = tween(600),
        label = "progress"
    )

    val accentColor = if (isGig) AccentOrange else AccentBlue

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isGig) Icons.Default.DeliveryDining else Icons.Default.Business,
                            null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            job.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            if (isGig) "Gig Work" else "$${job.defaultHourlyRate}/hr",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "$${"%.2f".format(earnings)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Text("Hours", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${"%.1f".format(hours)}h",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column {
                    Text("Shifts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${shiftsForJob.size}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (!isGig && overtimeHours > 0.0) {
                    Column {
                        Text("Overtime", fontSize = 11.sp, color = AccentOrange)
                        Text(
                            "${"%.1f".format(overtimeHours)}h (+$${"%.0f".format(overtimeEarnings)})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Weekly ${job.goalType} Target",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isHoursGoal) "${"%.1f".format(hours)}/${"%.0f".format(goalValue)}h"
                    else "$${"%.0f".format(earnings)}/$${"%.0f".format(goalValue)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progressFraction >= 1.0) PrimaryGreen else MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progressFraction >= 1.0) PrimaryGreen else accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (progressFraction >= 1.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Goal achieved!", fontSize = 12.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun UpcomingShiftsSection(shifts: List<Shift> = emptyList(), onEditShift: (String) -> Unit = {}) {
    val now = System.currentTimeMillis()
    val upcomingShifts = shifts.filter { it.startTime >= now }.sortedBy { it.startTime }.take(5)
    val timeFormat = remember { java.text.SimpleDateFormat("EEE, MMM dd · h:mm a", java.util.Locale.US) }

    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            "Upcoming Shifts",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (upcomingShifts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventAvailable,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No upcoming shifts",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    upcomingShifts.forEachIndexed { index, shift ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onEditShift(shift.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        (if (shift.isGig) AccentOrange else AccentBlue).copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (shift.isGig) Icons.Default.DeliveryDining else Icons.Default.Work,
                                    null,
                                    tint = if (shift.isGig) AccentOrange else AccentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shift.company,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = timeFormat.format(java.util.Date(shift.startTime)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "$${"%.2f".format(shift.totalEarned)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreen
                            )
                        }
                        if (index < upcomingShifts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(icon = Icons.Default.Home, label = "Home", selected = currentRoute == "dashboard", onClick = { onNavigate("dashboard") })
            NavBarItem(icon = Icons.Default.CalendarMonth, label = "Plan", selected = currentRoute == "plan", onClick = { onNavigate("plan") })
            Spacer(modifier = Modifier.width(56.dp))
            NavBarItem(icon = Icons.Default.WorkOutline, label = "Jobs", selected = currentRoute == "jobs", onClick = { onNavigate("jobs") })
            NavBarItem(icon = Icons.Default.Wallet, label = "Pay", selected = currentRoute == "pay", onClick = { onNavigate("pay") })
        }
    }
}

@Composable
fun NavBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}

@Composable
fun FabPlaceholder(onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .offset(y = 16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(PrimaryGreen, Color(0xFF1B4332)))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
