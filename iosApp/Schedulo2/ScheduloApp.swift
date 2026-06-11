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

// MARK: - ContentView (root navigation)

struct ContentView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    var body: some View {
        Group {
            switch authViewModel.authState {
            case .authenticated:
                MainTabView()
                    .onAppear {
                        dashboardViewModel.loadShifts()
                        // Reschedule reminders on launch
                        NotificationService.shared.rescheduleAllReminders(shifts: dashboardViewModel.shifts)
                    }
            case .loading:
                ProgressView("Signing in...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            default:
                AuthPlaceholderView()
            }
        }
        .animation(.easeInOut, value: authViewModel.authState)
    }
}

// MARK: - Placeholder views (to be replaced with actual screens)

struct MainTabView: View {
    var body: some View {
        TabView {
            Text("Dashboard")
                .tabItem { Label("Home", systemImage: "house.fill") }
            Text("Shifts")
                .tabItem { Label("Shifts", systemImage: "calendar") }
            Text("Insights")
                .tabItem { Label("Insights", systemImage: "chart.bar.fill") }
            Text("Settings")
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        .tint(.primaryGreen)
    }
}

struct AuthPlaceholderView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @State private var email = ""
    @State private var password = ""
    @State private var fullName = ""
    @State private var isSignUp = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Spacer()

                Text("Schedulo")
                    .font(.largeTitle.bold())
                    .foregroundColor(.primaryGreen)

                Text(isSignUp ? "Create your account" : "Sign in to continue")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(spacing: 14) {
                    if isSignUp {
                        TextField("Full Name", text: $fullName)
                            .textFieldStyle(.roundedBorder)
                            .textContentType(.name)
                    }
                    TextField("Email", text: $email)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(.emailAddress)
                        .autocapitalization(.none)
                        .keyboardType(.emailAddress)
                    SecureField("Password", text: $password)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(isSignUp ? .newPassword : .password)
                }
                .padding(.horizontal)

                if case .error(let msg) = authViewModel.authState {
                    Text(msg)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }

                Button {
                    if isSignUp {
                        authViewModel.signup(email: email, password: password, fullName: fullName)
                    } else {
                        authViewModel.login(email: email, password: password)
                    }
                } label: {
                    Text(isSignUp ? "Sign Up" : "Sign In")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.primaryGreen)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
                .padding(.horizontal)

                Button {
                    isSignUp.toggle()
                } label: {
                    Text(isSignUp ? "Already have an account? Sign In" : "Don't have an account? Sign Up")
                        .font(.footnote)
                        .foregroundColor(.accentBlue)
                }

                Spacer()
            }
            .navigationBarHidden(true)
        }
    }
}
