package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and dimension tokens for the app.
 *
 * Adopt these in new code and migrate existing hardcoded `.dp` literals
 * opportunistically so padding, radii, and sizes stay consistent across screens
 * instead of drifting per call site. Colors already flow through [Color.kt] /
 * `MaterialTheme.colorScheme`; this is the equivalent single source for spacing.
 */
object Dimens {
    // Spacing scale (4dp base).
    val spacingXxs = 2.dp
    val spacingXs = 4.dp
    val spacingS = 8.dp
    val spacingM = 12.dp
    val spacingL = 16.dp
    val spacingXl = 24.dp
    val spacingXxl = 32.dp

    // Corner radii.
    val radiusS = 8.dp
    val radiusM = 12.dp
    val radiusCard = 16.dp
    val radiusPill = 999.dp

    // Common sizes.
    val iconS = 16.dp
    val iconM = 24.dp
    val iconL = 32.dp

    // Minimum interactive target (accessibility).
    val minTouchTarget = 48.dp

    // Standard screen edge padding.
    val screenPadding = 16.dp
}
