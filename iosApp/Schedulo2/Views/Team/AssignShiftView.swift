import SwiftUI

struct AssignShiftView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedMemberId = ""
    @State private var company = ""
    @State private var role = ""
    @State private var startDate = Date()
    @State private var endDate = Date().addingTimeInterval(3600 * 4)
    @State private var hourlyRate = ""
    @State private var notes = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Assign To")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        Picker("Member", selection: $selectedMemberId) {
                            Text("Select member").tag("")
                            ForEach(teamViewModel.members) { member in
                                Text(member.displayName.isEmpty ? member.email : member.displayName)
                                    .tag(member.userId)
                            }
                        }
                        .pickerStyle(.menu)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Company")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        TextField("Company name", text: $company)
                            .textFieldStyle(.roundedBorder)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Role")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        TextField("Role / Position", text: $role)
                            .textFieldStyle(.roundedBorder)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Start Time")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        DatePicker("", selection: $startDate)
                            .labelsHidden()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("End Time")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        DatePicker("", selection: $endDate)
                            .labelsHidden()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Hourly Rate")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        TextField("0.00", text: $hourlyRate)
                            .textFieldStyle(.roundedBorder)
                            .keyboardType(.decimalPad)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Notes")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        TextField("Optional notes", text: $notes)
                            .textFieldStyle(.roundedBorder)
                    }

                    Button(action: assignShift) {
                        Text("Assign Shift")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(canAssign ? Color.primaryGreen : Color.gray)
                            )
                    }
                    .disabled(!canAssign)
                }
                .padding(16)
            }
            .navigationTitle("Assign Shift")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var canAssign: Bool {
        !selectedMemberId.isEmpty && !company.isEmpty && endDate > startDate
    }

    private func assignShift() {
        let startMillis = Int64(startDate.timeIntervalSince1970 * 1000)
        let endMillis = Int64(endDate.timeIntervalSince1970 * 1000)
        let rate = Double(hourlyRate) ?? 0.0

        teamViewModel.assignShift(
            to: selectedMemberId,
            company: company,
            role: role,
            startTime: startMillis,
            endTime: endMillis,
            hourlyRate: rate,
            notes: notes
        )
        dismiss()
    }
}
