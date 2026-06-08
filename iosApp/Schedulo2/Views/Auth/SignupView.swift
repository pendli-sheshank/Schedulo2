import SwiftUI

struct SignupView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var fullName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false

    private let darkGreen = Color(red: 0.176, green: 0.247, blue: 0.153)
    private let lightGreenBg = Color(red: 0.941, green: 0.961, blue: 0.933)

    private var nameError: String? {
        if fullName.isEmpty { return nil }
        if fullName.trimmingCharacters(in: .whitespaces).count > 100 {
            return "Name must be 100 characters or less"
        }
        return nil
    }

    private var emailError: String? {
        if email.isEmpty { return nil }
        let pattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        let pred = NSPredicate(format: "SELF MATCHES %@", pattern)
        return pred.evaluate(with: email.trimmingCharacters(in: .whitespaces)) ? nil : "Enter a valid email address"
    }

    private var passwordError: String? {
        if password.isEmpty { return nil }
        if !isStrongPassword(password) {
            return "Min 8 characters with at least 1 letter and 1 number"
        }
        return nil
    }

    private func isStrongPassword(_ pw: String) -> Bool {
        pw.count >= 8 && pw.rangeOfCharacter(from: .letters) != nil && pw.rangeOfCharacter(from: .decimalDigits) != nil
    }

    private var isValidEmail: Bool {
        let pattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        let pred = NSPredicate(format: "SELF MATCHES %@", pattern)
        return pred.evaluate(with: email.trimmingCharacters(in: .whitespaces))
    }

    private var isFormValid: Bool {
        let trimmedName = fullName.trimmingCharacters(in: .whitespaces)
        return !trimmedName.isEmpty && trimmedName.count <= 100 && isValidEmail && isStrongPassword(password)
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [.primaryGreen, darkGreen],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    Spacer().frame(height: 48)

                    // Logo row
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color.white.opacity(0.15))
                            .frame(width: 44, height: 44)
                            .overlay(
                                Image(systemName: "calendar")
                                    .font(.system(size: 24))
                                    .foregroundColor(.white)
                            )
                        Text("Schedulo")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)
                            .tracking(-0.5)
                    }

                    Spacer().frame(height: 32)

                    // Card
                    VStack(spacing: 0) {
                        Text("Create account")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(darkGreen)

                        Text("Start tracking your shifts")
                            .font(.system(size: 14))
                            .foregroundColor(.gray)
                            .padding(.top, 4)

                        Spacer().frame(height: 24)

                        // Full name
                        HStack {
                            Image(systemName: "person.fill")
                                .foregroundColor(.primaryGreen)
                                .frame(width: 20)
                            TextField("Full name", text: $fullName)
                                .textContentType(.name)
                                .onChange(of: fullName) { newVal in
                                    if newVal.count > 100 {
                                        fullName = String(newVal.prefix(100))
                                    }
                                }
                        }
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(nameError != nil ? Color.red : Color.gray.opacity(0.3), lineWidth: 1)
                                .background(lightGreenBg.cornerRadius(14))
                        )

                        if let error = nameError {
                            Text(error)
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 4)
                        }

                        Spacer().frame(height: 14)

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

                        Spacer().frame(height: 14)

                        // Password
                        HStack {
                            Image(systemName: "lock.fill")
                                .foregroundColor(.primaryGreen)
                                .frame(width: 20)
                            if passwordVisible {
                                TextField("Password", text: $password)
                                    .textContentType(.newPassword)
                            } else {
                                SecureField("Password", text: $password)
                                    .textContentType(.newPassword)
                            }
                            Button(action: { passwordVisible.toggle() }) {
                                Image(systemName: passwordVisible ? "eye.slash.fill" : "eye.fill")
                                    .foregroundColor(.gray)
                            }
                        }
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(passwordError != nil ? Color.red : Color.gray.opacity(0.3), lineWidth: 1)
                                .background(lightGreenBg.cornerRadius(14))
                        )

                        if let error = passwordError {
                            Text(error)
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 4)
                        }

                        // Password strength indicator
                        if !password.isEmpty {
                            HStack(spacing: 4) {
                                ForEach(0..<3, id: \.self) { index in
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(strengthColor(for: index))
                                        .frame(height: 4)
                                }
                            }
                            .padding(.top, 8)

                            Text(strengthLabel)
                                .font(.system(size: 11))
                                .foregroundColor(strengthTextColor)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 2)
                        }

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
                            .padding(.top, 14)
                        }

                        Spacer().frame(height: 24)

                        // Create Account Button
                        Button(action: {
                            authViewModel.signup(
                                email: email.trimmingCharacters(in: .whitespaces),
                                password: password,
                                fullName: fullName.trimmingCharacters(in: .whitespaces)
                            )
                        }) {
                            ZStack {
                                if authViewModel.isLoading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Text("Create Account")
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

                        Spacer().frame(height: 12)

                        Text("By signing up, you agree to our Terms of Service")
                            .font(.system(size: 12))
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 28)
                    .background(
                        RoundedRectangle(cornerRadius: 28)
                            .fill(Color.white)
                            .shadow(color: .black.opacity(0.1), radius: 12, y: 4)
                    )
                    .padding(.horizontal, 28)

                    Spacer().frame(height: 24)

                    HStack {
                        Text("Already have an account?")
                            .foregroundColor(.white.opacity(0.7))
                            .font(.system(size: 14))
                        Button("Sign In") {
                            dismiss()
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    }

                    Spacer().frame(height: 40)
                }
            }
        }
        .navigationBarHidden(true)
    }

    // MARK: - Password Strength

    private var strengthLevel: Int {
        var level = 0
        if password.count >= 8 { level += 1 }
        if password.rangeOfCharacter(from: .letters) != nil && password.rangeOfCharacter(from: .decimalDigits) != nil { level += 1 }
        if password.count >= 12 && password.rangeOfCharacter(from: CharacterSet.alphanumerics.inverted) != nil { level += 1 }
        return level
    }

    private func strengthColor(for index: Int) -> Color {
        if index < strengthLevel {
            switch strengthLevel {
            case 1: return .red
            case 2: return .orange
            case 3: return .primaryGreen
            default: return Color.gray.opacity(0.3)
            }
        }
        return Color.gray.opacity(0.3)
    }

    private var strengthLabel: String {
        switch strengthLevel {
        case 0: return "Too weak"
        case 1: return "Weak"
        case 2: return "Good"
        case 3: return "Strong"
        default: return ""
        }
    }

    private var strengthTextColor: Color {
        switch strengthLevel {
        case 1: return .red
        case 2: return .orange
        case 3: return .primaryGreen
        default: return .gray
        }
    }
}
