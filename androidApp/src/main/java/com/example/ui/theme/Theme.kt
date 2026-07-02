package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

  // Keep system status bar icons readable: dark icons over the light background
  // and light icons in dark mode, even when the in-app theme overrides the
  // system setting (edge-to-edge otherwise follows the OS dark mode only).
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
