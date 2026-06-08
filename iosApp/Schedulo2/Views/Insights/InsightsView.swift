import SwiftUI
import Charts

struct InsightsView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    private var completedShifts: [Shift] {
        dashboardViewModel.shifts.filter { $0.startTime < Date() }
    }

    private var totalEarnings: Double {
        completedShifts.reduce(0) { $0 + $1.totalEarned }
    }

    private var totalHours: Double {
        completedShifts.reduce(0) { $0 + $1.durationHours }
    }

    private var avgHourlyRate: Double {
        totalHours > 0 ? totalEarnings / totalHours : 0
    }

    private var weeklySummary: [WeekSummaryData] {
        getWeeklyEarningsSummary(weeks: 8)
    }

    private var earningsByEmployer: [(String, Double)] {
        let grouped = Dictionary(grouping: completedShifts, by: { $0.company })
        return grouped.map { ($0.key, $0.value.reduce(0) { $0 + $1.totalEarned }) }
            .sorted { $0.1 > $1.1 }
    }

    private var bestWeek: WeekSummaryData? {
        weeklySummary.max(by: { $0.earnings < $1.earnings })
    }

    private var avgWeeklyEarnings: Double {
        weeklySummary.isEmpty ? 0 : weeklySummary.reduce(0) { $0 + $1.earnings } / Double(weeklySummary.count)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Summary pills
                    HStack(spacing: 12) {
                        SummaryChip(label: "Total Earned", value: "$\(Int(totalEarnings))", color: .primaryGreen)
                        SummaryChip(label: "Total Hours", value: "\(Int(totalHours))h", color: .accentBlue)
                        SummaryChip(label: "Avg Rate", value: "$\(String(format: "%.2f", avgHourlyRate))/h", color: .accentOrange)
                    }
                    .padding(.horizontal, 16)

                    // Weekly earnings chart
                    weeklyEarningsChart
                        .padding(.horizontal, 16)

                    // Earnings by employer
                    earningsByEmployerCard
                        .padding(.horizontal, 16)

                    // Best week & avg weekly
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Best Week")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                            if let best = bestWeek, best.earnings > 0 {
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
                            Text("Avg Weekly")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                            Text("$\(Int(avgWeeklyEarnings))")
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.accentBlue)
                            Text("per week")
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
                    .padding(.horizontal, 16)

                    Spacer().frame(height: 40)
                }
                .padding(.top, 16)
            }
            .background(Color(UIColor.secondarySystemBackground).opacity(0.3))
            .navigationTitle("Insights")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    // MARK: - Weekly Earnings Chart

    private var weeklyEarningsChart: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Weekly Earnings")
                .font(.system(size: 16, weight: .bold))
            Text("Last 8 weeks")
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            if #available(iOS 16.0, *) {
                Chart(weeklySummary) { week in
                    BarMark(
                        x: .value("Week", week.label),
                        y: .value("Earnings", week.earnings)
                    )
                    .foregroundStyle(Color.primaryGreen)
                    .cornerRadius(6)
                }
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        if let doubleValue = value.as(Double.self) {
                            AxisValueLabel("$\(Int(doubleValue))")
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks { value in
                        AxisValueLabel()
                            .font(.system(size: 9))
                    }
                }
                .frame(height: 180)
                .padding(.top, 16)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
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

    // MARK: - Data Helpers

    private func getWeeklyEarningsSummary(weeks: Int) -> [WeekSummaryData] {
        let cal = Calendar.current
        let fmt = DateFormatter()
        fmt.dateFormat = "MMM dd"

        return (0..<weeks).reversed().map { offset in
            var comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: Date())
            comps.weekday = 2
            let thisMonday = cal.date(from: comps) ?? Date()
            let weekStart = cal.date(byAdding: .weekOfYear, value: -offset, to: thisMonday)!
            let weekEnd = cal.date(byAdding: .day, value: 7, to: weekStart)!

            let weekShifts = completedShifts.filter { $0.startTime >= weekStart && $0.startTime < weekEnd }

            return WeekSummaryData(
                weekStart: weekStart,
                label: fmt.string(from: weekStart),
                hours: weekShifts.reduce(0) { $0 + $1.durationHours },
                earnings: weekShifts.reduce(0) { $0 + $1.totalEarned },
                shiftCount: weekShifts.count
            )
        }
    }
}

// MARK: - Supporting Types

struct WeekSummaryData: Identifiable {
    let id = UUID()
    let weekStart: Date
    let label: String
    let hours: Double
    let earnings: Double
    let shiftCount: Int
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
