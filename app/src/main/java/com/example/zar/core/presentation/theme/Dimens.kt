package com.example.zar.core.presentation.theme

import androidx.compose.ui.unit.dp

/** Figma spacing and shape variables, plus screen-specific component geometry. */
object ZarDimens {
    val SpaceXxs = 4.dp
    val SpaceXs = 8.dp
    val SpaceSm = 12.dp
    val SpaceMd = 16.dp
    val SpaceLg = 20.dp
    val SpaceXl = 24.dp
    val Space2Xl = 32.dp
    val Space3Xl = 40.dp

    val RadiusXs = 8.dp
    val RadiusSm = 12.dp
    val RadiusMd = 16.dp
    val RadiusLg = 20.dp
    val RadiusXl = 25.dp
    val RadiusSheet = 28.dp
    val RadiusHero = 32.dp
    val RadiusFull = 999.dp

    val ScreenGutter = 9.dp
    val HeroGutter = 7.dp
    val SheetGutter = 24.dp
    val MinTouchTarget = 48.dp
    val CardRadius = RadiusLg
    val RowCardRadius = 18.dp
    val HeroRadius = RadiusXl // inset heroes; full-width hero corners use FullHeroRadius
    val FullHeroRadius = RadiusHero
    val SheetRadius = RadiusSheet
    val NavRadius = 34.dp
    val ChipRadius = RadiusSm

    val Hairline = 1.dp
    val CardPadding = 20.dp
    val AddButtonSize = 68.dp
    val NavHeight = 68.dp
    /** The four tab slots are equal, stationary 56dp columns; only the 59dp capsule moves behind them. */
    val NavSlotHeight = 56.dp
    val NavCapsuleHeight = 59.dp
    /** Figma floats the nav 36dp above the screen edge: this gap plus the 24dp gesture inset. */
    val NavBottomGap = 12.dp
    /** The last item of a scrolling tab ends this far above the floating chrome (Figma R2-H4). */
    val NavContentGap = 24.dp
    /** Bottom padding that lets the last item clear the floating chrome: gap + chrome height + 24dp, above the system inset. */
    val ChromeClearance = NavBottomGap + NavHeight + NavContentGap
}
