import WidgetKit
import SwiftUI

struct ShiftEntry: TimelineEntry {
    let date: Date
    let nextShiftCompany: String
    let nextShiftRole: String
    let nextShiftStart: Date?
    let nextShiftEnd: Date?
    let weeklyEarnings: Double
    let weeklyHours: Double
    let shiftCount: Int
}

struct ScheduloProvider: TimelineProvider {
    func placeholder(in context: Context) -> ShiftEntry {
        ShiftEntry(
            date: Date(),
            nextShiftCompany: "Work",
            nextShiftRole: "Shift",
            nextShiftStart: Date().addingTimeInterval(3600),
            nextShiftEnd: Date().addingTimeInterval(3600 * 5),
            weeklyEarnings: 450.00,
            weeklyHours: 28.5,
            shiftCount: 4
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (ShiftEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ShiftEntry>) -> Void) {
        let entry = loadEntry()
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: Date())!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }

    private func loadEntry() -> ShiftEntry {
        let defaults = UserDefaults(suiteName: "group.com.schedulo2.shared")
        let company = defaults?.string(forKey: "nextShiftCompany") ?? ""
        let role = defaults?.string(forKey: "nextShiftRole") ?? ""
        let startMillis = defaults?.integer(forKey: "nextShiftStart") ?? 0
        let endMillis = defaults?.integer(forKey: "nextShiftEnd") ?? 0
        let earnings = defaults?.double(forKey: "weeklyEarnings") ?? 0
        let hours = defaults?.double(forKey: "weeklyHours") ?? 0
        let count = defaults?.integer(forKey: "weeklyShiftCount") ?? 0

        return ShiftEntry(
            date: Date(),
            nextShiftCompany: company,
            nextShiftRole: role,
            nextShiftStart: startMillis > 0 ? Date(timeIntervalSince1970: Double(startMillis) / 1000.0) : nil,
            nextShiftEnd: endMillis > 0 ? Date(timeIntervalSince1970: Double(endMillis) / 1000.0) : nil,
            weeklyEarnings: earnings,
            weeklyHours: hours,
            shiftCount: count
        )
    }
}

struct NextShiftWidgetView: View {
    var entry: ShiftEntry
    @Environment(\.widgetFamily) var family

    private static let timeFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm a"
        return fmt
    }()

    var body: some View {
        switch family {
        case .systemSmall:
            smallView
        case .systemMedium:
            mediumView
        default:
            smallView
        }
    }

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "clock.fill")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.133, green: 0.329, blue: 0.243))
                Text("Next Shift")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.secondary)
            }

            if let start = entry.nextShiftStart {
                Text(entry.nextShiftCompany)
                    .font(.system(size: 16, weight: .bold))
                    .lineLimit(1)

                if !entry.nextShiftRole.isEmpty {
                    Text(entry.nextShiftRole)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }

                Spacer()

                Text(start, style: .relative)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Color(red: 0.133, green: 0.329, blue: 0.243))

                Text(Self.timeFormatter.string(from: start))
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            } else {
                Spacer()
                Text("No upcoming shifts")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            }
        }
        .padding(12)
        .widgetURL(URL(string: "schedulo://dashboard"))
        .widgetBackground()
    }

    private var mediumView: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: "clock.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.133, green: 0.329, blue: 0.243))
                    Text("Next Shift")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.secondary)
                }

                if let start = entry.nextShiftStart {
                    Text(entry.nextShiftCompany)
                        .font(.system(size: 18, weight: .bold))
                        .lineLimit(1)
                    Text(entry.nextShiftRole)
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(start, style: .relative)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(red: 0.133, green: 0.329, blue: 0.243))
                } else {
                    Spacer()
                    Text("No upcoming shifts")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Divider()

            VStack(alignment: .trailing, spacing: 8) {
                Text("This Week")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.secondary)
                Spacer()
                Text("$\(String(format: "%.0f", entry.weeklyEarnings))")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Color(red: 0.133, green: 0.329, blue: 0.243))
                Text("\(String(format: "%.1f", entry.weeklyHours)) hrs")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                Text("\(entry.shiftCount) shifts")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            .frame(width: 100)
        }
        .padding(14)
        .widgetURL(URL(string: "schedulo://dashboard"))
        .widgetBackground()
    }
}

extension View {
    @ViewBuilder
    func widgetBackground() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(.fill, for: .widget)
        } else {
            self.background(Color(UIColor.systemBackground))
        }
    }
}

@main
struct ScheduloWidgets: WidgetBundle {
    var body: some Widget {
        NextShiftWidget()
    }
}

struct NextShiftWidget: Widget {
    let kind = "NextShiftWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ScheduloProvider()) { entry in
            NextShiftWidgetView(entry: entry)
        }
        .configurationDisplayName("Next Shift")
        .description("See your next upcoming shift and weekly earnings.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
