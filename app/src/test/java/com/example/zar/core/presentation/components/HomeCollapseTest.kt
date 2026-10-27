package com.example.zar.core.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home collapse numbers from the Figma contract (H = 507, S = 50), checked at the frames the design draws. */
class HomeCollapseTest {
    private val h = 507f
    private val s = 50f
    private fun scrollAt(p: Float) = p * (h - s)

    @Test
    fun progressIsLinearAndClamped() {
        assertEquals(0f, HomeCollapse.progress(-30f, h, s), 0f)
        assertEquals(0.3f, HomeCollapse.progress(scrollAt(0.3f), h, s), 1e-5f)
        assertEquals(1f, HomeCollapse.progress(9999f, h, s), 0f)
    }

    @Test
    fun layerShiftsMatchTheDrawnFrames() {
        val s30 = scrollAt(0.3f)
        assertEquals(48f, HomeCollapse.backgroundShift(s30), 0.6f)
        assertEquals(41f, HomeCollapse.textShift(s30), 0.6f)
        assertEquals(25f, HomeCollapse.chartShift(s30), 0.6f)
        val s70 = scrollAt(0.7f)
        assertEquals(112f, HomeCollapse.backgroundShift(s70), 0.6f)
        assertEquals(96f, HomeCollapse.textShift(s70), 0.6f)
        assertEquals(58f, HomeCollapse.chartShift(s70), 0.6f)
        assertEquals(160f, HomeCollapse.backgroundShift(scrollAt(1f)), 0.6f)
    }

    @Test
    fun overlayBuildsToSeventyPercent() {
        assertEquals(0.21f, HomeCollapse.overlayAlpha(0.3f), 1e-4f)
        assertEquals(0.49f, HomeCollapse.overlayAlpha(0.7f), 1e-4f)
        assertEquals(0.70f, HomeCollapse.overlayAlpha(1f), 1e-4f)
    }

    @Test
    fun eachLineIsGoneBeforeItReachesTheStatusGlyphs() {
        // Line tops at rest from the card top (view switch, label, amount, status) and the p where they reach zero.
        val expected = listOf(54f to 0.05f, 140f to 0.31f, 157f to 0.36f, 208f to 0.52f)
        expected.forEach { (top, zeroAt) ->
            val zeroScroll = scrollAt(zeroAt)
            assertEquals("line at $top", 0f, HomeCollapse.lineAlpha(zeroScroll, top, s, 8f), 0.08f)
            assertEquals(1f, HomeCollapse.lineAlpha(0f, top, s, 8f), 0f)
            // Strictly before the zero point the line is still partly visible, and it never grows with more scroll.
            assertTrue(HomeCollapse.lineAlpha(zeroScroll * 0.4f, top, s, 8f) > 0.2f)
            assertTrue(HomeCollapse.lineAlpha(zeroScroll * 2f, top, s, 8f) == 0f)
        }
    }

    @Test
    fun chartFadesOverTheMiddleThenTheCardFlattensAndTheBandArrives() {
        assertEquals(1f, HomeCollapse.chartAlpha(0.3f), 1e-5f)
        assertEquals(0f, HomeCollapse.chartAlpha(0.7f), 1e-5f)
        assertEquals(0.5f, HomeCollapse.chartAlpha(0.5f), 1e-5f)
        assertEquals(32f, HomeCollapse.cornerRadius(0.75f, 32f), 1e-4f)
        assertEquals(0f, HomeCollapse.cornerRadius(1f, 32f), 1e-4f)
        assertEquals(0f, HomeCollapse.bandAlpha(0.85f), 1e-5f)
        assertEquals(1f, HomeCollapse.bandAlpha(1f), 1e-5f)
    }

    @Test
    fun theValuesRetraceExactlyOnReverseScroll() {
        val forward = (0..40).map { HomeCollapse.lineAlpha(it * 10f, 140f, s, 8f) }
        val back = (40 downTo 0).map { HomeCollapse.lineAlpha(it * 10f, 140f, s, 8f) }.reversed()
        assertEquals(forward, back)
    }
}
