import SwiftUI

struct JobsView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    @State private var showDialog = false
    @State private var editingJobId: String?
    @State private var title = ""
    @State private var isGigWork = false
    @State private var rateStr = "15.0"
    @State private var goalHoursStr = "20.0"
    @State private var goalType = "Hours"
    @State private var weeklyCycleStartDay = "Monday"
    @State private var overtimeThresholdStr = "40.0"
    @State private var overtimeMultiplierStr = "1.5"
    @State private var jobToDelete: Job?

    private let daysOfWeek = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Employers & Jobs")
                    .font(.system(size: 24, weight: .bold))
                Spacer()
                Button(action: { resetForm(); showDialog = true }) {
                    Text("+ Add Job")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.primaryGreen)
                        )
                }
            }
            .padding(16)

            if dashboardViewModel.jobs.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "briefcase")
                        .font(.system(size: 40))
                        .foregroundColor(.secondary.opacity(0.4))
                    Text("No employers added yet.")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(dashboardViewModel.jobs, id: \.id) { job in
                            jobCard(job)
                        }
                        Spacer().frame(height: 80)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
            }
        }
        .sheet(isPresented: $showDialog) {
            jobFormSheet
        }
        .alert("Delete Job", isPresented: Binding(
            get: { jobToDelete != nil },
            set: { if !$0 { jobToDelete = nil } }
        )) {
            Button("Delete", role: .destructive) {
                if let job = jobToDelete {
                    dashboardViewModel.deleteJob(jobId: job.id)
                    jobToDelete = nil
                }
            }
            Button("Cancel", role: .cancel) { jobToDelete = nil }
        } message: {
            Text("Are you sure you want to delete this employer?")
        }
    }

    // MARK: - Job Card

    private func jobCard(_ job: Job) -> some View {
        let cycleStart = job.getStartOfCurrentCycle()
        let cycleEnd = Calendar.current.date(byAdding: .day, value: 7, to: cycleStart)!
        let now = Date()
        let jobShifts = dashboardViewModel.shifts.filter {
            $0.company.lowercased() == job.title.lowercased() &&
            $0.startTime >= cycleStart &&
            $0.startTime < cycleEnd &&
            $0.startTime < now
        }

        return Button(action: { populateForm(job); showDialog = true }) {
            VStack(alignment: .leading, spacing: 0) {
                // Title row
                HStack {
                    Text(job.title)
                        .font(.system(size: 20, weight: .bold))
                    Spacer()
                    HStack(spacing: 8) {
                        Text(job.isGigWork ? "Gig" : "Hourly")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(job.isGigWork ? .accentOrange : .accentBlue)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill((job.isGigWork ? Color.accentOrange : Color.accentBlue).opacity(0.12))
                            )
                        Button(action: { jobToDelete = job }) {
                            Image(systemName: "trash")
                                .font(.system(size: 14))
                                .foregroundColor(.red)
                        }
                    }
                }

                Spacer().frame(height: 12)

                // Stats
                HStack {
                    VStack(alignment: .leading) {
                        Text("SHIFTS").font(.system(size: 10, weight: .semibold)).foregroundColor(.secondary)
                        Text("\(jobShifts.count)").font(.system(size: 16, weight: .bold))
                    }
                    Spacer()
                    VStack(alignment: .leading) {
                        Text("HOURS").font(.system(size: 10, weight: .semibold)).foregroundColor(.secondary)
                        Text("\(String(format: "%.1f", jobShifts.reduce(0) { $0 + $1.durationHours })) hrs")
                            .font(.system(size: 16, weight: .bold))
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("EARNED").font(.system(size: 10, weight: .semibold)).foregroundColor(.secondary)
                        Text("$\(jobShifts.reduce(0) { $0 + $1.totalEarned }, specifier: "%.2f")")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.primaryGreen)
                    }
                }

                Spacer().frame(height: 12)
                Divider()
                Spacer().frame(height: 8)

                // Details
                HStack {
                    Text("Weekly Target: \(job.goalType == "Hours" ? "\(String(format: "%.0f", job.goalHours)) hrs" : "$\(String(format: "%.0f", job.goalHours))")")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Spacer()
                    if !job.isGigWork {
                        Text("Base Rate: $\(job.defaultHourlyRate, specifier: "%.2f")/hr")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                }

                if !job.isGigWork {
                    let cycleStartDay = job.weeklyCycleStartDay ?? "Monday"
                    let endDay = dayBefore(cycleStartDay)
                    Text("Pay Cycle: \(cycleStartDay) to \(endDay)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                        .padding(.top, 6)
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
        .buttonStyle(.plain)
    }

    // MARK: - Form Sheet

    private var jobFormSheet: some View {
        NavigationStack {
            Form {
                Section("Job Details") {
                    TextField("Job / Employer Title", text: $title)

                    Toggle("Is Gig Work (e.g. DoorDash)?", isOn: $isGigWork)
                }

                if !isGigWork {
                    Section("Pay Settings") {
                        HStack {
                            Text("Hourly Rate ($)")
                            Spacer()
                            TextField("15.0", text: $rateStr)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 100)
                        }

                        Picker("Weekly Cycle Start Day", selection: $weeklyCycleStartDay) {
                            ForEach(daysOfWeek, id: \.self) { day in
                                Text(day).tag(day)
                            }
                        }

                        HStack {
                            Text("Overtime After (hrs/week)")
                            Spacer()
                            TextField("40.0", text: $overtimeThresholdStr)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 100)
                        }

                        HStack {
                            Text("Overtime Rate Multiplier")
                            Spacer()
                            TextField("1.5", text: $overtimeMultiplierStr)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 100)
                        }
                    }
                }

                Section("Weekly Target") {
                    Picker("Target Type", selection: $goalType) {
                        Text("Hours").tag("Hours")
                        Text("Earnings").tag("Earnings")
                    }
                    .pickerStyle(.segmented)

                    HStack {
                        Text(goalType == "Hours" ? "Weekly Hours Target" : "Weekly Earnings Target ($)")
                        Spacer()
                        TextField("20.0", text: $goalHoursStr)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 100)
                    }
                }
            }
            .navigationTitle(editingJobId == nil ? "Add Employer Job" : "Edit Employer Job")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showDialog = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveJob()
                        showDialog = false
                    }
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primaryGreen)
                }
            }
        }
    }

    // MARK: - Helpers

    private func resetForm() {
        editingJobId = nil
        title = ""
        isGigWork = false
        rateStr = "15.0"
        goalHoursStr = "20.0"
        goalType = "Hours"
        weeklyCycleStartDay = "Monday"
        overtimeThresholdStr = "40.0"
        overtimeMultiplierStr = "1.5"
    }

    private func populateForm(_ job: Job) {
        editingJobId = job.id
        title = job.title
        isGigWork = job.isGigWork
        rateStr = "\(job.defaultHourlyRate)"
        goalHoursStr = "\(job.goalHours)"
        goalType = job.goalType
        weeklyCycleStartDay = job.weeklyCycleStartDay ?? "Monday"
        overtimeThresholdStr = "\(job.overtimeThresholdHours)"
        overtimeMultiplierStr = "\(job.overtimeMultiplier)"
    }

    private func saveJob() {
        let finalRate = isGigWork ? 0.0 : (Double(rateStr) ?? 15.0)
        let finalGoal = Double(goalHoursStr) ?? 20.0
        let finalOT = Double(overtimeThresholdStr) ?? 40.0
        let finalOTM = Double(overtimeMultiplierStr) ?? 1.5

        if let id = editingJobId {
            dashboardViewModel.updateJob(
                jobId: id, title: title, isGigWork: isGigWork,
                defaultHourlyRate: finalRate, goalHours: finalGoal,
                goalType: goalType, weeklyCycleStartDay: weeklyCycleStartDay,
                overtimeThresholdHours: finalOT, overtimeMultiplier: finalOTM
            )
        } else {
            dashboardViewModel.addJob(
                title: title, isGigWork: isGigWork,
                defaultHourlyRate: finalRate, goalHours: finalGoal,
                goalType: goalType, weeklyCycleStartDay: weeklyCycleStartDay,
                overtimeThresholdHours: finalOT, overtimeMultiplier: finalOTM
            )
        }
    }

    private func dayBefore(_ day: String) -> String {
        switch day.lowercased() {
        case "monday": return "Sunday"
        case "tuesday": return "Monday"
        case "wednesday": return "Tuesday"
        case "thursday": return "Wednesday"
        case "friday": return "Thursday"
        case "saturday": return "Friday"
        case "sunday": return "Saturday"
        default: return "Sunday"
        }
    }
}
