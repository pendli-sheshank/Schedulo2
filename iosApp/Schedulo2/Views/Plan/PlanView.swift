import SwiftUI

struct PlanView: View {
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    var onEditShift: (String) -> Void = { _ in }
    var onAddShift: () -> Void = {}

    @State private var viewMode = "Month"
    @State private var selectedDate = Calendar.current.startOfDay(for: Date())
    @State private var searchQuery = ""

    private var filteredShifts: [Shift] {
        if searchQuery.isEmpty { return dashboardViewModel.shifts }
        return dashboardViewModel.shifts.filter {
            $0.company.localizedCaseInsensitiveContains(searchQuery)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                    .font(.system(size: 16))
                TextField("Search by employer...", text: $searchQuery)
                    .font(.system(size: 14))
                if !searchQuery.isEmpty {
                    Button(action: { searchQuery = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                            .font(.system(size: 14))
                    }
                }
            }
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(UIColor.secondarySystemBackground).opacity(0.5))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                    )
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // View mode toggle
            HStack(spacing: 8) {
                ForEach(["Month", "Week", "Day"], id: \.self) { mode in
                    let selected = viewMode == mode
                    Button(action: { viewMode = mode }) {
                        Text(mode)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(selected ? .white : .primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(selected ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                            )
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)

            // Views
            switch viewMode {
            case "Month":
                CalendarMonthView(
                    selectedDate: $selectedDate,
                    shifts: filteredShifts,
                    onEditShift: onEditShift,
                    onAddShift: onAddShift,
                    onSwitchToDay: { date in selectedDate = date; viewMode = "Day" }
                )
            case "Week":
                CalendarWeekView(
                    selectedDate: $selectedDate,
                    shifts: filteredShifts,
                    onEditShift: onEditShift,
                    onAddShift: onAddShift,
                    onDayTap: { date in selectedDate = date; viewMode = "Day" }
                )
            case "Day":
                CalendarDayView(
                    selectedDate: $selectedDate,
                    shifts: filteredShifts,
                    onEditShift: onEditShift,
                    onAddShift: onAddShift
                )
            default:
                EmptyView()
            }
        }
    }
}

// MARK: - Shift Card

struct ShiftCard: View {
    let shift: Shift
    var onTap: () -> Void = {}

    private static let timeFormat: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "hh:mm a"
        return f
    }()

    private var isActive: Bool {
        let now = Date()
        return now >= shift.startTime && now <= shift.endTime
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(shift.isGig ? Color.accentOrange : Color.accentBlue)
                    .frame(width: 4, height: 44)

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(shift.isGig ? "\(shift.company) (Gig)" : shift.company)
                            .font(.system(size: 15, weight: .semibold))
                        if isActive {
                            Text("LIVE")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(RoundedRectangle(cornerRadius: 3).fill(Color.primaryGreen))
                        }
                    }

