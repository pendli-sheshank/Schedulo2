import SwiftUI

struct CreateTeamView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var teamName = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Store Name")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.secondary)
                    TextField("e.g. Morning Crew", text: $teamName)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 15))
                }

                Text("You'll be the manager of this team. An invite code will be generated that members can use to join.")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)

                Button(action: {
                    teamViewModel.createTeam(name: teamName.trimmingCharacters(in: .whitespacesAndNewlines))
                    dismiss()
                }) {
                    Text("Create Store Team")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(teamName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.gray : Color.primaryGreen)
                        )
                }
                .disabled(teamName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                Spacer()
            }
            .padding(16)
            .navigationTitle("Create Store Team")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
