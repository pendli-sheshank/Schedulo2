package com.schedulo.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackTest {

    // --- FeedbackCategory ---

    @Test
    fun theThreeSupportedCategoriesAreValid() {
        assertTrue(FeedbackCategory.isValid(FeedbackCategory.BUG))
        assertTrue(FeedbackCategory.isValid(FeedbackCategory.FEATURE))
        assertTrue(FeedbackCategory.isValid(FeedbackCategory.OTHER))
    }

    @Test
    fun anUnknownCategoryIsRejected() {
        // The security rules compare against these literals, so anything the
        // client would send outside the set is a guaranteed write rejection.
        assertFalse(FeedbackCategory.isValid("crash"))
        assertFalse(FeedbackCategory.isValid(""))
    }

    @Test
    fun categoryMatchingIsCaseSensitive() {
        // Rules match "bug" exactly; "Bug" would be denied server-side.
        assertFalse(FeedbackCategory.isValid("Bug"))
    }

    @Test
    fun allListsEveryCategoryOnce() {
        assertEquals(listOf("bug", "feature", "other"), FeedbackCategory.ALL)
    }

    // --- FeedbackLimits.isValidDescription ---

    @Test
    fun aBlankDescriptionIsRejected() {
        // A report with nothing to act on is not worth a round trip.
        assertFalse(FeedbackLimits.isValidDescription(""))
        assertFalse(FeedbackLimits.isValidDescription("   \n\t "))
    }

    @Test
    fun anOrdinaryDescriptionIsAccepted() {
        assertTrue(FeedbackLimits.isValidDescription("The pay screen shows last week's total."))
    }

    @Test
    fun aDescriptionExactlyAtTheCapIsAccepted() {
        // The cap is inclusive here and in firestore.rules (size() <= 2000); an
        // off-by-one on either side rejects the write after the screenshot has
        // already been uploaded.
        val atCap = "x".repeat(FeedbackLimits.MAX_DESCRIPTION)
        assertTrue(FeedbackLimits.isValidDescription(atCap))
    }

    @Test
    fun aDescriptionOverTheCapIsRejected() {
        val overCap = "x".repeat(FeedbackLimits.MAX_DESCRIPTION + 1)
        assertFalse(FeedbackLimits.isValidDescription(overCap))
    }

    @Test
    fun surroundingWhitespaceDoesNotCountTowardTheCap() {
        // The client trims before sending, so a description that only exceeds
        // the cap because of padding must not be treated as too long.
        val padded = "  " + "x".repeat(FeedbackLimits.MAX_DESCRIPTION) + "  "
        assertTrue(FeedbackLimits.isValidDescription(padded))
    }

    // --- FeedbackLimits.isValidSteps ---

    @Test
    fun stepsAreOptionalSoBlankIsAccepted() {
        assertTrue(FeedbackLimits.isValidSteps(""))
    }

    @Test
    fun stepsOverTheCapAreRejected() {
        assertTrue(FeedbackLimits.isValidSteps("x".repeat(FeedbackLimits.MAX_STEPS)))
        assertFalse(FeedbackLimits.isValidSteps("x".repeat(FeedbackLimits.MAX_STEPS + 1)))
    }

    // --- Feedback defaults ---

    @Test
    fun aNewReportDefaultsToAnUntriagedBug() {
        val feedback = Feedback()
        assertEquals(FeedbackCategory.BUG, feedback.category)
        assertEquals("new", feedback.status)
        assertEquals("", feedback.screenshotUrl)
    }
}
