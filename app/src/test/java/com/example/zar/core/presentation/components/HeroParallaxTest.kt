package com.example.zar.core.presentation.components

import com.example.zar.core.presentation.motion.ZarMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure parallax maths: bounded, one-sided and layer-ordered, so rest and reduced motion are unchanged. */
class HeroParallaxTest {

    @Test
    fun atRestAndBeforeTheHeroScrollsThereIsNoOffset() {
        assertEquals(0f, HeroParallax.backgroundOffset(displacementPx = 0f, capPx = 24f), 0.0001f)
        assertEquals(0f, HeroParallax.textOffset(displacementPx = -200f, capPx = 8f), 0.0001f)
    }

    @Test
    fun displacementIsFractionalUntilItSaturatesAtTheCap() {
        assertEquals(100f * ZarMotion.PARALLAX_TEXT, HeroParallax.textOffset(100f, capPx = 8f), 0.0001f)
        assertEquals(8f, HeroParallax.textOffset(10_000f, capPx = 8f), 0.0001f)
        assertEquals(24f, HeroParallax.backgroundOffset(10_000f, capPx = 24f), 0.0001f)
        assertEquals(16f, HeroParallax.chartOffset(10_000f, capPx = 16f), 0.0001f)
    }

    @Test
    fun layersTravelAtDifferentDepthsBackgroundTextChart() {
        val d = 1_000f
        val background = HeroParallax.backgroundOffset(d, capPx = 10_000f)
        val chart = HeroParallax.chartOffset(d, capPx = 10_000f)
        val text = HeroParallax.textOffset(d, capPx = 10_000f)
        assertEquals(d * ZarMotion.PARALLAX_BACKGROUND, background, 0.0001f)
        assertEquals(d * ZarMotion.PARALLAX_CHART, chart, 0.0001f)
        assertEquals(d * ZarMotion.PARALLAX_TEXT, text, 0.0001f)
        // Deeper background moves furthest; text least.
        assert(background > chart && chart > text)
    }
}
