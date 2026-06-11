import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    var onEditShift: (String) -> Void = { _ in }
    var onNavigateToProfile: () -> Void = {}

    @State private var weekOffset = 0
    @State private var showWeekPicker = false

    private var greetingName: String {
        let name = dashboardViewModel.userName
        if !name.isEmpty {
            return name.components(separatedBy: " ").first ?? "there"
        }
        let email = authViewModel.currentUserEmail
        let prefix = email.components(separatedBy: "@").first ?? ""
        return prefix.isEmpty ? "there" : prefix
    }

    private var displayInitials: String {
        let name = dashboardViewModel.userName
        if !name.isEmpty {
            let parts = name.trimmingCharacters(in: .whitespaces).components(separatedBy: " ")
            if parts.count >= 2, let first = parts.first?.first, let last = parts.last?.first {
                return "\(first)\(last)".uppercased()
            }
            return String(name.prefix(2)).uppercased()
        }
        let email = authViewModel.currentUserEmail
        let prefix = email.components(separatedBy: "@").first ?? ""
        return prefix.count >= 2 ? String(prefix.prefix(2)).uppercased() : prefix.uppercased().isEmpty ? "U" : prefix.uppercased()
    }

    private func weekStart(for offset: Int) -> Date {
        let cal = Calendar.current
        var comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: Date())
        comps.weekday = 2 // Monday
        let thisMonday = cal.date(from: comps) ?? Date()
        return cal.date(byAdding: .weekOfYear, value: offset, to: thisMonday) ?? thisMonday
    }

    private func weekRangeLabel(for offset: Int) -> String {
        let start = weekStart(for: offset)
        let end = Calendar.current.date(byAdding: .day, value: 6, to: start) ?? start
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM dd"
        return "\(fmt.string(from: start)) - \(fmt.string(from: end))"
    }

    private var weekShifts: [Shift] {
        let start = weekStart(for: weekOffset)
        let end = Calendar.current.date(byAdding: .day, value: 7, to: start) ?? start
        return dashboardViewModel.shifts.filter { $0.startTime >= start && $0.startTime < end }
    }

    private var completedWeekShifts: [Shift] {
        weekShifts.filter { $0.startTime < Date() }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                // Header
                headerSection

                // Week picker
                weekPickerSection

                // Error banner
                if let error = dashboardViewModel.syncError {
                    errorBanner(error)
                }

                // Loading
                if dashboardViewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }

                // Earnings card
                earningsCard

                // Employer Goals
                if !dashboardViewModel.jobs.isEmpty {
                    Text("Employer Goals")
                        .font(.system(size: 17, weight: .bold))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)

                    ForEach(dashboardViewModel.jobs, id: \.id) { job in
                        JobGoalTrackerCard(job: job, shifts: dashboardViewModel.shifts, weekOffset: weekOffset)
                            .padding(.horizontal, 20)
                    }
                }

                // Upcoming shifts
                if weekOffset == 0 {
                    upcomingShiftsSection
                }

                Spacer().frame(height: 80)
            }
        }
        .refreshable {
            dashboardViewModel.refreshData()
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Hi, \(greetingName)")
                    .font(.system(size: 26, weight: .bold))
                    .tracking(-0.5)

                Text(weekRangeLabel(for: weekOffset) + (weekOffset == 0 ? " - This Week" : ""))
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }

            Spacer()

            HStack(spacing: 4) {
                Button(action: {
                    dashboardViewModel.reset()
                    authViewModel.logout()
                }) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 18))
                        .foregroundColor(.secondary)
                }

                Button(action: onNavigateToProfile) {
                    ZStack {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [.primaryGreen, .secondaryGreen],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 42, height: 42)
                        Text(displayInitials)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 8)
    }

    // MARK: - Week Picker

    private var weekPickerSection: some View {
        Menu {
            ForEach([0, -1, -2, -3, -4], id: \.self) { offset in
                Button(action: { weekOffset = offset }) {
                    HStack {
                        Text(weekRangeLabel(for: offset) + (offset == 0 ? " (Current)" : ""))
                        if offset == weekOffset {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "calendar")
                    .font(.system(size: 16))
                    .foregroundColor(.primaryGreen)
                Text(weekRangeLabel(for: weekOffset) + (weekOffset == 0 ? " (Current)" : ""))
                    .font(.system(size: 13, weight: .semibold))
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(UIColor.secondarySystemBackground).opacity(0.6))
            )
        }
        .padding(.horizontal, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Earnings Card

    private var earningsCard: some View {
        let totalEarned = completedWeekShifts.reduce(0.0) { $0 + $1.totalEarned }
        let totalHours = completedWeekShifts.reduce(0.0) { $0 + $1.durationHours }
        let shiftCount = weekShifts.count
        let avgPerShift = shiftCount > 0 ? totalEarned / Double(shiftCount) : 0.0

        return VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Weekly Earnings")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                    Text("$\(totalEarned, specifier: "%.2f")")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                        .tracking(-1)
                }
                Spacer()
                Button(action: {}) {
                    HStack(spacing: 4) {
                        Text("Pay Details")
                            .font(.system(size: 12, weight: .semibold))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.15))
                    )
                }
            }

            Spacer().frame(height: 20)

            HStack(spacing: 12) {
                statPill(label: "Hours", value: String(format: "%.1fh", totalHours))
                statPill(label: "Shifts", value: "\(shiftCount)")
                statPill(label: "Avg/Shift", value: shiftCount > 0 ? "$\(Int(avgPerShift))" : "$0")
            }
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(
                    LinearGradient(
                        colors: [.primaryGreen, Color(red: 0.106, green: 0.263, blue: 0.196)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        )
        .padding(.horizontal, 20)
    }

    private func statPill(label: String, value: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(.white.opacity(0.6))
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
        )
    }

    // MARK: - Error Banner

    private func errorBanner(_ error: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(.red)
                .font(.system(size: 16))
            Text(error)
                .font(.system(size: 13))
                .foregroundColor(.red)
            Spacer()
            Button(action: { dashboardViewModel.clearSyncError() }) {
                Image(systemName: "xmark")
                    .foregroundColor(.red)
                    .font(.system(size: 14))
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.red.opacity(0.1))
        )
        .padding(.horizontal, 20)
    }

    // MARK: - Upcoming Shifts

    private var upcomingShiftsSection: some View {
        let now = Date()
        let upcoming = dashboardViewModel.shifts
            .filter { $0.startTime >= now }
            .sorted { $0.startTime < $1.startTime }
            .prefix(5)

        let timeFormat: DateFormatter = {
            let f = DateFormatter()
            f.dateFormat = "EEE, MMM dd - h:mm a"
            return f
        }()

        return VStack(alignment: .leading, spacing: 12) {
            Text("Upcoming Shifts")
                .font(.system(size: 17, weight: .bold))

            if upcoming.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "calendar.badge.checkmark")
                        .font(.system(size: 28))
                        .foregroundColor(.secondary.opacity(0.4))
                    Text("No upcoming shifts")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(32)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color(UIColor.secondarySystemBackground).opacity(0.4))
                )
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(upcoming.enumerated()), id: \.element.id) { index, shift in
                        Button(action: { onEditShift(shift.id) }) {
                            HStack(spacing: 12) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill((shift.isGig ? Color.accentOrange : Color.accentBlue).opacity(0.1))
                                        .frame(width: 40, height: 40)
                                    Image(systemName: shift.isGig ? "car.fill" : "briefcase.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(shift.isGig ? .accentOrange : .accentBlue)
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(shift.company)
                                        .font(.system(size: 14, weight: .semibold))
                                    Text(timeFormat.string(from: shift.startTime))
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                }

                                Spacer()

                                Text("$\(shift.totalEarned, specifier: "%.2f")")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.primaryGreen)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)

                        if index < upcoming.count - 1 {
                            Divider().padding(.horizontal, 12)
                        }
                    }
                }
                .padding(4)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color(UIColor.systemBackground))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                        )
                )
            }
        }
        .padding(.horizontal, 20)
    }
}