                    Text("\(Self.timeFormat.string(from: shift.startTime)) -> \(Self.timeFormat.string(from: shift.endTime)) - \(String(format: "%.1f", shift.durationHours)) hrs")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)

                    if !shift.notes.isEmpty {
                        Text(String(shift.notes.components(separatedBy: "\n").first?.prefix(60) ?? ""))
                            .font(.system(size: 11))
                            .foregroundColor(.secondary.opacity(0.7))
                            .lineLimit(1)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("$\(shift.totalEarned, specifier: "%.2f")")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.primaryGreen)

                    if shift.reminderBeforeMinutes > 0 && shift.startTime > Date() {
                        Text("\(shift.reminderBeforeMinutes)m")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.primaryGreen)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.primaryGreen.opacity(0.1))
                            )
                    }
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isActive ? Color.primaryGreen.opacity(0.06) : Color(UIColor.systemBackground))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(isActive ? Color.primaryGreen.opacity(0.4) : Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Month View

private struct CalendarMonthView: View {
    @Binding var selectedDate: Date
    let shifts: [Shift]
    var onEditShift: (String) -> Void
    var onAddShift: () -> Void
    var onSwitchToDay: (Date) -> Void

    private let calendar = Calendar.current
    private let dayOfWeekHeaders = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    private var year: Int { calendar.component(.year, from: selectedDate) }
    private var month: Int { calendar.component(.month, from: selectedDate) }

    private var monthString: String {
        let fmt = DateFormatter()
        fmt.dateFormat = "MMMM yyyy"
        return fmt.string(from: selectedDate)
    }

    private var daysInMonth: Int {
        calendar.range(of: .day, in: .month, for: selectedDate)?.count ?? 30
    }

    private var firstWeekdayOffset: Int {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = 1
        let firstDay = calendar.date(from: comps)!
        let weekday = calendar.component(.weekday, from: firstDay)
        return (weekday + 5) % 7
    }

    private func dateFor(day: Int) -> Date {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        return calendar.date(from: comps) ?? selectedDate
    }

    private func shiftsFor(day: Int) -> [Shift] {
        let dayStart = dateFor(day: day)
        guard let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) else { return [] }
        return shifts.filter { $0.startTime >= dayStart && $0.startTime < dayEnd }
    }

    private var selectedDayShifts: [Shift] {
        let dayStart = calendar.startOfDay(for: selectedDate)
        guard let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) else { return [] }
        return shifts.filter { $0.startTime >= dayStart && $0.startTime < dayEnd }.sorted { $0.startTime < $1.startTime }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                // Month navigation
                HStack {
                    Button(action: { changeMonth(-1) }) {
                        Image(systemName: "chevron.left").foregroundColor(.primary)
                    }
                    Spacer()
                    VStack(spacing: 2) {
                        Text(monthString)
                            .font(.system(size: 18, weight: .bold))
                        let totalShifts = (1...daysInMonth).flatMap { shiftsFor(day: $0) }
                        Text("\(totalShifts.count) shifts - \(String(format: "%.0f", totalShifts.reduce(0) { $0 + $1.durationHours })) hrs")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button(action: { changeMonth(1) }) {
                        Image(systemName: "chevron.right").foregroundColor(.primary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // Day headers
                HStack(spacing: 0) {
                    ForEach(dayOfWeekHeaders, id: \.self) { day in
                        Text(day)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 12)

                // Grid
                let totalCells = firstWeekdayOffset + daysInMonth
                let rows = (totalCells + 6) / 7
                ForEach(0..<rows, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { col in
                            let cellIndex = row * 7 + col
                            let dayNum = cellIndex - firstWeekdayOffset + 1
                            if dayNum >= 1 && dayNum <= daysInMonth {
                                let isToday = calendar.isDateInToday(dateFor(day: dayNum))
                                let isSelected = calendar.isDate(dateFor(day: dayNum), inSameDayAs: selectedDate)
                                let dayShifts = shiftsFor(day: dayNum)

                                Button(action: { selectedDate = dateFor(day: dayNum) }) {
                                    VStack(spacing: 2) {
                                        Text("\(dayNum)")
                                            .font(.system(size: 14, weight: isToday || isSelected ? .bold : .regular))
                                            .foregroundColor(isSelected ? .white : isToday ? .primaryGreen : .primary)
                                        if !dayShifts.isEmpty {
                                            HStack(spacing: 2) {
                                                ForEach(0..<min(dayShifts.count, 3), id: \.self) { _ in
                                                    Circle()
                                                        .fill(isSelected ? Color.white : Color.primaryGreen)
                                                        .frame(width: 4, height: 4)
                                                }
                                            }
                                        }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .aspectRatio(1, contentMode: .fit)
                                    .background(
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(isSelected ? Color.primaryGreen : isToday ? Color.primaryGreen.opacity(0.12) : Color.clear)
                                    )
                                }
                                .buttonStyle(.plain)
                            } else {
                                Spacer().frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                }

                Divider().padding(.horizontal, 16).padding(.vertical, 8)

                // Selected day detail
                HStack {
                    let fmt = DateFormatter()
                    Text({
                        let f = DateFormatter(); f.dateFormat = "EEEE, MMM dd"; return f.string(from: selectedDate)
                    }())
                        .font(.system(size: 16, weight: .bold))
                    Spacer()
                    if !selectedDayShifts.isEmpty {
                        Button("Full Day View") { onSwitchToDay(selectedDate) }
                            .font(.system(size: 12))
                            .foregroundColor(.primaryGreen)
                    }
                }
                .padding(.horizontal, 16)

                if selectedDayShifts.isEmpty {
                    emptyState
                } else {
                    ForEach(selectedDayShifts, id: \.id) { shift in
                        ShiftCard(shift: shift) { onEditShift(shift.id) }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 4)
                    }

                    // Day summary
                    let totalHrs = selectedDayShifts.reduce(0) { $0 + $1.durationHours }
                    let totalEarned = selectedDayShifts.reduce(0) { $0 + $1.totalEarned }
                    HStack {
                        Text("\(selectedDayShifts.count) shift\(selectedDayShifts.count != 1 ? "s" : "") - \(String(format: "%.1f", totalHrs)) hrs")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.primaryGreen)
                        Spacer()
                        Text("$\(totalEarned, specifier: "%.2f")")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.primaryGreen)
                    }
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.primaryGreen.opacity(0.08))
                    )
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
                }

                Spacer().frame(height: 80)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "calendar.badge.checkmark")
                .font(.system(size: 32))
                .foregroundColor(.secondary.opacity(0.4))
            Text("No shifts scheduled")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
            Button(action: onAddShift) {
                HStack(spacing: 4) {
                    Image(systemName: "plus").font(.system(size: 14))
                    Text("Add Shift").font(.system(size: 14, weight: .semibold))
                }
                .foregroundColor(.primaryGreen)
            }
        }
        .padding(32)
    }

    private func changeMonth(_ offset: Int) {
        if let newDate = calendar.date(byAdding: .month, value: offset, to: selectedDate) {
            selectedDate = newDate
        }
    }
}

