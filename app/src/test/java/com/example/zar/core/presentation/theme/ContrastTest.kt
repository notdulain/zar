package com.example.zar.core.presentation.theme

import androidx.compose.ui.graphics.Color
import com.example.zar.core.domain.model.TransactionType
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast between two opaque colours. These are source-pair measurements, not sampled screenshot pixels. */
internal fun contrast(a: Color, b: Color): Double {
    fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> if (la > lb) la to lb else lb to la } }
    return (hi + 0.05) / (lo + 0.05)
}

class ContrastTest {

    private val light = ZarLightColors
    private val dark = ZarDarkColors

    @Test
    fun theMeasurementMatchesTheValuesRecordedInTheOpenSpec() {
        // From add-appearance-preference/design.md.
        assertEquals(1.531, contrast(light.fresh, light.canvas), 0.001)
        assertEquals(4.593, contrast(light.textIncome, light.canvas), 0.001)
        assertEquals(5.007, contrast(light.textIncome, light.surface), 0.001)
        assertEquals(11.134, contrast(light.fresh, dark.surface), 0.001)
        // The light teal on the dark surface measured 3.711:1 and forced a dark income override; the dark teal fixes
        // it to 7.641:1 (also asserted in darkIncomeAmountsMeetNormalTextContrastOnTheirOwnBackgrounds).
        assertEquals(7.641, contrast(dark.textIncome, dark.surface), 0.001)
    }

    @Test
    fun freshnessGreenIsNotLegibleAsAmountTextInLight() {
        assertTrue(contrast(light.fresh, light.canvas) < 4.5)
    }

    @Test
    fun lightIncomeAmountsMeetNormalTextContrastOnTheCanvasAndOnCards() {
        val color = light.amountText(TransactionType.INCOME)

        assertTrue(contrast(color, light.canvas) >= 4.5)
        assertTrue(contrast(color, light.surface) >= 4.5)
    }

    @Test
    fun lightOrdinaryAmountsMeetNormalTextContrast() {
        TransactionType.entries.filter { it != TransactionType.INCOME }.forEach { type ->
            val color = light.amountText(type)
            assertTrue("$type on canvas", contrast(color, light.canvas) >= 4.5)
            assertTrue("$type on card", contrast(color, light.surface) >= 4.5)
        }
    }

    @Test
    fun onlyRealIncomeUsesTheIncomeColour() {
        assertEquals(light.textIncome, light.amountText(TransactionType.INCOME))
        listOf(
            TransactionType.TRANSFER_IN,
            TransactionType.REFUND,
            TransactionType.ADJUSTMENT_IN,
            TransactionType.EXPENSE,
            TransactionType.TRANSFER_OUT,
            TransactionType.ADJUSTMENT_OUT,
        ).forEach { assertNotEquals("$it", light.textIncome, light.amountText(it)) }
    }

    @Test
    fun noAmountEverUsesTheFreshnessFill() {
        TransactionType.entries.forEach {
            assertNotEquals(light.fresh, light.amountText(it))
            assertNotEquals(dark.fresh, dark.amountText(it))
        }
    }

    @Test
    fun darkIncomeAmountsMeetNormalTextContrastOnTheirOwnBackgrounds() {
        // The dark income teal is a separate value from light: teal/300 #5FB3B9, not the light teal/600.
        assertNotEquals(light.textIncome, dark.textIncome)
        assertTrue(contrast(dark.textIncome, dark.canvas) >= 4.5)
        assertTrue(contrast(dark.textIncome, dark.surface) >= 4.5)
    }

    @Test
    fun incomeOnAnIncomeTintMeetsNormalTextContrastInBothThemes() {
        assertTrue(contrast(light.textIncomeOnTint, light.incomeTint) >= 4.5)
        assertTrue(contrast(dark.textIncomeOnTint, dark.incomeTint) >= 4.5)
    }

    @Test
    fun attentionTextMeetsNormalTextContrastWhereGraphicAttentionWouldNot() {
        // text/attention is graphic/large-text only (2.77:1 on white); text/attention-strong is the readable text role.
        assertTrue(contrast(light.textAttention, light.surface) < 4.5)
        listOf(light, dark).forEach { colors ->
            assertTrue(contrast(colors.textAttentionStrong, colors.surface) >= 4.5)
            assertTrue(contrast(colors.textAttentionStrong, colors.canvas) >= 4.5)
        }
    }

    @Test
    fun placeholderTextMeetsNormalTextContrastInBothThemes() {
        // Light gray/500 #B4B5BB measured 2.05:1 on white; the final Figma handoff moves it to gray/680 #696A71 (5.38:1).
        assertEquals(ZarPrimitives.gray680, light.textPlaceholder)
        assertEquals(5.38, contrast(light.textPlaceholder, light.surface), 0.01)
        listOf(light, dark).forEach { colors ->
            assertTrue(contrast(colors.textPlaceholder, colors.canvas) >= 4.5)
            assertTrue(contrast(colors.textPlaceholder, colors.surface) >= 4.5)
        }
    }

