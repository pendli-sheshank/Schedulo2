import Foundation
import Combine

// MARK: - Supporting Types

struct WeekSummary: Identifiable {
    let id = UUID()
    let weekStart: Int64
    let label: String
    let hours: Double
    let earnings: Double
    let shiftCount: Int
}

struct PayCycleOption: Identifiable {
    let id = UUID()
    let cycleStart: Int64
    let cycleEnd: Int64
    let employer: String
    let label: String
    let shiftCount: Int
    let isCurrent: Bool
}

struct WeekDayEntry {
    let dayOffset: Int
    let startH: Int
    let startM: Int
    let endH: Int
    let endM: Int
}

// MARK: - DashboardViewModel

@MainActor
final class DashboardViewModel: ObservableObject {
    // MARK: Published state
    @Published var shifts: [Shift] = []
    @Published var jobs: [Job] = []
    @Published var userName: String = ""
    @Published var memberSince: String = ""
    @Published var isLoading: Bool = false
    @Published var syncError: String?
    @Published var themeMode: String = "system"
    @Published var remindersEnabled: Bool = true
    @Published var defaultReminderMinutes: Int = 30
    @Published var defaultCompany: String = ""
    @Published var defaultRate: Double = 0.0

    private let service = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()
    private var loadedForUserId: String?

    init() {
        // Bind Combine subjects to @Published
        service.shiftsSubject
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.shifts = $0 }
            .store(in: &cancellables)

