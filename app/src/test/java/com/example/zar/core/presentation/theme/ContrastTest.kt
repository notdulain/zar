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
        assertEquals(3.711, contrast(dark.textIncome, dark.surface), 0.001)
        assertEquals(11.134, contrast(light.fresh, dark.surface), 0.001)
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
}
