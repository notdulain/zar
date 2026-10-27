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
import androidx.compose.ui.unit.sp
import com.example.zar.R

// Design tokens from the Zar-UI Figma file (Design System page). Names follow the Figma
// variables, e.g. text/primary -> ZarTheme.colors.textPrimary.

/** Raw palette values. Screen code should normally consume semantic colors instead. */
object ZarPrimitives {
    val gray0 = Color(0xFFFFFFFF)
    val gray50 = Color(0xFFF5F5F5)
    val gray100 = Color(0xFFF3F3F5)
    val gray150 = Color(0xFFEFEFF1)
    val gray200 = Color(0xFFECECEE)
    val gray250 = Color(0xFFE6E6EA)
    val gray300 = Color(0xFFD9DADE)
    val gray400 = Color(0xFFC4C5CA)
    val gray500 = Color(0xFFB4B5BB)
    val gray600 = Color(0xFF8B8C93)
    val gray650 = Color(0xFF7A7B82)
    val gray680 = Color(0xFF696A71)
    val gray700 = Color(0xFF5E5F66)
    val gray900 = Color(0xFF0E0F12)
    val gray950 = Color(0xFF05070B)
    val navy50 = Color(0xFFE9EEF3)
    val navy300 = Color(0xFF5A86A6)
    val navy400 = Color(0xFF41799C)
    val navy500 = Color(0xFF26506A)
    val navy600 = Color(0xFF1E3A55)
    val navy700 = Color(0xFF14304A)
    val navy800 = Color(0xFF0B1524)
    val navy900 = Color(0xFF0C1625)
    val teal50 = Color(0xFFE6F1F1)
    val teal300 = Color(0xFF5FB3B9)
    val teal500 = Color(0xFF2E7F86)
    val teal600 = Color(0xFF2A7A80)
    val teal700 = Color(0xFF23676C)
    val teal800 = Color(0xFF174257)
    val teal900 = Color(0xFF10292B)
    val coral300 = Color(0xFFF2B596)
    val coral500 = Color(0xFFDD8468)
    val coral700 = Color(0xFFC9685A)
    val coral800 = Color(0xFFA9573F)
    val plum700 = Color(0xFF7E3D52)
    val plum900 = Color(0xFF2A1828)
    val mint300 = Color(0xFF8FD6C4)
    val night950 = Color(0xFF07080B)
    val night900 = Color(0xFF111318)
    val night850 = Color(0xFF1A1D23)
    val night800 = Color(0xFF20232A)
    val night750 = Color(0xFF272B33)
    val night700 = Color(0xFF343944)
    val night600 = Color(0xFF4A4F5A)
}

@Immutable
class ZarColors(
    val canvas: Color,
    val surface: Color,
    val subtle: Color,
    val inverse: Color,
    val selectedTint: Color,
    val incomeTint: Color,
    val borderDefault: Color,
    val borderHairline: Color,
    val borderSelected: Color,
    /** The floating bottom-nav outline; a light edge on Light, a low-contrast night edge on Dark. */
    val borderNav: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textPlaceholder: Color,
    val textDisabled: Color,
    val textSelected: Color,
    val textOnInverse: Color,
    val textIncome: Color,
    /** Income text drawn on an income tint, where [textIncome] alone would not reach 4.5:1. */
    val textIncomeOnTint: Color,
    /** Attention/validation *text*; [textAttention] stays a graphic/large-text colour only. */
    val textAttentionStrong: Color,
    val textAttention: Color,
    /** Labels and icons drawn on a coral fill: #05070B reaches 7.27:1 where white measured 2.77:1. */
    val textOnAttention: Color,
    val iconDefault: Color,
    val iconStrong: Color,
    val iconMuted: Color,
    val iconOnInverse: Color,
    val navInactive: Color,
    val accentPrimary: Color,
    val accentSelected: Color,
    val accentIncome: Color,
    val accentDiscretionary: Color,
    val accentHighlight: Color,
    val accentFresh: Color,
    val chartCommitted: Color,
    val chartDiscretionary: Color,
    val chartUnknown: Color,
    val scrim: Color,
    /** The OFF track of a switch only (3:1 against the surface). Grabbers and progress tracks use [handle]. */
    val trackOff: Color,
    /** control/handle: sheet grabbers, progress tracks and keyboard modifier keys. Decorative, never a switch track. */
    val handle: Color,
    /** Validation text. Same value as [textAttentionStrong] (coral/700 measured 3.74:1 on white). */
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
    inverse = Color(0xFF0C1625),
    selectedTint = Color(0xFFE9EEF3),
    incomeTint = Color(0xFFE6F1F1),
    borderDefault = Color(0xFFECECEE),
    borderHairline = Color(0xFFEFEFF1),
    borderSelected = Color(0xFF1E3A55),
    borderNav = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0E0F12),
    textSecondary = Color(0xFF5E5F66),
    textTertiary = Color(0xFF696A71),
    textPlaceholder = ZarPrimitives.gray680,
    textDisabled = Color(0xFFC4C5CA),
    textSelected = Color(0xFF1E3A55),
    textOnInverse = Color(0xFFFFFFFF),
    textIncome = Color(0xFF2A7A80),
    textIncomeOnTint = Color(0xFF23676C),
    textAttentionStrong = Color(0xFFA9573F),
    textAttention = Color(0xFFDD8468),
    textOnAttention = ZarPrimitives.gray950,
    iconDefault = Color(0xFF5E5F66),
    iconStrong = Color(0xFF0E0F12),
    iconMuted = Color(0xFF8B8C93),
    iconOnInverse = Color(0xFFFFFFFF),
    navInactive = ZarPrimitives.gray680,
    accentPrimary = Color(0xFF0C1625),
    accentSelected = Color(0xFF1E3A55),
    accentIncome = Color(0xFF2A7A80),
    accentDiscretionary = Color(0xFFDD8468),
    accentHighlight = Color(0xFFF2B596),
    accentFresh = Color(0xFF8FD6C4),
    chartCommitted = Color(0xFF1E3A55),
    chartDiscretionary = Color(0xFFDD8468),
    chartUnknown = Color(0xFFE6E6EA),
    scrim = Color(0xFF05070B),
    trackOff = ZarPrimitives.gray650,
    handle = ZarPrimitives.gray300,
    danger = Color(0xFFA9573F),
    attention = Color(0xFFDD8468),
    fresh = Color(0xFF8FD6C4),
)

