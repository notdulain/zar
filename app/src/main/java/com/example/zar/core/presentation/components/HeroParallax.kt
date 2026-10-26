package com.example.zar.core.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarMotion

/**
 * Bounded scroll depth for the top hero/summary cards. A hero keeps its outer layout in normal flow; selected inner
 * layers are pushed down by a fraction of the hero's own scroll displacement, so they travel more slowly than the
 * content below. The maths is pure so the caps and layer ordering are unit-testable without a device.
 *
 * Displacement is positive when the hero has scrolled up (its top is above the viewport top). At rest it is zero, so
 * the resting composition is unchanged. Values are read in the layer phase, so scrolling never recomposes the hero.
 */
object HeroParallax {

    /** A layer is compensated by [fraction] of [displacementPx], never below zero or above [capPx]. */
    fun compensation(displacementPx: Float, fraction: Float, capPx: Float): Float =
        (displacementPx * fraction).coerceIn(0f, capPx)

    fun backgroundOffset(displacementPx: Float, capPx: Float): Float =
        compensation(displacementPx, ZarMotion.PARALLAX_BACKGROUND, capPx)

    fun textOffset(displacementPx: Float, capPx: Float): Float =
        compensation(displacementPx, ZarMotion.PARALLAX_TEXT, capPx)

    fun chartOffset(displacementPx: Float, capPx: Float): Float =
        compensation(displacementPx, ZarMotion.PARALLAX_CHART, capPx)
}

/** A hero's own scroll displacement in pixels, read lazily from its actual scroll owner. */
fun interface HeroScrollOffset {
    fun displacementPx(): Float
}

/** Displacement for an ordinary vertical scroll owner whose hero is the first thing in it. */
fun ScrollState.heroDisplacement(): HeroScrollOffset = HeroScrollOffset { value.toFloat() }

/**
 * Displacement for a lazy list whose hero is the first item. While the hero is the first visible item its own scroll
 * offset is the displacement; once it has left, a stable large value keeps the compensation saturated at its cap, so
 * re-entering on reverse scroll is continuous instead of snapping back to zero.
 */
fun LazyListState.heroDisplacement(): HeroScrollOffset = HeroScrollOffset {
    if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
}

/** Applies one bounded parallax layer. Zero displacement (and reduced motion) leaves the layer exactly at rest. */
@Composable
fun Modifier.heroParallax(offset: HeroScrollOffset, fraction: Float, cap: Dp): Modifier {
    val policy = LocalZarMotion.current
    val capPx = with(LocalDensity.current) { cap.toPx() }
    val enabled = policy.allowsSpatialMotion
    return graphicsLayer {
        translationY = if (enabled) HeroParallax.compensation(offset.displacementPx(), fraction, capPx) else 0f
    }
}
