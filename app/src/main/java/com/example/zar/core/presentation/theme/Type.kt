package com.example.zar.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// The Geist variable font is bundled in res/font and weighted in ZarDesign.kt; Zar has no second design family.
val ZarSansFont: FontFamily = GeistFamily

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
    labelMedium = ZarType.label,
    labelSmall = ZarType.caption,
)
