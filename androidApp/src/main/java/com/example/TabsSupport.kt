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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
                onEditShift = { id -> navController.navigate("add_shift?shiftId=$id") },
                onAddShift = { navController.navigate("add_shift") }
            )
            "jobs" -> JobsScreen(modifier = Modifier.padding(innerPadding), dashboardViewModel = dashboardViewModel)
            "pay" -> PayScreen(modifier = Modifier.padding(innerPadding), dashboardViewModel = dashboardViewModel)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlanScreen(
    modifier: Modifier = Modifier,
    dashboardViewModel: DashboardViewModel,
    onEditShift: (String) -> Unit,
    onAddShift: () -> Unit = {}
) {
    val shifts by dashboardViewModel.shifts.collectAsState(initial = emptyList())
    val isRefreshing by dashboardViewModel.isRefreshing.collectAsState()
    val now = System.currentTimeMillis()

    var viewMode by remember { mutableStateOf("Month") }
    var selectedDate by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredShifts = remember(shifts, searchQuery) {
        if (searchQuery.isBlank()) shifts
        else shifts.filter { it.company.contains(searchQuery, ignoreCase = true) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { dashboardViewModel.refreshData() },
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by employer...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp)) } }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )

            // View mode toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Month", "Week", "Day").forEach { mode ->
                    val selected = viewMode == mode
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewMode = mode }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mode, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            when (viewMode) {
                "Month" -> CalendarMonthView(
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                    shifts = filteredShifts,
                    now = now,
                    onEditShift = onEditShift,
                    onAddShift = onAddShift,
                    onSwitchToDay = { selectedDate = it; viewMode = "Day" }
                )
                "Week" -> CalendarWeekView(
                    selectedDate = selectedDate,
                    onDateChanged = { selectedDate = it },
                    shifts = filteredShifts,
                    now = now,
                    onEditShift = onEditShift,
                    onAddShift = onAddShift,
                    onDayTap = { selectedDate = it; viewMode = "Day" }
                )
                "Day" -> CalendarDayView(
                    selectedDate = selectedDate,
                    onDateChanged = { selectedDate = it },
                    shifts = filteredShifts,
                    now = now,
                    onEditShift = onEditShift,
                    onAddShift = onAddShift
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthView(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    shifts: List<Shift>,
    now: Long,
    onEditShift: (String) -> Unit,
    onAddShift: () -> Unit,
    onSwitchToDay: (Long) -> Unit
) {
    val cal = remember(selectedDate) {
        Calendar.getInstance().apply { timeInMillis = selectedDate }
    }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }

    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
    val selectedDay = selectedCal.get(Calendar.DAY_OF_MONTH)
    val selectedMonth = selectedCal.get(Calendar.MONTH)
    val selectedYear = selectedCal.get(Calendar.YEAR)

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfMonth = Calendar.getInstance().apply {
        set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }
    val startDayOfWeek = (firstDayOfMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7

    val shiftsByDay = remember(shifts, year, month) {
        val monthStart = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val monthEnd = Calendar.getInstance().apply { set(year, month, daysInMonth, 23, 59, 59) }.timeInMillis
        shifts.filter { it.startTime in monthStart..monthEnd }.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.DAY_OF_MONTH)
        }
    }

    val selectedDayShifts = remember(shifts, selectedDate) {
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = selectedDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L
        shifts.filter { it.startTime in dayStart until dayEnd }.sortedBy { it.startTime }
    }

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val dayLabelFormat = remember { SimpleDateFormat("EEEE, MMM dd", Locale.US) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Month navigation header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val prev = Calendar.getInstance().apply { set(year, month, 1); add(Calendar.MONTH, -1) }
                    onDateSelected(prev.timeInMillis)
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month", tint = MaterialTheme.colorScheme.onBackground) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthFormat.format(cal.time), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    val monthShiftCount = shiftsByDay.values.sumOf { it.size }
                    val monthHours = shiftsByDay.values.flatten().sumOf { it.durationHours }
                    Text("$monthShiftCount shifts · ${"%.0f".format(monthHours)} hrs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = {
                    val next = Calendar.getInstance().apply { set(year, month, 1); add(Calendar.MONTH, 1) }
                    onDateSelected(next.timeInMillis)
                }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month", tint = MaterialTheme.colorScheme.onBackground) }
            }
        }

        // Day-of-week headers
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(day, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Calendar grid
        val totalCells = startDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7
        items(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startDayOfWeek + 1
                    if (dayNum in 1..daysInMonth) {
                        val isToday = year == todayYear && month == todayMonth && dayNum == todayDay
                        val isSelected = year == selectedYear && month == selectedMonth && dayNum == selectedDay
                        val dayShifts = shiftsByDay[dayNum]
                        val hasShifts = dayShifts != null && dayShifts.isNotEmpty()
                        val shiftCount = dayShifts?.size ?: 0

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isSelected -> PrimaryGreen
                                        isToday -> PrimaryGreen.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable {
                                    val clickedDate = Calendar.getInstance().apply {
                                        set(year, month, dayNum, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    onDateSelected(clickedDate)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$dayNum",
                                    fontSize = 14.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> PrimaryGreen
                                        else -> MaterialTheme.colorScheme.onBackground
                                    }
                                )
                                if (hasShifts) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        repeat(shiftCount.coerceAtMost(3)) {
                                            Box(
                                                modifier = Modifier.size(4.dp).clip(CircleShape)
                                                    .background(if (isSelected) Color.White else PrimaryGreen)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Divider
        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }

        // Selected day detail
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayLabelFormat.format(Date(selectedDate)), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                if (selectedDayShifts.isNotEmpty()) {
                    TextButton(onClick = { onSwitchToDay(selectedDate) }) {
                        Text("Full Day View", fontSize = 12.sp, color = PrimaryGreen)
                    }
                }
            }
        }

        if (selectedDayShifts.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No shifts scheduled", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onAddShift) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Shift", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            items(selectedDayShifts, key = { it.id }) { shift ->
                ShiftCard(shift = shift, timeFormat = timeFormat, now = now, onEditShift = onEditShift, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item {
                val totalHrs = selectedDayShifts.sumOf { it.durationHours }
                val totalEarned = selectedDayShifts.sumOf { it.totalEarned }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${selectedDayShifts.size} shift${if (selectedDayShifts.size != 1) "s" else ""} · ${"%.1f".format(totalHrs)} hrs",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                        Text("$${"%.2f".format(totalEarned)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun CalendarWeekView(
    selectedDate: Long,
    onDateChanged: (Long) -> Unit,
    shifts: List<Shift>,
    now: Long,
    onEditShift: (String) -> Unit,
    onAddShift: () -> Unit = {},
    onDayTap: (Long) -> Unit
) {
    val weekStart = remember(selectedDate) {
        Calendar.getInstance().apply {
            timeInMillis = selectedDate
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000L

    val weekFormat = remember { SimpleDateFormat("MMM dd", Locale.US) }
    val dayNameFormat = remember { SimpleDateFormat("EEE", Locale.US) }
    val dayNumFormat = remember { SimpleDateFormat("dd", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    val todayMillis = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    val weekShifts = remember(shifts, weekStart) {
        shifts.filter { it.startTime in weekStart until weekEnd }.sortedBy { it.startTime }
    }
    val weekTotalHours = weekShifts.sumOf { it.durationHours }
    val weekTotalEarned = weekShifts.sumOf { it.totalEarned }

    val daysList = remember(weekStart) {
        (0..6).map { offset ->
            val dayStart = weekStart + offset * 24 * 60 * 60 * 1000L
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L
            dayStart to dayEnd
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        // Week navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDateChanged(selectedDate - 7 * 24 * 60 * 60 * 1000L) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week", tint = MaterialTheme.colorScheme.onBackground)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${weekFormat.format(Date(weekStart))} – ${weekFormat.format(Date(weekEnd - 1000L))}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("${weekShifts.size} shifts · ${"%.1f".format(weekTotalHours)} hrs · $${"%.2f".format(weekTotalEarned)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDateChanged(selectedDate + 7 * 24 * 60 * 60 * 1000L) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // Day strip
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                daysList.forEach { (dayStart, _) ->
                    val isToday = dayStart == todayMillis
                    val dayShiftCount = weekShifts.count { it.startTime in dayStart until dayStart + 24 * 60 * 60 * 1000L }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) PrimaryGreen.copy(alpha = 0.12f) else Color.Transparent)
                            .border(if (isToday) BorderStroke(1.5.dp, PrimaryGreen) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(10.dp))
                            .clickable { onDayTap(dayStart) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayNameFormat.format(Date(dayStart)), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = if (isToday) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dayNumFormat.format(Date(dayStart)), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (isToday) PrimaryGreen else MaterialTheme.colorScheme.onBackground)
                            if (dayShiftCount > 0) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryGreen))
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }

        // Day-by-day agenda
        daysList.forEach { (dayStart, dayEnd) ->
            val dayShifts = weekShifts.filter { it.startTime in dayStart until dayEnd }
            val isToday = dayStart == todayMillis

            if (dayShifts.isNotEmpty()) {
                item(key = "day_header_$dayStart") {
                    val fullDayFormat = SimpleDateFormat("EEEE, MMM dd", Locale.US)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fullDayFormat.format(Date(dayStart)), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (isToday) PrimaryGreen else MaterialTheme.colorScheme.onBackground)
                            if (isToday) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryGreen).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("TODAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Text("$${"%.2f".format(dayShifts.sumOf { it.totalEarned })}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                    }
                }
                items(dayShifts, key = { it.id }) { shift ->
                    ShiftCard(shift = shift, timeFormat = timeFormat, now = now, onEditShift = onEditShift, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
                }
            }
        }

        if (weekShifts.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No shifts this week", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onAddShift) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Shift", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayView(
    selectedDate: Long,
    onDateChanged: (Long) -> Unit,
    shifts: List<Shift>,
    now: Long,
    onEditShift: (String) -> Unit,
    onAddShift: () -> Unit = {}
) {
    val dayStart = remember(selectedDate) {
        Calendar.getInstance().apply {
            timeInMillis = selectedDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L

    val fullDateFormat = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    val todayMillis = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val isToday = dayStart == todayMillis

    val dayShifts = remember(shifts, dayStart) {
        shifts.filter { it.startTime in dayStart until dayEnd }.sortedBy { it.startTime }
    }
    val totalHours = dayShifts.sumOf { it.durationHours }
    val totalEarned = dayShifts.sumOf { it.totalEarned }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        // Day navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDateChanged(selectedDate - 24 * 60 * 60 * 1000L) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous day", tint = MaterialTheme.colorScheme.onBackground)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fullDateFormat.format(Date(dayStart)), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        if (isToday) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryGreen).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("TODAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
                IconButton(onClick = { onDateChanged(selectedDate + 24 * 60 * 60 * 1000L) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next day", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // Day summary card
        if (dayShifts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${dayShifts.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                            Text("SHIFTS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${"%.1f".format(totalHours)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                            Text("HOURS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$${"%.2f".format(totalEarned)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                            Text("EARNED", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
        }

        // Timeline view
        if (dayShifts.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No shifts scheduled", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onAddShift) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Shift", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            items(dayShifts, key = { it.id }) { shift ->
                DayViewShiftCard(shift = shift, timeFormat = timeFormat, now = now, onEditShift = onEditShift)
            }
        }
    }
}

@Composable
private fun DayViewShiftCard(shift: Shift, timeFormat: SimpleDateFormat, now: Long, onEditShift: (String) -> Unit) {
    val isActive = now in shift.startTime..shift.endTime
    val isPast = shift.endTime < now

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onEditShift(shift.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> PrimaryGreen.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                isActive -> PrimaryGreen.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Time column
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                Text(timeFormat.format(Date(shift.startTime)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else PrimaryGreen)
                Box(modifier = Modifier.width(2.dp).height(16.dp).background(if (isPast) MaterialTheme.colorScheme.outline else PrimaryGreen.copy(alpha = 0.4f)))
                Text(timeFormat.format(Date(shift.endTime)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Shift details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(if (shift.isGig) AccentOrange else AccentBlue))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (shift.isGig) "${shift.company} (Gig)" else shift.company, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${"%.1f".format(shift.durationHours)} hrs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${"%.2f".format(shift.totalEarned)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                }
                if (shift.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(shift.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 2)
                }
                if (isActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryGreen).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("IN PROGRESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Reminder badge
            if (shift.reminderBeforeMinutes > 0 && shift.startTime > now) {
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryGreen.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text("${shift.reminderBeforeMinutes}m", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                }
            }
        }
    }
}

@Composable
private fun ShiftCard(shift: Shift, timeFormat: SimpleDateFormat, now: Long, onEditShift: (String) -> Unit, modifier: Modifier = Modifier) {
    val isActive = now in shift.startTime..shift.endTime

    Card(
        modifier = modifier.fillMaxWidth().clickable { onEditShift(shift.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) PrimaryGreen.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isActive) PrimaryGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(if (shift.isGig) AccentOrange else AccentBlue))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (shift.isGig) "${shift.company} (Gig)" else shift.company, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(PrimaryGreen).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("LIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("${timeFormat.format(Date(shift.startTime))} → ${timeFormat.format(Date(shift.endTime))} · ${"%.1f".format(shift.durationHours)} hrs",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (shift.notes.isNotBlank()) {
                    Text(shift.notes.lines().first().take(60), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$${"%.2f".format(shift.totalEarned)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                if (shift.reminderBeforeMinutes > 0 && shift.startTime > now) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryGreen.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("${shift.reminderBeforeMinutes}m", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
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

data class WeeklyPayCycle(val startDate: Long, val endDate: Long, val employer: String, val shifts: List<Shift>, val totalEarned: Double, val status: PayCycleStatus) {
    val cycleKey: String get() = "${employer}_${startDate}"
}

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
    val cyclesMap = mutableMapOf<Triple<Long, Long, String>, MutableList<Shift>>()
    for (shift in nonGigShifts) {
        val (start, end) = getCycleStartAndEndForShift(shift, jobs)
        val key = Triple(start, end, shift.company)
        cyclesMap.getOrPut(key) { mutableListOf() }.add(shift)
    }
    val holdDays = 4L * 24 * 60 * 60 * 1000L
    return cyclesMap.map { (key, shiftList) ->
        val (start, end, employer) = key
        val holdEndMillis = end + holdDays
        val status = when {
            now < end -> PayCycleStatus.UPCOMING
            now in end until holdEndMillis -> PayCycleStatus.PENDING_HOLD
            else -> if (shiftList.isNotEmpty() && shiftList.all { it.isPaid }) PayCycleStatus.PAID else PayCycleStatus.DUE
        }
        WeeklyPayCycle(start, end, employer, shiftList.sortedBy { it.startTime }, shiftList.sumOf { it.totalEarned }, status)
    }.sortedByDescending { it.startDate }
}

@Composable
fun PayScreen(modifier: Modifier = Modifier, dashboardViewModel: DashboardViewModel) {
    val shifts by dashboardViewModel.shifts.collectAsState(initial = emptyList())
    val jobs by dashboardViewModel.jobs.collectAsState(initial = emptyList())
    val allAdjustments by dashboardViewModel.payAdjustments.collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val cycles = remember(shifts, jobs, now) { groupShiftsIntoCycles(shifts, jobs, now) }

    val gigShifts = remember(shifts) { shifts.filter { it.isGig } }
    val gigTotalEarned = gigShifts.sumOf { it.totalEarned }

    val cycleKeys = remember(cycles) { cycles.map { it.cycleKey }.toSet() }
    val relevantAdjustments = remember(allAdjustments, cycleKeys) { allAdjustments.filter { it.cycleKey in cycleKeys } }
    val totalAdjustments = relevantAdjustments.sumOf { adj -> if (adj.type == "Deduction" || adj.type == "Underpaid") -adj.amount else adj.amount }
    val totalPaid = shifts.filter { it.isPaid }.sumOf { it.totalEarned } + totalAdjustments
    val totalPendingHold = cycles.filter { it.status == PayCycleStatus.PENDING_HOLD }.sumOf { it.totalEarned }
    val totalDue = cycles.filter { it.status == PayCycleStatus.DUE }.sumOf { cycle -> cycle.shifts.filter { !it.isPaid }.sumOf { it.totalEarned } }
    val upcomingEarned = cycles.filter { it.status == PayCycleStatus.UPCOMING }.sumOf { it.totalEarned }

    val cycleFormat = remember { SimpleDateFormat("MMM dd", Locale.US) }
    val fullDateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val shiftTimeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    var expandedCycleKey by remember { mutableStateOf<String?>(null) }
    var cycleToConfirmPaid by remember { mutableStateOf<WeeklyPayCycle?>(null) }
    var showAdjustmentForCycle by remember { mutableStateOf<WeeklyPayCycle?>(null) }
    var adjustmentToDelete by remember { mutableStateOf<PayAdjustment?>(null) }

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
                val isExpanded = expandedCycleKey == cycle.cycleKey

                val cycleAdjustments = allAdjustments.filter { it.cycleKey == cycle.cycleKey }
                val adjustmentTotal = cycleAdjustments.sumOf { adj ->
                    if (adj.type == "Deduction" || adj.type == "Underpaid") -adj.amount else adj.amount
                }
                val actualPay = cycle.totalEarned + adjustmentTotal

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { expandedCycleKey = if (isExpanded) null else cycle.cycleKey },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, when (cycle.status) {
                        PayCycleStatus.DUE -> PrimaryGreen.copy(alpha = 0.5f); PayCycleStatus.PENDING_HOLD -> AccentOrange.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outline
                    })
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cycle.employer, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(cycleRangeStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${cycle.shifts.size} Work Shift${if (cycle.shifts.size == 1) "" else "s"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$${"%.2f".format(cycle.totalEarned)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryGreen)
                                if (adjustmentTotal != 0.0) {
                                    val sign = if (adjustmentTotal > 0) "+" else ""
                                    val adjColor = if (adjustmentTotal > 0) PrimaryGreen else MaterialTheme.colorScheme.error
                                    Text("$sign$${"%.2f".format(adjustmentTotal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = adjColor)
                                    Text("Net: $${"%.2f".format(actualPay)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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

                        // Adjustments summary (always visible)
                        if (cycleAdjustments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = AccentBlue.copy(alpha = 0.06f)),
                                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.2f))) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Receipt, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${cycleAdjustments.size} Adjustment${if (cycleAdjustments.size != 1) "s" else ""}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                                    }
                                    cycleAdjustments.forEach { adj ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                val typeIcon = when (adj.type) {
                                                    "Bonus" -> Icons.Default.TrendingUp
                                                    "Overpaid" -> Icons.Default.ArrowUpward
                                                    "Underpaid" -> Icons.Default.ArrowDownward
                                                    "Deduction" -> Icons.Default.RemoveCircleOutline
                                                    else -> Icons.Default.SwapVert
                                                }
                                                val typeColor = when (adj.type) {
                                                    "Bonus", "Overpaid" -> PrimaryGreen
                                                    "Underpaid", "Deduction" -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                                Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(adj.type, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = typeColor)
                                                    if (adj.notes.isNotBlank()) {
                                                        Text(adj.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                    }
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isNegative = adj.type == "Deduction" || adj.type == "Underpaid"
                                                Text(
                                                    "${if (isNegative) "-" else "+"}$${"%.2f".format(adj.amount)}",
                                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                    color = if (isNegative) MaterialTheme.colorScheme.error else PrimaryGreen
                                                )
                                                if (isExpanded) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(onClick = { adjustmentToDelete = adj }, modifier = Modifier.size(20.dp)) {
                                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Add Adjustment button (always visible for non-upcoming cycles)
                        if (cycle.status != PayCycleStatus.UPCOMING) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showAdjustmentForCycle = cycle },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Adjustment", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
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

    if (showAdjustmentForCycle != null) {
        AddAdjustmentDialog(
            cycle = showAdjustmentForCycle!!,
            onDismiss = { showAdjustmentForCycle = null },
            onSave = { type, amount, notes ->
                val cycle = showAdjustmentForCycle!!
                dashboardViewModel.addPayAdjustment(cycle.cycleKey, cycle.employer, type, amount, notes)
                showAdjustmentForCycle = null
            }
        )
    }

    if (adjustmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { adjustmentToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete Adjustment?", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                val adj = adjustmentToDelete!!
                Text("Remove ${adj.type} of $${"%.2f".format(adj.amount)}${if (adj.notes.isNotBlank()) " (${adj.notes})" else ""}?",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(onClick = { dashboardViewModel.deletePayAdjustment(adjustmentToDelete!!.id); adjustmentToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { adjustmentToDelete = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddAdjustmentDialog(cycle: WeeklyPayCycle, onDismiss: () -> Unit, onSave: (type: String, amount: Double, notes: String) -> Unit) {
    val adjustmentTypes = listOf("Bonus", "Overpaid", "Underpaid", "Deduction", "Correction")
    var selectedType by remember { mutableStateOf("Bonus") }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val cycleFormat = remember { SimpleDateFormat("MMM dd", Locale.US) }
    val cycleRange = "${cycleFormat.format(Date(cycle.startDate))} – ${cycleFormat.format(Date(cycle.endDate - 1000L))}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add Pay Adjustment", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${cycle.employer} · $cycleRange", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(expanded = typeMenuExpanded, onExpandedChange = { typeMenuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        adjustmentTypes.forEach { type ->
                            val desc = when (type) {
                                "Bonus" -> "Extra pay (holiday, performance)"
                                "Overpaid" -> "Paid more than expected"
                                "Underpaid" -> "Paid less than expected"
                                "Deduction" -> "Tax, uniform, penalty, etc."
                                "Correction" -> "Employer corrected amount"
                                else -> ""
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(type, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { selectedType = type; typeMenuExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Text("$", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (reason)") },
                    placeholder = { Text("e.g., Holiday bonus, Short shift on Tuesday", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2,
                    maxLines = 3
                )

                val isNegative = selectedType == "Deduction" || selectedType == "Underpaid"
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isNegative) MaterialTheme.colorScheme.error.copy(alpha = 0.08f) else PrimaryGreen.copy(alpha = 0.08f))) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isNegative) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null,
                                tint = if (isNegative) MaterialTheme.colorScheme.error else PrimaryGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "This will ${if (isNegative) "subtract" else "add"} $${"%.2f".format(amount)} ${if (isNegative) "from" else "to"} this cycle's earnings",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedType, amountStr.toDoubleOrNull() ?: 0.0, notes) },
                enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    dashboardViewModel: DashboardViewModel,
    authViewModel: AuthViewModel? = null,
    onBack: () -> Unit,
    onNavigateToInsights: () -> Unit = {}
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReauthDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    val deleteState by authViewModel?.deleteState?.collectAsState() ?: remember { mutableStateOf(DeleteAccountState.Idle) }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameValue by remember(userName) { mutableStateOf(userName) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordChangeState by authViewModel?.passwordChangeState?.collectAsState() ?: remember { mutableStateOf(PasswordChangeState.Idle) }

    LaunchedEffect(passwordChangeState) {
        if (passwordChangeState is PasswordChangeState.Success) {
            showChangePasswordDialog = false
            currentPassword = ""; newPassword = ""; confirmPassword = ""
        }
    }

    LaunchedEffect(deleteState) {
        when (deleteState) {
            is DeleteAccountState.NeedsReauth -> {
                showDeleteDialog = false
                showReauthDialog = true
            }
            is DeleteAccountState.Success -> {
                showDeleteDialog = false
                showReauthDialog = false
                onBack()
            }
            else -> {}
        }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { editNameValue = userName; showEditNameDialog = true }) {
                        Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, "Edit name", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
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

            // Change Password card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showChangePasswordDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text("Update your account password", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notification Preferences card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notification Preferences", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val remindersOn by dashboardViewModel.remindersEnabled.collectAsState()
                    val defaultReminder by dashboardViewModel.defaultReminderMinutes.collectAsState()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Enable Shift Reminders", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        Switch(checked = remindersOn, onCheckedChange = { dashboardViewModel.setRemindersEnabled(it) }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryGreen))
                    }
                    if (remindersOn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Default Reminder Time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (minutes, label) ->
                                val selected = defaultReminder == minutes
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { dashboardViewModel.setDefaultReminderMinutes(minutes) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
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

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onNavigateToInsights() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Insights, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Earnings Insights", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text("Charts, trends & analytics", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Danger Zone", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showExportDialog) {
        ExportFilterDialog(dashboardViewModel = dashboardViewModel, onDismiss = { showExportDialog = false })
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; authViewModel?.resetDeleteState() },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete Account", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This will permanently delete your account and all your shift data. This action cannot be undone.")
                    if (deleteState is DeleteAccountState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text((deleteState as DeleteAccountState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { authViewModel?.deleteAccount() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = deleteState !is DeleteAccountState.Loading
                ) {
                    if (deleteState is DeleteAccountState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false; authViewModel?.resetDeleteState() }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showReauthDialog) {
        AlertDialog(
            onDismissRequest = { showReauthDialog = false; deletePassword = ""; authViewModel?.resetDeleteState() },
            title = { Text("Re-enter Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("For security, please re-enter your password to delete your account.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (deleteState is DeleteAccountState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text((deleteState as DeleteAccountState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { authViewModel?.deleteAccount(deletePassword) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = deletePassword.isNotBlank() && deleteState !is DeleteAccountState.Loading
                ) {
                    if (deleteState is DeleteAccountState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showReauthDialog = false; deletePassword = ""; authViewModel?.resetDeleteState() }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editNameValue,
                    onValueChange = { if (it.length <= 100) editNameValue = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editNameValue.trim()
                        if (trimmed.isNotBlank()) {
                            dashboardViewModel.updateUserName(trimmed)
                            showEditNameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    enabled = editNameValue.trim().isNotBlank()
                ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false; currentPassword = ""; newPassword = ""; confirmPassword = ""; authViewModel?.resetPasswordChangeState() },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("Min 8 chars, letters and numbers") }
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword
                    )
                    if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
                        Text("Passwords do not match", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    if (passwordChangeState is PasswordChangeState.Error) {
                        Text((passwordChangeState as PasswordChangeState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    if (passwordChangeState is PasswordChangeState.Success) {
                        Text("Password changed successfully!", color = PrimaryGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { authViewModel?.changePassword(currentPassword, newPassword) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && newPassword == confirmPassword && passwordChangeState !is PasswordChangeState.Loading
                ) {
                    if (passwordChangeState is PasswordChangeState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Change", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showChangePasswordDialog = false; currentPassword = ""; newPassword = ""; confirmPassword = ""; authViewModel?.resetPasswordChangeState() }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ExportFilterDialog(dashboardViewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val jobs by dashboardViewModel.jobs.collectAsState()
    val availableWeeks = remember { dashboardViewModel.getAvailableWeeks() }
    val availableCycles = remember { dashboardViewModel.getAvailablePayCycles() }
    val context = LocalContext.current

    var exportMode by remember { mutableStateOf(if (availableCycles.isNotEmpty()) "Pay Cycle" else "Calendar Week") }
    var selectedWeekIndex by remember { mutableStateOf(if (availableWeeks.isEmpty()) 0 else availableWeeks.indexOfFirst { it.second.contains("Current") }.coerceIn(0, availableWeeks.size - 1)) }
    var selectedCycleIndex by remember { mutableStateOf(if (availableCycles.isEmpty()) 0 else availableCycles.indexOfFirst { it.isCurrent }.coerceIn(0, availableCycles.size - 1)) }
    var selectedEmployer by remember { mutableStateOf("All") }
    var exportFormat by remember { mutableStateOf("Text") }
    var weekExpanded by remember { mutableStateOf(false) }
    var cycleExpanded by remember { mutableStateOf(false) }
    var employerExpanded by remember { mutableStateOf(false) }

    val preview = remember(exportMode, selectedWeekIndex, selectedCycleIndex, selectedEmployer, exportFormat) {
        if (exportMode == "Pay Cycle" && availableCycles.isNotEmpty() && selectedCycleIndex in availableCycles.indices) {
            val cycle = availableCycles[selectedCycleIndex]
            val job = jobs.firstOrNull { it.title.equals(cycle.employer, ignoreCase = true) }
            if (exportFormat == "CSV") {
                dashboardViewModel.generateCycleCsvReport(cycle.cycleStart, cycle.cycleEnd, cycle.employer, job)
            } else {
                dashboardViewModel.generateCycleReport(cycle.cycleStart, cycle.cycleEnd, cycle.employer, job)
            }
        } else if (availableWeeks.isNotEmpty() && selectedWeekIndex in availableWeeks.indices) {
            if (exportFormat == "CSV") {
                dashboardViewModel.generateCsvReport(availableWeeks[selectedWeekIndex].first, selectedEmployer)
            } else {
                dashboardViewModel.generateFormattedReport(availableWeeks[selectedWeekIndex].first, selectedEmployer)
            }
        } else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Shift Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Export mode toggle
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pay Cycle", "Calendar Week").forEach { mode ->
                        val selected = exportMode == mode
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { exportMode = mode }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(mode, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                if (exportMode == "Pay Cycle") {
                    // Pay cycle selector
                    if (availableCycles.isEmpty()) {
                        Text("No pay cycles found. Add shifts to an employer first.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = availableCycles[selectedCycleIndex].label,
                                onValueChange = {}, readOnly = true, label = { Text("Pay Cycle") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { cycleExpanded = true })
                            DropdownMenu(expanded = cycleExpanded, onDismissRequest = { cycleExpanded = false }) {
                                availableCycles.forEachIndexed { index, cycle ->
                                    DropdownMenuItem(
                                        text = { Text("${cycle.label} (${cycle.shiftCount} shifts)", fontSize = 13.sp) },
                                        onClick = { selectedCycleIndex = index; cycleExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Calendar week selector
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

                    // Employer selector (only for calendar week mode)
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
                }

                // Format selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Text", "CSV").forEach { format ->
                        val selected = exportFormat == format
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { exportFormat = format }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(format, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }

                // Preview
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Text(preview.ifBlank { "No shifts for this selection." }, fontSize = 12.sp, modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onBackground, style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        lineHeight = 16.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val mimeType = if (exportFormat == "CSV") "text/csv" else "text/plain"
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_SUBJECT, "Schedulo Shift Report")
                    putExtra(Intent.EXTRA_TEXT, preview)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Report"))
                onDismiss()
            }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = preview.isNotBlank()
            ) { Text("Share") }
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

    val jobCycleStartDay = remember(selectedJob) {
        when (selectedJob?.weeklyCycleStartDay?.lowercase(Locale.US)) {
            "sunday" -> Calendar.SUNDAY; "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY; "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY; "saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }

    val daysOfWeek = remember(jobCycleStartDay) {
        val allDays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val calDayOrder = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
        val startIndex = calDayOrder.indexOf(jobCycleStartDay)
        (0 until 7).map { allDays[(startIndex + it) % 7] }
    }

    var dayEnabled by remember(selectedJob) { mutableStateOf(List(7) { it < 5 }) }
    var dayStartHours by remember { mutableStateOf(List(7) { 9 }) }
    var dayStartMinutes by remember { mutableStateOf(List(7) { 0 }) }
    var dayEndHours by remember { mutableStateOf(List(7) { 17 }) }
    var dayEndMinutes by remember { mutableStateOf(List(7) { 0 }) }

    var weekOffset by remember { mutableStateOf(0) }
    val weekStartMillis = remember(weekOffset, jobCycleStartDay) {
        Calendar.getInstance().apply {
            firstDayOfWeek = jobCycleStartDay
            set(Calendar.DAY_OF_WEEK, jobCycleStartDay)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }.timeInMillis
    }

    var showTimePickerForDay by remember { mutableStateOf(-1) }
    var isStartTimePicker by remember { mutableStateOf(true) }

    val dayFormat = remember { SimpleDateFormat("M/dd", Locale.US) }
    val duplicateDays = remember(shifts, selectedJob, weekStartMillis) {
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

    if (showTimePickerForDay >= 0) {
        val dayIndex = showTimePickerForDay
        val initialHour = if (isStartTimePicker) dayStartHours[dayIndex] else dayEndHours[dayIndex]
        val initialMinute = if (isStartTimePicker) dayStartMinutes[dayIndex] else dayEndMinutes[dayIndex]
        val pickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
        AlertDialog(
            onDismissRequest = { showTimePickerForDay = -1 },
            title = { Text(if (isStartTimePicker) "Start Time — ${daysOfWeek[dayIndex]}" else "End Time — ${daysOfWeek[dayIndex]}", fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                Button(onClick = {
                    if (isStartTimePicker) {
                        dayStartHours = dayStartHours.toMutableList().also { it[dayIndex] = pickerState.hour }
                        dayStartMinutes = dayStartMinutes.toMutableList().also { it[dayIndex] = pickerState.minute }
                    } else {
                        dayEndHours = dayEndHours.toMutableList().also { it[dayIndex] = pickerState.hour }
                        dayEndMinutes = dayEndMinutes.toMutableList().also { it[dayIndex] = pickerState.minute }
                    }
                    showTimePickerForDay = -1
                }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePickerForDay = -1 }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)
        )
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

            Spacer(modifier = Modifier.height(16.dp))

            // Week selector with past and future weeks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (weekOffset > -3) weekOffset-- }, enabled = weekOffset > -3) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week",
                        tint = if (weekOffset > -3) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${dayFormat.format(Date(weekStartMillis))} – ${dayFormat.format(Date(weekStartMillis + 6L * 24 * 60 * 60 * 1000L))}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    val cycleDay = selectedJob?.weeklyCycleStartDay ?: "Monday"
                    Text(
                        when {
                            weekOffset == 0 -> "Current Cycle ($cycleDay start)"
                            weekOffset == 1 -> "Next Week"
                            weekOffset == -1 -> "Last Week"
                            weekOffset < -1 -> "${-weekOffset} weeks ago"
                            else -> "In $weekOffset weeks"
                        },
                        fontSize = 12.sp, color = if (weekOffset < 0) AccentOrange else PrimaryGreen, fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { if (weekOffset < 12) weekOffset++ }, enabled = weekOffset < 12) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week",
                        tint = if (weekOffset < 12) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            daysOfWeek.forEachIndexed { index, dayName ->
                val dayMillis = weekStartMillis + index.toLong() * 24 * 60 * 60 * 1000L
                val dateStr = dayFormat.format(Date(dayMillis))
                val hasDuplicate = index in duplicateDays
                val enabled = dayEnabled[index]
                val startTimeStr = String.format(Locale.US, "%d:%02d %s",
                    if (dayStartHours[index] % 12 == 0) 12 else dayStartHours[index] % 12,
                    dayStartMinutes[index],
                    if (dayStartHours[index] < 12) "AM" else "PM")
                val endTimeStr = String.format(Locale.US, "%d:%02d %s",
                    if (dayEndHours[index] % 12 == 0) 12 else dayEndHours[index] % 12,
                    dayEndMinutes[index],
                    if (dayEndHours[index] < 12) "AM" else "PM")

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
                                OutlinedButton(
                                    onClick = { isStartTimePicker = true; showTimePickerForDay = index },
                                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                                ) { Text("Start: $startTimeStr", fontSize = 13.sp) }
                                OutlinedButton(
                                    onClick = { isStartTimePicker = false; showTimePickerForDay = index },
                                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                                ) { Text("End: $endTimeStr", fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val totalDays = dayEnabled.count { it } - duplicateDays.count { dayEnabled[it] }
            val totalHours = (0..6).filter { dayEnabled[it] && it !in duplicateDays }.sumOf { i ->
                val startMin = dayStartHours[i] * 60 + dayStartMinutes[i]
                val endMin = dayEndHours[i] * 60 + dayEndMinutes[i]
                val diff = if (endMin > startMin) endMin - startMin else 1440 - startMin + endMin
                diff / 60.0
            }
            Text("$totalDays days · ${"%.1f".format(totalHours)} hours planned", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val job = selectedJob ?: return@Button
                    val entries = (0..6).filter { dayEnabled[it] && it !in duplicateDays }
                        .map { DashboardViewModel.WeekDayEntry(it, dayStartHours[it], dayStartMinutes[it], dayEndHours[it], dayEndMinutes[it]) }
                    viewModel.addWeekPlanWithMinutes(
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
