import Foundation
import UserNotifications

final class NotificationService {
    static let shared = NotificationService()

    static let categoryIdentifier = "SHIFT_REMINDER"
    static let dismissActionIdentifier = "DISMISS_ACTION"
    static let snoozeActionIdentifier = "SNOOZE_ACTION"

    private let center = UNUserNotificationCenter.current()

    private init() {}

    // MARK: - Permission

    func requestPermission() {
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("Notification permission error: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - Register Notification Actions

    func registerCategories() {
        let dismissAction = UNNotificationAction(
            identifier: Self.dismissActionIdentifier,
            title: "Dismiss",
            options: [.destructive]
        )
        let snoozeAction = UNNotificationAction(
            identifier: Self.snoozeActionIdentifier,
            title: "Snooze 5 min",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: Self.categoryIdentifier,
            actions: [dismissAction, snoozeAction],
            intentIdentifiers: [],
            options: []
        )
        center.setNotificationCategories([category])
    }

    // MARK: - Schedule

    func scheduleReminder(shift: Shift) {
        guard shift.reminderBeforeMinutes > 0 else { return }

        let triggerTimeMs = shift.startTime - Int64(shift.reminderBeforeMinutes) * 60 * 1000
        let triggerDate = Date(timeIntervalSince1970: Double(triggerTimeMs) / 1000.0)

        guard triggerDate > Date() else { return }

        let content = UNMutableNotificationContent()
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mm a"
        let timeStr = timeFormatter.string(from: shift.startDate)

        content.title = "Upcoming Shift: \(shift.company)"
        content.body = "Your shift starts at \(timeStr)"
        content.sound = .default
        content.categoryIdentifier = Self.categoryIdentifier
        content.userInfo = [
            "shiftId": shift.id,
            "company": shift.company,
            "timeStr": timeStr
        ]

        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute, .second],
            from: triggerDate
        )
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)

        let request = UNNotificationRequest(
            identifier: "shift_\(shift.id)",
            content: content,
            trigger: trigger
        )

        center.add(request) { error in
            if let error = error {
                print("Failed to schedule reminder: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - Cancel

    func cancelReminder(shiftId: String) {
        center.removePendingNotificationRequests(withIdentifiers: ["shift_\(shiftId)"])
        center.removeDeliveredNotifications(withIdentifiers: ["shift_\(shiftId)"])
    }

    // MARK: - Snooze

    func snoozeReminder(shiftId: String, company: String, timeStr: String) {
        let content = UNMutableNotificationContent()
        content.title = "Upcoming Shift: \(company)"
        content.body = "Your shift starts at \(timeStr) (snoozed)"
        content.sound = .default
        content.categoryIdentifier = Self.categoryIdentifier
        content.userInfo = [
            "shiftId": shiftId,
            "company": company,
            "timeStr": timeStr
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 5 * 60, repeats: false)

        let request = UNNotificationRequest(
            identifier: "shift_\(shiftId)",
            content: content,
            trigger: trigger
        )

        center.add(request, withCompletionHandler: nil)
    }

    // MARK: - Reschedule All

    func rescheduleAllReminders(shifts: [Shift]) {
        center.removeAllPendingNotificationRequests()
        let now = Date()
        for shift in shifts {
            let triggerTimeMs = shift.startTime - Int64(shift.reminderBeforeMinutes) * 60 * 1000
            let triggerDate = Date(timeIntervalSince1970: Double(triggerTimeMs) / 1000.0)
            if shift.reminderBeforeMinutes > 0 && triggerDate > now {
                scheduleReminder(shift: shift)
            }
        }
    }
}