// MARK: - Week View

private struct CalendarWeekView: View {
    @Binding var selectedDate: Date
    let shifts: [Shift]
    var onEditShift: (String) -> Void
    var onAddShift: () -> Void
    var onDayTap: (Date) -> Void

    private let calendar = Calendar.current

    private var weekStart: Date {
        var cal = calendar
        cal.firstWeekday = 2
        let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: selectedDate)
        return cal.date(from: comps) ?? selectedDate
    }

    private var weekEnd: Date {
        calendar.date(byAdding: .day, value: 7, to: weekStart) ?? weekStart
    }

    private var weekShifts: [Shift] {
        shifts.filter { $0.startTime >= weekStart && $0.startTime < weekEnd }.sorted { $0.startTime < $1.startTime }
    }

    private var daysList: [(Date, Date)] {
        (0..<7).map { offset in
            let dayStart = calendar.date(byAdding: .day, value: offset, to: weekStart)!
            let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart)!
            return (dayStart, dayEnd)
        }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                // Navigation
                HStack {
                    Button(action: { changeWeek(-1) }) {
                        Image(systemName: "chevron.left").foregroundColor(.primary)
                    }
                    Spacer()
                    VStack(spacing: 2) {
                        let fmt = DateFormatter()
                        Text({
                            let f = DateFormatter(); f.dateFormat = "MMM dd"
                            let end = calendar.date(byAdding: .day, value: -1, to: weekEnd)!
                            return "\(f.string(from: weekStart)) - \(f.string(from: end))"
                        }())
                            .font(.system(size: 16, weight: .bold))
                        let totalHours = weekShifts.reduce(0) { $0 + $1.durationHours }
                        let totalEarned = weekShifts.reduce(0) { $0 + $1.totalEarned }
                        Text("\(weekShifts.count) shifts - \(String(format: "%.1f", totalHours)) hrs - $\(String(format: "%.2f", totalEarned))")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button(action: { changeWeek(1) }) {
                        Image(systemName: "chevron.right").foregroundColor(.primary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // Day strip
                HStack(spacing: 4) {
                    ForEach(daysList, id: \.0) { dayStart, _ in
                        let isToday = calendar.isDateInToday(dayStart)
                        let shiftCount = weekShifts.filter { $0.startTime >= dayStart && $0.startTime < calendar.date(byAdding: .day, value: 1, to: dayStart)! }.count

                        Button(action: { onDayTap(dayStart) }) {
                            VStack(spacing: 4) {
                                let f1 = DateFormatter(); let _ = (f1.dateFormat = "EEE")
                                Text(f1.string(from: dayStart))
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(isToday ? .primaryGreen : .secondary)
                                let f2 = DateFormatter(); let _ = (f2.dateFormat = "dd")
                                Text(f2.string(from: dayStart))
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(isToday ? .primaryGreen : .primary)
                                if shiftCount > 0 {
                                    Circle()
                                        .fill(Color.primaryGreen)
                                        .frame(width: 6, height: 6)
                                } else {
                                    Spacer().frame(height: 6)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(isToday ? Color.primaryGreen.opacity(0.12) : Color.clear)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(isToday ? Color.primaryGreen : Color.clear, lineWidth: 1.5)
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                Divider().padding(.horizontal, 16).padding(.vertical, 4)

                // Day-by-day agenda
                if weekShifts.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "calendar.badge.checkmark")
                            .font(.system(size: 40))
                            .foregroundColor(.secondary.opacity(0.4))
                        Text("No shifts this week")
                            .font(.system(size: 15))
                            .foregroundColor(.secondary)
                        Button(action: onAddShift) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus").font(.system(size: 14))
                                Text("Add Shift").font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundColor(.primaryGreen)
                        }
                    }
                    .padding(48)
                } else {
                    ForEach(daysList, id: \.0) { dayStart, dayEnd in
                        let dayShifts = weekShifts.filter { $0.startTime >= dayStart && $0.startTime < dayEnd }
                        if !dayShifts.isEmpty {
                            let isToday = calendar.isDateInToday(dayStart)
                            HStack {
                                HStack(spacing: 8) {
                                    let f = DateFormatter(); let _ = (f.dateFormat = "EEEE, MMM dd")
                                    Text(f.string(from: dayStart))
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(isToday ? .primaryGreen : .primary)
                                    if isToday {
                                        Text("TODAY")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(RoundedRectangle(cornerRadius: 4).fill(Color.primaryGreen))
                                    }
                                }
                                Spacer()
                                Text("$\(dayShifts.reduce(0) { $0 + $1.totalEarned }, specifier: "%.2f")")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.primaryGreen)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 6)

                            ForEach(dayShifts, id: \.id) { shift in
                                ShiftCard(shift: shift) { onEditShift(shift.id) }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 3)
                            }
                        }
                    }
                }

                Spacer().frame(height: 80)
            }
        }
    }

    private func changeWeek(_ offset: Int) {
        if let newDate = calendar.date(byAdding: .weekOfYear, value: offset, to: selectedDate) {
            selectedDate = newDate
        }
    }
}

