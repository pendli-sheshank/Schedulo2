import SwiftUI

struct ContentView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    var body: some View {
        Group {
            if authViewModel.requiresBiometricUnlock {
                BiometricLockView()
                    .environmentObject(authViewModel)
            } else if authViewModel.isAuthenticated {
                MainTabView()
                    .environmentObject(authViewModel)
                    .environmentObject(dashboardViewModel)
                    .onAppear {
                        dashboardViewModel.loadShifts()
                    }
            } else {
                LoginView()
                    .environmentObject(authViewModel)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: authViewModel.isAuthenticated)
        .animation(.easeInOut(duration: 0.3), value: authViewModel.requiresBiometricUnlock)
    }
}
