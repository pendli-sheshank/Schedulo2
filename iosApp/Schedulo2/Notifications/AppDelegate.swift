import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let center = UNUserNotificationCenter.current()
        center.delegate = self

        // Register notification categories (actions)
        NotificationService.shared.registerCategories()

        // Request permission
        NotificationService.shared.requestPermission()

        return true
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// Called when a notification is delivered while the app is in the foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    /// Called when the user interacts with a notification (tap or action button).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        let shiftId = userInfo["shiftId"] as? String ?? ""
        let company = userInfo["company"] as? String ?? "Work"
        let timeStr = userInfo["timeStr"] as? String ?? ""

        switch response.actionIdentifier {
        case NotificationService.dismissActionIdentifier,
             UNNotificationDismissActionIdentifier:
            // Remove delivered notification
            NotificationService.shared.cancelReminder(shiftId: shiftId)

        case NotificationService.snoozeActionIdentifier:
            NotificationService.shared.snoozeReminder(shiftId: shiftId, company: company, timeStr: timeStr)

        case UNNotificationDefaultActionIdentifier:
            // User tapped the notification body - no special action, app opens
            break

        default:
            break
        }

        completionHandler()
    }
}