// MARK: - Day View

private struct CalendarDayView: View {
    @Binding var selectedDate: Date
    let shifts: [Shift]
    var onEditShift: (String) -> Void
    var onAddShift: () -> Void

    private let calendar = Calendar.current

    private var dayStart: Date { calendar.startOfDay(for: selectedDate) }
    private var dayEnd: Date { calendar.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart }

    private var dayShifts: [Shift] {
        shifts.filter { $0.startTime >= dayStart && $0.startTime < dayEnd }.sorted { $0.startTime < $1.startTime }
    }

    private var isToday: Bool { calendar.isDateInToday(selectedDate) }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                // Navigation
                HStack {
                    Button(action: { changeDay(-1) }) {
                        Image(systemName: "chevron.left").foregroundColor(.primary)
                    }
                    Spacer()
                    VStack(spacing: 2) {
                        HStack(spacing: 8) {
                            let f = DateFormatter(); let _ = (f.dateFormat = "EEEE, MMMM dd, yyyy")
                            Text(f.string(from: selectedDate))
                                .font(.system(size: 16, weight: .bold))
                            if isToday {
                                Text("TODAY")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(RoundedRectangle(cornerRadius: 4).fill(Color.primaryGreen))
                            }
                        }
                    }
                    Spacer()
                    Button(action: { changeDay(1) }) {
                        Image(systemName: "chevron.right").foregroundColor(.primary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // Summary
                if !dayShifts.isEmpty {
                    let totalHours = dayShifts.reduce(0) { $0 + $1.durationHours }
                    let totalEarned = dayShifts.reduce(0) { $0 + $1.totalEarned }

                    HStack {
                        VStack {
                            Text("\(dayShifts.count)")
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.primaryGreen)
                            Text("SHIFTS")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        VStack {
                            Text(String(format: "%.1f", totalHours))
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.primaryGreen)
                            Text("HOURS")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        VStack {
                            Text("$\(totalEarned, specifier: "%.2f")")
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.primaryGreen)
                            Text("EARNED")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(16)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color.primaryGreen.opacity(0.08))
                    )
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)

                    Divider().padding(.horizontal, 16).padding(.vertical, 8)
                }

                // Shifts
                if dayShifts.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "calendar.badge.checkmark")
                            .font(.system(size: 40))
                            .foregroundColor(.secondary.opacity(0.4))
                        Text("No shifts scheduled")
                            .font(.system(size: 15))
                            .foregroundColor(.secondary)
                        Button(action: onAddShift) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus").font(.system(size: 14))
                                Text("Add Shift").font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundColor(.primaryGreen)
                        }
                    }
                    .padding(64)
                } else {
                    ForEach(dayShifts, id: \.id) { shift in
                        DayViewShiftCard(shift: shift) { onEditShift(shift.id) }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 4)
                    }
                }

                Spacer().frame(height: 80)
            }
        }
    }

    private func changeDay(_ offset: Int) {
        if let newDate = calendar.date(byAdding: .day, value: offset, to: selectedDate) {
            selectedDate = newDate
        }
    }
}