/**
 * Dark mode changes lightness only, never hue: the dark neutrals, and the few accents that failed contrast on the dark
 * canvas (income text, the primary/selected accents, the unknown chart mark, the placeholder and the selected outline).
 */
val ZarDarkColors = ZarColors(
    canvas = Color(0xFF07080B),
    surface = Color(0xFF111318),
    subtle = Color(0xFF1A1D23),
    inverse = Color(0xFF0C1625),
    selectedTint = Color(0xFF14304A),
    incomeTint = Color(0xFF10292B),
    borderDefault = Color(0xFF272B33),
    borderHairline = Color(0xFF20232A),
    borderSelected = Color(0xFF5A86A6),
    borderNav = Color(0xFF272B33),
    textPrimary = Color(0xFFF3F3F5),
    textSecondary = Color(0xFFC4C5CA),
    textTertiary = Color(0xFF8B8C93),
    textPlaceholder = Color(0xFF8B8C93),
    textDisabled = Color(0xFF4A4F5A),
    textSelected = Color(0xFFE9EEF3),
    textOnInverse = Color(0xFFFFFFFF),
    textIncome = Color(0xFF5FB3B9),
    textIncomeOnTint = Color(0xFF5FB3B9),
    textAttentionStrong = Color(0xFFDD8468),
    textAttention = Color(0xFFDD8468),
    textOnAttention = ZarPrimitives.gray950,
    iconDefault = Color(0xFFC4C5CA),
    iconStrong = Color(0xFFF3F3F5),
    iconMuted = Color(0xFF8B8C93),
    iconOnInverse = Color(0xFFFFFFFF),
    navInactive = Color(0xFF8B8C93),
    accentPrimary = Color(0xFF41799C),
    accentSelected = Color(0xFF41799C),
    accentIncome = Color(0xFF2A7A80),
    accentDiscretionary = Color(0xFFDD8468),
    accentHighlight = Color(0xFFF2B596),
    accentFresh = Color(0xFF8FD6C4),
    chartCommitted = Color(0xFF41799C),
    chartDiscretionary = Color(0xFFDD8468),
    chartUnknown = Color(0xFF7A7B82),
    scrim = Color(0xFF05070B),
    trackOff = ZarPrimitives.gray650,
    handle = ZarPrimitives.night700,
    danger = Color(0xFFDD8468),
    attention = Color(0xFFDD8468),
    fresh = Color(0xFF8FD6C4),
)

val LocalZarColors = staticCompositionLocalOf { ZarLightColors }

/** Radius tokens from the Figma variable collection. */
object ZarShapes {
    val xs = RoundedCornerShape(ZarDimens.RadiusXs)
    val sm = RoundedCornerShape(ZarDimens.RadiusSm)
    val md = RoundedCornerShape(ZarDimens.RadiusMd)
    val lg = RoundedCornerShape(ZarDimens.RadiusLg)
    val xl = RoundedCornerShape(ZarDimens.RadiusXl)
    val sheet = RoundedCornerShape(topStart = ZarDimens.RadiusSheet, topEnd = ZarDimens.RadiusSheet)
    val hero = RoundedCornerShape(bottomStart = ZarDimens.RadiusHero, bottomEnd = ZarDimens.RadiusHero)
    val full = RoundedCornerShape(ZarDimens.RadiusFull)
    val pill = full
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

/** Figma text styles. Percentage tracking is converted to sp at each size. */
object ZarType {
    val amountXL = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1.04).sp)
    val amountL = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 34.sp, letterSpacing = (-0.68).sp)
    val displayTitle = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 32.sp, letterSpacing = (-0.64).sp)
    val amountM = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 28.sp, letterSpacing = (-0.56).sp)
    val amountS = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Light, fontSize = 23.sp, lineHeight = 23.sp, letterSpacing = (-0.46).sp)
    val headingSheet = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 18.sp, letterSpacing = (-0.18).sp)
    val bodyL = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 16.sp, letterSpacing = (-0.16).sp)
    val bodyM = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 15.sp)
    val bodyS = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 14.sp, letterSpacing = (-0.14).sp)
    val bodyCaption = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 12.sp)
    val labelButton = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 15.sp)
    val labelChip = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 13.sp)
    val labelAction = labelChip
    val labelTab = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 8.sp, lineHeight = 8.sp, letterSpacing = 0.08.sp)
    val monoLabel = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 0.44.sp)
    val monoCaption = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 10.sp)
    val monoAxis = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 10.sp, letterSpacing = 0.2.sp)
}

object ZarTheme {
    val colors: ZarColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZarColors.current
}