// MARK: - Job Goal Tracker Card

struct JobGoalTrackerCard: View {
    let job: Job
    let shifts: [Shift]
    var weekOffset: Int = 0

    private var cycleStart: Date {
        let base = job.getStartOfCurrentCycle()
        return Calendar.current.date(byAdding: .weekOfYear, value: weekOffset, to: base) ?? base
    }

    private var cycleEnd: Date {
        Calendar.current.date(byAdding: .day, value: 7, to: cycleStart) ?? cycleStart
    }

    private var shiftsForJob: [Shift] {
        let now = Date()
        return shifts.filter {
            $0.company.lowercased() == job.title.lowercased() &&
            $0.startTime >= cycleStart &&
            $0.startTime < cycleEnd &&
            $0.startTime < now
        }
    }

    private var hours: Double { shiftsForJob.reduce(0) { $0 + $1.durationHours } }
    private var earnings: Double { shiftsForJob.reduce(0) { $0 + $1.totalEarned } }

    private var overtimeHours: Double {
        !job.isGigWork && hours > job.overtimeThresholdHours ? hours - job.overtimeThresholdHours : 0
    }

    private var overtimeEarnings: Double {
        overtimeHours * job.defaultHourlyRate * job.overtimeMultiplier
    }

    private var progressFraction: Double {
        guard job.goalHours > 0 else { return 0 }
        let actual = job.goalType == "Hours" ? hours : earnings
        return min(max(actual / job.goalHours, 0), 1)
    }

