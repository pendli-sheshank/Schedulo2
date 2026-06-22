import SwiftUI
import FirebaseCore

@main
struct ScheduloApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @AppStorage("themeMode") private var themeMode: String = "system"

    @State private var showSplash = true

    @StateObject private var authViewModel = AuthViewModel()
    @StateObject private var dashboardViewModel = DashboardViewModel()
    @StateObject private var teamViewModel = TeamViewModel()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                ContentView()
                    .environmentObject(authViewModel)
                    .environmentObject(dashboardViewModel)
                    .environmentObject(teamViewModel)
                    .preferredColorScheme(resolvedColorScheme)
                    .onReceive(dashboardViewModel.$themeMode) { mode in
                        themeMode = mode
                    }

                if showSplash {
                    SplashView()
                        .transition(.opacity)
                        .zIndex(1)
                }
            }
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    withAnimation(.easeOut(duration: 0.4)) {
                        showSplash = false
                    }
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
