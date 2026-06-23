import SwiftUI

private extension DateFormatter {
    static let teamTime: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "hh:mm a"
        return f
    }()
    static let teamDay: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "EEE, MMM dd"
        return f
    }()
    static let swapPickerTime: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()
}

struct TeamScheduleDetailView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showAssignShift = false
    @State private var showWeekPlan = false
    @State private var showSwapPicker = false
    @State private var swapSourceShift: TeamShiftInfo?

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
            .sheet(isPresented: $showSwapPicker) {
                SwapPickerView(sourceShift: swapSourceShift)
                    .environmentObject(teamViewModel)
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

            let myShifts: [TeamShiftInfo] = teamViewModel.teamShifts.filter { shift in
                teamViewModel.isManager || shift.assignedTo == teamViewModel.currentUserId
            }

            if myShifts.isEmpty {
                Text("No shifts assigned yet")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 20)
            } else {
                ForEach(myShifts) { (shift: TeamShiftInfo) in
                    TeamShiftCardView(
                        shift: shift,
                        onRequestSwap: {
                            swapSourceShift = shift
                            showSwapPicker = true
                        }
                    )
                }
            }
        }
    }
}

private struct TeamShiftCardView: View {
    let shift: TeamShiftInfo
    var onRequestSwap: (() -> Void)?
    @EnvironmentObject var teamViewModel: TeamViewModel

    var body: some View {
        let assignee: TeamMemberInfo? = teamViewModel.members.first { $0.userId == shift.assignedTo }
        let isMyShift: Bool = shift.assignedTo == teamViewModel.currentUserId

        VStack(alignment: .leading, spacing: 6) {
            headerRow
            dateRow
            if let assignee = assignee {
                let name: String = assignee.displayName.isEmpty ? assignee.email : assignee.displayName
                Text("Assigned to: \(name)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            if !shift.tasks.isEmpty {
                tasksSection
            }
            if isMyShift && shift.status == "assigned" {
                acceptDeclineButtons
            }
            if isMyShift && (shift.status == "accepted" || shift.status == "assigned") {
                swapButton
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }

    private var headerRow: some View {
        HStack {
            Text(shift.company)
                .font(.system(size: 14, weight: .semibold))
            Spacer()
            StatusBadgeView(status: shift.status)
        }
    }

    private var dateRow: some View {
        let dayStr: String = DateFormatter.teamDay.string(from: shift.startDate)
        let startStr: String = DateFormatter.teamTime.string(from: shift.startDate)
        let endStr: String = DateFormatter.teamTime.string(from: shift.endDate)
        return Text("\(dayStr) \(startStr) - \(endStr)")
            .font(.system(size: 12))
            .foregroundColor(.secondary)
    }

    private var tasksSection: some View {
        let completedCount: Int = shift.tasks.filter { $0.isCompleted }.count
        return VStack(alignment: .leading, spacing: 4) {
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

    private var acceptDeclineButtons: some View {
        HStack(spacing: 8) {
            Button(action: {
                teamViewModel.updateShiftStatus(shiftId: shift.id, newStatus: "accepted")
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .semibold))
                    Text("Accept")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.primaryGreen))
            }
            .buttonStyle(.plain)

            Button(action: {
                teamViewModel.updateShiftStatus(shiftId: shift.id, newStatus: "declined")
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                    Text("Decline")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(.red)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.red, lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 6)
    }

    private var swapButton: some View {
        Button(action: { onRequestSwap?() }) {
            HStack(spacing: 4) {
                Image(systemName: "arrow.triangle.swap")
                    .font(.system(size: 12))
                Text("Request Swap")
                    .font(.system(size: 12, weight: .semibold))
            }
            .foregroundColor(.accentOrange)
        }
        .buttonStyle(.plain)
        .padding(.top, 4)
    }
}

private struct StatusBadgeView: View {
    let status: String

    private var color: Color {
        switch status {
        case "accepted": return .green
        case "declined": return .red
        default: return .orange
        }
    }

    var body: some View {
        Text(status.capitalized)
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

struct SwapPickerView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    var sourceShift: TeamShiftInfo?

    private var otherMemberShifts: [TeamShiftInfo] {
        guard let source = sourceShift else { return [] }
        return teamViewModel.teamShifts.filter {
            $0.assignedTo != teamViewModel.currentUserId &&
            $0.id != source.id &&
            ($0.status == "accepted" || $0.status == "assigned")
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    if otherMemberShifts.isEmpty {
                        Text("No available shifts to swap with")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                            .padding(32)
                    } else {
                        Text("Select a shift to swap with:")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.bottom, 4)

                        ForEach(otherMemberShifts) { (shift: TeamShiftInfo) in
                            swapOptionRow(shift)
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Pick a Shift to Swap")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func swapOptionRow(_ shift: TeamShiftInfo) -> some View {
        let member: TeamMemberInfo? = teamViewModel.members.first { $0.userId == shift.assignedTo }
        let displayName: String = member?.displayName ?? shift.assignedTo
        let timeStr: String = DateFormatter.swapPickerTime.string(from: shift.startDate)

        return Button(action: {
            guard let source = sourceShift else { return }
            teamViewModel.requestSwap(
                requesterShiftId: source.id,
                targetMemberId: shift.assignedTo,
                targetShiftId: shift.id
            )
            dismiss()
        }) {
            VStack(alignment: .leading, spacing: 4) {
                Text(displayName)
                    .font(.system(size: 14, weight: .semibold))
                Text("\(shift.company) · \(timeStr)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(UIColor.secondarySystemBackground))
            )
        }
        .buttonStyle(.plain)
    }
}
