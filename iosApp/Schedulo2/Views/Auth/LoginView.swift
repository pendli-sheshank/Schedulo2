import SwiftUI

struct LoginView: View {
    @EnvironmentObject var authViewModel: AuthViewModel

    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var showForgotPassword = false
    @State private var resetEmail = ""

    private var emailError: String? {
        if email.isEmpty { return nil }
        let pattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        let pred = NSPredicate(format: "SELF MATCHES %@", pattern)
        return pred.evaluate(with: email.trimmingCharacters(in: .whitespaces)) ? nil : "Enter a valid email address"
    }

    private var isFormValid: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        let pattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        let pred = NSPredicate(format: "SELF MATCHES %@", pattern)
        return pred.evaluate(with: trimmed) && password.count >= 6
    }

    private let darkGreen = Color(red: 0.176, green: 0.247, blue: 0.153)
    private let lightGreenBg = Color(red: 0.941, green: 0.961, blue: 0.933)

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [.primaryGreen, darkGreen],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 0) {
                        Spacer().frame(height: 60)

                        // Logo
                        RoundedRectangle(cornerRadius: 22)
                            .fill(Color.white.opacity(0.15))
                            .frame(width: 72, height: 72)
                            .overlay(
                                Image(systemName: "calendar")
                                    .font(.system(size: 36))
                                    .foregroundColor(.white)
                            )

                        Spacer().frame(height: 20)

                        Text("Schedulo")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                            .tracking(-1)

                        Text("Track shifts. Manage earnings.")
                            .font(.system(size: 15))
                            .foregroundColor(.white.opacity(0.7))
                            .padding(.top, 4)

                        Spacer().frame(height: 44)

                        // Card
                        VStack(spacing: 0) {
                            Text("Welcome back")
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundColor(darkGreen)

                            Text("Sign in to continue")
                                .font(.system(size: 14))
                                .foregroundColor(.gray)
                                .padding(.top, 4)

                            Spacer().frame(height: 28)

                            // Email
                            HStack {
                                Image(systemName: "envelope.fill")
                                    .foregroundColor(.primaryGreen)
                                    .frame(width: 20)
                                TextField("Email address", text: $email)
                                    .keyboardType(.emailAddress)
                                    .textContentType(.emailAddress)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                            }
                            .padding(14)
                            .background(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(emailError != nil ? Color.red : Color.gray.opacity(0.3), lineWidth: 1)
                                    .background(lightGreenBg.cornerRadius(14))
                            )

                            if let error = emailError {
                                Text(error)
                                    .font(.system(size: 12))
                                    .foregroundColor(.red)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.top, 4)
                            }

                            Spacer().frame(height: 16)

                            // Password
                            HStack {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(.primaryGreen)
                                    .frame(width: 20)
                                if passwordVisible {
                                    TextField("Password", text: $password)
                                        .textContentType(.password)
                                } else {
                                    SecureField("Password", text: $password)
                                        .textContentType(.password)
                                }
                                Button(action: { passwordVisible.toggle() }) {
                                    Image(systemName: passwordVisible ? "eye.slash.fill" : "eye.fill")
                                        .foregroundColor(.gray)
                                }
                            }
                            .padding(14)
                            .background(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                                    .background(lightGreenBg.cornerRadius(14))
                            )

                            // Error
                            if let errorMsg = authViewModel.errorMessage {
                                HStack(spacing: 10) {
                                    Image(systemName: "exclamationmark.circle.fill")
                                        .foregroundColor(Color(red: 0.827, green: 0.184, blue: 0.184))
                                        .font(.system(size: 16))
                                    Text(errorMsg)
                                        .font(.system(size: 13))
                                        .foregroundColor(Color(red: 0.827, green: 0.184, blue: 0.184))
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color(red: 0.992, green: 0.925, blue: 0.925))
                                )
                                .padding(.top, 16)
                            }

                            Spacer().frame(height: 24)

                            // Sign In Button
                            Button(action: {
                                authViewModel.login(email: email.trimmingCharacters(in: .whitespaces), password: password)
                            }) {
                                ZStack {
                                    if authViewModel.isLoading {
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    } else {
                                        Text("Sign In")
                                            .font(.system(size: 16, weight: .semibold))
                                            .tracking(0.5)
                                    }
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(
                                    RoundedRectangle(cornerRadius: 14)
                                        .fill(isFormValid && !authViewModel.isLoading ? Color.primaryGreen : Color.primaryGreen.opacity(0.4))
                                )
                                .foregroundColor(.white)
                            }
                            .disabled(!isFormValid || authViewModel.isLoading)

                            Button("Forgot Password?") {
                                resetEmail = email.trimmingCharacters(in: .whitespaces)
                                showForgotPassword = true
                            }
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.primaryGreen)
                            .padding(.top, 8)
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 32)
                        .background(
                            RoundedRectangle(cornerRadius: 28)
                                .fill(Color.white)
                                .shadow(color: .black.opacity(0.1), radius: 12, y: 4)
                        )
                        .padding(.horizontal, 28)

                        Spacer().frame(height: 28)

                        HStack {
                            Text("Don't have an account?")
                                .foregroundColor(.white.opacity(0.7))
                                .font(.system(size: 14))
                            NavigationLink("Sign Up") {
                                SignupView()
                                    .environmentObject(authViewModel)
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        }

                        Spacer().frame(height: 40)
                    }
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showForgotPassword) {
                ForgotPasswordSheet(email: $resetEmail, authViewModel: authViewModel, isPresented: $showForgotPassword)
            }
        }
    }
}

private struct ForgotPasswordSheet: View {
    @Binding var email: String
    @ObservedObject var authViewModel: AuthViewModel
    @Binding var isPresented: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if authViewModel.resetState == .sent {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.primaryGreen)
                    Text("Check your email for a password reset link.")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.primaryGreen)
                        .multilineTextAlignment(.center)
                    Button("Done") {
                        authViewModel.resetResetState()
                        isPresented = false
                    }
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primaryGreen)
                    .padding(.top, 8)
                } else {
                    Text("Enter your email address and we'll send you a link to reset your password.")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)

                    TextField("Email address", text: $email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .textFieldStyle(.roundedBorder)

                    if let error = authViewModel.resetErrorMessage {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundColor(.red)
                    }

                    Button(action: {
                        authViewModel.sendPasswordReset(email: email)
                    }) {
                        Text("Send Reset Link")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(email.isEmpty ? Color.primaryGreen.opacity(0.4) : Color.primaryGreen)
                            )
                            .foregroundColor(.white)
                    }
                    .disabled(email.isEmpty)
                }
                Spacer()
            }
            .padding(24)
            .navigationTitle("Reset Password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        authViewModel.resetResetState()
                        isPresented = false
                    }
                }
            }
        }
    }
}
