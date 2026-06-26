package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.PrimaryGreen
import com.schedulo.shared.model.Team
import com.schedulo.shared.model.TeamMember
import com.schedulo.shared.model.SwapRequest
import com.schedulo.shared.model.TeamMessage
import com.schedulo.shared.model.TeamShift
import com.schedulo.shared.model.TeamTask
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    section: String,
    teamViewModel: TeamViewModel,
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel? = null,
    onBack: () -> Unit
) {
    val members by teamViewModel.members.collectAsState()
    val teamShifts by teamViewModel.teamShifts.collectAsState()
    val userRole by teamViewModel.userRole.collectAsState()
    val currentUserId by authViewModel.currentUserId.collectAsState()
    val currentTeam by teamViewModel.currentTeam.collectAsState()
    val jobs by (dashboardViewModel?.jobs ?: MutableStateFlow(emptyList<Job>())).collectAsState()
    val isManager = userRole == "manager"

    var showAssignDialog by remember { mutableStateOf(false) }
    var showWeekPlanDialog by remember { mutableStateOf(false) }
    var scheduleMenuExpanded by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    // Pre-selected member when creating a task (e.g. tapped from the roster grid). "" = none yet.
    var createTaskForMember by remember { mutableStateOf<String?>(null) }

    val teamMessages by teamViewModel.teamMessages.collectAsState()
    val swapRequests by teamViewModel.swapRequests.collectAsState()
    val teamTasks by teamViewModel.teamTasks.collectAsState()
    val errorMessage by teamViewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            teamViewModel.clearError()
        }
    }

    val title = when (section) {
        "dashboard" -> "Team Dashboard"
        "schedule" -> "Team Schedule"
        "tasks" -> "Team Tasks"
        "roster" -> "Team Roster"
        "chat" -> "Team Chat"
        "swaps" -> "Swap Requests"
        else -> "Team"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (section == "tasks" && isManager) {
                        IconButton(onClick = { createTaskForMember = "" }) {
                            Icon(Icons.Default.AddTask, "Create Task")
                        }
                    }
                    if (section == "schedule" && isManager) {
                        Box {
                            IconButton(onClick = { scheduleMenuExpanded = true }) {
                                Icon(Icons.Default.Add, "Assign Shift")
                            }
                            DropdownMenu(expanded = scheduleMenuExpanded, onDismissRequest = { scheduleMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Assign Single Shift") },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                    onClick = { scheduleMenuExpanded = false; showAssignDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Plan Entire Week") },
                                    leadingIcon = { Icon(Icons.Default.DateRange, null) },
                                    onClick = { scheduleMenuExpanded = false; showWeekPlanDialog = true }
                                )
                            }
                        }
                    }
                    // Per-section help — explains what the section does (the "?" tooltip).
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.HelpOutline, "Help")
                    }
                }
            )
        }
    ) { padding ->
        when (section) {
            "dashboard" -> DashboardDetailContent(
                modifier = Modifier.padding(padding),
                teamShifts = teamShifts,
                members = members,
                jobs = jobs,
                isManager = isManager,
                currentTeam = currentTeam,
                currentUserId = currentUserId,
                teamViewModel = teamViewModel
            )
            "schedule" -> ScheduleDetailContent(
                modifier = Modifier.padding(padding),
                teamShifts = teamShifts,
                members = members,
                isManager = isManager,
                currentUserId = currentUserId,
                teamViewModel = teamViewModel
            )
            "tasks" -> TasksDetailContent(
                modifier = Modifier.padding(padding),
                teamShifts = teamShifts,
                teamTasks = teamTasks,
                members = members,
                isManager = isManager,
                currentUserId = currentUserId,
                teamViewModel = teamViewModel
            )
            "roster" -> RosterDetailContent(
                modifier = Modifier.padding(padding),
                teamShifts = teamShifts,
                members = members,
                isManager = isManager,
                weeklyCycleStartDay = currentTeam?.weeklyCycleStartDay ?: "Monday",
                onAssignTask = { memberUserId -> createTaskForMember = memberUserId }
            )
            "chat" -> ChatDetailContent(
                modifier = Modifier.padding(padding),
                messages = teamMessages,
                isManager = isManager,
                currentUserId = currentUserId,
                currentTeam = currentTeam,
                teamViewModel = teamViewModel
            )
            "swaps" -> SwapRequestsContent(
                modifier = Modifier.padding(padding),
                swapRequests = swapRequests,
                teamShifts = teamShifts,
                members = members,
                isManager = isManager,
                currentUserId = currentUserId,
                teamViewModel = teamViewModel
            )
        }
    }

    val teamCompany = currentTeam?.companyName ?: ""
    val teamStartMin = if (currentTeam?.open24Hours != false) 9 * 60 else (currentTeam?.workStartMinutes ?: 9 * 60)
    val teamEndMin = if (currentTeam?.open24Hours != false) 17 * 60 else (currentTeam?.workEndMinutes ?: 17 * 60)

    if (showAssignDialog && members.isNotEmpty()) {
        AssignShiftDialog(
            onDismiss = { showAssignDialog = false },
            onAssign = { memberId, company, role, startTime, endTime, hourlyRate, notes, tasks ->
                teamViewModel.assignShift(memberId, company, role, startTime, endTime, hourlyRate, notes, tasks)
                showAssignDialog = false
            },
            members = members,
            companyName = teamCompany,
            defaultStartMinutes = teamStartMin,
            defaultEndMinutes = teamEndMin
        )
    }

    if (showWeekPlanDialog && members.isNotEmpty()) {
        TeamWeekPlanDialog(
            onDismiss = { showWeekPlanDialog = false },
            onAssignShifts = { memberId, company, role, hourlyRate, notes, tasks, weekStartMillis, dayEntries ->
                for (entry in dayEntries) {
                    val dayMillis = weekStartMillis + entry.dayOffset.toLong() * 24 * 60 * 60 * 1000L
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = dayMillis
                        set(Calendar.HOUR_OF_DAY, entry.startH)
                        set(Calendar.MINUTE, entry.startM)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startTime = cal.timeInMillis
                    cal.apply {
                        set(Calendar.HOUR_OF_DAY, entry.endH)
                        set(Calendar.MINUTE, entry.endM)
                    }
                    var endTime = cal.timeInMillis
                    if (endTime <= startTime) endTime += 24 * 60 * 60 * 1000L
                    teamViewModel.assignShift(memberId, company, role, startTime, endTime, hourlyRate, notes, tasks)
                }
                showWeekPlanDialog = false
            },
            members = members,
            companyName = teamCompany,
            teamViewModel = teamViewModel,
            weeklyCycleStartDay = currentTeam?.weeklyCycleStartDay ?: "Monday",
            defaultStartMinutes = teamStartMin,
            defaultEndMinutes = teamEndMin
        )
    }

    if (showHelp) {
        SectionHelpDialog(section = section, onDismiss = { showHelp = false })
    }

    createTaskForMember?.let { preselectedUserId ->
        CreateTaskDialog(
            members = members,
            preselectedUserId = preselectedUserId,
            onDismiss = { createTaskForMember = null },
            onCreate = { memberId, memberName, title, description ->
                teamViewModel.createTeamTask(memberId, memberName, title, description)
                createTaskForMember = null
            }
        )
    }
}

