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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.PrimaryGreen
import com.schedulo.shared.model.Team
import com.schedulo.shared.model.TeamMember
import com.schedulo.shared.model.SwapRequest
import com.schedulo.shared.model.TeamMessage
import com.schedulo.shared.model.TeamShift
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

    val teamMessages by teamViewModel.teamMessages.collectAsState()
    val swapRequests by teamViewModel.swapRequests.collectAsState()

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
                members = members,
                isManager = isManager,
                currentUserId = currentUserId,
                teamViewModel = teamViewModel
            )
            "roster" -> RosterDetailContent(
                modifier = Modifier.padding(padding),
                teamShifts = teamShifts,
                members = members,
                weeklyCycleStartDay = currentTeam?.weeklyCycleStartDay ?: "Monday"
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
            teamViewModel = teamViewModel,
            weeklyCycleStartDay = currentTeam?.weeklyCycleStartDay ?: "Monday"
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

@Composable
private fun RosterDetailContent(
    modifier: Modifier,
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    weeklyCycleStartDay: String = "Monday"
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
                            modifier = Modifier.width(100.dp).padding(4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                memberName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onBackground
                            )
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

    val pinnedMessages = remember(messages) { messages.filter { it.isPinned } }
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.createdAt } }

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
                        Text(
                            "${msg.senderName.ifBlank { "Unknown" }}: ${msg.text}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sortedMessages, key = { it.id }) { message ->
                val isMe = message.senderId == currentUserId
                ChatBubble(
                    message = message,
                    isMe = isMe,
                    isOwner = isOwner,
                    timeFormat = timeFormat,
                    onDelete = { teamViewModel.deleteMessage(message.id) },
                    onTogglePin = { teamViewModel.togglePin(message.id) }
                )
            }
        }

        HorizontalDivider()

        if (isManager) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { if (it.length <= 2000) messageText = it },
                placeholder = { Text("Type a message...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
                    tint = if (messageText.isNotBlank()) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    message: TeamMessage,
    isMe: Boolean,
    isOwner: Boolean,
    timeFormat: java.text.SimpleDateFormat,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val bgColor = when {
        message.isAnnouncement -> AccentOrange.copy(alpha = 0.12f)
        isMe -> PrimaryGreen.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.isAnnouncement) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Announcement", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (!isMe) {
                    Text(
                        message.senderName.ifBlank { "Unknown" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(message.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeFormat.format(java.util.Date(message.createdAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (message.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.PushPin, null, tint = AccentOrange, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
        if (isMe || isOwner) {
            Row(modifier = Modifier.padding(top = 2.dp)) {
                if (isOwner) {
                    Icon(
                        if (message.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                        if (message.isPinned) "Unpin" else "Pin",
                        tint = if (message.isPinned) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).clickable { onTogglePin() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp).clickable { onDelete() }
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (activeRequests.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
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
