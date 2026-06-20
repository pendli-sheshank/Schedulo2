import SwiftUI

struct TeamView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @State private var showCreateTeam = false
    @State private var showJoinTeam = false
    @State private var showAssignShift = false
    @State private var showWeekPlan = false
    @State private var showLeaveConfirm = false
    @State private var showEditTeam = false
    @State private var showDeleteConfirm = false
    @State private var editTeamName = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if teamViewModel.teams.isEmpty {
                        emptyState
                    } else {
                        teamSelector
                        if let team = teamViewModel.currentTeam {
                            teamHeader(team)
                            if teamViewModel.isManager && !teamViewModel.teamShifts.isEmpty {
                                managerDashboardSection
                            }
                            membersSection
                            shiftsSection
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Store Team")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button(action: { showCreateTeam = true }) {
                            Label("Create Team", systemImage: "plus.circle")
                        }
                        Button(action: { showJoinTeam = true }) {
                            Label("Join Team", systemImage: "person.badge.plus")
                        }
                        if teamViewModel.currentTeam != nil {
                            Divider()
                            if teamViewModel.isManager {
                                Button(action: {
                                    editTeamName = teamViewModel.currentTeam?.name ?? ""
                                    showEditTeam = true
                                }) {
                                    Label("Edit Team Name", systemImage: "pencil")
                                }
                            }
                            Button(role: .destructive, action: { showLeaveConfirm = true }) {
                                Label("Leave Team", systemImage: "rectangle.portrait.and.arrow.right")
                            }
                            if teamViewModel.isManager {
                                Button(role: .destructive, action: { showDeleteConfirm = true }) {
                                    Label("Delete Team", systemImage: "trash")
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .foregroundColor(.primaryGreen)
                    }
                }
            }
            .sheet(isPresented: $showCreateTeam) {
                CreateTeamView()
                    .environmentObject(teamViewModel)
            }
            .sheet(isPresented: $showJoinTeam) {
                JoinTeamView()
                    .environmentObject(teamViewModel)
            }
            .sheet(isPresented: $showAssignShift) {
                AssignShiftView()
                    .environmentObject(teamViewModel)
                    .environmentObject(dashboardViewModel)
            }
            .sheet(isPresented: $showWeekPlan) {
                TeamWeekPlanView()
                    .environmentObject(teamViewModel)
                    .environmentObject(dashboardViewModel)
            }
            .alert("Leave Team", isPresented: $showLeaveConfirm) {
                Button("Leave", role: .destructive) {
                    if let team = teamViewModel.currentTeam {
                        teamViewModel.leaveTeam(teamId: team.id)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to leave \"\(teamViewModel.currentTeam?.name ?? "")\"? You will lose access to team shifts and data.")
            }
            .alert("Edit Team Name", isPresented: $showEditTeam) {
                TextField("Store Name", text: $editTeamName)
                Button("Save") {
                    let trimmed = editTeamName.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty, let team = teamViewModel.currentTeam {
                        teamViewModel.updateTeamName(teamId: team.id, newName: trimmed)
                    }
                }
                Button("Cancel", role: .cancel) {}
            }
            .alert("Delete Team", isPresented: $showDeleteConfirm) {
                Button("Delete", role: .destructive) {
                    if let team = teamViewModel.currentTeam {
                        teamViewModel.deleteTeam(teamId: team.id)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Are you sure you want to permanently delete \"\(teamViewModel.currentTeam?.name ?? "")\"? All team data, members, and shifts will be removed.")
            }
            .onAppear { teamViewModel.loadTeams() }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.3.fill")
                .font(.system(size: 48))
                .foregroundColor(.secondary.opacity(0.4))
                .padding(.top, 60)

            Text("No Store Teams Yet")
                .font(.system(size: 20, weight: .bold))

            Text("Create a store team to manage shifts for your crew, or join an existing team with an invite code.")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            HStack(spacing: 12) {
                Button(action: { showCreateTeam = true }) {
                    Text("Create Team")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.primaryGreen))
                }

                Button(action: { showJoinTeam = true }) {
                    Text("Join Team")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.primaryGreen, lineWidth: 1.5)
                        )
                }
            }
            .padding(.top, 8)
        }
    }

    private var teamSelector: some View {
        Group {
            if teamViewModel.teams.count > 1 {
                Picker("Team", selection: Binding(
                    get: { teamViewModel.currentTeam?.id ?? "" },
                    set: { id in
                        if let team = teamViewModel.teams.first(where: { $0.id == id }) {
                            teamViewModel.selectTeam(team)
                        }
                    }
                )) {
                    ForEach(teamViewModel.teams) { team in
                        Text(team.name).tag(team.id)
                    }
                }
                .pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func teamHeader(_ team: TeamInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Store: \(team.name)")
                        .font(.system(size: 18, weight: .bold))
                    Text("\(teamViewModel.members.count) members")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text("Invite Code")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                    Text(team.inviteCode)
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(.primaryGreen)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }

    private var membersSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Members")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.secondary)

            ForEach(teamViewModel.members) { member in
                HStack {
                    Image(systemName: member.role == "manager" ? "star.circle.fill" : "person.circle.fill")
                        .foregroundColor(member.role == "manager" ? .orange : .secondary)
                        .font(.system(size: 24))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(member.displayName.isEmpty ? member.email : member.displayName)
                            .font(.system(size: 14, weight: .medium))
                        Text(member.role.capitalized)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(UIColor.secondarySystemBackground))
                )
            }
        }
    }

    private var shiftsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Team Shifts")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.secondary)
                Spacer()
                if teamViewModel.isManager {
                    Menu {
                        Button(action: { showAssignShift = true }) {
                            Label("Assign Single Shift", systemImage: "person.badge.plus")
                        }
                        Button(action: { showWeekPlan = true }) {
                            Label("Plan Entire Week", systemImage: "calendar.badge.plus")
                        }
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.primaryGreen)
                    }
                }
            }

            let myShifts = teamViewModel.teamShifts.filter { shift in
                teamViewModel.isManager || shift.assignedTo == teamViewModel.currentUserId
            }

            if myShifts.isEmpty {
                Text("No shifts assigned yet")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 20)
            } else {
                ForEach(myShifts) { shift in
                    teamShiftCard(shift)
                }
            }
        }
    }

    private func teamShiftCard(_ shift: TeamShiftInfo) -> some View {
        let timeFmt = DateFormatter()
        timeFmt.dateFormat = "hh:mm a"
        let dayFmt = DateFormatter()
        dayFmt.dateFormat = "EEE, MMM dd"
        let assignee = teamViewModel.members.first { $0.userId == shift.assignedTo }

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(shift.company)
                    .font(.system(size: 14, weight: .semibold))
                Spacer()
                statusBadge(shift.status)
            }
            Text("\(dayFmt.string(from: shift.startDate)) \(timeFmt.string(from: shift.startDate)) - \(timeFmt.string(from: shift.endDate))")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
            if let assignee = assignee {
                Text("Assigned to: \(assignee.displayName.isEmpty ? assignee.email : assignee.displayName)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }

            if !shift.tasks.isEmpty {
                let completedCount = shift.tasks.filter { $0.isCompleted }.count
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(completedCount)/\(shift.tasks.count) tasks done")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(completedCount == shift.tasks.count ? .primaryGreen : .secondary)
                    ForEach(shift.tasks) { task in
                        Button(action: {
                            if shift.assignedTo == teamViewModel.currentUserId || teamViewModel.isManager {
                                teamViewModel.toggleTaskCompletion(shiftId: shift.id, taskId: task.id)
                            }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(task.isCompleted ? .primaryGreen : .secondary)
                                    .font(.system(size: 14))
                                Text(task.title)
                                    .font(.system(size: 13))
                                    .foregroundColor(task.isCompleted ? .secondary : .primary)
                                    .strikethrough(task.isCompleted)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 4)
            }

        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }

    private var managerDashboardSection: some View {
        let acceptedShifts = teamViewModel.teamShifts.filter { $0.status == "accepted" }
        let memberShifts = Dictionary(grouping: acceptedShifts) { $0.assignedTo }

        return VStack(alignment: .leading, spacing: 8) {
            Text("Manager Dashboard")
                .font(.system(size: 16, weight: .bold))
            Text("Employee hours & pay overview")
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            ForEach(teamViewModel.members) { member in
                let shifts = memberShifts[member.userId] ?? []
                let totalHours = shifts.reduce(0.0) { $0 + $1.durationHours }
                let totalEarnings = shifts.reduce(0.0) { $0 + $1.hourlyRate * $1.durationHours }
                let memberName = member.displayName.isEmpty ? member.email : member.displayName

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(memberName)
                                .font(.system(size: 14, weight: .semibold))
                            Text("\(member.role.capitalized) · \(shifts.count) shifts")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("\(String(format: "%.1f", totalHours)) hrs")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.primaryGreen)
                            Text("$\(String(format: "%.2f", totalEarnings))")
                                .font(.system(size: 12, weight: .semibold))
                        }
                    }

                    if !shifts.isEmpty {
                        let byCompany = Dictionary(grouping: shifts) { $0.company }
                        ForEach(Array(byCompany.keys.sorted()), id: \.self) { company in
                            let companyShifts = byCompany[company] ?? []
                            let companyHours = companyShifts.reduce(0.0) { $0 + $1.durationHours }
                            let companyPay = companyShifts.reduce(0.0) { $0 + $1.hourlyRate * $1.durationHours }
                            let latestEnd = companyShifts.map { $0.endTime }.max() ?? 0
                            let now = Int64(Date().timeIntervalSince1970 * 1000)
                            let payDue = latestEnd + 4 * 24 * 3600 * 1000 < now

                            HStack {
                                HStack(spacing: 4) {
                                    Text(company)
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                    if payDue {
                                        Text("PAY DUE")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundColor(.accentOrange)
                                            .padding(.horizontal, 4)
                                            .padding(.vertical, 1)
                                            .background(
                                                RoundedRectangle(cornerRadius: 3)
                                                    .fill(Color.accentOrange.opacity(0.15))
                                            )
                                    }
                                }
                                Spacer()
                                Text("\(companyShifts.count) shifts · \(String(format: "%.1f", companyHours)) hrs · $\(String(format: "%.2f", companyPay))")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(UIColor.secondarySystemBackground))
                )
            }
        }
    }

    private func statusBadge(_ status: String) -> some View {
        let color: Color = {
            switch status {
            case "accepted": return .green
            case "declined": return .red
            default: return .orange
            }
        }()

        return Text(status.capitalized)
            .font(.system(size: 11, weight: .semibold))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(color.opacity(0.15))
            )
    }
}
