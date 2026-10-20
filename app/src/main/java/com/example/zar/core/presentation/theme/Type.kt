package com.example.zar.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Figma uses Geist for normal copy and amounts, and Geist Mono only for small uppercase labels.
// System families keep the APK self-contained while preserving that sans/mono distinction.
val ZarSansFont: FontFamily = FontFamily.SansSerif
val ZarMonoFont: FontFamily = FontFamily.Monospace

private fun TextStyle.sans() = copy(fontFamily = ZarSansFont)
private fun TextStyle.mono() = copy(fontFamily = ZarMonoFont)

private val Base = Typography()

/**
 * Shared type scale. Sizes follow the Figma frames:
 * labelSmall 10 (captions), labelMedium 11 (section labels), bodyMedium 14 (row titles),
 * titleMedium 16, headlineSmall 22 (goal amounts), headlineMedium 27 (summary amounts).
 */
val ZarTypography = Typography(
    displayLarge = Base.displayLarge.sans(),
    displayMedium = Base.displayMedium.sans(),
    displaySmall = Base.displaySmall.sans(),
    headlineLarge = Base.headlineLarge.sans().copy(fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = Base.headlineMedium.sans().copy(fontSize = 28.sp, lineHeight = 35.sp),
    headlineSmall = Base.headlineSmall.sans().copy(fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = Base.titleLarge.sans().copy(fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = Base.titleMedium.sans().copy(fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = Base.titleSmall.sans(),
    bodyLarge = Base.bodyLarge.sans(),
    bodyMedium = Base.bodyMedium.sans(),
    bodySmall = Base.bodySmall.sans().copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Base.labelLarge.sans(),
    labelMedium = Base.labelMedium.mono().copy(fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = Base.labelSmall.mono().copy(fontSize = 10.sp, lineHeight = 14.sp),
)
