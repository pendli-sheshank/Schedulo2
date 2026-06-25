import SwiftUI

func teamTaskStatusColor(_ status: String) -> Color {
    switch status {
    case "completed": return .primaryGreen
    case "in_progress": return .accentBlue
    default: return .accentOrange
    }
}

func teamTaskStatusLabel(_ status: String) -> String {
    switch status {
    case "completed": return "Completed"
    case "in_progress": return "In Progress"
    default: return "Pending"
    }
}

struct TeamTasksDetailView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showCreateTask = false
    @State private var showHelp = false

    private var shiftsWithTasks: [TeamShiftInfo] {
        teamViewModel.teamShifts.filter { !$0.tasks.isEmpty }
    }

    // Members see their own assigned tasks; managers see everyone's.
    private var visibleTasks: [TeamTaskInfo] {
        teamViewModel.isManager
            ? teamViewModel.teamTasks
            : teamViewModel.teamTasks.filter { $0.assignedTo == teamViewModel.currentUserId }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    summaryCard

                    if visibleTasks.isEmpty {
                        Text(teamViewModel.isManager ? "No tasks assigned yet. Tap + to assign one to a member." : "You have no tasks yet.")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 24)
                    } else {
                        sectionLabel("Assigned Tasks")
                        ForEach(visibleTasks) { task in
                            TeamTaskCardView(
                                task: task,
                                canEdit: teamViewModel.isManager || task.assignedTo == teamViewModel.currentUserId,
                                canDelete: teamViewModel.isManager,
                                showAssignee: teamViewModel.isManager,
                                currentUserId: teamViewModel.currentUserId ?? ""
                            )
                        }
                    }

                    if !shiftsWithTasks.isEmpty {
                        sectionLabel("Shift Checklists")
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
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showHelp = true }) {
                        Image(systemName: "questionmark.circle")
                    }
                }
                if teamViewModel.isManager {
                    ToolbarItem(placement: .primaryAction) {
                        Button(action: { showCreateTask = true }) {
                            Image(systemName: "plus")
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showCreateTask) {
                CreateTeamTaskView(preselectedUserId: "")
                    .environmentObject(teamViewModel)
            }
            .alert("Team Tasks", isPresented: $showHelp) {
                Button("Got it", role: .cancel) {}
            } message: {
                Text("Assign individual to-dos to team members and track their progress. Tap + to create a task; the assignee (or a manager) moves it through Pending → In Progress → Completed, and every change is kept in the task's history. Shift checklists also appear here.")
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { teamViewModel.errorMessage != nil },
                set: { if !$0 { teamViewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { teamViewModel.errorMessage = nil }
            } message: {
                Text(teamViewModel.errorMessage ?? "")
            }
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(.secondary)
            .padding(.top, 6)
    }

    private var summaryCard: some View {
        let completed = visibleTasks.filter { $0.status == "completed" }.count
        return VStack(alignment: .leading, spacing: 8) {
            Text("\(completed)/\(visibleTasks.count) tasks completed")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.accentOrange)
            if !visibleTasks.isEmpty {
                ProgressView(value: Double(completed), total: Double(visibleTasks.count))
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

private struct TeamTaskCardView: View {
    let task: TeamTaskInfo
    let canEdit: Bool
    let canDelete: Bool
    let showAssignee: Bool
    let currentUserId: String
    @EnvironmentObject var teamViewModel: TeamViewModel
    @State private var showHistory = false

    private static let timeFormat: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title)
                        .font(.system(size: 15, weight: .bold))
                        .strikethrough(task.status == "completed")
                    if task.assignedTo == currentUserId {
                        Text("Assigned to you")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    } else if showAssignee {
                        Text("Assigned to: \(task.assignedToName.isEmpty ? "Unknown" : task.assignedToName)")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                Text(teamTaskStatusLabel(task.status))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(teamTaskStatusColor(task.status))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(teamTaskStatusColor(task.status).opacity(0.12))
                    )
            }

            if !task.taskDescription.isEmpty {
                Text(task.taskDescription)
                    .font(.system(size: 13))
            }

            if canEdit {
                Picker("Status", selection: Binding(
                    get: { task.status },
                    set: { teamViewModel.updateTeamTaskStatus(taskId: task.id, newStatus: $0) }
                )) {
                    Text("Pending").tag("pending")
                    Text("In Progress").tag("in_progress")
                    Text("Completed").tag("completed")
                }
                .pickerStyle(.segmented)
            }

            HStack {
                if !task.history.isEmpty {
                    Button(action: { withAnimation { showHistory.toggle() } }) {
                        HStack(spacing: 4) {
                            Image(systemName: showHistory ? "chevron.up" : "clock.arrow.circlepath")
                                .font(.system(size: 11))
                            Text("History (\(task.history.count))")
                                .font(.system(size: 12))
                        }
                        .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                if canDelete {
                    Button(action: { teamViewModel.deleteTeamTask(taskId: task.id) }) {
                        Image(systemName: "trash")
                            .font(.system(size: 13))
                            .foregroundColor(.red.opacity(0.7))
                    }
                    .buttonStyle(.plain)
                }
            }

            if showHistory {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(task.history) { entry in
                        HStack(spacing: 6) {
                            Circle()
                                .fill(teamTaskStatusColor(entry.status))
                                .frame(width: 6, height: 6)
                            Text("\(teamTaskStatusLabel(entry.status)) · \(entry.changedByName.isEmpty ? "Someone" : entry.changedByName) · \(Self.timeFormat.string(from: entry.date))")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }
}

struct CreateTeamTaskView: View {
    let preselectedUserId: String
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedUserId: String = ""
    @State private var title = ""
    @State private var details = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Assign to") {
                    Picker("Member", selection: $selectedUserId) {
                        ForEach(teamViewModel.members) { member in
                            Text(member.displayName.isEmpty ? member.email : member.displayName)
                                .tag(member.userId)
                        }
                    }
                }
                Section("Task") {
                    TextField("Task title", text: $title)
                    TextField("Details (optional)", text: $details, axis: .vertical)
                        .lineLimit(2...5)
                }
            }
            .navigationTitle("Assign a Task")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Assign") {
                        let member = teamViewModel.members.first { $0.userId == selectedUserId }
                        let name = member.map { $0.displayName.isEmpty ? $0.email : $0.displayName } ?? ""
                        teamViewModel.createTeamTask(memberId: selectedUserId, memberName: name, title: title, description: details)
                        dismiss()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || selectedUserId.isEmpty)
                }
            }
            .onAppear {
                if selectedUserId.isEmpty {
                    selectedUserId = preselectedUserId.isEmpty ? (teamViewModel.members.first?.userId ?? "") : preselectedUserId
                }
            }
        }
    }
}
