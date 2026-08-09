import SwiftUI
import UIKit
import MessageUI
import PhotosUI
import FirebaseAuth
import FirebaseStorage

/// Where bug reports are emailed. Every report is also stored in Firestore, so a
/// change here only affects the mail hand-off, not the record of the submission.
let developerFeedbackEmail = "sheshank3336@gmail.com"

/// Mirrors `FeedbackCategory` / `FeedbackLimits` in the KMP shared module. The
/// caps have to match `firestore.rules` exactly — a client that lets the user
/// type past the server's limit turns a submission into a rules rejection at the
/// last step, after the screenshot has already been uploaded.
enum FeedbackCategory: String, CaseIterable {
    case bug
    case feature
    case other

    var label: String {
        switch self {
        case .bug: return "Bug"
        case .feature: return "Feature request"
        case .other: return "Other"
        }
    }
}

enum FeedbackLimits {
    static let maxDescription = 2000
    static let maxSteps = 2000
}

struct FeedbackReport {
    var id: String = UUID().uuidString
    var userId: String = ""
    var userEmail: String = ""
    var category: String = FeedbackCategory.bug.rawValue
    var description: String = ""
    var stepsToReproduce: String = ""
    var screenshotUrl: String = ""
    var appVersion: String = ""
    var platform: String = "ios"
    var osVersion: String = ""
    var deviceModel: String = ""
    var status: String = "new"
    var createdAt: Int64 = 0

    var categoryLabel: String {
        FeedbackCategory(rawValue: category)?.label ?? "Other"
    }

    /// The plain-text report that goes into the developer's inbox.
    var emailBody: String {
        var lines: [String] = [description, ""]
        if !stepsToReproduce.isEmpty {
            lines.append("Steps to reproduce:")
            lines.append(stepsToReproduce)
            lines.append("")
        }
        if !screenshotUrl.isEmpty {
            lines.append("Screenshot: \(screenshotUrl)")
            lines.append("")
        }
        lines.append("---")
        lines.append("Category: \(categoryLabel)")
        lines.append("App version: \(appVersion)")
        lines.append("Device: \(deviceModel)")
        lines.append("OS: \(osVersion)")
        lines.append("Reported by: \(userEmail.isEmpty ? "unknown" : userEmail) (\(userId))")
        lines.append("Report ID: \(id)")
        return lines.joined(separator: "\n")
    }

    var emailSubject: String {
        "Shifnex \(categoryLabel) — \(String(id.prefix(8)))"
    }
}

@MainActor
final class FeedbackViewModel: ObservableObject {
    @Published var isSubmitting = false
    @Published var errorMessage: String?
    /// Set once the report is safely in Firestore; drives the confirmation screen.
    @Published var submitted: FeedbackReport?

    private let service = FirebaseService.shared
    private let storageRef = Storage.storage().reference()

    func submit(category: FeedbackCategory, description: String, steps: String, screenshot: UIImage?) {
        guard let uid = service.currentUserId else {
            errorMessage = "Please sign in to send feedback."
            return
        }
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= FeedbackLimits.maxDescription else {
            errorMessage = "Please describe the issue before sending."
            return
        }

        isSubmitting = true
        errorMessage = nil

        var report = FeedbackReport()
        report.userId = uid
        report.userEmail = service.currentUser?.email ?? ""
        report.category = category.rawValue
        report.description = trimmed
        report.stepsToReproduce = steps.trimmingCharacters(in: .whitespacesAndNewlines)
        report.appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        report.osVersion = "iOS \(UIDevice.current.systemVersion)"
        report.deviceModel = UIDevice.current.model
        report.createdAt = Int64(Date().timeIntervalSince1970 * 1000)

        guard let screenshot = screenshot else {
            write(report)
            return
        }
        guard let data = compressImageForUpload(screenshot) else {
            isSubmitting = false
            errorMessage = "Failed to process screenshot."
            return
        }

        // The screenshot has to land in Storage before the document is written:
        // feedback documents are immutable, so there is no second write to attach
        // the URL afterwards.
        let ref = storageRef.child("feedback_screenshots/\(uid)/\(report.id).jpg")
        ref.putData(data, metadata: nil) { [weak self] _, error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.isSubmitting = false
                    self?.errorMessage = "Failed to upload screenshot: \(error.localizedDescription)"
                }
                return
            }
            ref.downloadURL { url, urlError in
                DispatchQueue.main.async {
                    guard let url = url else {
                        self?.isSubmitting = false
                        self?.errorMessage = "Failed to attach screenshot: \(urlError?.localizedDescription ?? "unknown error")"
                        return
                    }
                    var withScreenshot = report
                    withScreenshot.screenshotUrl = url.absoluteString
                    self?.write(withScreenshot)
                }
            }
        }
    }

    private func write(_ report: FeedbackReport) {
        service.submitFeedback(report) { [weak self] error in
            self?.isSubmitting = false
            if let error = error {
                self?.errorMessage = "Failed to send feedback: \(error.localizedDescription)"
            } else {
                self?.submitted = report
            }
        }
    }
}

