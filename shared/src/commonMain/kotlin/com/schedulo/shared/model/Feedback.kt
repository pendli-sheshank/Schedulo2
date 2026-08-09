package com.schedulo.shared.model

/**
 * A bug report or suggestion submitted from the in-app feedback screen.
 *
 * Submissions are immutable once written: the security rules deny update and
 * delete, so a report can't change out from under whoever is triaging it. That
 * also means the screenshot has to be uploaded before the document is created —
 * there is no second write to attach the URL afterwards.
 */
data class Feedback(
    var id: String = "",
    var userId: String = "",
    var userEmail: String = "",
    var category: String = FeedbackCategory.BUG,
    var description: String = "",
    var stepsToReproduce: String = "",
    var screenshotUrl: String = "",
    var appVersion: String = "",
    var platform: String = "",
    var osVersion: String = "",
    var deviceModel: String = "",
    var status: String = "new",
    var createdAt: Long = 0
)

/**
 * The categories a report can be filed under.
 *
 * Held as plain strings rather than an enum to match the other Firestore-backed
 * models ([TeamTask.status], [SwapRequest.status]), and because the security
 * rules compare against these exact literals — an enum would put the canonical
 * spelling one conversion away from the value that actually gets validated.
 */
object FeedbackCategory {
    const val BUG = "bug"
    const val FEATURE = "feature"
    const val OTHER = "other"

    val ALL = listOf(BUG, FEATURE, OTHER)

    fun isValid(category: String): Boolean = category in ALL
}

/**
 * Length limits for the free-text fields of a report.
 *
 * These are duplicated in `firestore.rules` and mirrored in the iOS Swift code,
 * which is exactly why they live here: a client that lets the user type past the
 * server's limit turns a submission into a rules rejection at the last step,
 * after the screenshot has already been uploaded.
 */
object FeedbackLimits {
    const val MAX_DESCRIPTION = 2000
    const val MAX_STEPS = 2000

    /** A report needs something to act on, so a blank description is rejected. */
    fun isValidDescription(description: String): Boolean {
        val trimmed = description.trim()
        return trimmed.isNotEmpty() && trimmed.length <= MAX_DESCRIPTION
    }

    fun isValidSteps(steps: String): Boolean = steps.trim().length <= MAX_STEPS
}
