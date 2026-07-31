import SwiftUI
import Combine
import FirebaseCore

@main
struct ScheduloApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @AppStorage("themeMode") private var themeMode: String = "system"

    @StateObject private var authViewModel = AuthViewModel()
    @StateObject private var dashboardViewModel = DashboardViewModel()
    @StateObject private var teamViewModel = TeamViewModel()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authViewModel)
                .environmentObject(dashboardViewModel)
                .environmentObject(teamViewModel)
                .preferredColorScheme(resolvedColorScheme)
                .onReceive(dashboardViewModel.$themeMode) { mode in
                    themeMode = mode
                }
                // Whenever Firebase reports no signed-in user, drop every team
                // listener and its state. These view models live for the whole
                // app lifetime, so anything left attached keeps streaming the
                // previous account's team data to whoever signs in next. Owning
                // it here — off the authoritative auth state rather than off
                // each logout button — means no sign-out path can miss it,
                // including a session revoked server-side.
                .onReceive(FirebaseService.shared.authStateSubject) { user in
                    if user == nil {
                        teamViewModel.removeAllListeners()
                    }
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
