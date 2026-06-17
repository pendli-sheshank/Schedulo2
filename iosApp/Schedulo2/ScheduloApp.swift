import SwiftUI
import FirebaseCore

@main
struct ScheduloApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @AppStorage("themeMode") private var themeMode: String = "system"

    @StateObject private var authViewModel = AuthViewModel()
    @StateObject private var dashboardViewModel = DashboardViewModel()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authViewModel)
                .environmentObject(dashboardViewModel)
                .preferredColorScheme(resolvedColorScheme)
                .onReceive(dashboardViewModel.$themeMode) { mode in
                    themeMode = mode
                }
        }
    }

    private var resolvedColorScheme: ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil // system
        }
    }
}
