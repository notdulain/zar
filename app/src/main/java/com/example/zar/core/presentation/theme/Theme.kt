package com.example.zar.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColors = lightColorScheme(
    primary = ZarGreen,
    background = ZarSand,
    surface = ZarSand,
    onBackground = ZarInk,
    onSurface = ZarInk,
)

private val DarkColors = darkColorScheme(
    primary = ZarGreenLight,
)

/**
 * App-wide theme. Every screen should be wrapped in this. Material 3 still drives the screens
 * built before the design system; screens built from the Figma design read [ZarTheme.colors],
 * [ZarType] and [ZarShapes] instead.
 */
@Composable
fun ZarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalZarColors provides if (darkTheme) ZarDarkColors else ZarLightColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
