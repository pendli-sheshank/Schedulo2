import SwiftUI

struct CreateTeamView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var form = TeamFormData()

    private var canCreate: Bool {
        !form.name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !form.companyName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                TeamFormSections(form: $form)
                Section {
                    Text("You'll be the manager of this team. An invite code will be generated that members can use to join.")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("Create Team")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        teamViewModel.createTeam(form: form)
                        dismiss()
                    }
                    .disabled(!canCreate)
                }
            }
        }
    }
}

/// Reusable Create/Edit team form sections. Embed inside a `Form`.
struct TeamFormSections: View {
    @Binding var form: TeamFormData

    private let days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

    private func timeBinding(_ value: Binding<Int>) -> Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(bySettingHour: (value.wrappedValue / 60) % 24,
                                      minute: value.wrappedValue % 60, second: 0, of: Date()) ?? Date()
            },
            set: { newDate in
                let c = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                value.wrappedValue = (c.hour ?? 0) * 60 + (c.minute ?? 0)
            }
        )
    }

    var body: some View {
        Section("Team") {
            TextField("Team name", text: $form.name)
            TextField("Company name", text: $form.companyName)
        }

        Section("Schedule") {
            Picker("Weekly cycle starts on", selection: $form.weeklyCycleStartDay) {
                ForEach(days, id: \.self) { Text($0).tag($0) }
            }
            Toggle("Open 24 hours", isOn: $form.open24Hours)
            if !form.open24Hours {
                DatePicker("Opens", selection: timeBinding($form.workStartMinutes), displayedComponents: .hourAndMinute)
                DatePicker("Closes", selection: timeBinding($form.workEndMinutes), displayedComponents: .hourAndMinute)
            }
        }

        Section("Location") {
            TextField("Address", text: $form.addressLine, axis: .vertical)
                .lineLimit(1...3)
            TextField("City", text: $form.city)
            TextField("State/Region", text: $form.region)
            TextField("Postal code", text: $form.postalCode)
        }
    }
}
