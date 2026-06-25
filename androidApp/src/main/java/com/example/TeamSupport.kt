package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentOrange
import com.schedulo.shared.model.ShiftTask
import com.schedulo.shared.model.TeamMember
import com.schedulo.shared.model.TeamShift
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    teamViewModel: TeamViewModel,
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel? = null,
    onBack: (() -> Unit)? = null,
    onNavigateToDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val teams by teamViewModel.teams.collectAsState()
    val currentTeam by teamViewModel.currentTeam.collectAsState()
    val members by teamViewModel.members.collectAsState()
    val teamShifts by teamViewModel.teamShifts.collectAsState()
    val isLoading by teamViewModel.isLoading.collectAsState()
    val errorMessage by teamViewModel.errorMessage.collectAsState()
    val userRole by teamViewModel.userRole.collectAsState()

    val currentUserId by authViewModel.currentUserId.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showEditTeamDialog by remember { mutableStateOf(false) }
    var showDeleteTeamConfirm by remember { mutableStateOf(false) }
    var teamSelectorExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        teamViewModel.loadTeams()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Team Management") },
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                } else {
                    {}
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Team Options")
                        }
                        DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Create Team") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { overflowMenuExpanded = false; showCreateDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Join Team") },
                                leadingIcon = { Icon(Icons.Default.GroupAdd, null) },
                                onClick = { overflowMenuExpanded = false; showJoinDialog = true }
                            )
                            if (currentTeam != null) {
                                HorizontalDivider()
                                if (userRole == "manager") {
                                    DropdownMenuItem(
                                        text = { Text("Edit Team") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { overflowMenuExpanded = false; showEditTeamDialog = true }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Leave Team") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                                    onClick = { overflowMenuExpanded = false; showLeaveConfirm = true }
                                )
                                if (userRole == "manager") {
                                    DropdownMenuItem(
                                        text = { Text("Delete Team") },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                        onClick = { overflowMenuExpanded = false; showDeleteTeamConfirm = true }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Error banner
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            errorMessage ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Close, "Dismiss",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { teamViewModel.clearError() }
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = PrimaryGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (teams.isEmpty() && !isLoading) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Store Teams Yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create a store team to manage shifts for your crew, or join an existing team with an invite code.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Team", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showJoinDialog = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PrimaryGreen),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                        ) {
                            Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join Team", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (currentTeam != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Team selector (if multiple teams)
                    if (teams.size > 1) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    onClick = { teamSelectorExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Groups, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            currentTeam!!.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.UnfoldMore, "Select Team", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                                DropdownMenu(expanded = teamSelectorExpanded, onDismissRequest = { teamSelectorExpanded = false }) {
                                    teams.forEach { team ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    team.name,
                                                    fontWeight = if (team.id == currentTeam!!.id) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                teamViewModel.selectTeam(team)
                                                teamSelectorExpanded = false
                                            },
                                            leadingIcon = if (team.id == currentTeam!!.id) {
                                                { Icon(Icons.Default.Check, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Team identity strip
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currentTeam!!.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "${members.size} members",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryGreen.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, tint = PrimaryGreen, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        currentTeam!!.inviteCode,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryGreen
                                    )
                                }
                            }
                        }
                    }

                    // Bento grid: Dashboard / Schedule / Tasks
                    item {
                        val allTasks = remember(teamShifts) { teamShifts.flatMap { it.tasks } }
                        val completedTasks = allTasks.count { it.isCompleted }

                        // Bento grid scales tile sizes with the available width and caps
                        // the content width on large screens so tiles never look stretched.
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val gap = 12.dp
                            val contentW = maxWidth.coerceAtMost(640.dp)
                            val colW = (contentW - gap) / 2
                            val fullH = (contentW * 0.30f).coerceIn(96.dp, 150.dp)
                            val pairH = (colW * 0.80f).coerceIn(108.dp, 170.dp)
                            val swapH = (contentW * 0.22f).coerceIn(84.dp, 120.dp)

                            Column(
                                modifier = Modifier
                                    .width(contentW)
                                    .align(Alignment.TopCenter),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                BentoTile(
                                    modifier = Modifier.fillMaxWidth().height(fullH),
                                    title = "Team Dashboard",
                                    subtitle = if (userRole == "manager") "Hours & pay overview" else "${members.size} members",
                                    icon = Icons.Default.Dashboard,
                                    tint = PrimaryGreen,
                                    onClick = { onNavigateToDetail("dashboard") }
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    BentoTile(
                                        modifier = Modifier.weight(1f).height(pairH),
                                        title = "Team Schedule",
                                        subtitle = "${teamShifts.size} shifts",
                                        icon = Icons.Default.CalendarMonth,
                                        tint = AccentBlue,
                                        onClick = { onNavigateToDetail("schedule") }
                                    )
                                    BentoTile(
                                        modifier = Modifier.weight(1f).height(pairH),
                                        title = "Team Tasks",
                                        subtitle = "$completedTasks/${allTasks.size} done",
                                        icon = Icons.Default.Checklist,
                                        tint = AccentOrange,
                                        progress = if (allTasks.isNotEmpty()) completedTasks.toFloat() / allTasks.size else null,
                                        onClick = { onNavigateToDetail("tasks") }
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    BentoTile(
                                        modifier = Modifier.weight(1f).height(pairH),
                                        title = "Team Roster",
                                        subtitle = "Weekly grid",
                                        icon = Icons.Default.ViewWeek,
                                        tint = SecondaryGreen,
                                        onClick = { onNavigateToDetail("roster") }
                                    )
                                    BentoTile(
                                        modifier = Modifier.weight(1f).height(pairH),
                                        title = "Team Chat",
                                        subtitle = "Messages",
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        tint = AccentBlue,
                                        onClick = { onNavigateToDetail("chat") }
                                    )
                                }
                                BentoTile(
                                    modifier = Modifier.fillMaxWidth().height(swapH),
                                    title = "Shift Swaps",
                                    subtitle = "Request & manage swaps",
                                    icon = Icons.Default.SwapHoriz,
                                    tint = AccentOrange,
                                    onClick = { onNavigateToDetail("swaps") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTeamDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { form ->
                teamViewModel.createTeam(form)
                showCreateDialog = false
            }
        )
    }

    if (showJoinDialog) {
        JoinTeamDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                teamViewModel.joinTeam(code)
                showJoinDialog = false
            }
        )
    }

    if (showLeaveConfirm && currentTeam != null) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Leave Team", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave \"${currentTeam!!.name}\"? You will lose access to team shifts and data.") },
            confirmButton = {
                Button(
                    onClick = {
                        teamViewModel.leaveTeam(currentTeam!!.id)
                        showLeaveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showEditTeamDialog && currentTeam != null) {
        EditTeamDialog(
            team = currentTeam!!,
            onDismiss = { showEditTeamDialog = false },
            onSave = { form ->
                teamViewModel.updateTeam(currentTeam!!.id, form)
                showEditTeamDialog = false
            }
        )
    }

    if (showDeleteTeamConfirm && currentTeam != null) {
        AlertDialog(
            onDismissRequest = { showDeleteTeamConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete Team", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${currentTeam!!.name}\"? All team data, members, and shifts will be removed. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        teamViewModel.deleteTeam(currentTeam!!.id)
                        showDeleteTeamConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteTeamConfirm = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    progress: Float? = null
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    tint = tint.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = tint,
                        trackColor = tint.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

@Composable
fun MemberCard(
    member: TeamMember,
    isOwner: Boolean = false,
    currentUserId: String = "",
    onPromote: (() -> Unit)? = null,
    onDemote: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onSetRate: ((Double) -> Unit)? = null
) {
    var showRateDialog by remember { mutableStateOf(false) }
    val initials = remember(member.displayName, member.email) {
        if (member.displayName.isNotBlank()) {
            val parts = member.displayName.trim().split(" ")
            if (parts.size >= 2) "${parts.first().first()}${parts.last().first()}".uppercase()
            else member.displayName.take(2).uppercase()
        } else {
            val prefix = member.email.substringBefore("@")
            if (prefix.length >= 2) prefix.take(2).uppercase() else prefix.uppercase().ifEmpty { "U" }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (member.role == "manager") PrimaryGreen else AccentBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    member.displayName.ifBlank { member.email.substringBefore("@") },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (member.email.isNotBlank()) {
                    Text(
                        member.email,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (member.defaultHourlyRate > 0.0) {
                    Text(
                        "$${String.format("%.2f", member.defaultHourlyRate)}/hr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryGreen
                    )
                } else if (onSetRate != null) {
                    Text("No pay rate set", fontSize = 12.sp, color = AccentOrange)
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (member.role == "manager") PrimaryGreen.copy(alpha = 0.1f) else AccentBlue.copy(alpha = 0.1f)
            ) {
                Text(
                    member.role.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (member.role == "manager") PrimaryGreen else AccentBlue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (isOwner && member.userId != currentUserId) {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, "Member options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (member.role == "member" && onPromote != null) {
                            DropdownMenuItem(
                                text = { Text("Promote to Manager") },
                                leadingIcon = { Icon(Icons.Default.Star, null, tint = PrimaryGreen) },
                                onClick = { menuExpanded = false; onPromote() }
                            )
                        }
                        if (member.role == "manager" && onDemote != null) {
                            DropdownMenuItem(
                                text = { Text("Demote to Member") },
                                leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = AccentOrange) },
                                onClick = { menuExpanded = false; onDemote() }
                            )
                        }
                        if (onSetRate != null) {
                            DropdownMenuItem(
                                text = { Text("Set Pay Rate") },
                                leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = PrimaryGreen) },
                                onClick = { menuExpanded = false; showRateDialog = true }
                            )
                        }
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from Team") },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; onRemove() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRateDialog && onSetRate != null) {
        var rateStr by remember { mutableStateOf(if (member.defaultHourlyRate > 0) String.format("%.2f", member.defaultHourlyRate) else "") }
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("Set Pay Rate", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Default hourly rate for ${member.displayName.ifBlank { member.email.substringBefore("@") }}. Used when assigning team shifts.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rateStr,
                        onValueChange = { rateStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Hourly rate ($)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSetRate(rateStr.toDoubleOrNull() ?: 0.0); showRateDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRateDialog = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun TeamShiftCard(
    shift: TeamShift,
    members: List<TeamMember>,
    isManager: Boolean,
    currentUserId: String,
    onDelete: () -> Unit,
    onToggleTask: (String) -> Unit = {},
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onRequestSwap: (() -> Unit)? = null
) {
    val assignedMember = members.find { it.userId == shift.assignedTo }
    val assignedName = assignedMember?.displayName?.ifBlank { assignedMember.email.substringBefore("@") } ?: "Unknown"
    val timeFormat = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.US) }

    val statusColor = when (shift.status) {
        "accepted" -> PrimaryGreen
        "declined" -> Color(0xFFEF4444)
        else -> AccentOrange
    }

    val isAssignedToMe = shift.assignedTo == currentUserId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(shift.company, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        shift.status.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            if (shift.role.isNotBlank()) {
                Text("Role: ${shift.role}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Assigned to: $assignedName",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${timeFormat.format(Date(shift.startTime))} - ${timeFormat.format(Date(shift.endTime))}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isManager && shift.hourlyRate > 0) {
                Text(
                    "$${String.format("%.2f", shift.hourlyRate)}/hr · ${String.format("%.1f", shift.durationHours)} hrs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen
                )
            }
            if (shift.notes.isNotBlank()) {
                Text(shift.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 2)
            }

            if (shift.tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val completedCount = shift.tasks.count { it.isCompleted }
                Text(
                    "$completedCount/${shift.tasks.size} tasks done",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completedCount == shift.tasks.size) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                shift.tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isAssignedToMe || isManager) { onToggleTask(task.id) }
                            .padding(vertical = 2.dp),
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

            if (isAssignedToMe && shift.status == "assigned") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onAccept != null) {
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onDecline != null) {
                        OutlinedButton(
                            onClick = onDecline,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Decline", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (!isAssignedToMe && shift.assignedTo != currentUserId && onRequestSwap != null && shift.status == "accepted") {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onRequestSwap,
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Request Swap", fontSize = 12.sp)
                }
            }

            if (isManager) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Shift", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CreateTeamDialog(onDismiss: () -> Unit, onCreate: (TeamFormData) -> Unit) {
    var form by remember { mutableStateOf(TeamFormData()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Team", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                TeamFormFields(form = form, onChange = { form = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(form) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = form.name.trim().isNotBlank() && form.companyName.trim().isNotBlank()
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamFormFields(form: TeamFormData, onChange: (TeamFormData) -> Unit) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var dayMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { if (it.length <= 50) onChange(form.copy(name = it)) },
            label = { Text("Team name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = form.companyName,
            onValueChange = { if (it.length <= 100) onChange(form.copy(companyName = it)) },
            label = { Text("Company name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Weekly cycle start day
        ExposedDropdownMenuBox(expanded = dayMenuExpanded, onExpandedChange = { dayMenuExpanded = it }) {
            OutlinedTextField(
                value = form.weeklyCycleStartDay,
                onValueChange = {},
                readOnly = true,
                label = { Text("Weekly cycle starts on") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                days.forEach { day ->
                    DropdownMenuItem(text = { Text(day) }, onClick = { onChange(form.copy(weeklyCycleStartDay = day)); dayMenuExpanded = false })
                }
            }
        }

        // Working hours
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Open 24 hours", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = form.open24Hours, onCheckedChange = { onChange(form.copy(open24Hours = it)) })
        }
        if (!form.open24Hours) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamTimeField(
                    label = "Opens",
                    minutes = form.workStartMinutes,
                    context = context,
                    onPick = { onChange(form.copy(workStartMinutes = it)) },
                    modifier = Modifier.weight(1f)
                )
                TeamTimeField(
                    label = "Closes",
                    minutes = form.workEndMinutes,
                    context = context,
                    onPick = { onChange(form.copy(workEndMinutes = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("Location", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = form.addressLine,
            onValueChange = { if (it.length <= 200) onChange(form.copy(addressLine = it)) },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            maxLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.city,
                onValueChange = { if (it.length <= 100) onChange(form.copy(city = it)) },
                label = { Text("City") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = form.region,
                onValueChange = { if (it.length <= 100) onChange(form.copy(region = it)) },
                label = { Text("State/Region") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }
        OutlinedTextField(
            value = form.postalCode,
            onValueChange = { if (it.length <= 20) onChange(form.copy(postalCode = it)) },
            label = { Text("Postal code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun TeamTimeField(
    label: String,
    minutes: Int,
    context: android.content.Context,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val display = remember(minutes) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, (minutes / 60) % 24); set(java.util.Calendar.MINUTE, minutes % 60)
        }
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(cal.time)
    }
    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        modifier = modifier.clickable {
            android.app.TimePickerDialog(
                context,
                { _, h, m -> onPick(h * 60 + m) },
                (minutes / 60) % 24, minutes % 60, false
            ).show()
        },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun EditTeamDialog(
    team: com.schedulo.shared.model.Team,
    onDismiss: () -> Unit,
    onSave: (TeamFormData) -> Unit
) {
    var form by remember { mutableStateOf(TeamFormData.from(team)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Team", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                TeamFormFields(form = form, onChange = { form = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(form) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = form.name.trim().isNotBlank() && form.companyName.trim().isNotBlank()
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun JoinTeamDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var inviteCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Store Team", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter the 6-character invite code from your team manager.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { if (it.length <= 6) inviteCode = it.uppercase() },
                    label = { Text("Invite Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(inviteCode.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = inviteCode.trim().length == 6
            ) { Text("Join", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignShiftDialog(
    onDismiss: () -> Unit,
    onAssign: (memberId: String, company: String, role: String, startTime: Long, endTime: Long, hourlyRate: Double, notes: String, tasks: List<ShiftTask>) -> Unit,
    members: List<TeamMember>,
    companyName: String,
    defaultStartMinutes: Int = 9 * 60,
    defaultEndMinutes: Int = 17 * 60
) {
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("") }
    var hourlyRateStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf(listOf<ShiftTask>()) }
    var newTaskTitle by remember { mutableStateOf("") }

    // Prefill the rate from the selected member's default rate.
    LaunchedEffect(selectedMember) {
        selectedMember?.let { m ->
            hourlyRateStr = if (m.defaultHourlyRate > 0) String.format("%.2f", m.defaultHourlyRate) else hourlyRateStr
        }
    }

    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var startHour by remember { mutableStateOf(defaultStartMinutes / 60) }
    var startMinute by remember { mutableStateOf(defaultStartMinutes % 60) }
    var endHour by remember { mutableStateOf(defaultEndMinutes / 60) }
    var endMinute by remember { mutableStateOf(defaultEndMinutes % 60) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }

    val initialUtcMillis = remember {
        val localCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(localCal.get(Calendar.YEAR), localCal.get(Calendar.MONTH), localCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }
        utcCal.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
    val startTimePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)
    val endTimePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMs ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = utcMs
                        val localCal = Calendar.getInstance()
                        localCal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                        selectedDateMillis = localCal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startHour = startTimePickerState.hour
                    startMinute = startTimePickerState.minute
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            text = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = startTimePickerState) } }
        )
    }

    if (showEndTimePicker) {
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endHour = endTimePickerState.hour
                    endMinute = endTimePickerState.minute
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            text = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = endTimePickerState) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Shift", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Member picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMember?.let { it.displayName.ifBlank { it.email.substringBefore("@") } } ?: "Select member...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign To") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) }
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { memberDropdownExpanded = true })
                    DropdownMenu(expanded = memberDropdownExpanded, onDismissRequest = { memberDropdownExpanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.displayName.ifBlank { member.email.substringBefore("@") }) },
                                onClick = {
                                    selectedMember = member
                                    memberDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Company comes from the team (no personal-jobs picker).
                OutlinedTextField(
                    value = companyName.ifBlank { "—" },
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Company") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Date
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedDateMillis)),
                    onValueChange = {},
                    label = { Text("Date") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Time row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = String.format(Locale.US, "%02d:%02d", startHour, startMinute),
                        onValueChange = {},
                        label = { Text("Start") },
                        enabled = false,
                        modifier = Modifier.weight(1f).clickable { showStartTimePicker = true },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = String.format(Locale.US, "%02d:%02d", endHour, endMinute),
                        onValueChange = {},
                        label = { Text("End") },
                        enabled = false,
                        modifier = Modifier.weight(1f).clickable { showEndTimePicker = true },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = hourlyRateStr,
                    onValueChange = { hourlyRateStr = it },
                    label = { Text("Hourly Rate (\$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 500) notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )

                Text("Tasks", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                tasks.forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(task.title, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { tasks = tasks.filter { it.id != task.id } },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("Add task...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (newTaskTitle.isNotBlank()) {
                                tasks = tasks + ShiftTask(
                                    id = UUID.randomUUID().toString(),
                                    title = newTaskTitle.trim(),
                                    isCompleted = false
                                )
                                newTaskTitle = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, "Add Task", tint = PrimaryGreen)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val member = selectedMember ?: return@Button
                    val calStart = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                    }
                    val calEnd = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, endHour)
                        set(Calendar.MINUTE, endMinute)
                        set(Calendar.SECOND, 0)
                    }
                    var finalEnd = calEnd.timeInMillis
                    if (finalEnd <= calStart.timeInMillis) finalEnd += 86400000L

                    onAssign(
                        member.userId,
                        companyName,
                        role,
                        calStart.timeInMillis,
                        finalEnd,
                        hourlyRateStr.toDoubleOrNull() ?: 0.0,
                        notes,
                        tasks
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = selectedMember != null
            ) { Text("Assign", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

data class TeamWeekDayEntry(val dayOffset: Int, val startH: Int, val startM: Int, val endH: Int, val endM: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamWeekPlanDialog(
    onDismiss: () -> Unit,
    onAssignShifts: (memberId: String, company: String, role: String, hourlyRate: Double, notes: String, tasks: List<ShiftTask>, weekStartMillis: Long, dayEntries: List<TeamWeekDayEntry>) -> Unit,
    members: List<TeamMember>,
    companyName: String,
    teamViewModel: TeamViewModel? = null,
    weeklyCycleStartDay: String = "Monday",
    defaultStartMinutes: Int = 9 * 60,
    defaultEndMinutes: Int = 17 * 60
) {
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("") }
    var hourlyRateStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var dayEnabled by remember { mutableStateOf(List(7) { it < 5 }) }
    var dayStartHours by remember { mutableStateOf(List(7) { defaultStartMinutes / 60 }) }
    var dayStartMinutes by remember { mutableStateOf(List(7) { defaultStartMinutes % 60 }) }
    var dayEndHours by remember { mutableStateOf(List(7) { defaultEndMinutes / 60 }) }
    var dayEndMinutes by remember { mutableStateOf(List(7) { defaultEndMinutes % 60 }) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var showTimePickerForDay by remember { mutableIntStateOf(-1) }
    var isStartTimePicker by remember { mutableStateOf(true) }

    // Prefill the rate from the selected member's default rate.
    LaunchedEffect(selectedMember) {
        selectedMember?.let { m ->
            if (m.defaultHourlyRate > 0) hourlyRateStr = String.format("%.2f", m.defaultHourlyRate)
        }
    }

    val allDays = remember { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") }
    val daysOfWeek = remember(weeklyCycleStartDay) {
        val idx = allDays.indexOf(weeklyCycleStartDay).coerceAtLeast(0)
        allDays.subList(idx, allDays.size) + allDays.subList(0, idx)
    }
    val dayFormat = remember { SimpleDateFormat("M/dd", Locale.US) }

    val calendarDayOfWeek = remember(weeklyCycleStartDay) {
        when (weeklyCycleStartDay) {
            "Sunday" -> Calendar.SUNDAY; "Monday" -> Calendar.MONDAY; "Tuesday" -> Calendar.TUESDAY
            "Wednesday" -> Calendar.WEDNESDAY; "Thursday" -> Calendar.THURSDAY
            "Friday" -> Calendar.FRIDAY; "Saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }

    val weekStartMillis = remember(weekOffset, calendarDayOfWeek) {
        Calendar.getInstance().apply {
            firstDayOfWeek = calendarDayOfWeek
            set(Calendar.DAY_OF_WEEK, calendarDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }.timeInMillis
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Plan Team Week", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                val member = selectedMember ?: return@Button
                                val entries = (0..6).filter { dayEnabled[it] }
                                    .map { TeamWeekDayEntry(it, dayStartHours[it], dayStartMinutes[it], dayEndHours[it], dayEndMinutes[it]) }
                                onAssignShifts(
                                    member.userId,
                                    companyName,
                                    role,
                                    hourlyRateStr.toDoubleOrNull() ?: 0.0,
                                    notes,
                                    emptyList(),
                                    weekStartMillis,
                                    entries
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            enabled = selectedMember != null && dayEnabled.any { it },
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text("Assign", fontWeight = FontWeight.Bold) }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text("Assign To", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMember?.let { it.displayName.ifBlank { it.email.substringBefore("@") } } ?: "Select member...",
                        onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) }
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { memberDropdownExpanded = true })
                    DropdownMenu(expanded = memberDropdownExpanded, onDismissRequest = { memberDropdownExpanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.displayName.ifBlank { member.email.substringBefore("@") }) },
                                onClick = { selectedMember = member; memberDropdownExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Company", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = companyName.ifBlank { "—" },
                    onValueChange = {}, readOnly = true, enabled = false,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = role, onValueChange = { role = it },
                        label = { Text("Role (optional)") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = hourlyRateStr, onValueChange = { hourlyRateStr = it },
                        label = { Text("Pay Rate ($/hr)") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                if (selectedMember != null && (selectedMember!!.defaultHourlyRate <= 0.0)) {
                    Text(
                        "No saved pay rate for this member — enter one above.",
                        fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                weekOffset == 0 -> "This Week"
                                weekOffset == 1 -> "Next Week"
                                weekOffset == -1 -> "Last Week"
                                weekOffset < -1 -> "${-weekOffset} weeks ago"
                                else -> "In $weekOffset weeks"
                            },
                            fontSize = 12.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { if (weekOffset < 12) weekOffset++ }, enabled = weekOffset < 12) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week",
                            tint = if (weekOffset < 12) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                daysOfWeek.forEachIndexed { index, dayName ->
                    val dayMillis = weekStartMillis + index.toLong() * 24 * 60 * 60 * 1000L
                    val dateStr = dayFormat.format(Date(dayMillis))
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, if (enabled) PrimaryGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { newVal ->
                                        dayEnabled = List(7) { i -> if (i == index) newVal else dayEnabled[i] }
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = PrimaryGreen),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("$dayName ($dateStr)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            if (enabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { isStartTimePicker = true; showTimePickerForDay = index },
                                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.5f))
                                    ) { Text("Start: $startTimeStr", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = { isStartTimePicker = false; showTimePickerForDay = index },
                                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.5f))
                                    ) { Text("End: $endTimeStr", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val totalDays = dayEnabled.count { it }
                val totalHours = (0..6).filter { dayEnabled[it] }.sumOf { i ->
                    val startMin = dayStartHours[i] * 60 + dayStartMinutes[i]
                    var endMin = dayEndHours[i] * 60 + dayEndMinutes[i]
                    if (endMin <= startMin) endMin += 24 * 60
                    (endMin - startMin).toDouble() / 60.0
                }
                val rate = hourlyRateStr.toDoubleOrNull() ?: 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("$totalDays days · ${String.format("%.1f", totalHours)} hours", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (rate > 0) {
                            Text("Estimated pay: $${String.format("%.2f", totalHours * rate)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PrimaryGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    minLines = 2, maxLines = 4
                )

                Spacer(modifier = Modifier.height(24.dp))
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
    }
}

@Composable
fun ManagerDashboardSection(
    teamShifts: List<TeamShift>,
    members: List<TeamMember>,
    jobs: List<Job>
) {
    val acceptedShifts = teamShifts.filter { it.status == "accepted" }
    var expandedMemberId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Manager Dashboard",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Employee hours & pay overview",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        val memberShifts = acceptedShifts.groupBy { it.assignedTo }

        members.forEach { member ->
            val shifts = memberShifts[member.userId] ?: emptyList()
            val totalHours = shifts.sumOf { it.durationHours }
            val totalEarnings = shifts.sumOf { it.hourlyRate * it.durationHours }
            val isExpanded = expandedMemberId == member.userId
            val memberName = member.displayName.ifBlank { member.email.substringBefore("@") }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { expandedMemberId = if (isExpanded) null else member.userId },
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(memberName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${member.role.replaceFirstChar { it.uppercase() }} · ${shifts.size} shifts",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${String.format("%.1f", totalHours)} hrs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                            Text("$${String.format("%.2f", totalEarnings)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (isExpanded && shifts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        val byCompany = shifts.groupBy { it.company }
                        byCompany.forEach { (company, companyShifts) ->
                            val companyHours = companyShifts.sumOf { it.durationHours }
                            val companyPay = companyShifts.sumOf { it.hourlyRate * it.durationHours }
                            val now = System.currentTimeMillis()
                            val latestEnd = companyShifts.maxOf { it.endTime }
                            val payDue = latestEnd + 4L * 24 * 60 * 60 * 1000L < now

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(company, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        if (payDue) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentOrange.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "PAY DUE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentOrange,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text("${companyShifts.size} shifts · ${String.format("%.1f", companyHours)} hrs",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("$${String.format("%.2f", companyPay)}",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}
