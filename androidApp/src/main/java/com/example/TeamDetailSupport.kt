package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.PrimaryGreen
import com.schedulo.shared.model.Team
import com.schedulo.shared.model.TeamMember
import com.schedulo.shared.model.TeamShift
import kotlinx.coroutines.flow.MutableStateFlow
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

    val title = when (section) {
        "dashboard" -> "Team Dashboard"
        "schedule" -> "Team Schedule"
        "tasks" -> "Team Tasks"
        else -> "Team"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
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
                currentTeam = currentTeam
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
                members = members,
                isManager = isManager,
                currentUserId = currentUserId,
                teamViewModel = teamViewModel
            )
        }
    }

    if (showAssignDialog && members.isNotEmpty()) {
        AssignShiftDialog(
            onDismiss = { showAssignDialog = false },
            onAssign = { memberId, company, role, startTime, endTime, hourlyRate, notes, tasks ->
                teamViewModel.assignShift(memberId, company, role, startTime, endTime, hourlyRate, notes, tasks)
                showAssignDialog = false
            },
            members = members,
            jobs = jobs
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
            jobs = jobs,
            teamViewModel = teamViewModel
        )
    }
}

@Composable
private fun DashboardDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    jobs: List<Job>,
    isManager: Boolean,
    currentTeam: Team?
) {
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
        items(members, key = { it.id }) { member -> MemberCard(member = member) }
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
                    onToggleTask = { taskId -> teamViewModel.toggleTaskCompletion(shift.id, taskId) }
                )
            }
        }
    }
}

@Composable
private fun TasksDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    isManager: Boolean,
    currentUserId: String,
    teamViewModel: TeamViewModel
) {
    val shiftsWithTasks = remember(teamShifts) { teamShifts.filter { it.tasks.isNotEmpty() } }
    val allTasks = remember(shiftsWithTasks) { shiftsWithTasks.flatMap { it.tasks } }
    val completedCount = allTasks.count { it.isCompleted }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "$completedCount/${allTasks.size} tasks completed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    if (allTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { completedCount.toFloat() / allTasks.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentOrange,
                            trackColor = AccentOrange.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        if (shiftsWithTasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No tasks yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(shiftsWithTasks, key = { it.id }) { shift ->
                val assignedMember = members.find { it.userId == shift.assignedTo }
                val assignedName = assignedMember?.displayName?.ifBlank { assignedMember.email.substringBefore("@") } ?: "Unknown"
                val isAssignedToMe = shift.assignedTo == currentUserId
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
                                Text(
                                    "Assigned to: $assignedName",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                    .clickable(enabled = isAssignedToMe || isManager) {
                                        teamViewModel.toggleTaskCompletion(shift.id, task.id)
                                    }
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
        }
    }
}
