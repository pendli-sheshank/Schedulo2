import SwiftUI

struct TeamSwapRequestsView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showNewSwap = false
    @State private var showHelp = false

    private var activeRequests: [SwapRequestInfo] {
        teamViewModel.swapRequests.filter { $0.status != "approved" && $0.status != "declined" }
    }

    private var myAcceptedShifts: [TeamShiftInfo] {
        teamViewModel.teamShifts.filter { $0.assignedTo == teamViewModel.currentUserId && $0.status == "accepted" }
    }

    private var swappableTargets: [TeamShiftInfo] {
        teamViewModel.teamShifts.filter { $0.assignedTo != teamViewModel.currentUserId && $0.status == "accepted" }
    }

    private var canRequestSwap: Bool {
        !myAcceptedShifts.isEmpty && !swappableTargets.isEmpty
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    Button(action: { showNewSwap = true }) {
                        HStack {
                            Image(systemName: "arrow.left.arrow.right")
                            Text("Request a Swap").fontWeight(.bold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(canRequestSwap ? Color.primaryGreen : Color.gray.opacity(0.4))
                        )
                        .foregroundColor(.white)
                    }
                    .disabled(!canRequestSwap)

                    if !canRequestSwap {
                        Text("You need an accepted shift, and a teammate must have one too, before you can request a swap.")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    if activeRequests.isEmpty {
                        Text("No pending swap requests")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                            .padding(.vertical, 24)
                    } else {
                        ForEach(activeRequests) { (request: SwapRequestInfo) in
                            SwapCardView(request: request)
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Shift Swaps")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showHelp = true }) {
                        Image(systemName: "questionmark.circle")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showNewSwap) {
                NewSwapRequestView(myShifts: myAcceptedShifts, targetShifts: swappableTargets)
                    .environmentObject(teamViewModel)
            }
            .alert("Shift Swaps", isPresented: $showHelp) {
                Button("Got it", role: .cancel) {}
            } message: {
                Text("Request to trade one of your shifts for a teammate's. Tap \"Request a Swap\" to pick the shift you want and the shift you'll give up. The teammate accepts, then a manager approves the trade.")
            }
        }
    }
}

struct NewSwapRequestView: View {
    let myShifts: [TeamShiftInfo]
    let targetShifts: [TeamShiftInfo]
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedTargetId: String = ""
    @State private var selectedMineId: String = ""

    private static let timeFormat: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()

    private func memberName(_ userId: String) -> String {
        let m = teamViewModel.members.first { $0.userId == userId }
        return m.map { $0.displayName.isEmpty ? $0.email : $0.displayName } ?? "Unknown"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("1. Pick the shift you want") {
                    Picker("Their shift", selection: $selectedTargetId) {
                        ForEach(targetShifts) { shift in
                            Text("\(memberName(shift.assignedTo)) · \(shift.company) · \(Self.timeFormat.string(from: shift.startDate))")
                                .tag(shift.id)
                        }
                    }
                    .pickerStyle(.inline)
                }
                Section("2. Pick your shift to offer") {
                    Picker("Your shift", selection: $selectedMineId) {
                        ForEach(myShifts) { shift in
                            Text("\(shift.company) · \(Self.timeFormat.string(from: shift.startDate))")
                                .tag(shift.id)
                        }
                    }
                    .pickerStyle(.inline)
                }
            }
            .navigationTitle("Request a Swap")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") {
                        guard let target = targetShifts.first(where: { $0.id == selectedTargetId }) else { return }
                        teamViewModel.requestSwap(myShiftId: selectedMineId, targetMemberId: target.assignedTo, targetShiftId: target.id)
                        dismiss()
                    }
                    .disabled(selectedTargetId.isEmpty || selectedMineId.isEmpty)
                }
            }
            .onAppear {
                if selectedTargetId.isEmpty { selectedTargetId = targetShifts.first?.id ?? "" }
                if selectedMineId.isEmpty { selectedMineId = myShifts.first?.id ?? "" }
            }
        }
    }
}

private extension DateFormatter {
    static let swapTime: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()
}

private struct SwapCardView: View {
    let request: SwapRequestInfo
    @EnvironmentObject var teamViewModel: TeamViewModel

    private var statusLabel: String {
        switch request.status {
        case "pending": return "Pending"
        case "target_accepted": return "Awaiting Manager"
        default: return request.status.capitalized
        }
    }

    private var statusColor: Color {
        switch request.status {
        case "pending": return .accentOrange
        case "target_accepted": return .accentBlue
        default: return .secondary
        }
    }

    var body: some View {
        let requesterShift: TeamShiftInfo? = teamViewModel.teamShifts.first { $0.id == request.requesterShiftId }
        let targetShift: TeamShiftInfo? = teamViewModel.teamShifts.first { $0.id == request.targetShiftId }

        VStack(alignment: .leading, spacing: 8) {
            headerRow
            requesterSection(requesterShift)
            Image(systemName: "arrow.up.arrow.down")
                .font(.system(size: 14))
                .foregroundColor(.accentBlue)
            targetSection(targetShift)
            if request.status == "pending" && request.targetMemberId == teamViewModel.currentUserId {
                targetActionButtons
            }
            if request.status == "target_accepted" && teamViewModel.isManager {
                managerActionButtons
            }
            if request.status == "pending" && request.requesterId == teamViewModel.currentUserId {
                cancelButton
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }

    private var headerRow: some View {
        HStack {
            Text("Shift Swap")
                .font(.system(size: 15, weight: .bold))
            Spacer()
            Text(statusLabel)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(statusColor)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(statusColor.opacity(0.12))
                )
        }
    }

    private func requesterSection(_ shift: TeamShiftInfo?) -> some View {
        let name: String = request.requesterName.isEmpty ? "Unknown" : request.requesterName
        return VStack(alignment: .leading, spacing: 2) {
            Text("\(name) offers:")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
            if let shift = shift {
                let timeStr: String = DateFormatter.swapTime.string(from: shift.startDate)
                Text("\(shift.company) · \(timeStr)")
                    .font(.system(size: 13, weight: .semibold))
            }
        }
    }

    private func targetSection(_ shift: TeamShiftInfo?) -> some View {
        let name: String = request.targetMemberName.isEmpty ? "Unknown" : request.targetMemberName
        return VStack(alignment: .leading, spacing: 2) {
            Text("\(name) offers:")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
            if let shift = shift {
                let timeStr: String = DateFormatter.swapTime.string(from: shift.startDate)
                Text("\(shift.company) · \(timeStr)")
                    .font(.system(size: 13, weight: .semibold))
            }
        }
    }

    private var targetActionButtons: some View {
        HStack(spacing: 8) {
            Button(action: { teamViewModel.respondToSwap(requestId: request.id, accept: true) }) {
                Text("Accept")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.primaryGreen))
            }
            .buttonStyle(.plain)

            Button(action: { teamViewModel.respondToSwap(requestId: request.id, accept: false) }) {
                Text("Decline")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.red)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.red, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    private var managerActionButtons: some View {
        HStack(spacing: 8) {
            Button(action: { teamViewModel.approveSwap(requestId: request.id, approve: true) }) {
                Text("Approve Swap")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.primaryGreen))
            }
            .buttonStyle(.plain)

            Button(action: { teamViewModel.approveSwap(requestId: request.id, approve: false) }) {
                Text("Decline")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.red)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.red, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    private var cancelButton: some View {
        Button(action: { teamViewModel.cancelSwapRequest(requestId: request.id) }) {
            Text("Cancel Request")
                .font(.system(size: 12))
                .foregroundColor(.red)
        }
        .buttonStyle(.plain)
    }
}
