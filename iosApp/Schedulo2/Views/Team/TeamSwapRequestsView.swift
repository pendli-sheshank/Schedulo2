import SwiftUI

struct TeamSwapRequestsView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss

    private var activeRequests: [SwapRequestInfo] {
        teamViewModel.swapRequests.filter { $0.status != "approved" && $0.status != "declined" }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    if activeRequests.isEmpty {
                        Text("No pending swap requests")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                            .padding(32)
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
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
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
