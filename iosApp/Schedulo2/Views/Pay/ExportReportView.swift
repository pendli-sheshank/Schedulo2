import SwiftUI

struct ExportReportView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var exportMode = "Calendar Week"
    @State private var exportFormat = "Text"
    @State private var selectedWeekIndex = 0
    @State private var selectedCycleIndex = 0
    @State private var selectedEmployer = "All"
    @State private var showShareSheet = false

    private var employers: [String] {
        let names = Set(dashboardViewModel.shifts.map { $0.company })
        return ["All"] + names.sorted()
    }

    private var availableWeeks: [(weekStart: Int64, label: String)] {
        dashboardViewModel.getAvailableWeeks()
    }

    private var availableCycles: [PayCycleOption] {
        dashboardViewModel.getAvailablePayCycles()
    }

    private var preview: String {
        if exportMode == "Pay Cycle" {
            guard !availableCycles.isEmpty, selectedCycleIndex < availableCycles.count else {
                return "No pay cycles available."
            }
            let cycle = availableCycles[selectedCycleIndex]
            let job = dashboardViewModel.jobs.first { $0.title.caseInsensitiveCompare(cycle.employer) == .orderedSame }
            if exportFormat == "CSV" {
                return dashboardViewModel.generateCycleCsvReport(cycleStart: cycle.cycleStart, cycleEnd: cycle.cycleEnd, employer: cycle.employer, job: job)
            } else {
                return dashboardViewModel.generateCycleReport(cycleStart: cycle.cycleStart, cycleEnd: cycle.cycleEnd, employer: cycle.employer, job: job)
            }
        } else {
            guard !availableWeeks.isEmpty, selectedWeekIndex < availableWeeks.count else {
                return "No weeks available."
            }
            let week = availableWeeks[selectedWeekIndex]
            if exportFormat == "CSV" {
                return dashboardViewModel.generateCsvReport(weekStart: week.weekStart, employer: selectedEmployer)
            } else {
                return dashboardViewModel.generateFormattedReport(weekStartMillis: week.weekStart, employer: selectedEmployer == "All" ? nil : selectedEmployer)
            }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Export Mode
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Export Mode")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        Picker("Mode", selection: $exportMode) {
                            Text("Calendar Week").tag("Calendar Week")
                            Text("Pay Cycle").tag("Pay Cycle")
                        }
                        .pickerStyle(.segmented)
                    }

                    // Export Format
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Format")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)
                        Picker("Format", selection: $exportFormat) {
                            Text("Text").tag("Text")
                            Text("CSV").tag("CSV")
                        }
                        .pickerStyle(.segmented)
                    }

                    // Selection pickers
                    if exportMode == "Pay Cycle" {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Pay Cycle")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                            if availableCycles.isEmpty {
                                Text("No pay cycles found")
                                    .font(.system(size: 14))
                                    .foregroundColor(.secondary)
                            } else {
                                Picker("Cycle", selection: $selectedCycleIndex) {
                                    ForEach(0..<availableCycles.count, id: \.self) { i in
                                        Text(availableCycles[i].label).tag(i)
                                    }
                                }
                                .pickerStyle(.menu)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Week")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                            if availableWeeks.isEmpty {
                                Text("No weeks available")
                                    .font(.system(size: 14))
                                    .foregroundColor(.secondary)
                            } else {
                                Picker("Week", selection: $selectedWeekIndex) {
                                    ForEach(0..<availableWeeks.count, id: \.self) { i in
                                        Text(availableWeeks[i].label).tag(i)
                                    }
                                }
                                .pickerStyle(.menu)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Employer")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.secondary)
                            Picker("Employer", selection: $selectedEmployer) {
                                ForEach(employers, id: \.self) { emp in
                                    Text(emp).tag(emp)
                                }
                            }
                            .pickerStyle(.menu)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    Divider()

                    // Preview
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Preview")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)

                        Text(preview)
                            .font(.system(size: 12, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color(UIColor.secondarySystemBackground))
                            )
                    }

                    // Share button
                    Button(action: { showShareSheet = true }) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Share Report")
                                .font(.system(size: 15, weight: .semibold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.primaryGreen)
                        )
                    }
                }
                .padding(16)
            }
            .navigationTitle("Export Report")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .sheet(isPresented: $showShareSheet) {
                ShareSheet(activityItems: [preview])
            }
        }
    }
}
