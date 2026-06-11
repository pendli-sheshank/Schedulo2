import SwiftUI

struct AddShiftView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    var shiftId: String?

    @State private var selectedJob: Job?
    @State private var company = ""
    @State private var isGig = false
    @State private var rateStr = "0.0"
    @State private var customEarningsStr = ""
    @State private var selectedDate = Date()
    @State private var startTime = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var endTime = Calendar.current.date(bySettingHour: 17, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var reminderMinutes = 30
    @State private var shiftNotes = ""
    @State private var recurrence = "None"
    @State private var recurrenceWeeks = "4"
    @State private var initialized = false

    private var existingShift: Shift? {
        guard let id = shiftId else { return nil }
        return dashboardViewModel.shifts.first { $0.id == id }
    }

    private var conflicts: [Shift] {
        let cal = Calendar.current
        let dayStart = cal.startOfDay(for: selectedDate)
        let startComps = cal.dateComponents([.hour, .minute], from: startTime)
        let endComps = cal.dateComponents([.hour, .minute], from: endTime)
        var actualStart = cal.date(bySettingHour: startComps.hour ?? 9, minute: startComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart
        var actualEnd = cal.date(bySettingHour: endComps.hour ?? 17, minute: endComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart
        if actualEnd <= actualStart {
            actualEnd = actualEnd.addingTimeInterval(86400)
        }
        return dashboardViewModel.detectConflicts(startTime: actualStart, endTime: actualEnd, excludeShiftId: existingShift?.id)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Job selector
                VStack(alignment: .leading, spacing: 8) {
                    Text("Select Job")
                        .font(.system(size: 16, weight: .bold))

                    if !dashboardViewModel.jobs.isEmpty {
                        Picker("Job", selection: $selectedJob) {
                            Text("Select a Job...").tag(nil as Job?)
                            ForEach(dashboardViewModel.jobs, id: \.id) { job in
                                Text("\(job.title) (\(job.isGigWork ? "Gig" : "$\(String(format: "%.2f", job.defaultHourlyRate))/hr"))")
                                    .tag(job as Job?)
                            }
                        }
                        .pickerStyle(.menu)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(UIColor.separator), lineWidth: 1)
                        )
                    }
                }

                // Pay
                if isGig {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Shift Earnings ($) [Gig Work]")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                        TextField("0.00", text: $customEarningsStr)
                            .keyboardType(.decimalPad)
                            .textFieldStyle(.roundedBorder)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Hourly Rate ($)")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                        TextField("0.00", text: $rateStr)
                            .keyboardType(.decimalPad)
                            .textFieldStyle(.roundedBorder)
                    }
                }

                // Date
                DatePicker("Date", selection: $selectedDate, displayedComponents: .date)

                // Time
                HStack(spacing: 16) {
                    DatePicker("Start", selection: $startTime, displayedComponents: .hourAndMinute)
                    DatePicker("End", selection: $endTime, displayedComponents: .hourAndMinute)
                }

                // Reminder
                VStack(alignment: .leading, spacing: 8) {
                    Text("Remind Me")
                        .font(.system(size: 14, weight: .bold))
                    HStack(spacing: 8) {
                        ForEach([(0, "None"), (15, "15m"), (30, "30m"), (60, "1h"), (120, "2h")], id: \.0) { minutes, label in
                            let selected = reminderMinutes == minutes
                            Button(action: { reminderMinutes = minutes }) {
                                Text(label)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(selected ? .white : .primary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(selected ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                                    )
                            }
                        }
                    }
                }

                // Recurrence (only for new shifts)
                if existingShift == nil {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Repeat Shift")
                            .font(.system(size: 14, weight: .bold))
                        HStack(spacing: 8) {
                            ForEach(["None", "Daily", "Weekly", "Biweekly"], id: \.self) { option in
                                let selected = recurrence == option
                                Button(action: { recurrence = option }) {
                                    Text(option)
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(selected ? .white : .primary)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(
                                            RoundedRectangle(cornerRadius: 8)
                                                .fill(selected ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                                        )
                                }
                            }
                        }
                        if recurrence != "None" {
                            TextField("Repeat for (weeks)", text: $recurrenceWeeks)
                                .keyboardType(.numberPad)
                                .textFieldStyle(.roundedBorder)
                        }
                    }
                }

                // Notes
                VStack(alignment: .leading, spacing: 4) {
                    Text("Notes (optional)")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                    TextEditor(text: $shiftNotes)
                        .frame(minHeight: 60)
                        .padding(4)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color(UIColor.separator), lineWidth: 1)
                        )
                        .onChange(of: shiftNotes) { newVal in
                            if newVal.count > 1000 { shiftNotes = String(newVal.prefix(1000)) }
                        }
                }

                // Conflict warning
                if !conflicts.isEmpty {
                    conflictWarning
                }

                Spacer().frame(height: 16)

                // Save
                Button(action: saveShift) {
                    Text(existingShift != nil ? "Update Shift" : "Save Shift")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.primaryGreen)
                        )
                }

                // Delete (editing only)
                if existingShift != nil {
                    Button(action: {
                        if let id = shiftId {
                            dashboardViewModel.deleteShift(shiftId: id)
                            dismiss()
                        }
                    }) {
                        Text("Delete Shift")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.red, lineWidth: 1)
                            )
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle(existingShift != nil ? "Edit Shift" : "Add Shift")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            if existingShift != nil {
                ToolbarItem(placement: .destructiveAction) {
                    Button(action: {
                        if let id = shiftId {
                            dashboardViewModel.deleteShift(shiftId: id)
                            dismiss()
                        }
                    }) {
                        Image(systemName: "trash")
                            .foregroundColor(.red)
                    }
                }
            }
        }
        .onAppear {
            guard !initialized else { return }
            initialized = true
            populateFields()
        }
        .onChange(of: selectedJob) { job in
            guard let job = job else { return }
            company = job.title
            isGig = job.isGigWork
            rateStr = String(format: "%.2f", job.defaultHourlyRate)
        }
    }

    // MARK: - Conflict Warning

    private var conflictWarning: some View {
        let timeFmt = DateFormatter()
        timeFmt.dateFormat = "MMM dd h:mm a"

        return HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.accentOrange)
                .font(.system(size: 16))

            VStack(alignment: .leading, spacing: 4) {
                Text("Shift Overlap Detected")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Color(red: 0.573, green: 0.251, blue: 0.055))
                ForEach(conflicts, id: \.id) { conflict in
                    Text("\(conflict.company): \(timeFmt.string(from: conflict.startTime)) - \(timeFmt.string(from: conflict.endTime))")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.573, green: 0.251, blue: 0.055))
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(red: 0.996, green: 0.953, blue: 0.780))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.accentOrange, lineWidth: 1)
                )
        )
    }

    // MARK: - Populate Fields

    private func populateFields() {
        if let shift = existingShift {
            company = shift.company
            isGig = shift.isGig
            rateStr = String(format: "%.2f", shift.hourlyRate)
            customEarningsStr = String(format: "%.2f", shift.customEarned)
            selectedDate = shift.startTime
            startTime = shift.startTime
            endTime = shift.endTime
            reminderMinutes = shift.reminderBeforeMinutes
            shiftNotes = shift.notes

            selectedJob = dashboardViewModel.jobs.first { $0.title.lowercased() == shift.company.lowercased() }
        } else {
            if let firstJob = dashboardViewModel.jobs.first {
                selectedJob = firstJob
                company = firstJob.title
                isGig = firstJob.isGigWork
                rateStr = String(format: "%.2f", firstJob.defaultHourlyRate)
            }
            reminderMinutes = dashboardViewModel.defaultReminderMinutes
        }
    }

    // MARK: - Save

    private func saveShift() {
        let cal = Calendar.current
        let dayStart = cal.startOfDay(for: selectedDate)
        let startComps = cal.dateComponents([.hour, .minute], from: startTime)
        let endComps = cal.dateComponents([.hour, .minute], from: endTime)
        var actualStart = cal.date(bySettingHour: startComps.hour ?? 9, minute: startComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart
        var actualEnd = cal.date(bySettingHour: endComps.hour ?? 17, minute: endComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart
        if actualEnd <= actualStart {
            actualEnd = actualEnd.addingTimeInterval(86400)
        }

        let hourly = isGig ? 0.0 : (Double(rateStr) ?? 0.0)
        let earned = isGig ? (Double(customEarningsStr) ?? 0.0) : 0.0
        let effectiveReminder = dashboardViewModel.remindersEnabled ? reminderMinutes : 0

        if let shift = existingShift {
            dashboardViewModel.updateShift(
                shiftId: shift.id,
                company: company,
                startTime: actualStart,
                endTime: actualEnd,
                hourlyRate: hourly,
                isGig: isGig,
                customEarned: earned,
                reminderBeforeMinutes: effectiveReminder,
                notes: shiftNotes.trimmingCharacters(in: .whitespaces)
            )
        } else {
            dashboardViewModel.addShift(
                company: company,
                startTime: actualStart,
                endTime: actualEnd,
                hourlyRate: hourly,
                isGig: isGig,
                customEarned: earned,
                reminderBeforeMinutes: effectiveReminder,
                notes: shiftNotes.trimmingCharacters(in: .whitespaces)
            )

            // Handle recurrence
            if recurrence != "None" {
                let weeks = Int(recurrenceWeeks) ?? 4
                let clampedWeeks = max(1, min(52, weeks))
                let dayIncrement: Int = {
                    switch recurrence {
                    case "Daily": return 1
                    case "Weekly": return 7
                    case "Biweekly": return 14
                    default: return 0
                    }
                }()
                let totalOccurrences = recurrence == "Daily" ? clampedWeeks * 7 : clampedWeeks
                for i in 1...totalOccurrences {
                    let offset = TimeInterval(i * dayIncrement * 86400)
                    dashboardViewModel.addShift(
                        company: company,
                        startTime: actualStart.addingTimeInterval(offset),
                        endTime: actualEnd.addingTimeInterval(offset),
                        hourlyRate: hourly,
                        isGig: isGig,
                        customEarned: earned,
                        reminderBeforeMinutes: effectiveReminder,
                        notes: shiftNotes.trimmingCharacters(in: .whitespaces)
                    )
                }
            }
        }

        dismiss()
    }
}
