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

    /**
     * How far a lazy hero has scrolled above its resting place. [heroOffsetPx] is the hero's layout offset in the list
     * (null when it is not among the visible items). A lazy item's offset is measured from where the first item rests,
     * so content padding above it is negative space and does not count. Never negative, so overscroll at the top
     * leaves a hero at rest; a hero that has scrolled out reports a stable saturating value, and an unmeasured list with
     * nothing visible reports zero.
     */
    fun lazyDisplacement(heroOffsetPx: Int?, anyItemVisible: Boolean): Float = when {
        heroOffsetPx != null -> (-heroOffsetPx).toFloat().coerceAtLeast(0f)
        anyItemVisible -> Float.MAX_VALUE
        else -> 0f
    }
}

/** A hero's own scroll displacement in pixels, read lazily from its actual scroll owner. */
fun interface HeroScrollOffset {
    fun displacementPx(): Float
}

/** Displacement for an ordinary vertical scroll owner whose hero is the first thing in it. */
fun ScrollState.heroDisplacement(): HeroScrollOffset = HeroScrollOffset { value.toFloat() }

/** The item key every lazy top card gives its hero, so its displacement is read from that item and not from a position. */
const val ZarHeroItemKey = "zar-hero"

/**
 * Displacement for a lazy list, read from the hero item itself: its distance above where it rests, which is zero at rest. Once the hero has left the viewport a stable large value keeps every layer saturated at
 * its cap, so re-entering on reverse scroll is continuous instead of snapping back to zero.
 *
 * The hero is found by [heroKey]; if nothing carries it the list's first item stands in for it, so a list that forgot to
 * tag its hero still rests at zero. Before the list has been measured (a restored scroll position, the first frame) the
 * saved first-item offset is used, so a restored screen starts at the offset it was left at, not at zero.
 */
fun LazyListState.heroDisplacement(heroKey: Any = ZarHeroItemKey): HeroScrollOffset = HeroScrollOffset {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) {
        HeroParallax.lazyDisplacement(heroOffsetPx = if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset else null, anyItemVisible = firstVisibleItemIndex != 0)
    } else {
        val hero = visible.firstOrNull { it.key == heroKey } ?: visible.first().takeIf { it.index == 0 }
        HeroParallax.lazyDisplacement(hero?.offset, anyItemVisible = true)
    }
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
