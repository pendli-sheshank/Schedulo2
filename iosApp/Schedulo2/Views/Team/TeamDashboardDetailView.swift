import SwiftUI

struct TeamDashboardDetailView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let team = teamViewModel.currentTeam {
                        inviteCodeCard(team)
                    }
                    if teamViewModel.isManager && !teamViewModel.teamShifts.isEmpty {
                        managerDashboardSection
                    }
                    membersSection
                }
                .padding(16)
            }
            .navigationTitle("Team Dashboard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func inviteCodeCard(_ team: TeamInfo) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Invite Code")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.secondary)
            Text(team.inviteCode)
                .font(.system(size: 20, weight: .bold, design: .monospaced))
                .foregroundColor(.primaryGreen)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.primaryGreen.opacity(0.08))
        )
    }

    private var isOwner: Bool {
        teamViewModel.currentTeam?.ownerId == teamViewModel.currentUserId
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
                    if isOwner && member.userId != teamViewModel.currentUserId {
                        Menu {
                            if member.role == "member" {
                                Button(action: { teamViewModel.promoteMember(memberDocId: member.id) }) {
                                    Label("Promote to Manager", systemImage: "star.fill")
                                }
                            }
                            if member.role == "manager" {
                                Button(action: { teamViewModel.demoteMember(memberDocId: member.id) }) {
                                    Label("Demote to Member", systemImage: "person.fill")
                                }
                            }
                            Button(role: .destructive, action: {
                                teamViewModel.removeMember(memberDocId: member.id, teamId: teamViewModel.currentTeam?.id ?? "")
                            }) {
                                Label("Remove from Team", systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .foregroundColor(.secondary)
                                .font(.system(size: 18))
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

    private var managerDashboardSection: some View {
        let acceptedShifts: [TeamShiftInfo] = teamViewModel.teamShifts.filter { $0.status == "accepted" }
        let memberShifts: [String: [TeamShiftInfo]] = Dictionary(grouping: acceptedShifts) { $0.assignedTo }

        return VStack(alignment: .leading, spacing: 8) {
            Text("Manager Dashboard")
                .font(.system(size: 16, weight: .bold))
            Text("Employee hours & pay overview")
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            ForEach(teamViewModel.members) { (member: TeamMemberInfo) in
                ManagerMemberCardView(member: member, shifts: memberShifts[member.userId] ?? [])
            }
        }
    }
}

private struct ManagerMemberCardView: View {
    let member: TeamMemberInfo
    let shifts: [TeamShiftInfo]

    var body: some View {
        let totalHours: Double = shifts.reduce(0.0) { $0 + $1.durationHours }
        let totalEarnings: Double = shifts.reduce(0.0) { $0 + $1.hourlyRate * $1.durationHours }
        let memberName: String = member.displayName.isEmpty ? member.email : member.displayName

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
                let byCompany: [String: [TeamShiftInfo]] = Dictionary(grouping: shifts) { $0.company }
                ForEach(Array(byCompany.keys.sorted()), id: \.self) { (company: String) in
                    companyRow(company: company, companyShifts: byCompany[company] ?? [])
                }
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }

    private func companyRow(company: String, companyShifts: [TeamShiftInfo]) -> some View {
        let companyHours: Double = companyShifts.reduce(0.0) { $0 + $1.durationHours }
        let companyPay: Double = companyShifts.reduce(0.0) { $0 + $1.hourlyRate * $1.durationHours }
        let latestEnd: Int64 = companyShifts.map { $0.endTime }.max() ?? 0
        let now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
        let fourDaysMs: Int64 = 4 * 24 * 3600 * 1000
        let payDue: Bool = latestEnd + fourDaysMs < now

        return HStack {
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
