package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.schedulo.shared.util.DesignTokens

// Brand + surface palette derived from the shared cross-platform source of truth
// (shared/.../util/DesignTokens.kt) so Android and iOS cannot drift apart.
val PrimaryGreen = Color(DesignTokens.PrimaryGreen)
val SecondaryGreen = Color(DesignTokens.SecondaryGreen)
val AccentBlue = Color(DesignTokens.AccentBlue)
val AccentOrange = Color(DesignTokens.AccentOrange)

val BackgroundLight = Color(DesignTokens.LightBackground)
val SurfaceLight = Color(DesignTokens.LightSurface)
val OutlineLight = Color(DesignTokens.LightOutline)

val BackgroundDark = Color(DesignTokens.DarkBackground)
val SurfaceDark = Color(DesignTokens.DarkSurface)
val SurfaceVariantDark = Color(DesignTokens.DarkSurfaceVariant)
val OutlineDark = Color(DesignTokens.DarkOutline)

// Palette values not (yet) represented in the shared token set.
val OnBackgroundLight = Color(0xFF1A1C1E)
val SurfaceVariantLight = Color(0xFFF0F1F5)
val OnSurfaceVariantLight = Color(0xFF6B7280)
val OnBackgroundDark = Color(0xFFE8EAED)
val OnSurfaceVariantDark = Color(0xFF9CA3AF)

// Semantic accents used across feature screens. Named here so the same value is
// not re-hardcoded as a literal in individual composables.
val AlarmOrange = Color(0xFFF97316)
val DestructiveRed = Color(0xFFEF4444)
val DarkGreenGradientEnd = Color(0xFF1B4332)
val WarningAmber = Color(0xFFF59E0B)
val WarningAmberContainer = Color(0xFFFEF3C7)
val WarningAmberText = Color(0xFF92400E)