private fun sectionHelpText(section: String): Pair<String, String> = when (section) {
    "dashboard" -> "Team Dashboard" to
        "See your team's invite code, member list, and (for managers) an overview of upcoming shifts. Owners can promote, demote, or remove members here."
    "schedule" -> "Team Schedule" to
        "View every assigned shift. Managers can assign a single shift or plan an entire week with the + button. Members can accept or decline shifts assigned to them, and request a swap on a teammate's shift."
    "tasks" -> "Team Tasks" to
        "Assign individual to-dos to team members and track their progress. Use the + button to create a task; the assignee (or a manager) moves it through Pending → In Progress → Completed, and every change is kept in the task's history. Shift checklists also appear here."
    "roster" -> "Team Roster" to
        "A week-at-a-glance grid of who works when. Managers can tap a member's name to assign them a task. Use the arrows to move between weeks."
    "chat" -> "Team Chat" to
        "Message your whole team in real time. Managers can post announcements and pin messages. Share photos with the attach button — images auto-expire once everyone has seen them."
    "swaps" -> "Shift Swaps" to
        "Request to trade one of your shifts for a teammate's. Tap \"Request a Swap\" to pick the shift you want and the shift you'll give up. The teammate accepts, then a manager approves the trade."
    else -> "Team" to "Manage your team."
}

