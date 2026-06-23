import SwiftUI

struct TeamView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @State private var showCreateTeam = false
    @State private var showJoinTeam = false
    @State private var showLeaveConfirm = false
    @State private var showEditTeam = false
    @State private var showDeleteConfirm = false
    @State private var editTeamName = ""
    @State private var showDashboardDetail = false
    @State private var showScheduleDetail = false
    @State private var showTasksDetail = false
    @State private var showRosterDetail = false
    @State private var showChatDetail = false
    @State private var showSwapRequests = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let error = teamViewModel.errorMessage {
                        errorBanner(error)
                    }
                    if teamViewModel.teams.isEmpty {
                        emptyState
                    } else {
                        teamSelector
                        if let team = teamViewModel.currentTeam {
                            teamHeader(team)
                            bentoGrid(team)
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
            .sheet(isPresented: $showDashboardDetail) {
                TeamDashboardDetailView()
                    .environmentObject(teamViewModel)
            }
            .sheet(isPresented: $showScheduleDetail) {
                TeamScheduleDetailView()
                    .environmentObject(teamViewModel)
                    .environmentObject(dashboardViewModel)
            }
            .sheet(isPresented: $showTasksDetail) {
                TeamTasksDetailView()
                    .environmentObject(teamViewModel)
            }
            .sheet(isPresented: $showRosterDetail) {
                TeamRosterView()
                    .environmentObject(teamViewModel)
            }
            .sheet(isPresented: $showChatDetail) {
                TeamChatView()
                    .environmentObject(teamViewModel)
            }
            .sheet(isPresented: $showSwapRequests) {
                TeamSwapRequestsView()
                    .environmentObject(teamViewModel)
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

    private func errorBanner(_ message: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 14))
                .foregroundColor(.red)
            Text(message)
                .font(.system(size: 13))
                .foregroundColor(.red)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: { teamViewModel.errorMessage = nil }) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.red)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.red.opacity(0.12))
        )
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
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.primaryGreen)
                    .frame(width: 44, height: 44)
                Image(systemName: "person.3.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(team.name)
                    .font(.system(size: 18, weight: .bold))
                Text("\(teamViewModel.members.count) members")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            Spacer()
            HStack(spacing: 4) {
                Image(systemName: "doc.on.doc")
                    .font(.system(size: 11))
                Text(team.inviteCode)
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
            }
            .foregroundColor(.primaryGreen)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.primaryGreen.opacity(0.1))
            )
        }
    }

    private func bentoGrid(_ team: TeamInfo) -> some View {
        let allTasks = teamViewModel.teamShifts.flatMap { $0.tasks }
        let completedTasks = allTasks.filter { $0.isCompleted }.count
        let dashboardSubtitle = teamViewModel.isManager ? "Hours & pay overview" : "\(teamViewModel.members.count) members"

        return VStack(spacing: 12) {
            BentoTile(
                title: "Team Dashboard",
                subtitle: dashboardSubtitle,
                systemImage: "square.grid.2x2.fill",
                tint: .primaryGreen,
                action: { showDashboardDetail = true }
            )
            .frame(height: 110)

            HStack(spacing: 12) {
                BentoTile(
                    title: "Team Schedule",
                    subtitle: "\(teamViewModel.teamShifts.count) shifts",
                    systemImage: "calendar",
                    tint: .accentBlue,
                    action: { showScheduleDetail = true }
                )
                .frame(height: 130)

                BentoTile(
                    title: "Team Tasks",
                    subtitle: "\(completedTasks)/\(allTasks.count) done",
                    systemImage: "checklist",
                    tint: .accentOrange,
                    progress: allTasks.isEmpty ? nil : Double(completedTasks) / Double(allTasks.count),
                    action: { showTasksDetail = true }
                )
                .frame(height: 130)
            }

            HStack(spacing: 12) {
                BentoTile(
                    title: "Team Roster",
                    subtitle: "Weekly grid",
                    systemImage: "rectangle.split.3x3",
                    tint: .secondaryGreen,
                    action: { showRosterDetail = true }
                )
                .frame(height: 100)

                BentoTile(
                    title: "Team Chat",
                    subtitle: "Messages",
                    systemImage: "bubble.left.and.bubble.right.fill",
                    tint: .accentBlue,
                    action: { showChatDetail = true }
                )
                .frame(height: 100)
            }

            let pendingSwaps = teamViewModel.swapRequests.filter { $0.status != "approved" && $0.status != "declined" }.count
            BentoTile(
                title: "Shift Swaps",
                subtitle: pendingSwaps > 0 ? "\(pendingSwaps) pending" : "No requests",
                systemImage: "arrow.triangle.swap",
                tint: .accentOrange,
                action: { showSwapRequests = true }
            )
            .frame(height: 100)
        }
    }
}
