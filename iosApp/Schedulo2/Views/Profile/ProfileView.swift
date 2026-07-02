import SwiftUI
import LocalAuthentication
import EventKit

struct ProfileView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @EnvironmentObject var teamViewModel: TeamViewModel
    @Environment(\.dismiss) private var dismiss

    var onNavigateToInsights: () -> Void = {}
    var onNavigateToJobs: () -> Void = {}

    @State private var showEditNameAlert = false
    @State private var editNameValue = ""
    @State private var showChangePassword = false
    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var showDeleteAccount = false
    @State private var showReauthDialog = false
    @State private var deletePassword = ""

    @ObservedObject private var calendarService = CalendarService.shared
    @State private var availableCalendars: [EKCalendar] = []

    private var displayName: String {
        let name = dashboardViewModel.userName
        if !name.isEmpty { return name }
        let email = authViewModel.currentUserEmail
        let prefix = email.components(separatedBy: "@").first ?? ""
        return prefix.isEmpty ? "User" : prefix
    }

    private var initials: String {
        let name = dashboardViewModel.userName
        if !name.isEmpty {
            let parts = name.trimmingCharacters(in: .whitespaces).components(separatedBy: " ")
            if parts.count >= 2, let first = parts.first?.first, let last = parts.last?.first {
                return "\(first)\(last)".uppercased()
            }
            return String(name.prefix(2)).uppercased()
        }
        let email = authViewModel.currentUserEmail
        let prefix = email.components(separatedBy: "@").first ?? ""
        return prefix.count >= 2 ? String(prefix.prefix(2)).uppercased() : prefix.uppercased().isEmpty ? "U" : prefix.uppercased()
    }

    private var completedShifts: [Shift] {
        dashboardViewModel.shifts.filter { $0.startDate < Date() }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Profile Card
                    profileCard

                    // Stats
                    statsRow

                    // Theme
                    themeCard

                    // Notifications
                    notificationsCard

                    // Biometric Login
                    if authViewModel.biometricAvailable {
                        biometricCard
                    }

                    // Calendar Sync
                    calendarSyncCard

                    // Change Password
                    changePasswordCard

                    // Employers & Jobs
                    employersCard

                    // Insights
                    insightsCard

                    Divider().padding(.horizontal, 16)

                    // Logout
                    Button(action: {
                        dashboardViewModel.reset()
                        teamViewModel.removeAllListeners()
                        authViewModel.logout()
                        dismiss()
                    }) {
                        HStack {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                            Text("Logout")
                                .font(.system(size: 16, weight: .bold))
                        }
                        .foregroundColor(.primaryGreen)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.primaryGreen, lineWidth: 1)
                        )
                    }
                    .padding(.horizontal, 16)

                    // Danger zone
                    dangerZone

                    Spacer().frame(height: 40)
                }
            }
            .navigationTitle("Profile & Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .alert("Edit Name", isPresented: $showEditNameAlert) {
            TextField("Full Name", text: $editNameValue)
            Button("Save") {
                let trimmed = editNameValue.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty {
                    dashboardViewModel.updateUserName(trimmed)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showChangePassword) {
            changePasswordSheet
        }
        .alert("Delete Account", isPresented: $showDeleteAccount) {
            Button("Delete", role: .destructive) {
                dashboardViewModel.reset()
                teamViewModel.removeAllListeners()
                authViewModel.deleteAccount(password: nil)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will permanently delete your account and all your shift data. This action cannot be undone.")
        }
        .alert("Re-enter Password", isPresented: $showReauthDialog) {
            SecureField("Password", text: $deletePassword)
            Button("Delete", role: .destructive) {
                dashboardViewModel.reset()
                teamViewModel.removeAllListeners()
                authViewModel.deleteAccount(password: deletePassword)
                deletePassword = ""
            }
            Button("Cancel", role: .cancel) { deletePassword = "" }
        } message: {
            Text("For security, please re-enter your password to delete your account.")
        }
        .onChange(of: authViewModel.deleteState) { state in
            if state == .needsReauth {
                showDeleteAccount = false
                showReauthDialog = true
            } else if state == .success {
                dismiss()
            }
        }
    }

    // MARK: - Profile Card

    private var profileCard: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [.primaryGreen, .secondaryGreen],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 88, height: 88)
                    .overlay(
                        Circle().stroke(Color.white, lineWidth: 3)
                    )
                Text(initials)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
            }

            Button(action: {
                editNameValue = dashboardViewModel.userName
                showEditNameAlert = true
            }) {
                HStack(spacing: 6) {
                    Text(displayName)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                    Image(systemName: "pencil")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.7))
                }
            }

            if !authViewModel.currentUserEmail.isEmpty {
                Text(authViewModel.currentUserEmail)
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
            }

            if !dashboardViewModel.memberSince.isEmpty {
                Text("Member since \(dashboardViewModel.memberSince)")
                    .font(.system(size: 12))
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Color.white.opacity(0.2)))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(Color.primaryGreen)
        )
        .padding(.horizontal, 16)
    }

    // MARK: - Stats

    private var statsRow: some View {
        let totalHours = completedShifts.reduce(0) { $0 + $1.durationHours }
        let totalEarned = completedShifts.reduce(0) { $0 + $1.totalEarned }

        return HStack(spacing: 12) {
            statCard(label: "SHIFTS", value: "\(completedShifts.count)")
            statCard(label: "HOURS", value: String(format: "%.1f", totalHours))
            statCard(label: "EARNED", value: "$\(Int(totalEarned))")
        }
        .padding(.horizontal, 16)
    }

    private func statCard(label: String, value: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.primaryGreen)
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.secondary)
                .tracking(1)
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                )
        )
    }

    // MARK: - Theme

    private var themeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "paintpalette.fill")
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                Text("Appearance")
                    .font(.system(size: 16, weight: .bold))
            }

            HStack(spacing: 8) {
                ForEach([("light", "Light"), ("dark", "Dark"), ("system", "System")], id: \.0) { mode, label in
                    let selected = dashboardViewModel.themeMode == mode
                    Button(action: { dashboardViewModel.setThemeMode(mode) }) {
                        Text(label)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(selected ? .white : .primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(selected ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                            )
                    }
                }
            }
        }
        .padding(16)
        .background(settingsCardBackground)
        .padding(.horizontal, 16)
    }

    // MARK: - Notifications

    private var notificationsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "bell.fill")
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                Text("Notification Preferences")
                    .font(.system(size: 16, weight: .bold))
            }

            Toggle("Enable Shift Reminders", isOn: Binding(
                get: { dashboardViewModel.remindersEnabled },
                set: { dashboardViewModel.setRemindersEnabled($0) }
            ))
            .tint(.primaryGreen)

            if dashboardViewModel.remindersEnabled {
                Text("Default Reminder Time")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)

                HStack(spacing: 8) {
                    ForEach([(15, "15m"), (30, "30m"), (60, "1h"), (120, "2h")], id: \.0) { minutes, label in
                        let selected = dashboardViewModel.defaultReminderMinutes == minutes
                        Button(action: { dashboardViewModel.setDefaultReminderMinutes(minutes) }) {
                            Text(label)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(selected ? .white : .primary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(selected ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                                )
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(settingsCardBackground)
        .padding(.horizontal, 16)
    }

    // MARK: - Biometric Card

    private var biometricCard: some View {
        let biometryLabel = authViewModel.biometryType == .faceID ? "Face ID" : "Touch ID"
        let biometryIcon = authViewModel.biometryType == .faceID ? "faceid" : "touchid"

        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: biometryIcon)
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                Text("Biometric Login")
                    .font(.system(size: 16, weight: .bold))
            }

            Toggle("Use \(biometryLabel) to sign in", isOn: Binding(
                get: { authViewModel.biometricEnabled },
                set: { newValue in
                    if newValue {
                        authViewModel.enableBiometric { _ in }
                    } else {
                        authViewModel.disableBiometric()
                    }
                }
            ))
            .tint(.primaryGreen)

            Text("Quickly sign in using \(biometryLabel) instead of your password")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .padding(16)
        .background(settingsCardBackground)
        .padding(.horizontal, 16)
    }

    // MARK: - Calendar Sync Card

    private var calendarSyncCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "calendar")
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                Text("Calendar Sync")
                    .font(.system(size: 16, weight: .bold))
            }

            Toggle("Sync shifts to calendar", isOn: Binding(
                get: { calendarService.calendarSyncEnabled },
                set: { newValue in
                    if newValue {
                        Task {
                            let granted = await calendarService.requestAccess()
                            await MainActor.run {
                                if granted {
                                    calendarService.calendarSyncEnabled = true
                                    availableCalendars = calendarService.getAvailableCalendars()
                                    calendarService.syncAllShifts(shifts: dashboardViewModel.shifts)
                                } else {
                                    calendarService.calendarSyncEnabled = false
                                }
                            }
                        }
                    } else {
                        calendarService.removeAllSyncedEvents()
                        calendarService.calendarSyncEnabled = false
                    }
                }
            ))
            .tint(.primaryGreen)

            if calendarService.calendarSyncEnabled {
                if !availableCalendars.isEmpty {
                    Text("Sync to Calendar")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)

                    Picker("Calendar", selection: Binding(
                        get: { calendarService.selectedCalendarIdentifier ?? "" },
                        set: { newValue in
                            calendarService.selectedCalendarIdentifier = newValue.isEmpty ? nil : newValue
                        }
                    )) {
                        Text("Default Calendar").tag("")
                        ForEach(availableCalendars, id: \.calendarIdentifier) { calendar in
                            Text(calendar.title).tag(calendar.calendarIdentifier)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(.primaryGreen)
                }
            }

            Text("Automatically add your shifts to the device calendar")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .padding(16)
        .background(settingsCardBackground)
        .padding(.horizontal, 16)
        .onAppear {
            if calendarService.calendarSyncEnabled {
                availableCalendars = calendarService.getAvailableCalendars()
            }
        }
    }

    // MARK: - Change Password Card

    private var changePasswordCard: some View {
        Button(action: { showChangePassword = true }) {
            HStack(spacing: 8) {
                Image(systemName: "lock.fill")
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                VStack(alignment: .leading) {
                    Text("Change Password")
                        .font(.system(size: 16, weight: .bold))
                    Text("Update your account password")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            .padding(16)
            .background(settingsCardBackground)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }

    // MARK: - Employers Card

    private var employersCard: some View {
        Button(action: onNavigateToJobs) {
            HStack(spacing: 8) {
                Image(systemName: "briefcase.fill")
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                VStack(alignment: .leading) {
                    Text("Employers & Jobs (\(dashboardViewModel.jobs.count))")
                        .font(.system(size: 16, weight: .bold))
                    Text(dashboardViewModel.jobs.isEmpty ? "Add employers to track shifts" : dashboardViewModel.jobs.map(\.title).joined(separator: " · "))
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            .padding(16)
            .background(settingsCardBackground)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }

    // MARK: - Insights Card

    private var insightsCard: some View {
        Button(action: onNavigateToInsights) {
            HStack(spacing: 8) {
                Image(systemName: "chart.line.uptrend.xyaxis")
                    .foregroundColor(.primaryGreen)
                    .font(.system(size: 16))
                VStack(alignment: .leading) {
                    Text("Earnings Insights")
                        .font(.system(size: 16, weight: .bold))
                    Text("Charts, trends & analytics")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            .padding(16)
            .background(settingsCardBackground)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }

    // MARK: - Danger Zone

    private var dangerZone: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Danger Zone")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.red)

            Button(action: { showDeleteAccount = true }) {
                HStack {
                    Image(systemName: "trash.fill")
                    Text("Delete Account")
                        .font(.system(size: 16, weight: .bold))
                }
                .foregroundColor(.red)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.red, lineWidth: 1)
                )
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    // MARK: - Change Password Sheet

    private var changePasswordSheet: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("Current Password", text: $currentPassword)
                    SecureField("New Password", text: $newPassword)
                    SecureField("Confirm New Password", text: $confirmPassword)
                }

                if !newPassword.isEmpty {
                    Section {
                        Text("Min 8 chars, letters and numbers")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }

                if !confirmPassword.isEmpty && confirmPassword != newPassword {
                    Section {
                        Text("Passwords do not match")
                            .font(.system(size: 13))
                            .foregroundColor(.red)
                    }
                }

                if let error = authViewModel.passwordChangeError {
                    Section {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundColor(.red)
                    }
                }

                if authViewModel.passwordChangeSuccess {
                    Section {
                        Text("Password changed successfully!")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.primaryGreen)
                    }
                }
            }
            .navigationTitle("Change Password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        resetPasswordForm()
                        showChangePassword = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Change") {
                        authViewModel.changePassword(currentPassword: currentPassword, newPassword: newPassword)
                    }
                    .disabled(
                        currentPassword.isEmpty || newPassword.isEmpty ||
                        newPassword != confirmPassword || authViewModel.isLoading
                    )
                    .foregroundColor(.primaryGreen)
                }
            }
            .onChange(of: authViewModel.passwordChangeSuccess) { success in
                if success {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        resetPasswordForm()
                        showChangePassword = false
                    }
                }
            }
        }
    }

    private func resetPasswordForm() {
        currentPassword = ""
        newPassword = ""
        confirmPassword = ""
        authViewModel.resetPasswordChangeState()
    }

    private var settingsCardBackground: some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(Color(UIColor.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
            )
    }
}
