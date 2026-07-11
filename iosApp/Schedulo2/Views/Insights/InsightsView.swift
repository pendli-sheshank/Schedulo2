import SwiftUI
import Charts

struct InsightsView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    // View customization state
    @State private var viewMode = "Weekly" // Weekly | Monthly
    @State private var weekCount = 8
    @State private var monthCount = 6
    @State private var employerFilter: String?
    @State private var selectedPeriodIndex: Int?

    private var filteredShifts: [Shift] {
        guard let employer = employerFilter else { return dashboardViewModel.shifts }
        return dashboardViewModel.shifts.filter { $0.company.caseInsensitiveCompare(employer) == .orderedSame }
    }

    private var completedShifts: [Shift] {
        filteredShifts.filter { $0.startDate < Date() }
    }

    private var periodSummary: [PeriodSummaryData] {
        viewMode == "Weekly"
            ? getWeeklyPeriodSummary(weeks: weekCount)
            : getMonthlyPeriodSummary(months: monthCount)
    }

    // Default to the latest period; clamp when the period count shrinks.
    private var effectiveSelectedIndex: Int {
        guard !periodSummary.isEmpty else { return 0 }
        return min(selectedPeriodIndex ?? periodSummary.count - 1, periodSummary.count - 1)
    }

    private var selectedPeriod: PeriodSummaryData? {
        periodSummary.indices.contains(effectiveSelectedIndex) ? periodSummary[effectiveSelectedIndex] : nil
    }

    private var selectedPeriodShifts: [Shift] {
        guard let period = selectedPeriod else { return [] }
        return completedShifts
            .filter { $0.startDate >= period.periodStart && $0.startDate < period.periodEnd }
            .sorted { $0.startTime > $1.startTime }
    }

    private var upcomingShifts: [Shift] {
        let now = Date()
        return filteredShifts.filter { $0.startDate >= now }.sorted { $0.startTime < $1.startTime }
    }

    private var allEmployers: [String] {
        let now = Date()
        let grouped = Dictionary(grouping: dashboardViewModel.shifts.filter { $0.startDate < now }, by: { $0.company })
        return grouped.map { ($0.key, $0.value.reduce(0) { $0 + $1.totalEarned }) }
            .sorted { $0.1 > $1.1 }
            .map(\.0)
    }

    private var earningsByEmployer: [(String, Double)] {
        let grouped = Dictionary(grouping: completedShifts, by: { $0.company })
        return grouped.map { ($0.key, $0.value.reduce(0) { $0 + $1.totalEarned }) }
            .sorted { $0.1 > $1.1 }
    }

    private var totalEarnings: Double { periodSummary.reduce(0) { $0 + $1.earnings } }
    private var totalHours: Double { periodSummary.reduce(0) { $0 + $1.hours } }
    private var avgHourlyRate: Double { totalHours > 0 ? totalEarnings / totalHours : 0 }
    private var bestPeriod: PeriodSummaryData? { periodSummary.max(by: { $0.earnings < $1.earnings }) }
    private var avgPeriodEarnings: Double {
        periodSummary.isEmpty ? 0 : totalEarnings / Double(periodSummary.count)
    }
    private var periodNoun: String { viewMode == "Weekly" ? "week" : "month" }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Weekly / Monthly toggle
                    Picker("View", selection: $viewMode) {
                        Text("Weekly").tag("Weekly")
                        Text("Monthly").tag("Monthly")
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 16)
                    .onChange(of: viewMode) { _ in selectedPeriodIndex = nil }

                    // Period count selector
                    periodCountSelector

                    // Employer filter chips
                    if allEmployers.count > 1 {
                        employerFilterChips
                    }

                    // Summary pills
                    HStack(spacing: 12) {
                        SummaryChip(label: "Total Earned", value: "$\(Int(totalEarnings))", color: .primaryGreen)
                        SummaryChip(label: "Total Hours", value: "\(Int(totalHours))h", color: .accentBlue)
                        SummaryChip(label: "Avg Rate", value: "$\(String(format: "%.2f", avgHourlyRate))/h", color: .accentOrange)
                    }
                    .padding(.horizontal, 16)

                    // Earnings chart (tappable)
                    earningsChart
                        .padding(.horizontal, 16)

                    // Selected-period shift list
                    if selectedPeriod != nil {
                        selectedPeriodCard
                            .padding(.horizontal, 16)
                    }

                    // Upcoming projections
                    projectionsCard
                        .padding(.horizontal, 16)

                    // Earnings by employer
                    earningsByEmployerCard
                        .padding(.horizontal, 16)

                    // Best period & avg per period
                    bestAndAvgCards
                        .padding(.horizontal, 16)

                    Spacer().frame(height: 40)
                }
                .padding(.top, 16)
            }
            .background(Color(UIColor.secondarySystemBackground).opacity(0.3))
            .navigationTitle("Earnings Insights")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    // MARK: - Controls

    private var periodCountSelector: some View {
        HStack(spacing: 8) {
            Text("Show:")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            let options = viewMode == "Weekly" ? [4, 8, 12] : [3, 6, 12]
            ForEach(options, id: \.self) { count in
                let isSelected = (viewMode == "Weekly" ? weekCount : monthCount) == count
                Button(action: {
                    if viewMode == "Weekly" { weekCount = count } else { monthCount = count }
                    selectedPeriodIndex = nil
                }) {
                    Text("\(count) \(viewMode == "Weekly" ? "wks" : "mos")")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(isSelected ? .white : .primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            Capsule().fill(isSelected ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                        )
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
    }

    private var employerFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                employerChip(label: "All employers", isSelected: employerFilter == nil) {
                    employerFilter = nil
                }
                ForEach(allEmployers, id: \.self) { employer in
                    employerChip(label: employer, isSelected: employerFilter == employer) {
                        employerFilter = employerFilter == employer ? nil : employer
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func employerChip(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(isSelected ? .white : .primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    Capsule().fill(isSelected ? Color.accentBlue : Color(UIColor.secondarySystemBackground))
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Earnings Chart

    private var earningsChart: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(viewMode) Earnings")
                .font(.system(size: 16, weight: .bold))
            Text("Tap a bar to see that \(periodNoun)'s shifts")
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            if #available(iOS 16.0, *) {
                Chart(Array(periodSummary.enumerated()), id: \.element.id) { index, period in
                    BarMark(
                        x: .value("Period", period.label),
                        y: .value("Earnings", period.earnings)
                    )
                    .foregroundStyle(index == effectiveSelectedIndex ? Color.accentBlue : Color.primaryGreen.opacity(0.75))
                    .cornerRadius(6)
                    .annotation(position: .top) {
                        if index == effectiveSelectedIndex && period.earnings > 0 {
                            Text("$\(Int(period.earnings))")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.accentBlue)
                        }
                    }
                }
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        if let doubleValue = value.as(Double.self) {
                            AxisValueLabel("$\(Int(doubleValue))")
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks { _ in
                        AxisValueLabel()
                            .font(.system(size: 9))
                    }
                }
                // Bar selection via overlay gesture — chartXSelection is iOS 17+,
                // and the deployment target is iOS 16.
                .chartOverlay { proxy in
                    GeometryReader { geo in
                        Rectangle().fill(Color.clear).contentShape(Rectangle())
                            .onTapGesture { location in
                                let origin = geo[proxy.plotAreaFrame].origin
                                let xPos = location.x - origin.x
                                if let label: String = proxy.value(atX: xPos),
                                   let index = periodSummary.firstIndex(where: { $0.label == label }) {
                                    selectedPeriodIndex = index
                                }
                            }
                    }
                }
                .frame(height: 190)
                .padding(.top, 16)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
        )
    }

    // MARK: - Selected Period

    private var selectedPeriodCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Shifts · \(selectedPeriod?.label ?? "")")
                    .font(.system(size: 16, weight: .bold))
                Spacer()
                Text("$\(String(format: "%.2f", selectedPeriod?.earnings ?? 0))")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primaryGreen)
            }

            if selectedPeriodShifts.isEmpty {
                Text("No completed shifts in this \(periodNoun).")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            } else {
                ForEach(selectedPeriodShifts) { shift in
                    InsightShiftRow(shift: shift, isProjection: false)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
        )
    }

    // MARK: - Projections

    private var projectionsCard: some View {
        let projected = upcomingShifts.reduce(0.0) { $0 + $1.totalEarned }
        let hours = upcomingShifts.reduce(0.0) { $0 + $1.durationHours }

        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "chart.line.uptrend.xyaxis")
                    .font(.system(size: 15))
                    .foregroundColor(.accentBlue)
                Text("Upcoming Projections")
                    .font(.system(size: 16, weight: .bold))
            }

            HStack(spacing: 12) {
                SummaryChip(label: "Projected", value: "$\(Int(projected))", color: .accentBlue)
                SummaryChip(label: "Hours", value: "\(Int(hours))h", color: .accentBlue)
                SummaryChip(label: "Shifts", value: "\(upcomingShifts.count)", color: .accentBlue)
            }

            if upcomingShifts.isEmpty {
                Text("No upcoming shifts scheduled.")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            } else {
                ForEach(upcomingShifts.prefix(5)) { shift in
                    InsightShiftRow(shift: shift, isProjection: true)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.accentBlue.opacity(0.4), lineWidth: 1)
                )
        )
    }

    // MARK: - Earnings by Employer

    private var earningsByEmployerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Earnings by Employer")
                .font(.system(size: 16, weight: .bold))

            let total = earningsByEmployer.reduce(0) { $0 + $1.1 }

            if total > 0 {
                ForEach(earningsByEmployer, id: \.0) { employer, earnings in
                    let fraction = earnings / total

                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(employer)
                                .font(.system(size: 14, weight: .medium))
                            ProgressView(value: fraction)
                                .tint(.primaryGreen)
                                .scaleEffect(y: 2)
                        }

                        VStack(alignment: .trailing) {
                            Text("$\(Int(earnings))")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.primaryGreen)
                            Text("\(Int(fraction * 100))%")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                        .frame(width: 60)
                    }
                    .padding(.vertical, 6)
                }
            } else {
                Text("No earnings data yet.")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
        )
    }

    // MARK: - Best & Avg Cards

    private var bestAndAvgCards: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Best \(periodNoun.capitalized)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                if let best = bestPeriod, best.earnings > 0 {
                    Text("$\(Int(best.earnings))")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.primaryGreen)
                    Text(best.label)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                } else {
                    Text("--")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.primaryGreen)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(UIColor.systemBackground))
            )

            VStack(alignment: .leading, spacing: 4) {
                Text("Avg \(periodNoun.capitalized)ly")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                Text("$\(Int(avgPeriodEarnings))")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.accentBlue)
                Text("per \(periodNoun)")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(UIColor.systemBackground))
            )
        }
    }

    // MARK: - Data Helpers

    private func getWeeklyPeriodSummary(weeks: Int) -> [PeriodSummaryData] {
        let cal = Calendar.current
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM dd"

        // Buckets follow the fiscal pay week: the filtered employer's
        // weeklyCycleStartDay, or the cross-job day when showing all employers.
        let weekStartDay = employerFilter.flatMap { emp in
            dashboardViewModel.jobs.first { $0.title.caseInsensitiveCompare(emp) == .orderedSame }?.weeklyCycleStartDay
        } ?? dashboardViewModel.resolveGlobalWeekStartDay()
        let anchor = dashboardViewModel.startOfWeek(containing: Date(), weekStartDay: weekStartDay)

        return (0..<weeks).reversed().map { offset in
            let weekStart = cal.date(byAdding: .weekOfYear, value: -offset, to: anchor)!
            let weekEnd = cal.date(byAdding: .day, value: 7, to: weekStart)!

            let weekShifts = completedShifts.filter { $0.startDate >= weekStart && $0.startDate < weekEnd }

            return PeriodSummaryData(
                periodStart: weekStart,
                periodEnd: weekEnd,
                label: fmt.string(from: weekStart),
                hours: weekShifts.reduce(0) { $0 + $1.durationHours },
                earnings: weekShifts.reduce(0) { $0 + $1.totalEarned },
                shiftCount: weekShifts.count
            )
        }
    }

    private func getMonthlyPeriodSummary(months: Int) -> [PeriodSummaryData] {
        let cal = Calendar.current
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM"

        var comps = cal.dateComponents([.year, .month], from: Date())
        comps.day = 1
        let thisMonthStart = cal.startOfDay(for: cal.date(from: comps) ?? Date())

        return (0..<months).reversed().map { offset in
            let monthStart = cal.date(byAdding: .month, value: -offset, to: thisMonthStart)!
            let monthEnd = cal.date(byAdding: .month, value: 1, to: monthStart)!

            let monthShifts = completedShifts.filter { $0.startDate >= monthStart && $0.startDate < monthEnd }

            return PeriodSummaryData(
                periodStart: monthStart,
                periodEnd: monthEnd,
                label: fmt.string(from: monthStart),
                hours: monthShifts.reduce(0) { $0 + $1.durationHours },
                earnings: monthShifts.reduce(0) { $0 + $1.totalEarned },
                shiftCount: monthShifts.count
            )
        }
    }
}

// MARK: - Supporting Types

struct PeriodSummaryData: Identifiable {
    let id = UUID()
    let periodStart: Date
    let periodEnd: Date
    let label: String
    let hours: Double
    let earnings: Double
    let shiftCount: Int
}

private struct InsightShiftRow: View {
    let shift: Shift
    let isProjection: Bool

    private static let dateFormat: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "EEE, MMM dd · h:mm a"
        return f
    }()

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 9)
                    .fill((shift.isGig ? Color.accentOrange : Color.accentBlue).opacity(0.1))
                    .frame(width: 34, height: 34)
                Image(systemName: shift.isGig ? "car.fill" : "building.2.fill")
                    .font(.system(size: 14))
                    .foregroundColor(shift.isGig ? .accentOrange : .accentBlue)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(shift.company)
                    .font(.system(size: 13, weight: .semibold))
                Text(Self.dateFormat.string(from: shift.startDate))
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text("\(isProjection ? "Est. " : "")$\(shift.totalEarned, specifier: "%.2f")")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(isProjection ? .accentBlue : .primaryGreen)
                Text(String(format: "%.1fh", shift.durationHours))
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct SummaryChip: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(color)
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(color.opacity(0.7))
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(color.opacity(0.1))
        )
    }
}
