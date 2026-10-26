package com.example.zar.core.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
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

    @Test
    fun anOrdinaryScrollOwnerReportsItsScrollValue() {
        val state = ScrollState(initial = 120)
        assertEquals(120f, state.heroDisplacement().displacementPx(), 0.0001f)
    }

    @Test
    fun aLazyHeroUsesItsOwnOffsetWhileItIsTheFirstVisibleItem() {
        val state = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 90)
        assertEquals(90f, state.heroDisplacement().displacementPx(), 0.0001f)
    }

    @Test
    fun aLazyHeroSaturatesAtItsCapOnceItHasLeftInsteadOfResettingToZero() {
        // Once the hero is no longer the first visible item, the offset must be a stable terminal value, not zero:
        // returning to the top on reverse scroll then stays continuous and never replays an entry flourish.
        val scrolledPast = LazyListState(firstVisibleItemIndex = 3, firstVisibleItemScrollOffset = 0)
        val terminal = scrolledPast.heroDisplacement().displacementPx()
        assert(terminal > 1_000f)
        assertEquals(ZarMotion.ParallaxTextCap.value, HeroParallax.textOffset(terminal, capPx = ZarMotion.ParallaxTextCap.value), 0.0001f)
        assertEquals(ZarMotion.ParallaxChartCap.value, HeroParallax.chartOffset(terminal, capPx = ZarMotion.ParallaxChartCap.value), 0.0001f)
        assertEquals(ZarMotion.ParallaxBackgroundCap.value, HeroParallax.backgroundOffset(terminal, capPx = ZarMotion.ParallaxBackgroundCap.value), 0.0001f)
    }

    @Test
    fun aMeasuredLazyHeroIsReadFromItsOwnOffsetFromWhereItRests() {
        assertEquals(0f, HeroParallax.lazyDisplacement(heroOffsetPx = 0, anyItemVisible = true), 0f)
        assertEquals(40f, HeroParallax.lazyDisplacement(heroOffsetPx = -40, anyItemVisible = true), 0f)
    }

    @Test
    fun overscrollPastTheTopNeverMovesALayerBackwards() {
        assertEquals(0f, HeroParallax.lazyDisplacement(heroOffsetPx = 60, anyItemVisible = true), 0f)
    }

    @Test
    fun aHeroThatHasLeftTheViewportStaysSaturatedWhileAnUnmeasuredListIsAtRest() {
        assertEquals(Float.MAX_VALUE, HeroParallax.lazyDisplacement(heroOffsetPx = null, anyItemVisible = true), 0f)
        assertEquals(0f, HeroParallax.lazyDisplacement(heroOffsetPx = null, anyItemVisible = false), 0f)
    }

    @Test
    fun aRestoredScrollOffsetIsUsedBeforeTheFirstMeasure() {
        val restored = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 140)
        assertEquals(140f, restored.heroDisplacement().displacementPx(), 0f)
    }
}