@Composable
private fun SectionHelpDialog(section: String, onDismiss: () -> Unit) {
    val (heading, body) = sectionHelpText(section)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HelpOutline, null, tint = PrimaryGreen) },
        title = { Text(heading, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", color = PrimaryGreen, fontWeight = FontWeight.Bold) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskDialog(
    members: List<TeamMember>,
    preselectedUserId: String,
    onDismiss: () -> Unit,
    onCreate: (memberId: String, memberName: String, title: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedUserId by remember {
        mutableStateOf(preselectedUserId.ifBlank { members.firstOrNull()?.userId ?: "" })
    }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    val selectedMember = members.find { it.userId == selectedUserId }
    val selectedName = selectedMember?.displayName?.ifBlank { selectedMember.email.substringBefore("@") } ?: "Select member"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign a Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Assign to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(
                    expanded = memberMenuExpanded,
                    onExpandedChange = { memberMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    ExposedDropdownMenu(
                        expanded = memberMenuExpanded,
                        onDismissRequest = { memberMenuExpanded = false }
                    ) {
                        members.forEach { member ->
                            val name = member.displayName.ifBlank { member.email.substringBefore("@") }
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { selectedUserId = member.userId; memberMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 200) title = it },
                    label = { Text("Task title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1000) description = it },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(selectedUserId, selectedName, title, description) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = title.isNotBlank() && selectedUserId.isNotBlank()
            ) { Text("Assign", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun TeamInfoCard(team: Team) {
    val address = listOf(team.addressLine, team.city, team.region, team.postalCode)
        .filter { it.isNotBlank() }.joinToString(", ")
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (team.companyName.isNotBlank()) {
                TeamInfoRow(Icons.Default.Business, "Company", team.companyName)
            }
            TeamInfoRow(Icons.Default.Schedule, "Working hours", formatWorkHours(team.open24Hours, team.workStartMinutes, team.workEndMinutes))
            TeamInfoRow(Icons.Default.CalendarMonth, "Week starts", team.weeklyCycleStartDay)
            if (address.isNotBlank()) {
                TeamInfoRow(Icons.Default.LocationOn, "Location", address)
            }
        }
    }
}

@Composable
private fun TeamInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun DashboardDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    jobs: List<Job>,
    isManager: Boolean,
    currentTeam: Team?,
    currentUserId: String = "",
    teamViewModel: TeamViewModel? = null
) {
    val isOwner = currentTeam?.ownerId == currentUserId
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (currentTeam != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Invite Code",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(currentTeam.inviteCode, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                    }
                }
            }

            item { TeamInfoCard(team = currentTeam) }
        }

        if (isManager && teamShifts.isNotEmpty()) {
            item { ManagerDashboardSection(teamShifts = teamShifts, members = members, jobs = jobs) }
        }

        item {
            Text(
                "Members",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(members, key = { it.id }) { member ->
            MemberCard(
                member = member,
                isOwner = isOwner,
                currentUserId = currentUserId,
                onPromote = if (isOwner && member.userId != currentUserId && member.role == "member") {
                    { teamViewModel?.promoteMember(member.id) }
                } else null,
                onDemote = if (isOwner && member.userId != currentUserId && member.role == "manager") {
                    { teamViewModel?.demoteMember(member.id) }
                } else null,
                onRemove = if (isOwner && member.userId != currentUserId) {
                    { teamViewModel?.removeMember(member.id, currentTeam?.id ?: "") }
                } else null,
                onSetRate = if (isOwner && member.userId != currentUserId) {
                    { rate -> teamViewModel?.updateMemberRate(member.id, rate) }
                } else null
            )
        }
    }
}

@Composable
private fun ScheduleDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    isManager: Boolean,
    currentUserId: String,
    teamViewModel: TeamViewModel
) {
    var swapTargetShift by remember { mutableStateOf<TeamShift?>(null) }
    val myAcceptedShifts = remember(teamShifts, currentUserId) {
        teamShifts.filter { it.assignedTo == currentUserId && it.status == "accepted" }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        if (teamShifts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No shifts assigned yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(teamShifts, key = { it.id }) { shift ->
                TeamShiftCard(
                    shift = shift,
                    members = members,
                    isManager = isManager,
                    currentUserId = currentUserId,
                    onDelete = { teamViewModel.deleteTeamShift(shift.id) },
                    onToggleTask = { taskId -> teamViewModel.toggleTaskCompletion(shift.id, taskId) },
                    onAccept = if (shift.assignedTo == currentUserId && shift.status == "assigned") {
                        { teamViewModel.updateShiftStatus(shift.id, "accepted") }
                    } else null,
                    onDecline = if (shift.assignedTo == currentUserId && shift.status == "assigned") {
                        { teamViewModel.updateShiftStatus(shift.id, "declined") }
                    } else null,
                    onRequestSwap = if (shift.assignedTo != currentUserId && shift.status == "accepted" && myAcceptedShifts.isNotEmpty()) {
                        { swapTargetShift = shift }
                    } else null
                )
            }
        }
    }

    if (swapTargetShift != null) {
        SwapPickerDialog(
            targetShift = swapTargetShift!!,
            myShifts = myAcceptedShifts,
            members = members,
            onDismiss = { swapTargetShift = null },
            onConfirm = { myShiftId ->
                teamViewModel.requestSwap(myShiftId, swapTargetShift!!.assignedTo, swapTargetShift!!.id)
                swapTargetShift = null
            }
        )
    }
}

private fun taskStatusColor(status: String): androidx.compose.ui.graphics.Color = when (status) {
    "completed" -> PrimaryGreen
    "in_progress" -> AccentBlue
    else -> AccentOrange
}

private fun taskStatusLabel(status: String): String = when (status) {
    "completed" -> "Completed"
    "in_progress" -> "In Progress"
    else -> "Pending"
}

@Composable
private fun TasksDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    teamTasks: List<TeamTask>,
    members: List<TeamMember>,
    isManager: Boolean,
    currentUserId: String,
    teamViewModel: TeamViewModel
) {
    val shiftsWithTasks = remember(teamShifts) { teamShifts.filter { it.tasks.isNotEmpty() } }
    // Members see their own tasks; managers see everyone's.
    val visibleTasks = remember(teamTasks, isManager, currentUserId) {
        if (isManager) teamTasks else teamTasks.filter { it.assignedTo == currentUserId }
    }
    val completedCount = visibleTasks.count { it.status == "completed" }
    val timeFormat = remember { java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "$completedCount/${visibleTasks.size} tasks completed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    if (visibleTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { completedCount.toFloat() / visibleTasks.size },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = AccentOrange,
                            trackColor = AccentOrange.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        if (visibleTasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isManager) "No tasks assigned yet. Tap + to assign one to a member." else "You have no tasks yet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item { SectionLabel("Assigned Tasks") }
            items(visibleTasks, key = { it.id }) { task ->
                val canEdit = isManager || task.assignedTo == currentUserId
                TeamTaskCard(
                    task = task,
                    canEdit = canEdit,
                    canDelete = isManager,
                    showAssignee = isManager,
                    currentUserId = currentUserId,
                    timeFormat = timeFormat,
                    onStatusChange = { newStatus -> teamViewModel.updateTeamTaskStatus(task.id, newStatus) },
                    onDelete = { teamViewModel.deleteTeamTask(task.id) }
                )
            }
        }

        if (shiftsWithTasks.isNotEmpty()) {
            item { SectionLabel("Shift Checklists") }
            items(shiftsWithTasks, key = { it.id }) { shift ->
                ShiftChecklistCard(
                    shift = shift,
                    members = members,
                    isAssignedToMe = shift.assignedTo == currentUserId,
                    isManager = isManager,
                    onToggle = { taskId -> teamViewModel.toggleTaskCompletion(shift.id, taskId) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun TeamTaskCard(
    task: TeamTask,
    canEdit: Boolean,
    canDelete: Boolean,
    showAssignee: Boolean,
    currentUserId: String,
    timeFormat: java.text.SimpleDateFormat,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showHistory by remember { mutableStateOf(false) }
    val statusColor = taskStatusColor(task.status)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (task.status == "completed") androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                    val whoLabel = when {
                        task.assignedTo == currentUserId -> "Assigned to you"
                        showAssignee -> "Assigned to: ${task.assignedToName.ifBlank { "Unknown" }}"
                        else -> null
                    }
                    if (whoLabel != null) {
                        Text(whoLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.12f)) {
                    Text(
                        taskStatusLabel(task.status),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(task.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
            }

            if (canEdit) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("pending", "in_progress", "completed").forEach { status ->
                        FilterChip(
                            selected = task.status == status,
                            onClick = { if (task.status != status) onStatusChange(status) },
                            label = { Text(taskStatusLabel(status), fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.clickable { showHistory = !showHistory },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (showHistory) Icons.Default.ExpandLess else Icons.Default.History,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "History (${task.history.size})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (canDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete task",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp).clickable { onDelete() }
                    )
                }
            }

            if (showHistory) {
                Spacer(modifier = Modifier.height(4.dp))
                task.history.forEach { entry ->
                    Row(modifier = Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(taskStatusColor(entry.status))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${taskStatusLabel(entry.status)} · ${entry.changedByName.ifBlank { "Someone" }} · ${timeFormat.format(java.util.Date(entry.timestamp))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftChecklistCard(
    shift: TeamShift,
    members: List<TeamMember>,
    isAssignedToMe: Boolean,
    isManager: Boolean,
    onToggle: (String) -> Unit
) {
    val assignedMember = members.find { it.userId == shift.assignedTo }
    val assignedName = assignedMember?.displayName?.ifBlank { assignedMember.email.substringBefore("@") } ?: "Unknown"
    val completed = shift.tasks.count { it.isCompleted }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(shift.company, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Assigned to: $assignedName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "$completed/${shift.tasks.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completed == shift.tasks.size) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            shift.tasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isAssignedToMe || isManager) { onToggle(task.id) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (task.isCompleted) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        task.title,
                        fontSize = 13.sp,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun RosterDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    isManager: Boolean = false,
    weeklyCycleStartDay: String = "Monday",
    onAssignTask: (String) -> Unit = {}
) {
    var weekOffset by remember { mutableIntStateOf(0) }

    val calendarDayOfWeek = remember(weeklyCycleStartDay) {
        dayNameToCalendar(weeklyCycleStartDay)
    }

    val weekStartMillis = remember(weekOffset, calendarDayOfWeek) {
        Calendar.getInstance().apply {
            firstDayOfWeek = calendarDayOfWeek
            set(Calendar.DAY_OF_WEEK, calendarDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }.timeInMillis
    }
    val weekEndMillis = weekStartMillis + 7L * 24 * 60 * 60 * 1000L

    val dayFormat = remember { SimpleDateFormat("EEE", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("M/dd", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val weekLabelFormat = remember { SimpleDateFormat("MMM dd", Locale.US) }

    val daysMillis = remember(weekStartMillis) {
        (0..6).map { weekStartMillis + it.toLong() * 24 * 60 * 60 * 1000L }
    }

    val weekShifts = remember(teamShifts, weekStartMillis, weekEndMillis) {
        teamShifts.filter { it.startTime < weekEndMillis && it.endTime > weekStartMillis }
    }

    val statusColor = @Composable { status: String ->
        when (status) {
            "accepted" -> PrimaryGreen
            "declined" -> MaterialTheme.colorScheme.error
            else -> AccentOrange
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${weekLabelFormat.format(Date(weekStartMillis))} – ${weekLabelFormat.format(Date(weekEndMillis - 24 * 60 * 60 * 1000L))}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (weekOffset) {
                        0 -> "This Week"
                        1 -> "Next Week"
                        -1 -> "Last Week"
                        else -> if (weekOffset > 0) "In $weekOffset weeks" else "${-weekOffset} weeks ago"
                    },
                    fontSize = 12.sp,
                    color = AccentBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier.width(100.dp).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Member", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            daysMillis.forEach { dayMillis ->
                Box(
                    modifier = Modifier.width(100.dp).padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayFormat.format(Date(dayMillis)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(dateFormat.format(Date(dayMillis)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (members.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No members", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(members, key = { it.id }) { member ->
                    val memberName = member.displayName.ifBlank { member.email.substringBefore("@") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .padding(4.dp)
                                .then(
                                    if (isManager) Modifier.clickable { onAssignTask(member.userId) } else Modifier
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    memberName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isManager) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.AddTask,
                                        "Assign task",
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        daysMillis.forEach { dayMillis ->
                            val dayEnd = dayMillis + 24L * 60 * 60 * 1000L
                            val dayShifts = weekShifts.filter {
                                it.assignedTo == member.userId && it.startTime < dayEnd && it.endTime > dayMillis
                            }
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .heightIn(min = 48.dp)
                                    .padding(2.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (dayShifts.isEmpty()) {
                                    Text("—", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        dayShifts.forEach { shift ->
                                            val color = statusColor(shift.status)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = color.copy(alpha = 0.12f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        timeFormat.format(Date(shift.startTime)),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = color
                                                    )
                                                    Text(
                                                        timeFormat.format(Date(shift.endTime)),
                                                        fontSize = 9.sp,
                                                        color = color.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatDetailContent(
    modifier: Modifier,
    messages: List<TeamMessage>,
    isManager: Boolean,
    currentUserId: String,
    currentTeam: Team?,
    teamViewModel: TeamViewModel
) {
    var messageText by remember { mutableStateOf("") }
    var isAnnouncement by remember { mutableStateOf(false) }
    val isOwner = currentTeam?.ownerId == currentUserId
    val listState = rememberLazyListState()
    val timeFormat = remember { java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US) }
    val context = LocalContext.current
    val isUploading by teamViewModel.isUploadingImage.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) teamViewModel.sendImage(uri, context) }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) teamViewModel.sendImage(uri, context) }

    // ACTION_OPEN_DOCUMENT is handled by the system DocumentsUI app which is
    // always present on every Android device — the most reliable final fallback.
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) teamViewModel.sendImage(uri, context) }

    // Every launch() is guarded: a launcher can throw (no handler, or a framework
    // requestCode error) and an uncaught exception here crashes the whole app.
    val launchImagePicker = {
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                getContentLauncher.launch("image/*")
            }
        } catch (_: Exception) {
            try {
                getContentLauncher.launch("image/*")
            } catch (_: Exception) {
                try {
                    openDocumentLauncher.launch(arrayOf("image/*"))
                } catch (_: Exception) {
                    teamViewModel.reportError("No gallery app available to pick a photo.")
                }
            }
        }
    }

    val pinnedMessages = remember(messages) { messages.filter { it.isPinned } }
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.createdAt } }

    LaunchedEffect(sortedMessages) {
        sortedMessages.filter { it.imageUrl.isNotEmpty() && currentUserId !in it.seenBy }
            .forEach { teamViewModel.markMessageSeen(it.id) }
    }

    // Keep the newest message in view (WhatsApp/Telegram behaviour). With
    // reverseLayout the newest item lives at index 0, so scroll there.
    LaunchedEffect(sortedMessages.firstOrNull()?.id) {
        if (sortedMessages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    val memberCount by teamViewModel.members.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        if (pinnedMessages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pinned", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    pinnedMessages.take(3).forEach { msg ->
                        val preview = if (msg.imageUrl.isNotEmpty()) "📷 Photo" else msg.text
                        Text(
                            "${msg.senderName.ifBlank { "Unknown" }}: $preview",
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(sortedMessages, key = { it.id }) { message ->
                val index = sortedMessages.indexOf(message)
                // In a descending list the "previous" (older) message sits at index+1.
                val prev = sortedMessages.getOrNull(index + 1)
                val isMe = message.senderId == currentUserId
                // First in a run from this sender → show the name + more top spacing.
                val isGroupStart = prev == null || prev.senderId != message.senderId
                ChatBubble(
                    message = message,
                    isMe = isMe,
                    isOwner = isOwner,
                    isGroupStart = isGroupStart,
                    memberCount = memberCount.size,
                    timeFormat = timeFormat,
                    onDelete = { teamViewModel.deleteMessage(message.id) },
                    onTogglePin = { teamViewModel.togglePin(message.id) }
                )
            }
        }

        if (isUploading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryGreen
            )
        }

        if (isManager) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = isAnnouncement,
                    onClick = { isAnnouncement = !isAnnouncement },
                    label = { Text("Announce", fontSize = 11.sp) },
                    leadingIcon = if (isAnnouncement) {
                        { Icon(Icons.Default.Campaign, null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Rounded "pill" wrapping the attach button + text field, WhatsApp style.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { launchImagePicker() },
                        enabled = !isUploading
                    ) {
                        Icon(Icons.Default.Image, "Send photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextFieldRow(
                        value = messageText,
                        onValueChange = { if (it.length <= 2000) messageText = it }
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Circular send button (filled when there is something to send).
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (messageText.isNotBlank()) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                teamViewModel.sendMessage(messageText, isAnnouncement)
                                messageText = ""
                                isAnnouncement = false
                            }
                        },
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Send",
                            tint = if (messageText.isNotBlank()) androidx.compose.ui.graphics.Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BasicTextFieldRow(
    value: String,
    onValueChange: (String) -> Unit
) {
    Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 4.dp)) {
        if (value.isEmpty()) {
            Text(
                "Message",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(PrimaryGreen),
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    message: TeamMessage,
    isMe: Boolean,
    isOwner: Boolean,
    isGroupStart: Boolean,
    memberCount: Int,
    timeFormat: java.text.SimpleDateFormat,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val bgColor = when {
        message.isAnnouncement -> AccentOrange.copy(alpha = 0.15f)
        isMe -> PrimaryGreen.copy(alpha = 0.20f)
        else -> MaterialTheme.colorScheme.surface
    }
    // Asymmetric "tail" corner, like WhatsApp/Telegram bubbles.
    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isGroupStart) 6.dp else 1.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bgColor,
            tonalElevation = if (isMe) 0.dp else 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clickable { if (isMe || isOwner) showActions = !showActions }
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (message.isAnnouncement) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Announcement", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (!isMe && isGroupStart) {
                    Text(
                        message.senderName.ifBlank { "Unknown" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (message.imageUrl.isNotEmpty()) {
                    var bmp by remember(message.imageUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(message.imageUrl) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val url = java.net.URL(message.imageUrl)
                                val connection = url.openConnection()
                                connection.connectTimeout = 5000
                                val stream = connection.getInputStream()
                                bmp = BitmapFactory.decodeStream(stream)
                            } catch (_: Exception) {}
                        }
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp!!.asImageBitmap(),
                            contentDescription = "Shared photo",
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Box(
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PrimaryGreen,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                if (message.text.isNotEmpty()) {
                    Text(message.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                // Footer: time, pin marker, read-receipts — laid out bottom-right like WhatsApp.
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isPinned) {
                        Icon(Icons.Default.PushPin, null, tint = AccentOrange, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        timeFormat.format(java.util.Date(message.createdAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (message.imageUrl.isNotEmpty() && message.seenBy.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        // Everyone has seen it → double check, otherwise single.
                        val allSeen = memberCount > 0 && message.seenBy.size >= memberCount
                        Icon(
                            if (allSeen) Icons.Default.DoneAll else Icons.Default.Done,
                            "Seen",
                            tint = if (allSeen) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            " ${message.seenBy.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (showActions && (isMe || isOwner)) {
            Row(modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
                if (isOwner) {
                    Icon(
                        Icons.Default.PushPin,
                        if (message.isPinned) "Unpin" else "Pin",
                        tint = if (message.isPinned) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).clickable { onTogglePin(); showActions = false }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp).clickable { onDelete(); showActions = false }
                )
            }
        }
    }
}

@Composable
private fun SwapPickerDialog(
    targetShift: TeamShift,
    myShifts: List<TeamShift>,
    members: List<TeamMember>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedShiftId by remember { mutableStateOf(myShifts.firstOrNull()?.id ?: "") }
    val timeFormat = remember { java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US) }
    val targetMember = members.find { it.userId == targetShift.assignedTo }
    val targetName = targetMember?.displayName?.ifBlank { targetMember.email.substringBefore("@") } ?: "Unknown"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Swap", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Swap with $targetName's shift:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${targetShift.company} · ${timeFormat.format(java.util.Date(targetShift.startTime))}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select your shift to offer:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                myShifts.forEach { shift ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedShiftId = shift.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selectedShiftId == shift.id) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (selectedShiftId == shift.id) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(shift.company, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(timeFormat.format(java.util.Date(shift.startTime)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedShiftId) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = selectedShiftId.isNotBlank()
            ) { Text("Request Swap", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SwapRequestsContent(
    modifier: Modifier,
    swapRequests: List<SwapRequest>,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    isManager: Boolean,
    currentUserId: String,
    teamViewModel: TeamViewModel
) {
    val timeFormat = remember { java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US) }
    val activeRequests = remember(swapRequests) { swapRequests.filter { it.status != "approved" && it.status != "declined" } }
    var showNewSwap by remember { mutableStateOf(false) }
    val myAcceptedShifts = remember(teamShifts, currentUserId) {
        teamShifts.filter { it.assignedTo == currentUserId && it.status == "accepted" }
    }
    val swappableTargets = remember(teamShifts, currentUserId) {
        teamShifts.filter { it.assignedTo != currentUserId && it.status == "accepted" }
    }
    val canRequestSwap = myAcceptedShifts.isNotEmpty() && swappableTargets.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary entry point for *starting* a swap — pick the shift you want and the one you'll give up.
        item {
            Button(
                onClick = { showNewSwap = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp),
                enabled = canRequestSwap
            ) {
                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Request a Swap", fontWeight = FontWeight.Bold)
            }
            if (!canRequestSwap) {
                Text(
                    "You need an accepted shift, and a teammate must have one too, before you can request a swap.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                )
            }
        }

        if (activeRequests.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No pending swap requests", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(activeRequests, key = { it.id }) { request ->
                val requesterShift = teamShifts.find { it.id == request.requesterShiftId }
                val targetShift = teamShifts.find { it.id == request.targetShiftId }

                val statusColor = when (request.status) {
                    "pending" -> AccentOrange
                    "target_accepted" -> AccentBlue
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Shift Swap", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    when (request.status) {
                                        "pending" -> "Pending"
                                        "target_accepted" -> "Awaiting Manager"
                                        else -> request.status.replaceFirstChar { it.uppercase() }
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${request.requesterName} offers:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (requesterShift != null) {
                            Text("${requesterShift.company} · ${timeFormat.format(java.util.Date(requesterShift.startTime))}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(Icons.Default.SwapVert, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${request.targetMemberName} offers:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (targetShift != null) {
                            Text("${targetShift.company} · ${timeFormat.format(java.util.Date(targetShift.startTime))}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target member can accept/decline pending requests
                        if (request.status == "pending" && request.targetMemberId == currentUserId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { teamViewModel.respondToSwap(request.id, true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Accept", fontSize = 13.sp) }
                                OutlinedButton(
                                    onClick = { teamViewModel.respondToSwap(request.id, false) },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Decline", fontSize = 13.sp) }
                            }
                        }

                        // Manager can approve/decline after target accepted
                        if (request.status == "target_accepted" && isManager) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { teamViewModel.approveSwap(request.id, true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Approve Swap", fontSize = 13.sp) }
                                OutlinedButton(
                                    onClick = { teamViewModel.approveSwap(request.id, false) },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Decline", fontSize = 13.sp) }
                            }
                        }

                        // Requester can cancel pending requests
                        if (request.status == "pending" && request.requesterId == currentUserId) {
                            TextButton(
                                onClick = { teamViewModel.cancelSwapRequest(request.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("Cancel Request", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    if (showNewSwap) {
        NewSwapRequestDialog(
            myShifts = myAcceptedShifts,
            targetShifts = swappableTargets,
            members = members,
            onDismiss = { showNewSwap = false },
            onConfirm = { myShiftId, targetMemberId, targetShiftId ->
                teamViewModel.requestSwap(myShiftId, targetMemberId, targetShiftId)
                showNewSwap = false
            }
        )
    }
}

@Composable
private fun NewSwapRequestDialog(
    myShifts: List<TeamShift>,
    targetShifts: List<TeamShift>,
    members: List<TeamMember>,
    onDismiss: () -> Unit,
    onConfirm: (myShiftId: String, targetMemberId: String, targetShiftId: String) -> Unit
) {
    val timeFormat = remember { java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US) }
    var selectedTargetId by remember { mutableStateOf(targetShifts.firstOrNull()?.id ?: "") }
    var selectedMineId by remember { mutableStateOf(myShifts.firstOrNull()?.id ?: "") }
    val selectedTarget = targetShifts.find { it.id == selectedTargetId }

    fun memberName(userId: String): String {
        val m = members.find { it.userId == userId }
        return m?.displayName?.ifBlank { m.email.substringBefore("@") } ?: "Unknown"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request a Swap", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Pick the shift you want", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                Column(
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    targetShifts.forEach { shift ->
                        SwapShiftOption(
                            selected = selectedTargetId == shift.id,
                            title = "${memberName(shift.assignedTo)} · ${shift.company}",
                            subtitle = timeFormat.format(java.util.Date(shift.startTime)),
                            onClick = { selectedTargetId = shift.id }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("2. Pick your shift to offer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                Column(
                    modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    myShifts.forEach { shift ->
                        SwapShiftOption(
                            selected = selectedMineId == shift.id,
                            title = shift.company,
                            subtitle = timeFormat.format(java.util.Date(shift.startTime)),
                            onClick = { selectedMineId = shift.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = selectedTarget ?: return@Button
                    onConfirm(selectedMineId, target.assignedTo, target.id)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = selectedTargetId.isNotBlank() && selectedMineId.isNotBlank()
            ) { Text("Send Request", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SwapShiftOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (selected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun dayNameToCalendar(dayName: String): Int = when (dayName) {
    "Sunday" -> Calendar.SUNDAY
    "Monday" -> Calendar.MONDAY
    "Tuesday" -> Calendar.TUESDAY
    "Wednesday" -> Calendar.WEDNESDAY
    "Thursday" -> Calendar.THURSDAY
    "Friday" -> Calendar.FRIDAY
    "Saturday" -> Calendar.SATURDAY
    else -> Calendar.MONDAY
}
