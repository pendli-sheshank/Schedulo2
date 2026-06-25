import SwiftUI

struct TeamRosterView: View {
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var weekOffset = 0
    @State private var taskMemberId: String?
    @State private var showHelp = false

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
        let cycleDay: String = teamViewModel.currentTeam?.weeklyCycleStartDay ?? "Monday"
        let targetWeekday: Int = Self.weekdayNumber(for: cycleDay)
        let today = Date()
        let todayWeekday: Int = cal.component(.weekday, from: today)
        let diff: Int = (todayWeekday - targetWeekday + 7) % 7
        let cycleStart: Date = cal.startOfDay(for: cal.date(byAdding: .day, value: -diff, to: today)!)
        return cal.date(byAdding: .weekOfYear, value: weekOffset, to: cycleStart)!
    }

    private var daysInWeek: [Date] {
        (0..<7).map { Calendar.current.date(byAdding: .day, value: $0, to: weekStartDate)! }
    }

    private var weekShifts: [TeamShiftInfo] {
        let start = Int64(weekStartDate.timeIntervalSince1970 * 1000)
        let end = start + 7 * 24 * 3600 * 1000
        return teamViewModel.teamShifts.filter { $0.startTime < end && $0.endTime > start }
    }

    private let dayFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "EEE"; return f
    }()
    private let dateFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "M/dd"; return f
    }()
    private let timeFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "h:mm a"; return f
    }()
    private let weekLabelFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "MMM dd"; return f
    }()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                weekNavigator
                dayHeaders
                Divider()
                rosterGrid
            }
            .navigationTitle("Team Roster")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showHelp = true }) {
                        Image(systemName: "questionmark.circle")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: Binding(
                get: { taskMemberId.map { IdentifiableString(value: $0) } },
                set: { taskMemberId = $0?.value }
            )) { wrapped in
                CreateTeamTaskView(preselectedUserId: wrapped.value)
                    .environmentObject(teamViewModel)
            }
            .alert("Team Roster", isPresented: $showHelp) {
                Button("Got it", role: .cancel) {}
            } message: {
                Text("A week-at-a-glance grid of who works when. Managers can tap a member's name to assign them a task. Use the arrows to move between weeks.")
            }
        }
    }

    private var weekNavigator: some View {
        HStack {
            Button(action: { weekOffset -= 1 }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
            }
            Spacer()
            VStack(spacing: 2) {
                let endDate = Calendar.current.date(byAdding: .day, value: 6, to: weekStartDate)!
                Text("\(weekLabelFmt.string(from: weekStartDate)) – \(weekLabelFmt.string(from: endDate))")
                    .font(.system(size: 16, weight: .bold))
                Text(weekLabel)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.accentBlue)
            }
            Spacer()
            Button(action: { weekOffset += 1 }) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var weekLabel: String {
        switch weekOffset {
        case 0: return "This Week"
        case 1: return "Next Week"
        case -1: return "Last Week"
        default: return weekOffset > 0 ? "In \(weekOffset) weeks" : "\(-weekOffset) weeks ago"
        }
    }

    private var dayHeaders: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                Text("Member")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.secondary)
                    .frame(width: 90)
                ForEach(daysInWeek, id: \.self) { day in
                    VStack(spacing: 1) {
                        Text(dayFmt.string(from: day))
                            .font(.system(size: 11, weight: .bold))
                        Text(dateFmt.string(from: day))
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                    .frame(width: 90)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
        }
    }

    private var rosterGrid: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if teamViewModel.members.isEmpty {
                    Text("No members")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                        .padding(32)
                } else {
                    ForEach(teamViewModel.members) { member in
                        memberRow(member)
                        Divider().padding(.horizontal, 8)
                    }
                }
            }
        }
    }

    private func memberRow(_ member: TeamMemberInfo) -> some View {
        let memberName: String = member.displayName.isEmpty ? member.email : member.displayName
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                Button(action: {
                    if teamViewModel.isManager { taskMemberId = member.userId }
                }) {
                    HStack(spacing: 2) {
                        Text(memberName)
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                        if teamViewModel.isManager {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 10))
                                .foregroundColor(.primaryGreen)
                        }
                    }
                    .frame(width: 90, alignment: .leading)
                }
                .buttonStyle(.plain)
                .disabled(!teamViewModel.isManager)
                ForEach(daysInWeek, id: \.self) { (day: Date) in
                    let dayStartDate: Date = Calendar.current.startOfDay(for: day)
                    let dayStart: Int64 = Int64(dayStartDate.timeIntervalSince1970 * 1000)
                    let dayEnd: Int64 = dayStart + 24 * 3600 * 1000
                    let dayShifts: [TeamShiftInfo] = weekShifts.filter {
                        $0.assignedTo == member.userId && $0.startTime < dayEnd && $0.endTime > dayStart
                    }
                    VStack(spacing: 2) {
                        if dayShifts.isEmpty {
                            Text("—")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary.opacity(0.4))
                                .frame(minHeight: 40)
                        } else {
                            ForEach(dayShifts) { shift in
                                shiftCell(shift)
                            }
                        }
                    }
                    .frame(width: 90, alignment: .center)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
        }
    }

    private func shiftCell(_ shift: TeamShiftInfo) -> some View {
        let color = statusColor(shift.status)
        return VStack(spacing: 1) {
            Text(timeFmt.string(from: shift.startDate))
                .font(.system(size: 9, weight: .semibold))
                .foregroundColor(color)
            Text(timeFmt.string(from: shift.endDate))
                .font(.system(size: 9))
                .foregroundColor(color.opacity(0.7))
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 3)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(color.opacity(0.12))
        )
    }

    private func statusColor(_ status: String) -> Color {
        switch status {
        case "accepted": return .primaryGreen
        case "declined": return .red
        default: return .accentOrange
        }
    }
}

/// Lightweight Identifiable wrapper so a plain String can drive `.sheet(item:)`.
struct IdentifiableString: Identifiable {
    let value: String
    var id: String { value }
}
