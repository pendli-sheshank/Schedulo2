import SwiftUI

struct WeekPlanView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedJob: Job?
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
        comps.weekday = 2 // Monday
        let thisMonday = cal.date(from: comps) ?? Date()
        return cal.date(byAdding: .weekOfYear, value: weekOffset, to: thisMonday) ?? thisMonday
    }

    private func dayDate(for index: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: index, to: weekStartDate) ?? weekStartDate
    }

    private var duplicateDays: Set<Int> {
        guard let job = selectedJob else { return [] }
        let cal = Calendar.current
        var result = Set<Int>()
        for i in 0..<7 {
            let dayStart = cal.startOfDay(for: dayDate(for: i))
            let dayEnd = cal.date(byAdding: .day, value: 1, to: dayStart)!
            let exists = dashboardViewModel.shifts.contains {
                $0.company.lowercased() == job.title.lowercased() &&
                $0.startTime >= dayStart && $0.startTime < dayEnd
            }
            if exists { result.insert(i) }
        }
        return result
    }

    private var totalDays: Int {
        (0..<7).filter { dayEnabled[$0] && !duplicateDays.contains($0) }.count
    }

    private var totalHours: Double {
        let cal = Calendar.current
        return (0..<7).filter { dayEnabled[$0] && !duplicateDays.contains($0) }.reduce(0.0) { total, i in
            let startComps = cal.dateComponents([.hour, .minute], from: dayStartTimes[i])
            let endComps = cal.dateComponents([.hour, .minute], from: dayEndTimes[i])
            let startMin = (startComps.hour ?? 0) * 60 + (startComps.minute ?? 0)
            let endMin = (endComps.hour ?? 0) * 60 + (endComps.minute ?? 0)
            let diff = endMin > startMin ? endMin - startMin : 1440 - startMin + endMin
            return total + Double(diff) / 60.0
        }
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

                // Week selector
                HStack {
                    Button(action: { if weekOffset > 0 { weekOffset -= 1 } }) {
                        Image(systemName: "chevron.left")
                            .foregroundColor(weekOffset > 0 ? .primary : .secondary.opacity(0.3))
                    }
                    .disabled(weekOffset <= 0)

                    Spacer()

                    VStack(spacing: 2) {
                        let fmt = DateFormatter()
                        let _ = (fmt.dateFormat = "M/dd")
                        let endDate = Calendar.current.date(byAdding: .day, value: 6, to: weekStartDate) ?? weekStartDate
                        Text("\(fmt.string(from: weekStartDate)) - \(fmt.string(from: endDate))")
                            .font(.system(size: 16, weight: .bold))

                        Text(weekOffset == 0 ? "This Week" : weekOffset == 1 ? "Next Week" : "In \(weekOffset) weeks")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.primaryGreen)
                    }

                    Spacer()

                    Button(action: { if weekOffset < 12 { weekOffset += 1 } }) {
                        Image(systemName: "chevron.right")
                            .foregroundColor(weekOffset < 12 ? .primary : .secondary.opacity(0.3))
                    }
                    .disabled(weekOffset >= 12)
                }

                // Days
                ForEach(0..<7, id: \.self) { index in
                    let hasDuplicate = duplicateDays.contains(index)
                    let fmt = DateFormatter()
                    let _ = (fmt.dateFormat = "M/dd")
                    let dateStr = fmt.string(from: dayDate(for: index))

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Toggle("", isOn: Binding(
                                get: { dayEnabled[index] && !hasDuplicate },
                                set: { dayEnabled[index] = $0 }
                            ))
                            .labelsHidden()
                            .disabled(hasDuplicate)
                            .tint(.primaryGreen)

                            VStack(alignment: .leading) {
                                Text("\(daysOfWeek[index]) (\(dateStr))")
                                    .font(.system(size: 15, weight: .semibold))
                                if hasDuplicate {
                                    Text("Shift already exists")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.accentOrange)
                                }
                            }
                        }

                        if dayEnabled[index] && !hasDuplicate {
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
                            .fill(hasDuplicate ? Color.accentOrange.opacity(0.06) : Color(UIColor.systemBackground))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(
                                        hasDuplicate ? Color.accentOrange.opacity(0.3) : Color(UIColor.separator).opacity(0.3),
                                        lineWidth: 1
                                    )
                            )
                    )
                }

                // Summary
                Text("\(totalDays) days - \(String(format: "%.1f", totalHours)) hours planned")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.secondary)

                // Save
                Button(action: saveWeekPlan) {
                    Text("Save Week Plan")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(selectedJob != nil && dayEnabled.contains(true) ? Color.primaryGreen : Color.primaryGreen.opacity(0.4))
                        )
                }
                .disabled(selectedJob == nil || !dayEnabled.contains(true))

                Spacer().frame(height: 24)
            }
            .padding(16)
        }
        .navigationTitle("Plan Entire Week")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
        }
        .onAppear {
            if let firstJob = dashboardViewModel.jobs.first {
                selectedJob = firstJob
            }
        }
    }

    // MARK: - Save

    private func saveWeekPlan() {
        guard let job = selectedJob else { return }
        let cal = Calendar.current

        for i in 0..<7 {
            guard dayEnabled[i] && !duplicateDays.contains(i) else { continue }

            let dayStart = cal.startOfDay(for: dayDate(for: i))
            let startComps = cal.dateComponents([.hour, .minute], from: dayStartTimes[i])
            let endComps = cal.dateComponents([.hour, .minute], from: dayEndTimes[i])

            var actualStart = cal.date(bySettingHour: startComps.hour ?? 9, minute: startComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart
            var actualEnd = cal.date(bySettingHour: endComps.hour ?? 17, minute: endComps.minute ?? 0, second: 0, of: dayStart) ?? dayStart

            if actualEnd <= actualStart {
                actualEnd = actualEnd.addingTimeInterval(86400)
            }

            dashboardViewModel.addShift(
                company: job.title,
                startTime: actualStart,
                endTime: actualEnd,
                hourlyRate: job.defaultHourlyRate,
                isGig: job.isGigWork,
                customEarned: 0.0,
                reminderBeforeMinutes: dashboardViewModel.defaultReminderMinutes,
                notes: ""
            )
        }

        dismiss()
    }
}
