import SwiftUI

struct TeamScheduleDetailView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showAssignShift = false
    @State private var showWeekPlan = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    shiftsSection
                }
                .padding(16)
            }
            .navigationTitle("Team Schedule")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
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
