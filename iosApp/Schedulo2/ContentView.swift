import SwiftUI

struct ContentView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel

    var body: some View {
        Group {
            if authViewModel.isAuthenticated {
                MainTabView()
                    .environmentObject(authViewModel)
                    .environmentObject(dashboardViewModel)
                    .transition(.asymmetric(
                        insertion: .scale(scale: 0.95).combined(with: .opacity),
                        removal: .opacity
                    ))
                    .onAppear {
                        dashboardViewModel.loadShifts()
                    }
            } else {
                LoginView()
                    .environmentObject(authViewModel)
                    .transition(.asymmetric(
                        insertion: .opacity,
                        removal: .scale(scale: 0.95).combined(with: .opacity)
                    ))
            }
        }
        .animation(.easeInOut(duration: 0.4), value: authViewModel.isAuthenticated)
    }
}
