import Foundation
import Combine
import FirebaseAuth

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

    private let service = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()

    init() {
        // Set initial state
        if let user = service.currentUser {
            currentUserEmail = user.email ?? ""
            authState = .authenticated
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
        } catch {
            authState = .error("Failed to logout")
        }
    }

    // MARK: - Delete Account

    func deleteAccount(password: String) {
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

    // MARK: - Helpers

    private func isValidEmail(_ email: String) -> Bool {
        let pattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        return NSPredicate(format: "SELF MATCHES %@", pattern).evaluate(with: email)
    }
}