struct SendFeedbackView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = FeedbackViewModel()

    @State private var category: FeedbackCategory = .bug
    @State private var description = ""
    @State private var steps = ""
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var screenshot: UIImage?
    @State private var showMailComposer = false
    @State private var mailUnavailable = false

    private var canSubmit: Bool {
        !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !viewModel.isSubmitting
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let report = viewModel.submitted {
                    confirmation(for: report)
                } else {
                    form
                }
            }
            .navigationTitle("Send Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(viewModel.submitted == nil ? "Cancel" : "Done") { dismiss() }
                }
            }
            .onChange(of: selectedPhoto) { item in
                guard let item = item else { return }
                item.loadTransferable(type: Data.self) { result in
                    if case .success(let data) = result, let data = data, let image = UIImage(data: data) {
                        DispatchQueue.main.async { screenshot = image }
                    }
                }
            }
            // Reaching the confirmation state hands the report straight to the
            // mail app; the Firestore record already exists either way.
            .onChange(of: viewModel.submitted?.id) { newId in
                guard newId != nil else { return }
                if MFMailComposeViewController.canSendMail() {
                    showMailComposer = true
                } else {
                    openMailFallback()
                }
            }
            .sheet(isPresented: $showMailComposer) {
                if let report = viewModel.submitted {
                    MailComposeView(
                        recipient: developerFeedbackEmail,
                        subject: report.emailSubject,
                        body: report.emailBody
                    )
                }
            }
        }
    }

    // MARK: - Form

    private var form: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Found a bug or have an idea? Tell us what happened and it goes straight to the developer.")
                .font(.system(size: 14))
                .foregroundColor(.secondary)

            VStack(alignment: .leading, spacing: 8) {
                Text("What kind of feedback is this?")
                    .font(.system(size: 16, weight: .bold))
                HStack(spacing: 8) {
                    ForEach(FeedbackCategory.allCases, id: \.self) { option in
                        Button(action: { category = option }) {
                            Text(option.label)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(category == option ? .white : .primary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(category == option ? Color.primaryGreen : Color(UIColor.secondarySystemBackground))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("What went wrong?")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
                TextEditor(text: $description)
                    .frame(minHeight: 120)
                    .padding(4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(UIColor.separator), lineWidth: 1)
                    )
                    .onChange(of: description) { newVal in
                        if newVal.count > FeedbackLimits.maxDescription {
                            description = String(newVal.prefix(FeedbackLimits.maxDescription))
                        }
                    }
                Text("\(description.count) / \(FeedbackLimits.maxDescription)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Steps to reproduce (optional)")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
                TextEditor(text: $steps)
                    .frame(minHeight: 80)
                    .padding(4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(UIColor.separator), lineWidth: 1)
                    )
                    .onChange(of: steps) { newVal in
                        if newVal.count > FeedbackLimits.maxSteps {
                            steps = String(newVal.prefix(FeedbackLimits.maxSteps))
                        }
                    }
            }

            screenshotRow

            Text("Your app version, device model and account email are included so the report can be reproduced.")
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.system(size: 13))
                    .foregroundColor(.red)
            }

            if mailUnavailable {
                Text("Your report was saved, but no email app is set up on this device.")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            }

            Button(action: {
                viewModel.submit(category: category, description: description, steps: steps, screenshot: screenshot)
            }) {
                ZStack {
                    if viewModel.isSubmitting {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("Send Feedback").font(.system(size: 16, weight: .bold))
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(canSubmit ? Color.primaryGreen : Color.primaryGreen.opacity(0.4))
                )
                .foregroundColor(.white)
            }
            .disabled(!canSubmit)
        }
        .padding(16)
    }

    private var screenshotRow: some View {
        HStack(spacing: 12) {
            PhotosPicker(selection: $selectedPhoto, matching: .images) {
                HStack(spacing: 12) {
                    if let screenshot = screenshot {
                        Image(uiImage: screenshot)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 48, height: 48)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    } else {
                        Image(systemName: "paperclip")
                            .font(.system(size: 16))
                            .foregroundColor(.primaryGreen)
                            .frame(width: 48, height: 48)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(screenshot == nil ? "Attach a screenshot" : "Screenshot attached")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.primary)
                        Text(screenshot == nil ? "Optional, but it helps a lot" : "Tap to choose a different image")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
            }
            .buttonStyle(.plain)

            if screenshot != nil {
                Button(action: {
                    screenshot = nil
                    selectedPhoto = nil
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                )
        )
    }

    // MARK: - Confirmation

    private func confirmation(for report: FeedbackReport) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundColor(.primaryGreen)
                .padding(.top, 32)
            Text("Report submitted")
                .font(.system(size: 20, weight: .bold))
            Text(mailUnavailable
                 ? "Thanks — your report was saved. No email app is set up on this device, so the developer will pick it up from the report list."
                 : "Thanks — your report was saved and handed to your email app. Send the draft to finish delivering it.")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button(action: {
                if MFMailComposeViewController.canSendMail() {
                    showMailComposer = true
                } else {
                    openMailFallback()
                }
            }) {
                HStack {
                    Image(systemName: "envelope.fill")
                    Text("Email it again").font(.system(size: 16, weight: .semibold))
                }
                .foregroundColor(.primaryGreen)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.primaryGreen, lineWidth: 1)
                )
            }

            Button(action: { dismiss() }) {
                Text("Done")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.primaryGreen))
            }
        }
        .padding(16)
    }

    /// No in-app composer available — hand the report to whatever app claims
    /// mailto:. If nothing does, the Firestore record is still the source of truth.
    private func openMailFallback() {
        guard let report = viewModel.submitted else { return }
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = developerFeedbackEmail
        components.queryItems = [
            URLQueryItem(name: "subject", value: report.emailSubject),
            URLQueryItem(name: "body", value: report.emailBody),
        ]
        guard let url = components.url else {
            mailUnavailable = true
            return
        }
        UIApplication.shared.open(url) { opened in
            mailUnavailable = !opened
        }
    }
}

/// Bridges `MFMailComposeViewController` into SwiftUI, in the same style as the
/// `ShareSheet` wrapper used by the pay export.
struct MailComposeView: UIViewControllerRepresentable {
    let recipient: String
    let subject: String
    let body: String

    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let controller = MFMailComposeViewController()
        controller.mailComposeDelegate = context.coordinator
        controller.setToRecipients([recipient])
        controller.setSubject(subject)
        controller.setMessageBody(body, isHTML: false)
        return controller
    }

    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onFinish: { dismiss() })
    }

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        private let onFinish: () -> Void

        init(onFinish: @escaping () -> Void) {
            self.onFinish = onFinish
        }

        func mailComposeController(
            _ controller: MFMailComposeViewController,
            didFinishWith result: MFMailComposeResult,
            error: Error?
        ) {
            onFinish()
        }
    }
}
