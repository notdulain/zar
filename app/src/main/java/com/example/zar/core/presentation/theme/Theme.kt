package com.example.zar.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ZarInk,
    onPrimary = ZarWhite,
    background = ZarWhite,
    onBackground = ZarInk,
    surface = ZarWhite,
    onSurface = ZarInk,
    surfaceVariant = ZarTrack,
    onSurfaceVariant = ZarMuted,
    secondaryContainer = ZarTrack,
    onSecondaryContainer = ZarInk,
    outline = ZarBorder,
    outlineVariant = ZarBorder,
    inverseSurface = ZarInk,
    inverseOnSurface = ZarWhite,
)

private val DarkColors = darkColorScheme(
    primary = ZarWhite,
    onPrimary = ZarInk,
    background = ZarInk,
    onBackground = ZarWhite,
    surface = ZarInk,
    onSurface = ZarWhite,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = ZarSubtle,
    secondaryContainer = Color(0xFF1C1C1C),
    onSecondaryContainer = ZarWhite,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF3A3A3A),
    inverseSurface = ZarWhite,
    inverseOnSurface = ZarInk,
)

/** App-wide Material 3 theme. Every screen should be wrapped in this. */
@Composable
fun ZarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ZarTypography,
        content = content,
    )
}