private struct DayViewShiftCard: View {
    let shift: Shift
    var onTap: () -> Void = {}

    private static let timeFormat: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "hh:mm a"
        return f
    }()

    private var isActive: Bool {
        let now = Date()
        return now >= shift.startTime && now <= shift.endTime
    }

    private var isPast: Bool { shift.endTime < Date() }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Time column
                VStack(spacing: 4) {
                    Text(Self.timeFormat.string(from: shift.startTime))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(isPast ? .secondary : .primaryGreen)
                    Rectangle()
                        .fill(isPast ? Color(UIColor.separator) : Color.primaryGreen.opacity(0.4))
                        .frame(width: 2, height: 16)
                    Text(Self.timeFormat.string(from: shift.endTime))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.secondary)
                }
                .frame(width: 70)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(shift.isGig ? Color.accentOrange : Color.accentBlue)
                            .frame(width: 4, height: 20)
                        Text(shift.isGig ? "\(shift.company) (Gig)" : shift.company)
                            .font(.system(size: 16, weight: .bold))
                    }
                    HStack(spacing: 12) {
                        Text("\(String(format: "%.1f", shift.durationHours)) hrs")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                        Text("$\(shift.totalEarned, specifier: "%.2f")")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.primaryGreen)
                    }
                    if !shift.notes.isEmpty {
                        Text(shift.notes)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary.opacity(0.7))
                            .lineLimit(2)
                    }
                    if isActive {
                        Text("IN PROGRESS")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 4).fill(Color.primaryGreen))
                    }
                }

                Spacer()

                if shift.reminderBeforeMinutes > 0 && shift.startTime > Date() {
                    Text("\(shift.reminderBeforeMinutes)m")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.primaryGreen)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(
                            RoundedRectangle(cornerRadius: 4)
                                .fill(Color.primaryGreen.opacity(0.1))
                        )
                }
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(isActive ? Color.primaryGreen.opacity(0.06) : Color(UIColor.systemBackground))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isActive ? Color.primaryGreen.opacity(0.4) : Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}
