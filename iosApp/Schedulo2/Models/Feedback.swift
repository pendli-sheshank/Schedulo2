import Foundation

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

    /// A report needs something to act on, so a blank description is rejected.
    static func isValidDescription(_ description: String) -> Bool {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= maxDescription
    }

    static func isValidSteps(_ steps: String) -> Bool {
        steps.trimmingCharacters(in: .whitespacesAndNewlines).count <= maxSteps
    }
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
