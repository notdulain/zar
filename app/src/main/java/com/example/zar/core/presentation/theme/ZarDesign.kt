package com.example.zar.core.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R

// Design tokens from the Zar-UI Figma file (Design System page). Names follow the Figma
// variables, e.g. text/primary -> ZarTheme.colors.textPrimary.

@Immutable
class ZarColors(
    val canvas: Color,
    val surface: Color,
    val subtle: Color,
    val selectedTint: Color,
    val borderDefault: Color,
    val borderHairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textPlaceholder: Color,
    val textSelected: Color,
    val textOnInverse: Color,
    val iconStrong: Color,
    val iconMuted: Color,
    val accentPrimary: Color,
    val accentSelected: Color,
    val scrim: Color,
    val trackOff: Color,
    /** coral/700, for validation text. */
    val danger: Color,
    /** accent/discretionary: something needs attention. */
    val attention: Color,
    /** accent/fresh: up to date. */
    val fresh: Color,
)

val ZarLightColors = ZarColors(
    canvas = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    subtle = Color(0xFFF3F3F5),
    selectedTint = Color(0xFFE9EEF3),
    borderDefault = Color(0xFFECECEE),
    borderHairline = Color(0xFFEFEFF1),
    textPrimary = Color(0xFF0E0F12),
    textSecondary = Color(0xFF5E5F66),
    textTertiary = Color(0xFF696A71),
    textPlaceholder = Color(0xFFB4B5BB),
    textSelected = Color(0xFF1E3A55),
    textOnInverse = Color(0xFFFFFFFF),
    iconStrong = Color(0xFF0E0F12),
    iconMuted = Color(0xFF8B8C93),
    accentPrimary = Color(0xFF0C1625),
    accentSelected = Color(0xFF1E3A55),
    scrim = Color(0xFF05070B),
    trackOff = Color(0xFFD9DADE),
    danger = Color(0xFFC9685A),
    attention = Color(0xFFDD8468),
    fresh = Color(0xFF8FD6C4),
)

/** Dark mode only changes the neutrals; accents are identical to light. */
val ZarDarkColors = ZarColors(
    canvas = Color(0xFF07080B),
    surface = Color(0xFF111318),
    subtle = Color(0xFF1A1D23),
    selectedTint = Color(0xFF14304A),
    borderDefault = Color(0xFF272B33),
    borderHairline = Color(0xFF20232A),
    textPrimary = Color(0xFFF3F3F5),
    textSecondary = Color(0xFFC4C5CA),
    textTertiary = Color(0xFF8B8C93),
    textPlaceholder = Color(0xFF5E5F66),
    textSelected = Color(0xFFE9EEF3),
    textOnInverse = Color(0xFFFFFFFF),
    iconStrong = Color(0xFFF3F3F5),
    iconMuted = Color(0xFF8B8C93),
    accentPrimary = Color(0xFF0C1625),
    accentSelected = Color(0xFF1E3A55),
    scrim = Color(0xFF05070B),
    trackOff = Color(0xFF343944),
    danger = Color(0xFFC9685A),
    attention = Color(0xFFDD8468),
    fresh = Color(0xFF8FD6C4),
)

val LocalZarColors = staticCompositionLocalOf { ZarLightColors }

/** Radius tokens: radius/sm, radius/lg, radius/sheet, radius/full. */
object ZarShapes {
    val sm = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(28.dp)
}

private fun geist(weight: Int) = Font(
    resId = R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val GeistFamily = FontFamily(geist(300), geist(400), geist(500))

val GeistMonoFamily = FontFamily(
    Font(
        resId = R.font.geist_mono,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

/** Text styles from the Figma text styles. Letter spacing is the Figma percentage times the size. */
object ZarType {
    val displayTitle = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-0.64).sp)
    val amountL = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = (-0.68).sp)
    val amountM = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = (-0.56).sp)
    val headingSheet = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = (-0.18).sp)
    val bodyM = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val bodyS = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = (-0.14).sp)
    val bodyCaption = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val labelButton = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val labelChip = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val labelAction = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val monoLabel = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.44.sp)
}

object ZarTheme {
    val colors: ZarColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZarColors.current
}
