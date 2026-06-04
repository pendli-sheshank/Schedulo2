package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    secondary = SecondaryGreen,
    onSecondary = OnBackgroundLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    errorContainer = Color(0xFFFEF2F2),
    error = Color(0xFFDC2626)
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = SecondaryGreen,
    onPrimary = Color(0xFF003822),
    secondary = SecondaryGreen,
    onSecondary = OnBackgroundDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    errorContainer = Color(0xFF2D1516),
    error = Color(0xFFF87171)
  )

@Composable
fun MyApplicationTheme(
  themeMode: String = "system",
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