        service.jobsSubject
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.jobs = $0 }
            .store(in: &cancellables)

        service.profileSubject
            .receive(on: DispatchQueue.main)
            .sink { [weak self] profile in
                guard let self = self, let profile = profile else { return }
                self.userName = profile.fullName
                if profile.createdAt > 0 {
                    let date = Date(timeIntervalSince1970: Double(profile.createdAt) / 1000.0)
                    let fmt = DateFormatter()
                    fmt.dateFormat = "MMMM yyyy"
                    self.memberSince = fmt.string(from: date)
                }
            }
            .store(in: &cancellables)

        service.settingsSubject
            .receive(on: DispatchQueue.main)
            .sink { [weak self] settings in
                guard let self = self, let settings = settings else { return }
                self.defaultCompany = settings.defaultCompany
                self.defaultRate = settings.defaultRate
                self.themeMode = settings.themeMode
                self.remindersEnabled = settings.remindersEnabled
                self.defaultReminderMinutes = settings.defaultReminderMinutes
            }
            .store(in: &cancellables)
    }

    // MARK: - Data Loading

    func loadShifts() {
        guard let uid = service.currentUserId else {
            isLoading = false
            syncError = "Please sign in to access your data."
            return
        }
        guard loadedForUserId != uid else { return }
        loadedForUserId = uid
        isLoading = true
        syncError = nil
        service.startAllListeners()
        // Loading will complete when Firestore listeners fire
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.isLoading = false
        }
    }

    func loadJobs() {
        service.listenToJobs()
    }

    func loadSettings() {
        service.listenToSettings()
        service.listenToProfile()
    }

    func refreshData() {
        loadedForUserId = nil
        loadShifts()
    }

    func clearSyncError() {
        syncError = nil
    }

    func reset() {
        loadedForUserId = nil
        service.removeAllListeners()
        shifts = []
        jobs = []
        defaultCompany = ""
        defaultRate = 0.0
        userName = ""
        memberSince = ""
        isLoading = false
        syncError = nil
        themeMode = "system"
    }

    // MARK: - Shift CRUD

    func addShift(company: String, startTime: Int64, endTime: Int64, hourlyRate: Double, isGig: Bool, customEarned: Double, reminderBeforeMinutes: Int, notes: String = "") {
        guard let uid = service.currentUserId else {
            syncError = "Please sign in to save shifts."
            return
        }
        let shift = Shift(
            id: UUID().uuidString,
            userId: uid,
            company: company,
            role: "",
            startTime: startTime,
            endTime: endTime,
            hourlyRate: hourlyRate,
            isGig: isGig,
            customEarned: customEarned,
            reminderBeforeMinutes: reminderBeforeMinutes,
            isPaid: isGig,
            notes: notes
        )
        shifts = (shifts + [shift]).sorted { $0.startTime > $1.startTime }
        service.addShift(shift)
    }

    func updateShift(shiftId: String, company: String, startTime: Int64, endTime: Int64, hourlyRate: Double, isGig: Bool, customEarned: Double, reminderBeforeMinutes: Int, notes: String = "") {
        guard let existing = shifts.first(where: { $0.id == shiftId }) else { return }
        var updated = existing
        updated.company = company
        updated.role = ""
        updated.startTime = startTime
        updated.endTime = endTime
        updated.hourlyRate = hourlyRate
        updated.isGig = isGig
        updated.customEarned = customEarned
        updated.reminderBeforeMinutes = reminderBeforeMinutes
        updated.isPaid = isGig ? true : existing.isPaid
        updated.notes = notes

        shifts = shifts.map { $0.id == shiftId ? updated : $0 }.sorted { $0.startTime > $1.startTime }
        service.updateShift(updated)
    }

    func deleteShift(shiftId: String) {
        shifts = shifts.filter { $0.id != shiftId }
        service.deleteShift(shiftId)
    }

    func toggleShiftPaidStatus(shiftId: String, isPaid: Bool) {
        shifts = shifts.map { $0.id == shiftId ? {
            var s = $0; s.isPaid = isPaid; return s
        }() : $0 }
        service.toggleShiftPaid(shiftId, isPaid: isPaid, allShifts: shifts)
    }

    func markCycleAsPaid(shiftIds: [String], isPaid: Bool) {
        let idSet = Set(shiftIds)
        shifts = shifts.map { idSet.contains($0.id) ? { var s = $0; s.isPaid = isPaid; return s }() : $0 }
        service.markCycleAsPaid(shiftIds: shiftIds, isPaid: isPaid, allShifts: shifts)
    }

    // MARK: - Job CRUD

    func addJob(title: String, isGigWork: Bool, defaultHourlyRate: Double, goalHours: Double, goalType: String, weeklyCycleStartDay: String = "Monday", overtimeThresholdHours: Double = 40.0, overtimeMultiplier: Double = 1.5) {
        guard let uid = service.currentUserId else {
            syncError = "Please sign in to add employers."
            return
        }
        let job = Job(
            id: UUID().uuidString,
            userId: uid,
            title: title,
            isGigWork: isGigWork,
            defaultHourlyRate: defaultHourlyRate,
            goalHours: goalHours,
            goalType: goalType,
            weeklyCycleStartDay: weeklyCycleStartDay,
            overtimeThresholdHours: overtimeThresholdHours,
            overtimeMultiplier: overtimeMultiplier
        )
        jobs.append(job)
        service.addJob(job)
    }

    func updateJob(jobId: String, title: String, isGigWork: Bool, defaultHourlyRate: Double, goalHours: Double, goalType: String, weeklyCycleStartDay: String, overtimeThresholdHours: Double = 40.0, overtimeMultiplier: Double = 1.5) {
        guard let existing = jobs.first(where: { $0.id == jobId }) else { return }
        var updated = existing
        updated.title = title
        updated.isGigWork = isGigWork
        updated.defaultHourlyRate = defaultHourlyRate
        updated.goalHours = goalHours
        updated.goalType = goalType
        updated.weeklyCycleStartDay = weeklyCycleStartDay
        updated.overtimeThresholdHours = overtimeThresholdHours
        updated.overtimeMultiplier = overtimeMultiplier

        jobs = jobs.map { $0.id == jobId ? updated : $0 }
        service.updateJob(updated)
    }

    func deleteJob(jobId: String) {
        jobs = jobs.filter { $0.id != jobId }
        service.deleteJob(jobId)
    }

    // MARK: - Conflict Detection

    func detectConflicts(startTime: Int64, endTime: Int64, excludeShiftId: String? = nil) -> [Shift] {
        shifts.filter { shift in
            shift.id != excludeShiftId &&
            shift.startTime < endTime && shift.endTime > startTime
        }
    }

    // MARK: - Overtime Calculation

    func calculateEarningsWithOvertime(shifts: [Shift], job: Job) -> (regular: Double, overtime: Double) {
        if job.isGigWork {
            return (shifts.reduce(0) { $0 + $1.totalEarned }, 0.0)
        }
        let totalHours = shifts.reduce(0.0) { $0 + $1.durationHours }
        let threshold = job.overtimeThresholdHours
        let rate = job.defaultHourlyRate
        let multiplier = job.overtimeMultiplier

        if totalHours <= threshold {
            return (totalHours * rate, 0.0)
        } else {
            let regularEarnings = threshold * rate
            let overtimeEarnings = (totalHours - threshold) * rate * multiplier
            return (regularEarnings, overtimeEarnings)
        }
    }

    // MARK: - Report Generation

    func generateFormattedReport(weekStartMillis: Int64, employer: String?) -> String {
        let weekEndMillis = weekStartMillis + 7 * 24 * 60 * 60 * 1000
        let filtered = shifts.filter { shift in
            shift.startTime >= weekStartMillis && shift.startTime < weekEndMillis &&
            (employer == nil || employer == "All" || shift.company.caseInsensitiveCompare(employer!) == .orderedSame)
        }.sorted { $0.startTime < $1.startTime }

        let weekFmt = DateFormatter(); weekFmt.dateFormat = "MMM dd"
        let dayFmt = DateFormatter(); dayFmt.dateFormat = "EEEE (M/dd)"
        let timeFmt = DateFormatter(); timeFmt.dateFormat = "h:mm a"

        var sb = ""
        let startDate = Date(timeIntervalSince1970: Double(weekStartMillis) / 1000)
        let endDate = Date(timeIntervalSince1970: Double(weekEndMillis - 1000) / 1000)
        sb += "Schedule: \(weekFmt.string(from: startDate)) – \(weekFmt.string(from: endDate))\n"
        if let emp = employer, emp != "All" { sb += "Employer: \(emp)\n" }
        sb += "\n"

        var totalHours = 0.0
        var totalEarnings = 0.0
        for shift in filtered {
            let day = dayFmt.string(from: shift.startDate)
            let start = timeFmt.string(from: shift.startDate)
            let end = timeFmt.string(from: shift.endDate)
            let hrs = shift.durationHours
            totalHours += hrs
            totalEarnings += shift.totalEarned
            sb += "\(day): \(start) – \(end) (\(String(format: "%.1f", hrs)) hrs) $\(String(format: "%.2f", shift.totalEarned))\n"
            if !shift.notes.trimmingCharacters(in: .whitespaces).isEmpty {
                sb += "  Notes: \(shift.notes)\n"
            }
        }
        sb += "\nTotal \(String(format: "%.1f", totalHours)) hours · $\(String(format: "%.2f", totalEarnings))\n"
        if filtered.contains(where: { $0.isPaid }) {
            let paidCount = filtered.filter { $0.isPaid }.count
            sb += "Paid: \(paidCount)/\(filtered.count) shifts\n"
        }
        return sb
    }

    func generateCycleReport(cycleStart: Int64, cycleEnd: Int64, employer: String, job: Job?) -> String {
        let filtered = shifts.filter { shift in
            shift.startTime >= cycleStart && shift.startTime < cycleEnd &&
            shift.company.caseInsensitiveCompare(employer) == .orderedSame
        }.sorted { $0.startTime < $1.startTime }

        let weekFmt = DateFormatter(); weekFmt.dateFormat = "MMM dd, yyyy"
        let dayFmt = DateFormatter(); dayFmt.dateFormat = "EEEE (M/dd)"
        let timeFmt = DateFormatter(); timeFmt.dateFormat = "h:mm a"

        var sb = "TIMESHEET REPORT\n"
        sb += "Employer: \(employer)\n"
        let startDate = Date(timeIntervalSince1970: Double(cycleStart) / 1000)
        let endDate = Date(timeIntervalSince1970: Double(cycleEnd - 1000) / 1000)
        sb += "Pay Period: \(weekFmt.string(from: startDate)) – \(weekFmt.string(from: endDate))\n"
        if let j = job { sb += "Cycle Start Day: \(j.weeklyCycleStartDay ?? "Monday")\n" }
        sb += String(repeating: "\u{2500}", count: 40) + "\n"

        var totalHours = 0.0
        var totalEarnings = 0.0
        for shift in filtered {
            let day = dayFmt.string(from: shift.startDate)
            let start = timeFmt.string(from: shift.startDate)
            let end = timeFmt.string(from: shift.endDate)
            let hrs = shift.durationHours
            totalHours += hrs
            totalEarnings += shift.totalEarned
            let status = shift.isPaid ? " [PAID]" : ""
            sb += "\(day): \(start) – \(end) (\(String(format: "%.1f", hrs)) hrs)\(status)\n"
            if !shift.notes.trimmingCharacters(in: .whitespaces).isEmpty {
                sb += "  Notes: \(shift.notes)\n"
            }
        }
        sb += String(repeating: "\u{2500}", count: 40) + "\n"

        if let j = job, !j.isGigWork {
            let (regular, overtime) = calculateEarningsWithOvertime(shifts: filtered, job: j)
            let regularHours = min(totalHours, j.overtimeThresholdHours)
            let overtimeHours = max(totalHours - regularHours, 0.0)
            sb += "Regular: \(String(format: "%.1f", regularHours)) hrs × $\(String(format: "%.2f", j.defaultHourlyRate)) = $\(String(format: "%.2f", regular))\n"
            if overtimeHours > 0 {
                sb += "Overtime: \(String(format: "%.1f", overtimeHours)) hrs × $\(String(format: "%.2f", j.defaultHourlyRate * j.overtimeMultiplier)) = $\(String(format: "%.2f", overtime))\n"
            }
            sb += "TOTAL: \(String(format: "%.1f", totalHours)) hours · $\(String(format: "%.2f", regular + overtime))\n"
        } else {
            sb += "TOTAL: \(String(format: "%.1f", totalHours)) hours · $\(String(format: "%.2f", totalEarnings))\n"
        }

        let paidCount = filtered.filter { $0.isPaid }.count
        sb += "Payment Status: \(paidCount)/\(filtered.count) shifts paid\n"
        return sb
    }

    func generateCsvReport(weekStart: Int64, employer: String) -> String {
        let weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000
        let dateFmt = DateFormatter(); dateFmt.dateFormat = "yyyy-MM-dd"
        let timeFmt = DateFormatter(); timeFmt.dateFormat = "HH:mm"
        let filtered = shifts.filter { shift in
            shift.startTime >= weekStart && shift.startTime < weekEnd &&
            (employer == "All" || shift.company.caseInsensitiveCompare(employer) == .orderedSame)
        }.sorted { $0.startTime < $1.startTime }

        var sb = "Date,Company,Start,End,Hours,Rate,Earned,Gig,Paid,Notes\n"
        for s in filtered {
            let notes = s.notes.replacingOccurrences(of: ",", with: ";").replacingOccurrences(of: "\n", with: " ")
            sb += "\(dateFmt.string(from: s.startDate)),\(s.company),\(timeFmt.string(from: s.startDate)),\(timeFmt.string(from: s.endDate)),\(String(format: "%.2f", s.durationHours)),\(s.hourlyRate),\(String(format: "%.2f", s.totalEarned)),\(s.isGig),\(s.isPaid),\(notes)\n"
        }
        return sb
    }

    func generateCycleCsvReport(cycleStart: Int64, cycleEnd: Int64, employer: String, job: Job?) -> String {
        let dateFmt = DateFormatter(); dateFmt.dateFormat = "yyyy-MM-dd"
        let timeFmt = DateFormatter(); timeFmt.dateFormat = "HH:mm"
        let filtered = shifts.filter { shift in
            shift.startTime >= cycleStart && shift.startTime < cycleEnd &&
            shift.company.caseInsensitiveCompare(employer) == .orderedSame
        }.sorted { $0.startTime < $1.startTime }

        var sb = "Date,Company,Start,End,Hours,Rate,Earned,Gig,Paid,Notes\n"
        for s in filtered {
            let notes = s.notes.replacingOccurrences(of: ",", with: ";").replacingOccurrences(of: "\n", with: " ")
            sb += "\(dateFmt.string(from: s.startDate)),\(s.company),\(timeFmt.string(from: s.startDate)),\(timeFmt.string(from: s.endDate)),\(String(format: "%.2f", s.durationHours)),\(s.hourlyRate),\(String(format: "%.2f", s.totalEarned)),\(s.isGig),\(s.isPaid),\(notes)\n"
        }
        return sb
    }

    // MARK: - Available Weeks

    func getAvailableWeeks() -> [(weekStart: Int64, label: String)] {
        let calendar = Calendar.current
        let now = Date()
        let weekFmt = DateFormatter(); weekFmt.dateFormat = "MMM dd"
        var weeks = Set<Int64>()

        for offset in -8...4 {
            var comps = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)
            comps.weekday = 2 // Monday
            guard var monday = calendar.date(from: comps) else { continue }
            monday = calendar.date(byAdding: .weekOfYear, value: offset, to: monday)!
            monday = calendar.startOfDay(for: monday)
            weeks.insert(Int64(monday.timeIntervalSince1970 * 1000))
        }

        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        return weeks.sorted().reversed().map { start in
            let end = start + 7 * 24 * 60 * 60 * 1000
            let startDate = Date(timeIntervalSince1970: Double(start) / 1000)
            let endDate = Date(timeIntervalSince1970: Double(end - 1000) / 1000)
            let isCurrent = nowMs >= start && nowMs < end
            let label = "\(weekFmt.string(from: startDate)) – \(weekFmt.string(from: endDate))" + (isCurrent ? " (Current)" : "")
            return (start, label)
        }
    }

    // MARK: - Available Pay Cycles

    func getAvailablePayCycles() -> [PayCycleOption] {
        let now = Date()
        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        let weekFmt = DateFormatter(); weekFmt.dateFormat = "MMM dd"

        var cycles: [PayCycleOption] = []

        for job in jobs {
            let jobShifts = shifts.filter { $0.company.caseInsensitiveCompare(job.title) == .orderedSame && !$0.isGig }
            if jobShifts.isEmpty { continue }

            var seenCycles = Set<Int64>()
            for shift in jobShifts {
                let (start, end) = getCycleStartAndEnd(forShiftStartTime: shift.startTime, jobs: jobs)
                if seenCycles.insert(start).inserted {
                    let shiftsInCycle = jobShifts.filter { $0.startTime >= start && $0.startTime < end }.count
                    let isCurrent = nowMs >= start && nowMs < end
                    let startDate = Date(timeIntervalSince1970: Double(start) / 1000)
                    let endDate = Date(timeIntervalSince1970: Double(end - 1000) / 1000)
                    let label = "\(job.title): \(weekFmt.string(from: startDate)) – \(weekFmt.string(from: endDate))" + (isCurrent ? " (Current)" : "")
                    cycles.append(PayCycleOption(cycleStart: start, cycleEnd: end, employer: job.title, label: label, shiftCount: shiftsInCycle, isCurrent: isCurrent))
                }
            }

            // Add current cycle if not seen
            let currentCycleStart = job.getStartOfCurrentCycle(targetDate: now)
            let currentCycleEnd = currentCycleStart + 7 * 24 * 60 * 60 * 1000
            if seenCycles.insert(currentCycleStart).inserted {
                let startDate = Date(timeIntervalSince1970: Double(currentCycleStart) / 1000)
                let endDate = Date(timeIntervalSince1970: Double(currentCycleEnd - 1000) / 1000)
                let label = "\(job.title): \(weekFmt.string(from: startDate)) – \(weekFmt.string(from: endDate)) (Current)"
                cycles.append(PayCycleOption(cycleStart: currentCycleStart, cycleEnd: currentCycleEnd, employer: job.title, label: label, shiftCount: 0, isCurrent: true))
            }
        }

        return cycles.sorted { lhs, rhs in
            if lhs.cycleStart != rhs.cycleStart { return lhs.cycleStart > rhs.cycleStart }
            return lhs.employer < rhs.employer
        }
    }

    private func getCycleStartAndEnd(forShiftStartTime startTime: Int64, jobs: [Job]) -> (Int64, Int64) {
        let shiftDate = Date(timeIntervalSince1970: Double(startTime) / 1000)
        let calendar = Calendar.current

        // Find matching job by company name (already matched externally, but look up cycle start day)
        let job = jobs.first { $0.title.caseInsensitiveCompare(
            shifts.first(where: { $0.startTime == startTime })?.company ?? "") == .orderedSame }

        let startDayName = job?.weeklyCycleStartDay ?? "Monday"
        let targetWeekday = dayOfWeekNumber(from: startDayName)

        var date = calendar.startOfDay(for: shiftDate)
        while calendar.component(.weekday, from: date) != targetWeekday {
            date = calendar.date(byAdding: .day, value: -1, to: date)!
        }

        let cycleStart = Int64(date.timeIntervalSince1970 * 1000)
        return (cycleStart, cycleStart + 7 * 24 * 60 * 60 * 1000)
    }

    private func dayOfWeekNumber(from name: String) -> Int {
        switch name.lowercased() {
        case "sunday":    return 1
        case "monday":    return 2
        case "tuesday":   return 3
        case "wednesday": return 4
        case "thursday":  return 5
        case "friday":    return 6
        case "saturday":  return 7
        default:          return 2
        }
    }

    // MARK: - Insights

    func getWeeklyEarningsSummary(weeks: Int = 8) -> [WeekSummary] {
        let calendar = Calendar.current
        let now = Date()
        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        let completedShifts = shifts.filter { $0.startTime < nowMs }
        let weekFmt = DateFormatter(); weekFmt.dateFormat = "MMM dd"

        return (0..<weeks).map { offset in
            var comps = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)
            comps.weekday = 2 // Monday
            guard var monday = calendar.date(from: comps) else {
                return WeekSummary(weekStart: 0, label: "", hours: 0, earnings: 0, shiftCount: 0)
            }
            monday = calendar.date(byAdding: .weekOfYear, value: -offset, to: monday)!
            monday = calendar.startOfDay(for: monday)
            let weekStart = Int64(monday.timeIntervalSince1970 * 1000)
            let weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000

            let weekShifts = completedShifts.filter { $0.startTime >= weekStart && $0.startTime < weekEnd }
            return WeekSummary(
                weekStart: weekStart,
                label: weekFmt.string(from: monday),
                hours: weekShifts.reduce(0) { $0 + $1.durationHours },
                earnings: weekShifts.reduce(0) { $0 + $1.totalEarned },
                shiftCount: weekShifts.count
            )
        }.reversed()
    }

    func getEarningsByEmployer() -> [(employer: String, earnings: Double)] {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let completed = shifts.filter { $0.startTime < nowMs }
        var dict: [String: Double] = [:]
        for s in completed {
            dict[s.company, default: 0] += s.totalEarned
        }
        return dict.sorted { $0.value > $1.value }.map { ($0.key, $0.value) }
    }

    // MARK: - Week Plan

    func addWeekPlanWithMinutes(company: String, hourlyRate: Double, isGig: Bool, customEarned: Double, reminderMinutes: Int, weekStartMillis: Int64, dayEntries: [WeekDayEntry]) {
        let calendar = Calendar.current
        let dateFmt = DateFormatter(); dateFmt.dateFormat = "yyyyMMdd"

        for entry in dayEntries {
            let dayMillis = weekStartMillis + Int64(entry.dayOffset) * 24 * 60 * 60 * 1000
            let dayDate = Date(timeIntervalSince1970: Double(dayMillis) / 1000)
            let dateKey = dateFmt.string(from: dayDate)

            let alreadyExists = shifts.contains { shift in
                shift.company.caseInsensitiveCompare(company) == .orderedSame &&
                dateFmt.string(from: shift.startDate) == dateKey
            }
            if alreadyExists { continue }

            var startComps = calendar.dateComponents([.year, .month, .day], from: dayDate)
            startComps.hour = entry.startH
            startComps.minute = entry.startM
            startComps.second = 0

            var endComps = calendar.dateComponents([.year, .month, .day], from: dayDate)
            endComps.hour = entry.endH
            endComps.minute = entry.endM
            endComps.second = 0

            let startDate = calendar.date(from: startComps)!
            var endDate = calendar.date(from: endComps)!
            if endDate <= startDate { endDate = endDate.addingTimeInterval(86400) }

            let startMs = Int64(startDate.timeIntervalSince1970 * 1000)
            let endMs = Int64(endDate.timeIntervalSince1970 * 1000)

            addShift(company: company, startTime: startMs, endTime: endMs, hourlyRate: hourlyRate, isGig: isGig, customEarned: customEarned, reminderBeforeMinutes: reminderMinutes)
        }
    }

    // MARK: - Settings

    func updateUserName(_ newName: String) {
        Task {
            try? await service.updateUserName(newName)
        }
    }

    func setThemeMode(_ mode: String) {
        themeMode = mode
        service.updateSettings(["themeMode": mode])
    }

    func setRemindersEnabled(_ enabled: Bool) {
        remindersEnabled = enabled
        service.updateSettings(["remindersEnabled": enabled])
    }

    func setDefaultReminderMinutes(_ minutes: Int) {
        defaultReminderMinutes = minutes
        service.updateSettings(["defaultReminderMinutes": minutes])
    }

    func saveSettings(company: String, rate: Double) {
        defaultCompany = company
        defaultRate = rate
        service.saveSettings(company: company, rate: rate)
    }
}
