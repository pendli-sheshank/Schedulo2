import Foundation
import Combine
import FirebaseAuth
import LocalAuthentication

// MARK: - State Enums

enum AuthState: Equatable {
    case idle
    case loading
    case authenticated
    case error(String)
}

enum PasswordChangeState: Equatable {
    case idle
    case loading
    case success
    case error(String)
}

enum ResetState: Equatable {
    case idle
    case sent
    case error(String)
}

enum DeleteAccountState: Equatable {
    case idle
    case loading
    case needsReauth
    case success
    case error(String)
}

// MARK: - AuthViewModel

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var authState: AuthState = .idle
    @Published var currentUserEmail: String = ""
    @Published var passwordChangeState: PasswordChangeState = .idle
    @Published var resetState: ResetState = .idle
    @Published var deleteState: DeleteAccountState = .idle
    @Published var biometricAvailable: Bool = false
    @Published var biometricEnabled: Bool {
        didSet { UserDefaults.standard.set(biometricEnabled, forKey: "biometricEnabled") }
    }
    @Published var shouldPromptBiometric: Bool = false
    // True when the app cold-started with a persisted Firebase session and biometric
    // login is enabled, so the dashboard must stay gated until the prompt succeeds.
    @Published var requiresBiometricUnlock: Bool = false

    private let service = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Derived UI State

    var isAuthenticated: Bool {
        authState == .authenticated
    }

    var isLoading: Bool {
        authState == .loading || passwordChangeState == .loading || deleteState == .loading
    }

    var errorMessage: String? {
        if case .error(let message) = authState { return message }
        return nil
    }

    var resetErrorMessage: String? {
        if case .error(let message) = resetState { return message }
        return nil
    }

    var passwordChangeError: String? {
        if case .error(let message) = passwordChangeState { return message }
        return nil
    }

    var passwordChangeSuccess: Bool {
        passwordChangeState == .success
    }

    var biometryType: LABiometryType {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.biometryType
    }

    init() {
        biometricEnabled = UserDefaults.standard.bool(forKey: "biometricEnabled")
        checkBiometricAvailability()

        // Set initial state
        if let user = service.currentUser {
            currentUserEmail = user.email ?? ""
            authState = .authenticated
            if biometricEnabled {
                requiresBiometricUnlock = true
            }
        }

        // Listen for auth state changes
        service.authStateSubject
            .receive(on: DispatchQueue.main)
            .sink { [weak self] user in
                guard let self = self else { return }
                if let user = user {
                    self.currentUserEmail = user.email ?? ""
                    self.authState = .authenticated
                } else {
                    self.currentUserEmail = ""
                    if self.authState == .authenticated {
                        self.authState = .idle
                    }
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Login

    func login(email: String, password: String) {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidEmail(trimmed) else {
            authState = .error("Please enter a valid email address.")
            return
        }
        authState = .loading
        Task {
            do {
                try await service.signIn(email: trimmed, password: password)
                authState = .authenticated
                currentUserEmail = service.currentUser?.email ?? trimmed
                if biometricAvailable && !biometricEnabled {
                    shouldPromptBiometric = true
                }
            } catch {
                authState = .error(error.localizedDescription)
            }
        }
    }

    // MARK: - Signup

    func signup(email: String, password: String, fullName: String) {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)

        guard isValidEmail(trimmed) else {
            authState = .error("Please enter a valid email address.")
            return
        }
        guard password.count >= 8,
              password.contains(where: { $0.isLetter }),
              password.contains(where: { $0.isNumber }) else {
            authState = .error("Password must be at least 8 characters with letters and numbers.")
            return
        }
        guard !trimmedName.isEmpty, trimmedName.count <= 100 else {
            authState = .error("Please enter a valid name (max 100 characters).")
            return
        }

        authState = .loading
        Task {
            do {
                try await service.signUp(email: trimmed, password: password, fullName: trimmedName)
                authState = .authenticated
            } catch {
                authState = .error(error.localizedDescription)
            }
        }
    }

    // MARK: - Logout

    func logout() {
        do {
            try service.signOut()
            authState = .idle
            requiresBiometricUnlock = false
        } catch {
            authState = .error("Failed to logout")
        }
    }

    // MARK: - Delete Account

    func deleteAccount(password: String?) {
        deleteState = .loading
        Task {
            do {
                try await service.deleteAccount(password: password)
                deleteState = .success
                authState = .idle
            } catch let error as NSError {
                if error.code == AuthErrorCode.requiresRecentLogin.rawValue {
                    deleteState = .needsReauth
                } else {
                    deleteState = .error(error.localizedDescription)
                }
            }
        }
    }

    func resetDeleteState() {
        deleteState = .idle
    }

    // MARK: - Change Password

    func changePassword(currentPassword: String, newPassword: String) {
        guard newPassword.count >= 8,
              newPassword.contains(where: { $0.isLetter }),
              newPassword.contains(where: { $0.isNumber }) else {
            passwordChangeState = .error("New password must be at least 8 characters with letters and numbers.")
            return
        }
        passwordChangeState = .loading
        Task {
            do {
                try await service.changePassword(currentPassword: currentPassword, newPassword: newPassword)
                passwordChangeState = .success
            } catch {
                passwordChangeState = .error(error.localizedDescription)
            }
        }
    }

    func resetPasswordChangeState() {
        passwordChangeState = .idle
    }

    // MARK: - Password Reset

    func sendPasswordReset(email: String) {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidEmail(trimmed) else {
            resetState = .error("Please enter a valid email address.")
            return
        }
        Task {
            do {
                try await service.sendPasswordReset(email: trimmed)
                resetState = .sent
            } catch {
                resetState = .error(error.localizedDescription)
            }
        }
    }

    func resetResetState() {
        resetState = .idle
    }

    // MARK: - Biometric Authentication

    func checkBiometricAvailability() {
        let context = LAContext()
        var error: NSError?
        biometricAvailable = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    func authenticateWithBiometric() {
        let context = LAContext()
        let reason = "Log in to Schedulo"

        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, error in
            DispatchQueue.main.async {
                if success {
                    if self.requiresBiometricUnlock {
                        // Already-authenticated session was gated behind the lock screen.
                        self.requiresBiometricUnlock = false
                    } else if let user = self.service.currentUser {
                        // Biometric succeeded — restore the existing Firebase session
                        self.currentUserEmail = user.email ?? ""
                        self.authState = .authenticated
                    } else {
                        self.authState = .error("No saved session. Please sign in with email and password.")
                    }
                } else {
                    if let laError = error as? LAError, laError.code != .userCancel {
                        self.authState = .error("Biometric authentication failed.")
                    }
                }
            }
        }
    }

    func enableBiometric(completion: @escaping (Bool) -> Void) {
        let context = LAContext()
        let reason = "Confirm your identity to enable biometric login"

        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, _ in
            DispatchQueue.main.async {
                if success {
                    self.biometricEnabled = true
                }
                completion(success)
            }
        }
    }

    func disableBiometric() {
        biometricEnabled = false
    }

    // MARK: - Helpers

    private func isValidEmail(_ email: String) -> Bool {
        let pattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        return NSPredicate(format: "SELF MATCHES %@", pattern).evaluate(with: email)
    }
}
