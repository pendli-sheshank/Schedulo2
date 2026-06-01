package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = SurfaceLight,
    secondary = SecondaryGreen,
    onSecondary = OnBackgroundLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = SurfaceDark,
    secondary = SecondaryGreen,
    onSecondary = OnBackgroundDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
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
