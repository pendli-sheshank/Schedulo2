import SwiftUI

struct TeamWeekPlanView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedMemberId = ""
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

    private static let allDays: [String] = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

    private var cycleStartDay: String {
        teamViewModel.currentTeam?.weeklyCycleStartDay ?? "Monday"
    }

    private var daysOfWeek: [String] {
        let all = Self.allDays
        let idx: Int = all.firstIndex(of: cycleStartDay) ?? 0
        return Array(all[idx...]) + Array(all[..<idx])
    }

    private static func weekdayNumber(for dayName: String) -> Int {
        switch dayName {
        case "Sunday": return 1
        case "Monday": return 2
        case "Tuesday": return 3
        case "Wednesday": return 4
        case "Thursday": return 5
        case "Friday": return 6
        case "Saturday": return 7
        default: return 2
        }
    }

    private var weekStartDate: Date {
        let cal = Calendar.current
        let targetWeekday: Int = Self.weekdayNumber(for: cycleStartDay)
        let today = Date()
        let todayWeekday: Int = cal.component(.weekday, from: today)
        let diff: Int = (todayWeekday - targetWeekday + 7) % 7
        let cycleStart: Date = cal.startOfDay(for: cal.date(byAdding: .day, value: -diff, to: today)!)
        return cal.date(byAdding: .weekOfYear, value: weekOffset, to: cycleStart) ?? cycleStart
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

    private var companyName: String {
        teamViewModel.currentTeam?.companyName ?? ""
    }

    private var canAssign: Bool {
        !selectedMemberId.isEmpty && dayEnabled.contains(true)
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
                            if let m = teamViewModel.members.first(where: { $0.userId == newValue }), m.defaultHourlyRate > 0 {
                                hourlyRate = String(format: "%.2f", m.defaultHourlyRate)
                            }
                        }
                    }

                    // Company (from the team)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Company")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        Text(companyName.isEmpty ? "—" : companyName)
                            .font(.system(size: 15))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color(UIColor.secondarySystemBackground))
                            )
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

                    if let m = teamViewModel.members.first(where: { $0.userId == selectedMemberId }), m.defaultHourlyRate <= 0 {
                        Text("No saved pay rate for this member — enter one above.")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.accentOrange)
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
    }

    private func saveWeekPlan() {
        let cal = Calendar.current
        let rate = Double(hourlyRate) ?? 0.0

        for i in 0..<7 {
            guard dayEnabled[i] else { continue }

            let dayStart: Date = cal.startOfDay(for: dayDate(for: i))
            let startComps: DateComponents = cal.dateComponents([.hour, .minute], from: dayStartTimes[i])
            let endComps: DateComponents = cal.dateComponents([.hour, .minute], from: dayEndTimes[i])

            let startHour: Int = startComps.hour ?? 9
            let startMinute: Int = startComps.minute ?? 0
            let endHour: Int = endComps.hour ?? 17
            let endMinute: Int = endComps.minute ?? 0

            let actualStart: Date = cal.date(bySettingHour: startHour, minute: startMinute, second: 0, of: dayStart) ?? dayStart
            var actualEnd: Date = cal.date(bySettingHour: endHour, minute: endMinute, second: 0, of: dayStart) ?? dayStart

            if actualEnd <= actualStart {
                actualEnd = actualEnd.addingTimeInterval(86400)
            }

            teamViewModel.assignShift(
                to: selectedMemberId,
                company: companyName,
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
