package com.example.zar.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ZarInk,
    onPrimary = ZarWhite,
    background = ZarCanvas,
    onBackground = ZarInk,
    surface = ZarWhite,
    onSurface = ZarInk,
    surfaceVariant = ZarTrack,
    onSurfaceVariant = ZarMuted,
    secondaryContainer = ZarNavy,
    onSecondaryContainer = ZarWhite,
    outline = ZarBorder,
    outlineVariant = ZarBorder,
    inverseSurface = ZarNavy,
    inverseOnSurface = ZarWhite,
)

private val DarkColors = darkColorScheme(
    primary = ZarWhite,
    onPrimary = ZarNavy,
    background = ZarNavy,
    onBackground = ZarWhite,
    surface = ZarNavy,
    onSurface = ZarWhite,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = ZarSubtle,
    secondaryContainer = Color(0xFF1C1C1C),
    onSecondaryContainer = ZarWhite,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF3A3A3A),
    inverseSurface = ZarWhite,
    inverseOnSurface = ZarNavy,
)

/** App-wide Material 3 theme. Every screen should be wrapped in this. */
@Composable
fun ZarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalZarColors provides if (darkTheme) ZarDarkColors else ZarLightColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ZarTypography,
            content = content,
        )
    }
}
