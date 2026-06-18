import SwiftUI

struct TeamView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @State private var showCreateTeam = false
    @State private var showJoinTeam = false
    @State private var showAssignShift = false

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
                            membersSection
                            shiftsSection
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Team")
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

            Text("No Teams Yet")
                .font(.system(size: 20, weight: .bold))

            Text("Create a team to manage shifts for your crew, or join an existing team with an invite code.")
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
                    Text(team.name)
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
                    Button(action: { showAssignShift = true }) {
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

            if shift.assignedTo == teamViewModel.currentUserId && shift.status == "assigned" {
                HStack(spacing: 8) {
                    Button("Accept") {
                        teamViewModel.updateShiftStatus(shiftId: shift.id, status: "accepted")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.primaryGreen))

                    Button("Decline") {
                        teamViewModel.updateShiftStatus(shiftId: shift.id, status: "declined")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.red)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 8).stroke(Color.red, lineWidth: 1))
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
