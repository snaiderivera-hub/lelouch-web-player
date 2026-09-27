package com.lelouch.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

data class TvFocusTokens(
    val focusedScale: Float = 1.08f,
    val focusedBorderWidth: Dp = 2.5.dp,
    val focusedBorderColor: Color = LelouchCyanAccent,
    val focusedGlowColor: Color = LelouchCyanGlow,
    val unfocusedAlpha: Float = 0.82f
)

val LocalTvFocusTokens = staticCompositionLocalOf { TvFocusTokens() }

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
private val LelouchTvDarkColorScheme = tvDarkColorScheme(
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
    border = LelouchBorder,
    error = LelouchError,
    onError = LelouchTextPrimary
)

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LelouchTvTheme(
    focusTokens: TvFocusTokens = TvFocusTokens(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTvFocusTokens provides focusTokens
    ) {
        TvMaterialTheme(
            colorScheme = LelouchTvDarkColorScheme,
            typography = androidx.tv.material3.Typography(),
            content = content
        )
    }
}
