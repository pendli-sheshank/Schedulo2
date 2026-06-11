import SwiftUI

struct PayView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    @State private var selectedCycleIndex: Int?
    @State private var expandedCycleStart: Date?
    @State private var cycleToConfirmPaid: PayCycleInfo?
    @State private var showExportSheet = false
    @State private var exportText = ""

    private var cycles: [PayCycleInfo] {
        buildPayCycles()
    }

    private var gigShifts: [Shift] {
        dashboardViewModel.shifts.filter { $0.isGig }
    }

    private var gigTotalEarned: Double {
        gigShifts.reduce(0) { $0 + $1.totalEarned }
    }

    private var totalPaid: Double {
        dashboardViewModel.shifts.filter { $0.isPaid }.reduce(0) { $0 + $1.totalEarned }
    }

    private var totalDue: Double {
        cycles.filter { $0.status == .due }
            .flatMap { $0.shifts.filter { !$0.isPaid } }
            .reduce(0) { $0 + $1.totalEarned }
    }

    private var totalPendingHold: Double {
        cycles.filter { $0.status == .pendingHold }.reduce(0) { $0 + $1.totalEarned }
    }

    private var upcomingEarned: Double {
        cycles.filter { $0.status == .upcoming }.reduce(0) { $0 + $1.totalEarned }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                // Title
                Text("Pay & Earnings")
                    .font(.system(size: 28, weight: .bold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                Spacer().frame(height: 24)

                // Summary card
                summaryCard

                Spacer().frame(height: 24)

                // Gig earnings
                if !gigShifts.isEmpty {
                    gigEarningsSection
                    Spacer().frame(height: 24)
                }

                // Pay cycles
                if cycles.isEmpty && gigShifts.isEmpty {
                    emptyState
                }

                if !cycles.isEmpty {
                    Text("Weekly Payroll Cycles")
                        .font(.system(size: 20, weight: .bold))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)

                    Spacer().frame(height: 12)

                    ForEach(cycles, id: \.startDate) { cycle in
                        cycleCard(cycle)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 12)
                    }
                }

                // Export button
                if !dashboardViewModel.shifts.isEmpty {
                    Button(action: { prepareExport() }) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Export Report")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundColor(.primaryGreen)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.primaryGreen, lineWidth: 1)
                        )
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }

                Spacer().frame(height: 80)
            }
        }
        .sheet(isPresented: $showExportSheet) {
            ShareSheet(activityItems: [exportText])
        }
        .alert("Confirm Payment", isPresented: Binding(
            get: { cycleToConfirmPaid != nil },
            set: { if !$0 { cycleToConfirmPaid = nil } }
        )) {
            Button("Confirm") {
                if let cycle = cycleToConfirmPaid {
                    dashboardViewModel.markCycleAsPaid(shiftIds: cycle.shifts.map { $0.id }, isPaid: true)
                    cycleToConfirmPaid = nil
                }
            }
            Button("Cancel", role: .cancel) { cycleToConfirmPaid = nil }
        } message: {
            if let cycle = cycleToConfirmPaid {
                let fmt = DateFormatter()
                fmt.dateFormat = "MMM dd"
                Text("Mark the week of \(fmt.string(from: cycle.startDate)) - \(fmt.string(from: cycle.endDate.addingTimeInterval(-1))) ($\(String(format: "%.2f", cycle.totalEarned))) as Paid?")
            }
        }
    }

    // MARK: - Summary Card

    private var summaryCard: some View {
        VStack(spacing: 0) {
            Text("Out-of-Pocket / Due Payout")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
            Text("$\(totalDue, specifier: "%.2f")")
                .font(.system(size: 42, weight: .black))
                .foregroundColor(.primaryGreen)
                .padding(.top, 4)

            Spacer().frame(height: 16)
            Divider()
            Spacer().frame(height: 16)

            HStack {
                VStack(spacing: 4) {
                    Text("Received").font(.system(size: 12, weight: .medium)).foregroundColor(.secondary)
                    Text("$\(totalPaid, specifier: "%.2f")")
                        .font(.system(size: 16, weight: .bold))
                }
                .frame(maxWidth: .infinity)
                VStack(spacing: 4) {
                    Text("On Hold").font(.system(size: 12, weight: .medium)).foregroundColor(.secondary)
                    Text("$\(totalPendingHold, specifier: "%.2f")")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.accentOrange)
                }
                .frame(maxWidth: .infinity)
                VStack(spacing: 4) {
                    Text("Upcoming").font(.system(size: 12, weight: .medium)).foregroundColor(.secondary)
                    Text("$\(upcomingEarned, specifier: "%.2f")")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.accentBlue)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.primaryGreen.opacity(0.08))
        )
        .padding(.horizontal, 16)
    }

    // MARK: - Gig Earnings

    private var gigEarningsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Gig Earnings (Direct Payout)")
                .font(.system(size: 20, weight: .bold))
                .padding(.horizontal, 16)

            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(gigShifts.count) Gig Shift\(gigShifts.count == 1 ? "" : "s")")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                    Text("Paid daily via direct payout")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary.opacity(0.7))
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text("$\(gigTotalEarned, specifier: "%.2f")")
                        .font(.system(size: 20, weight: .heavy))
                        .foregroundColor(.accentOrange)
                    Text("PAID")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.primaryGreen)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 6).fill(Color.primaryGreen.opacity(0.12)))
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.accentOrange.opacity(0.08))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.accentOrange.opacity(0.3), lineWidth: 1)
                    )
            )
            .padding(.horizontal, 16)
        }
    }

    // MARK: - Cycle Card

    private func cycleCard(_ cycle: PayCycleInfo) -> some View {
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM dd"
        let rangeStr = "\(fmt.string(from: cycle.startDate)) - \(fmt.string(from: cycle.endDate.addingTimeInterval(-1)))"
        let isExpanded = expandedCycleStart == cycle.startDate

        let statusColor: Color = {
            switch cycle.status {
            case .paid: return .primaryGreen
            case .due: return .primaryGreen
            case .pendingHold: return .accentOrange
            case .upcoming: return .secondary
            }
        }()

        let statusLabel: String = {
            switch cycle.status {
            case .paid: return "RECEIVED"
            case .due: return "DUE"
            case .pendingHold: return "ON HOLD"
            case .upcoming: return "ACTIVE"
            }
        }()

        return VStack(alignment: .leading, spacing: 0) {
            Button(action: { withAnimation { expandedCycleStart = isExpanded ? nil : cycle.startDate } }) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(rangeStr)
                            .font(.system(size: 16, weight: .bold))
                        Text("\(cycle.shifts.count) Work Shift\(cycle.shifts.count == 1 ? "" : "s")")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("$\(cycle.totalEarned, specifier: "%.2f")")
                            .font(.system(size: 18, weight: .heavy))
                            .foregroundColor(.primaryGreen)
                        Text(statusLabel)
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(statusColor)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(RoundedRectangle(cornerRadius: 6).fill(statusColor.opacity(0.12)))
                    }
                }
            }
            .buttonStyle(.plain)

            // Mark as paid
            if cycle.status == .due {
                Button(action: { cycleToConfirmPaid = cycle }) {
                    Text("Mark Entire Week as Paid")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.primaryGreen))
                }
                .padding(.top, 12)
            }

            // Expanded details
            if isExpanded {
                Divider().padding(.vertical, 12)
                Text("Timesheet Details")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.secondary)
                    .padding(.bottom, 8)

                ForEach(cycle.shifts, id: \.id) { shift in
                    shiftDetailRow(shift, cycle: cycle)
                        .padding(.vertical, 4)
                }
            } else {
                Text("Tap to view timesheet details")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary.opacity(0.8))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 8)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(UIColor.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            cycle.status == .due ? Color.primaryGreen.opacity(0.5) :
                            cycle.status == .pendingHold ? Color.accentOrange.opacity(0.4) :
                            Color(UIColor.separator).opacity(0.3),
                            lineWidth: 1
                        )
                )
        )
    }

    private func shiftDetailRow(_ shift: Shift, cycle: PayCycleInfo) -> some View {
        let timeFmt = DateFormatter()
        timeFmt.dateFormat = "hh:mm a"
        let dayFmt = DateFormatter()
        dayFmt.dateFormat = "EEE, MMM dd"

        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(shift.company)
                    .font(.system(size: 14, weight: .semibold))
                Text(dayFmt.string(from: shift.startTime))
                    .font(.system(size: 12))
                    .foregroundColor(.secondary.opacity(0.7))
                Text("\(timeFmt.string(from: shift.startTime)) -> \(timeFmt.string(from: shift.endTime)) - \(String(format: "%.1f", shift.durationHours)) hrs")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text("$\(shift.totalEarned, specifier: "%.2f")")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.primaryGreen)
                if cycle.status != .upcoming {
                    Toggle("", isOn: Binding(
                        get: { shift.isPaid },
                        set: { dashboardViewModel.toggleShiftPaidStatus(shiftId: shift.id, isPaid: $0) }
                    ))
                    .labelsHidden()
                    .tint(.primaryGreen)
                }
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(UIColor.secondarySystemBackground).opacity(0.3))
        )
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("No shifts reported yet.")
                .font(.system(size: 15))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(40)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(UIColor.secondarySystemBackground).opacity(0.3))
        )
        .padding(.horizontal, 16)
    }

    // MARK: - Export

    private func prepareExport() {
        var report = "SCHEDULO PAY REPORT\n"
        report += String(repeating: "-", count: 40) + "\n\n"

        let timeFmt = DateFormatter()
        timeFmt.dateFormat = "hh:mm a"
        let dayFmt = DateFormatter()
        dayFmt.dateFormat = "EEE, MMM dd"

        for cycle in cycles {
            let fmt = DateFormatter()
            fmt.dateFormat = "MMM dd, yyyy"
            report += "Pay Period: \(fmt.string(from: cycle.startDate)) - \(fmt.string(from: cycle.endDate.addingTimeInterval(-1)))\n"
            for shift in cycle.shifts {
                let status = shift.isPaid ? "[PAID]" : ""
                report += "\(dayFmt.string(from: shift.startTime)): \(timeFmt.string(from: shift.startTime)) - \(timeFmt.string(from: shift.endTime)) (\(String(format: "%.1f", shift.durationHours)) hrs) $\(String(format: "%.2f", shift.totalEarned)) \(status)\n"
            }
            report += "Total: \(String(format: "%.1f", cycle.shifts.reduce(0) { $0 + $1.durationHours })) hours - $\(String(format: "%.2f", cycle.totalEarned))\n"
            report += "Paid: \(cycle.shifts.filter { $0.isPaid }.count)/\(cycle.shifts.count) shifts\n\n"
        }

        exportText = report
        showExportSheet = true
    }

    // MARK: - Build Pay Cycles

    private func buildPayCycles() -> [PayCycleInfo] {
        let nonGigShifts = dashboardViewModel.shifts.filter { !$0.isGig }
        let jobs = dashboardViewModel.jobs
        let now = Date()

        var cyclesMap: [Date: [Shift]] = [:]

        for shift in nonGigShifts {
            let (start, _) = cycleStartAndEnd(for: shift, jobs: jobs)
            cyclesMap[start, default: []].append(shift)
        }

        let holdDays: TimeInterval = 4 * 24 * 60 * 60

        return cyclesMap.map { (start, shiftList) in
            let end = Calendar.current.date(byAdding: .day, value: 7, to: start)!
            let holdEnd = end.addingTimeInterval(holdDays)

            let status: PayCycleStatus = {
                if now < end { return .upcoming }
                if now >= end && now < holdEnd { return .pendingHold }
                if !shiftList.isEmpty && shiftList.allSatisfy({ $0.isPaid }) { return .paid }
                return .due
            }()

            return PayCycleInfo(
                startDate: start,
                endDate: end,
                shifts: shiftList.sorted { $0.startTime < $1.startTime },
                totalEarned: shiftList.reduce(0) { $0 + $1.totalEarned },
                status: status
            )
        }
        .sorted { $0.startDate > $1.startDate }
    }

    private func cycleStartAndEnd(for shift: Shift, jobs: [Job]) -> (Date, Date) {
        let job = jobs.first { $0.title.lowercased() == shift.company.lowercased() }
        let startDay = job?.weeklyCycleStartDay ?? "Monday"
        let cal = Calendar.current
        var date = cal.startOfDay(for: shift.startTime)

        let targetWeekday = weekdayNumber(for: startDay)

        while cal.component(.weekday, from: date) != targetWeekday {
            date = cal.date(byAdding: .day, value: -1, to: date)!
        }

        let end = cal.date(byAdding: .day, value: 7, to: date)!
        return (date, end)
    }

    private func weekdayNumber(for day: String) -> Int {
        switch day.lowercased() {
        case "sunday": return 1
        case "monday": return 2
        case "tuesday": return 3
        case "wednesday": return 4
        case "thursday": return 5
        case "friday": return 6
        case "saturday": return 7
        default: return 2
        }
    }
}

// MARK: - Models

enum PayCycleStatus {
    case upcoming, pendingHold, due, paid
}

struct PayCycleInfo {
    let startDate: Date
    let endDate: Date
    let shifts: [Shift]
    let totalEarned: Double
    let status: PayCycleStatus
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