    private var accentColor: Color { job.isGigWork ? .accentOrange : .accentBlue }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(accentColor.opacity(0.1))
                            .frame(width: 36, height: 36)
                        Image(systemName: job.isGigWork ? "car.fill" : "building.2.fill")
                            .font(.system(size: 18))
                            .foregroundColor(accentColor)
                    }
                    VStack(alignment: .leading) {
                        Text(job.title)
                            .font(.system(size: 16, weight: .bold))
                        Text(job.isGigWork ? "Gig Work" : "$\(job.defaultHourlyRate, specifier: "%.2f")/hr")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                Text("$\(earnings, specifier: "%.2f")")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.primaryGreen)
            }

            Spacer().frame(height: 16)

            // Stats
            HStack(spacing: 24) {
                VStack(alignment: .leading) {
                    Text("Hours").font(.system(size: 11)).foregroundColor(.secondary)
                    Text(String(format: "%.1fh", hours))
                        .font(.system(size: 15, weight: .semibold))
                }
                VStack(alignment: .leading) {
                    Text("Shifts").font(.system(size: 11)).foregroundColor(.secondary)
                    Text("\(shiftsForJob.count)")
                        .font(.system(size: 15, weight: .semibold))
                }
                if !job.isGigWork && overtimeHours > 0 {
                    VStack(alignment: .leading) {
                        Text("Overtime").font(.system(size: 11)).foregroundColor(.accentOrange)
                        Text(String(format: "%.1fh (+$%.0f)", overtimeHours, overtimeEarnings))
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.accentOrange)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Spacer().frame(height: 14)

            // Progress
            HStack {
                Text("Weekly \(job.goalType) Target")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                Spacer()
                Text(job.goalType == "Hours"
                     ? String(format: "%.1f/%.0fh", hours, job.goalHours)
                     : String(format: "$%.0f/$%.0f", earnings, job.goalHours))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(progressFraction >= 1.0 ? .primaryGreen : .primary)
            }

            Spacer().frame(height: 8)

            ProgressView(value: progressFraction)
                .tint(progressFraction >= 1.0 ? .primaryGreen : accentColor)
                .scaleEffect(y: 1.5)

            if progressFraction >= 1.0 {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 12))
                    Text("Goal achieved!")
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundColor(.primaryGreen)
                .padding(.top, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                )
        )
    }
}
