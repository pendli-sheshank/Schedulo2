import SwiftUI

struct TeamTasksDetailView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss

    private var shiftsWithTasks: [TeamShiftInfo] {
        teamViewModel.teamShifts.filter { !$0.tasks.isEmpty }
    }

    private var allTasks: [ShiftTaskInfo] {
        shiftsWithTasks.flatMap { $0.tasks }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    summaryCard
                    if shiftsWithTasks.isEmpty {
                        Text("No tasks yet")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 32)
                    } else {
                        ForEach(shiftsWithTasks) { shift in
                            taskShiftCard(shift)
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Team Tasks")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var summaryCard: some View {
        let completed = allTasks.filter { $0.isCompleted }.count
        return VStack(alignment: .leading, spacing: 8) {
            Text("\(completed)/\(allTasks.count) tasks completed")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.accentOrange)
            if !allTasks.isEmpty {
                ProgressView(value: Double(completed), total: Double(allTasks.count))
                    .tint(.accentOrange)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.accentOrange.opacity(0.1))
        )
    }

    private func taskShiftCard(_ shift: TeamShiftInfo) -> some View {
        let assignee = teamViewModel.members.first { $0.userId == shift.assignedTo }
        let assignedName = assignee.map { $0.displayName.isEmpty ? $0.email : $0.displayName } ?? "Unknown"
        let isAssignedToMe = shift.assignedTo == teamViewModel.currentUserId
        let completed = shift.tasks.filter { $0.isCompleted }.count

        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(shift.company)
                        .font(.system(size: 15, weight: .bold))
                    Text("Assigned to: \(assignedName)")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                Spacer()
                Text("\(completed)/\(shift.tasks.count)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(completed == shift.tasks.count ? .primaryGreen : .secondary)
            }

            ForEach(shift.tasks) { task in
                Button(action: {
                    if isAssignedToMe || teamViewModel.isManager {
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
                        Spacer()
                    }
                }
                .buttonStyle(.plain)
                .disabled(!(isAssignedToMe || teamViewModel.isManager))
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }
}