    @Test
    fun inactiveNavLabelsMeetNormalTextContrastOnTheNavSurface() {
        // The 8sp label measured 4.21:1 in Light with #7A7B82; #696A71 reaches 5.38:1. Dark keeps #8B8C93.
        assertEquals(ZarPrimitives.gray680, light.navInactive)
        listOf(light, dark).forEach { assertTrue("${it.navInactive}", contrast(it.navInactive, it.surface) >= 4.5) }
    }

    @Test
    fun theSwitchOffTrackIsAVisibleControlAgainstItsCardInBothThemes() {
        assertEquals(ZarPrimitives.gray650, light.trackOff)
        assertEquals(ZarPrimitives.gray650, dark.trackOff)
        listOf(light, dark).forEach { colors ->
            assertTrue("off track on surface", contrast(colors.trackOff, colors.surface) >= 3.0)
            assertTrue("off track on subtle", contrast(colors.trackOff, colors.subtle) >= 3.0)
            // The white thumb sits on the track.
            assertTrue("thumb on off track", contrast(Color.White, colors.trackOff) >= 3.0)
        }
    }

    @Test
    fun grabbersAndProgressTracksUseTheHandleNotTheSwitchTrack() {
        // control/handle keeps the old, faint trackOff values: decorative, and never a switch track.
        assertEquals(Color(0xFFD9DADE), light.handle)
        assertEquals(Color(0xFF343944), dark.handle)
        assertNotEquals(light.trackOff, light.handle)
        assertNotEquals(dark.trackOff, dark.handle)
        // Keyboard modifier keys carry text on the handle colour.
        assertTrue(contrast(light.textPrimary, light.handle) >= 4.5)
        assertTrue(contrast(dark.textPrimary, dark.handle) >= 4.5)
    }

    @Test
    fun labelsOnACoralFillUseTheNearBlackTextOnAttentionNotWhite() {
        listOf(light, dark).forEach { colors ->
            assertEquals(ZarPrimitives.gray950, colors.textOnAttention)
            assertEquals(7.27, contrast(colors.textOnAttention, colors.textAttention), 0.01)
            assertTrue(contrast(colors.textOnAttention, colors.textAttention) >= 4.5)
            // White on the same coral is the failing pair the Void confirm button used to ship.
            assertTrue(contrast(colors.textOnInverse, colors.textAttention) < 4.5)
        }
    }

    @Test
    fun validationTextUsesTheStrongAttentionColourAndReadsOnEverySurface() {
        listOf(light, dark).forEach { colors ->
            assertEquals(colors.textAttentionStrong, colors.danger)
            assertTrue(contrast(colors.danger, colors.surface) >= 4.5)
            assertTrue(contrast(colors.danger, colors.canvas) >= 4.5)
            assertTrue(contrast(colors.danger, colors.subtle) >= 4.5)
        }
        // coral/700, the old danger value, was 3.74:1 on white.
        assertTrue(contrast(ZarPrimitives.coral700, light.surface) < 4.5)
    }

    @Test
    fun authButtonsReadOnTheirAccentFillInBothThemes() {
        // PrimaryAuthButton draws textOnInverse on accentPrimary: 18.15:1 Light, 4.73:1 Dark.
        assertEquals(18.15, contrast(light.textOnInverse, light.accentPrimary), 0.01)
        assertEquals(4.73, contrast(dark.textOnInverse, dark.accentPrimary), 0.01)
    }

    @Test
    fun primaryAndSelectedAccentsMeetNonTextContrastOnTheDarkCanvas() {
        // The dark primary/selected accent is navy/400 #41799C; white on it still clears 4.5:1 for the button label.
        assertTrue(contrast(dark.accentPrimary, dark.canvas) >= 3.0)
        assertTrue(contrast(dark.accentSelected, dark.surface) >= 3.0)
        assertTrue(contrast(light.textOnInverse, dark.accentPrimary) >= 4.5)
    }

    @Test
    fun darkGraphicalIndicatorsMeetNonTextContrast() {
        assertTrue(contrast(dark.borderSelected, dark.surface) >= 3.0)
        assertTrue(contrast(dark.chartUnknown, dark.surface) >= 3.0)
        assertTrue(contrast(dark.chartCommitted, dark.surface) >= 3.0)
        assertTrue(contrast(dark.accentIncome, dark.surface) >= 3.0)
    }

    @Test
    fun navOutlineIsALightEdgeOnLightAndANightEdgeOnDark() {
        assertEquals(Color(0xFFFFFFFF), light.borderNav)
        assertEquals(Color(0xFF272B33), dark.borderNav)
        // A hairline outline is decorative chrome, never a text colour.
        assertNotEquals(light.borderNav, light.textPrimary)
        assertNotEquals(dark.borderNav, dark.textPrimary)
    }

    @Test
    fun boundedControlOutlineMeetsNonTextContrastAgainstItsBackgroundsInBothThemes() {
        listOf(light, dark).forEach { colors ->
            assertTrue(contrast(colors.borderControl, colors.canvas) >= 3.0)
            assertTrue(contrast(colors.borderControl, colors.surface) >= 3.0)
            // The Sign out label sits on the surface fill.
            assertTrue(contrast(colors.textPrimary, colors.surface) >= 4.5)
        }
    }
}
