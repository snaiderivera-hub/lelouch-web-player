package com.lelouch.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LelouchDarkColorScheme = darkColorScheme(
    primary = LelouchCyanAccent,
    onPrimary = LelouchBackground,
    primaryContainer = LelouchSurfaceVariant,
    onPrimaryContainer = LelouchCyanAccent,
    secondary = LelouchBlueSecondary,
    onSecondary = LelouchTextPrimary,
    background = LelouchBackground,
    onBackground = LelouchTextPrimary,
    surface = LelouchSurface,
    onSurface = LelouchTextPrimary,
    surfaceVariant = LelouchSurfaceVariant,
    onSurfaceVariant = LelouchTextSecondary,
    outline = LelouchBorder,
    error = LelouchError,
    onError = LelouchTextPrimary
)

@Composable
fun LelouchTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LelouchDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = LelouchBackground.toArgb()
                window.navigationBarColor = LelouchBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LelouchTypography,
        shapes = LelouchShapes,
        content = content
    )
}
