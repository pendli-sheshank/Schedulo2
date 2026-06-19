import SwiftUI

struct JoinTeamView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var inviteCode = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Invite Code")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.secondary)
                    TextField("Enter 6-character code", text: $inviteCode)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 18, design: .monospaced))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                }

                Text("Ask your team manager for the invite code to join their team.")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)

                if let error = teamViewModel.errorMessage {
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundColor(.red)
                }

                Button(action: {
                    teamViewModel.joinTeam(inviteCode: inviteCode.trimmingCharacters(in: .whitespacesAndNewlines))
                    dismiss()
                }) {
                    Text("Join Store Team")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(inviteCode.count < 6 ? Color.gray : Color.primaryGreen)
                        )
                }
                .disabled(inviteCode.count < 6)

                Spacer()
            }
            .padding(16)
            .navigationTitle("Join Store Team")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
