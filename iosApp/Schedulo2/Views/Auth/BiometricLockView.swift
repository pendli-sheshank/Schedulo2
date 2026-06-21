import SwiftUI
import LocalAuthentication

struct BiometricLockView: View {
    @EnvironmentObject var authViewModel: AuthViewModel

    private let darkGreen = Color(red: 0.176, green: 0.247, blue: 0.153)

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [.primaryGreen, darkGreen],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 28)
                    .fill(Color.white.opacity(0.15))
                    .frame(width: 88, height: 88)
                    .overlay(
                        Image(systemName: authViewModel.biometryType == .faceID ? "faceid" : "touchid")
                            .font(.system(size: 44))
                            .foregroundColor(.white)
                    )

                Spacer().frame(height: 24)

                Text("Schedulo is locked")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)

                Text("Unlock with biometrics to continue")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.top, 8)

                if let errorMsg = authViewModel.errorMessage {
                    Text(errorMsg)
                        .font(.system(size: 13))
                        .foregroundColor(Color(red: 1.0, green: 0.804, blue: 0.804))
                        .multilineTextAlignment(.center)
                        .padding(.top, 16)
                }

                Spacer().frame(height: 32)

                Button(action: {
                    authViewModel.authenticateWithBiometric()
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: authViewModel.biometryType == .faceID ? "faceid" : "touchid")
                            .font(.system(size: 20))
                        Text("Unlock")
                            .font(.system(size: 15, weight: .semibold))
                    }
                    .foregroundColor(.primaryGreen)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color.white)
                    )
                }

                Button("Sign out") {
                    authViewModel.logout()
                }
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.7))
                .padding(.top, 16)
            }
            .padding(.horizontal, 32)
        }
        .onAppear {
            authViewModel.authenticateWithBiometric()
        }
    }
}
