import SwiftUI

struct TeamWeekPlanView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedMemberId = ""
    @State private var selectedJobTitle = ""
    @State private var role = ""
    @State private var hourlyRate = ""
    @State private var notes = ""
    @State private var weekOffset = 0
    @State private var dayEnabled = [true, true, true, true, true, false, false]
    @State private var dayStartTimes: [Date] = (0..<7).map { _ in
        Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date()
    }
    @State private var dayEndTimes: [Date] = (0..<7).map { _ in
        Calendar.current.date(bySettingHour: 17, minute: 0, second: 0, of: Date()) ?? Date()
    }

    private let daysOfWeek = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

    private var weekStartDate: Date {
        let cal = Calendar.current
        var comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: Date())
        comps.weekday = 2
        let thisMonday = cal.date(from: comps) ?? Date()
        return cal.date(byAdding: .weekOfYear, value: weekOffset, to: thisMonday) ?? thisMonday
    }

    private func dayDate(for index: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: index, to: weekStartDate) ?? weekStartDate
    }

    private var totalDays: Int {
        (0..<7).filter { dayEnabled[$0] }.count
    }

    private var totalHours: Double {
        let cal = Calendar.current
        return (0..<7).filter { dayEnabled[$0] }.reduce(0.0) { total, i in
            let startComps = cal.dateComponents([.hour, .minute], from: dayStartTimes[i])
            let endComps = cal.dateComponents([.hour, .minute], from: dayEndTimes[i])
            let startMin = (startComps.hour ?? 0) * 60 + (startComps.minute ?? 0)
            let endMin = (endComps.hour ?? 0) * 60 + (endComps.minute ?? 0)
            let diff = endMin > startMin ? endMin - startMin : 1440 - startMin + endMin
            return total + Double(diff) / 60.0
        }
    }

    private var canAssign: Bool {
        !selectedMemberId.isEmpty && !selectedJobTitle.isEmpty && dayEnabled.contains(true)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Member selector
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
                        .onChange(of: selectedMemberId) { newValue in
                            if !newValue.isEmpty {
                                teamViewModel.fetchMemberJobs(userId: newValue)
                            }
                        }
                    }

                    // Employer selector
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Employer")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        Picker("Employer", selection: $selectedJobTitle) {
                            Text("Select employer").tag("")
                            ForEach(dashboardViewModel.jobs, id: \.id) { job in
                                Text("\(job.title) (\(job.isGigWork ? "Gig" : "$\(String(format: "%.0f", job.defaultHourlyRate))/hr"))")
                                    .tag(job.title)
                            }
                        }
                        .pickerStyle(.menu)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .onChange(of: selectedJobTitle) { newValue in
                            updateHourlyRate()
                        }
                    }

                    // Role and Pay Rate
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Role")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                            TextField("Optional", text: $role)
                                .textFieldStyle(.roundedBorder)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Pay Rate ($/hr)")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                            TextField("0.00", text: $hourlyRate)
                                .textFieldStyle(.roundedBorder)
                                .keyboardType(.decimalPad)
                        }
                    }

                    if !selectedMemberId.isEmpty && !selectedJobTitle.isEmpty {
                        if let memberJob = teamViewModel.memberJobs.first(where: { $0.title.caseInsensitiveCompare(selectedJobTitle) == .orderedSame }) {
                            let memberName = teamViewModel.members.first(where: { $0.userId == selectedMemberId })?.displayName ?? "member"
                            Text("Rate from \(memberName)'s account: $\(String(format: "%.2f", memberJob.defaultHourlyRate))/hr")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.primaryGreen)
                        }
                    }

                    // Week selector
                    HStack {
                        Button(action: { if weekOffset > -3 { weekOffset -= 1 } }) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(weekOffset > -3 ? .primary : .secondary.opacity(0.3))
                        }
                        .disabled(weekOffset <= -3)

                        Spacer()

                        VStack(spacing: 2) {
                            let fmt = DateFormatter()
                            let _ = (fmt.dateFormat = "M/dd")
                            let endDate = Calendar.current.date(byAdding: .day, value: 6, to: weekStartDate) ?? weekStartDate
                            Text("\(fmt.string(from: weekStartDate)) - \(fmt.string(from: endDate))")
                                .font(.system(size: 18, weight: .bold))

                            Text(weekOffset == 0 ? "This Week" : weekOffset == 1 ? "Next Week" : weekOffset == -1 ? "Last Week" : weekOffset < -1 ? "\(-weekOffset) weeks ago" : "In \(weekOffset) weeks")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primaryGreen)
                        }

                        Spacer()

                        Button(action: { if weekOffset < 12 { weekOffset += 1 } }) {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(weekOffset < 12 ? .primary : .secondary.opacity(0.3))
                        }
                        .disabled(weekOffset >= 12)
                    }
                    .padding(.vertical, 4)

                    // Day toggles
                    ForEach(0..<7, id: \.self) { index in
                        let fmt = DateFormatter()
                        let _ = (fmt.dateFormat = "M/dd")
                        let dateStr = fmt.string(from: dayDate(for: index))
                        let isEnabled = dayEnabled[index]

                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Toggle("", isOn: Binding(
                                    get: { dayEnabled[index] },
                                    set: { newVal in
                                        var updated = dayEnabled
                                        updated[index] = newVal
                                        dayEnabled = updated
                                    }
                                ))
                                .labelsHidden()
                                .tint(.primaryGreen)

                                Text("\(daysOfWeek[index]) (\(dateStr))")
                                    .font(.system(size: 15, weight: .semibold))
                            }

                            if isEnabled {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading) {
                                        Text("Start")
                                            .font(.system(size: 11))
                                            .foregroundColor(.secondary)
                                        DatePicker("", selection: $dayStartTimes[index], displayedComponents: .hourAndMinute)
                                            .labelsHidden()
                                    }
                                    .frame(maxWidth: .infinity)

                                    VStack(alignment: .leading) {
                                        Text("End")
                                            .font(.system(size: 11))
                                            .foregroundColor(.secondary)
                                        DatePicker("", selection: $dayEndTimes[index], displayedComponents: .hourAndMinute)
                                            .labelsHidden()
                                    }
                                    .frame(maxWidth: .infinity)
                                }
                            }
                        }
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(isEnabled ? Color.primaryGreen.opacity(0.08) : Color(UIColor.systemBackground))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(isEnabled ? Color.primaryGreen.opacity(0.4) : Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                                )
                        )
                    }

                    // Summary
                    let rate = Double(hourlyRate) ?? 0.0
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(totalDays) days · \(String(format: "%.1f", totalHours)) hours")
                            .font(.system(size: 15, weight: .bold))
                        if rate > 0 {
                            Text("Estimated pay: $\(String(format: "%.2f", totalHours * rate))")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.primaryGreen)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.primaryGreen.opacity(0.08))
                    )

                    // Notes
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Notes")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        TextField("Optional notes...", text: $notes, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                    }

                    // Save
                    Button(action: saveWeekPlan) {
                        Text("Assign Week")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(canAssign ? Color.primaryGreen : Color.primaryGreen.opacity(0.4))
                            )
                    }
                    .disabled(!canAssign)

                    Spacer().frame(height: 24)
                }
                .padding(16)
            }
            .navigationTitle("Plan Team Week")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .interactiveDismissDisabled()
        .onChange(of: teamViewModel.memberJobs) { _ in
            updateHourlyRate()
        }
    }

    private func updateHourlyRate() {
        guard !selectedJobTitle.isEmpty else { return }
        if !selectedMemberId.isEmpty,
           let memberJob = teamViewModel.memberJobs.first(where: { $0.title.caseInsensitiveCompare(selectedJobTitle) == .orderedSame }) {
            hourlyRate = String(format: "%.2f", memberJob.defaultHourlyRate)
        } else if let job = dashboardViewModel.jobs.first(where: { $0.title == selectedJobTitle }) {
            hourlyRate = String(format: "%.2f", job.defaultHourlyRate)
        }
    }

    private func saveWeekPlan() {
        let cal = Calendar.current
        let rate = Double(hourlyRate) ?? 0.0

        for i in 0..<7 {
            guard dayEnabled[i] else { continue }

            let dayStart = cal.startOfDay(for: dayDate(for: i))
            let startComps = cal.dateComponents([.hour, .minute], from: dayStartTimes[i])
            let endComps = cal.dateComponents([.hour, .minute], from: dayEndTimes[i])

            var actualStart = cal.date(bySettingHour: startComps.hour ?? 9, minute: startComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart
            var actualEnd = cal.date(bySettingHour: endComps.hour ?? 17, minute: endComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart

            if actualEnd <= actualStart {
                actualEnd = actualEnd.addingTimeInterval(86400)
            }

            teamViewModel.assignShift(
                to: selectedMemberId,
                company: selectedJobTitle,
                role: role,
                startTime: Int64(actualStart.timeIntervalSince1970 * 1000),
                endTime: Int64(actualEnd.timeIntervalSince1970 * 1000),
                hourlyRate: rate,
                notes: notes
            )
        }

        dismiss()
    }
}
