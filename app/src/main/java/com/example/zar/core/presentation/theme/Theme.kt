package com.example.zar.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

/** App-wide Material 3 theme. Every screen should be wrapped in this. */
@Composable
fun ZarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
