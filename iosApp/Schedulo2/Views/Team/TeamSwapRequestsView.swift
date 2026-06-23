import SwiftUI

struct TeamSwapRequestsView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss

    private var activeRequests: [SwapRequestInfo] {
        teamViewModel.swapRequests.filter { $0.status != "approved" && $0.status != "declined" }
    }

    private let timeFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM dd, h:mm a"
        return f
    }()

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
                        ForEach(activeRequests) { request in
                            swapCard(request)
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

    private func swapCard(_ request: SwapRequestInfo) -> some View {
        let requesterShift = teamViewModel.teamShifts.first { $0.id == request.requesterShiftId }
        let targetShift = teamViewModel.teamShifts.first { $0.id == request.targetShiftId }
        let statusColor: Color = {
            switch request.status {
            case "pending": return .accentOrange
            case "target_accepted": return .accentBlue
            default: return .secondary
            }
        }()

        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Shift Swap")
                    .font(.system(size: 15, weight: .bold))
                Spacer()
                Text(request.status == "pending" ? "Pending" : request.status == "target_accepted" ? "Awaiting Manager" : request.status.capitalized)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(statusColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(statusColor.opacity(0.12))
                    )
            }

            Text("\(request.requesterName.isEmpty ? "Unknown" : request.requesterName) offers:")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
            if let shift = requesterShift {
                Text("\(shift.company) · \(timeFmt.string(from: shift.startDate))")
                    .font(.system(size: 13, weight: .semibold))
            }

            Image(systemName: "arrow.up.arrow.down")
                .font(.system(size: 14))
                .foregroundColor(.accentBlue)

            Text("\(request.targetMemberName.isEmpty ? "Unknown" : request.targetMemberName) offers:")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
            if let shift = targetShift {
                Text("\(shift.company) · \(timeFmt.string(from: shift.startDate))")
                    .font(.system(size: 13, weight: .semibold))
            }

            // Target member can accept/decline pending
            if request.status == "pending" && request.targetMemberId == teamViewModel.currentUserId {
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

            // Manager can approve/decline after target accepted
            if request.status == "target_accepted" && teamViewModel.isManager {
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

            // Requester can cancel pending
            if request.status == "pending" && request.requesterId == teamViewModel.currentUserId {
                Button(action: { teamViewModel.cancelSwapRequest(requestId: request.id) }) {
                    Text("Cancel Request")
                        .font(.system(size: 12))
                        .foregroundColor(.red)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(UIColor.secondarySystemBackground))
        )
    }
}
