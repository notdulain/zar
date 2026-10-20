package com.example.zar.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ZarLightColors.accentPrimary,
    onPrimary = ZarLightColors.textOnInverse,
    primaryContainer = ZarLightColors.selectedTint,
    onPrimaryContainer = ZarLightColors.textSelected,
    secondary = ZarLightColors.accentIncome,
    onSecondary = ZarLightColors.textOnInverse,
    background = ZarLightColors.canvas,
    onBackground = ZarLightColors.textPrimary,
    surface = ZarLightColors.surface,
    onSurface = ZarLightColors.textPrimary,
    surfaceVariant = ZarLightColors.subtle,
    onSurfaceVariant = ZarLightColors.textSecondary,
    secondaryContainer = ZarLightColors.inverse,
    onSecondaryContainer = ZarLightColors.textOnInverse,
    outline = ZarLightColors.borderDefault,
    outlineVariant = ZarLightColors.borderHairline,
    inverseSurface = ZarLightColors.inverse,
    inverseOnSurface = ZarLightColors.textOnInverse,
    error = ZarPrimitives.coral700,
    onError = ZarLightColors.textOnInverse,
)

private val DarkColors = darkColorScheme(
    primary = ZarDarkColors.accentPrimary,
    onPrimary = ZarDarkColors.textOnInverse,
    primaryContainer = ZarDarkColors.selectedTint,
    onPrimaryContainer = ZarDarkColors.textSelected,
    secondary = ZarDarkColors.accentIncome,
    onSecondary = ZarDarkColors.textOnInverse,
    background = ZarDarkColors.canvas,
    onBackground = ZarDarkColors.textPrimary,
    surface = ZarDarkColors.surface,
    onSurface = ZarDarkColors.textPrimary,
    surfaceVariant = ZarDarkColors.subtle,
    onSurfaceVariant = ZarDarkColors.textSecondary,
    secondaryContainer = ZarDarkColors.inverse,
    onSecondaryContainer = ZarDarkColors.textOnInverse,
    outline = ZarDarkColors.borderDefault,
    outlineVariant = ZarDarkColors.borderHairline,
    inverseSurface = ZarDarkColors.surface,
    inverseOnSurface = ZarDarkColors.textPrimary,
    error = ZarPrimitives.coral700,
    onError = ZarDarkColors.textOnInverse,
)

private val ZarMaterialShapes = Shapes(
    extraSmall = ZarShapes.xs,
    small = ZarShapes.sm,
    medium = ZarShapes.md,
    large = ZarShapes.lg,
    extraLarge = ZarShapes.xl,
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
            shapes = ZarMaterialShapes,
            content = content,
        )
    }
}
