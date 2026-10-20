package com.example.zar.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Both variable fonts are bundled in res/font and weighted in ZarDesign.kt.
val ZarSansFont: FontFamily = GeistFamily
val ZarMonoFont: FontFamily = GeistMonoFamily

/** Material slots point at the same Figma styles exposed explicitly by [ZarType]. */
val ZarTypography = Typography(
    displayLarge = ZarType.amountXL,
    displayMedium = ZarType.amountL,
    displaySmall = ZarType.displayTitle,
    headlineLarge = ZarType.amountL,
    headlineMedium = ZarType.amountM,
    headlineSmall = ZarType.amountS,
    titleLarge = ZarType.headingSheet,
    titleMedium = ZarType.bodyL,
    titleSmall = ZarType.labelButton,
    bodyLarge = ZarType.bodyL,
    bodyMedium = ZarType.bodyM,
    bodySmall = ZarType.bodyS,
    labelLarge = ZarType.labelButton,
    labelMedium = ZarType.monoLabel,
    labelSmall = ZarType.monoCaption,
)
