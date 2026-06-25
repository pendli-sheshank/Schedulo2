import SwiftUI

struct EditTeamView: View {
    let team: TeamInfo
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var form: TeamFormData

    init(team: TeamInfo) {
        self.team = team
        _form = State(initialValue: TeamFormData(from: team))
    }

    private var canSave: Bool {
        !form.name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !form.companyName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                TeamFormSections(form: $form)
            }
            .navigationTitle("Edit Team")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        teamViewModel.updateTeam(teamId: team.id, form: form)
                        dismiss()
                    }
                    .disabled(!canSave)
                }
            }
        }
    }
}
