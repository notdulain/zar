package com.example.zar.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// The design uses a monospace face. The system monospace keeps the APK free of font files; swap
// `ZarFont` for a bundled Roboto Mono (res/font) to match Figma exactly.
val ZarFont: FontFamily = FontFamily.Monospace

private fun TextStyle.mono() = copy(fontFamily = ZarFont)

private val Base = Typography()

/**
 * Shared type scale. Sizes follow the Figma frames:
 * labelSmall 10 (captions), labelMedium 11 (section labels), bodyMedium 14 (row titles),
 * titleMedium 16, headlineSmall 22 (goal amounts), headlineMedium 27 (summary amounts).
 */
val ZarTypography = Typography(
    displayLarge = Base.displayLarge.mono(),
    displayMedium = Base.displayMedium.mono(),
    displaySmall = Base.displaySmall.mono(),
    headlineLarge = Base.headlineLarge.mono().copy(fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = Base.headlineMedium.mono().copy(fontSize = 27.sp, lineHeight = 35.sp),
    headlineSmall = Base.headlineSmall.mono().copy(fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = Base.titleLarge.mono().copy(fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = Base.titleMedium.mono().copy(fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = Base.titleSmall.mono(),
    bodyLarge = Base.bodyLarge.mono(),
    bodyMedium = Base.bodyMedium.mono(),
    bodySmall = Base.bodySmall.mono().copy(fontSize = 10.sp, lineHeight = 16.sp),
    labelLarge = Base.labelLarge.mono(),
    labelMedium = Base.labelMedium.mono().copy(fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = Base.labelSmall.mono().copy(fontSize = 10.sp, lineHeight = 14.sp),
)
