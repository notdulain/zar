package com.example.zar.core.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import kotlin.math.max
import kotlin.math.min

/**
 * The Home top card collapsing into the status bar as the page scrolls (Figma R2-H0..H4). Every value is a pure function
 * of the scroll distance `s`, so it is linear in scroll, reverse scroll retraces it exactly and nothing keeps time.
 * `p = clamp(s / (H - S), 0, 1)`, where H is the card's height and S the status-bar inset.
 */
object HomeCollapse {
    const val BACKGROUND_RATE = 0.35f
    const val TEXT_RATE = 0.30f
    const val CHART_RATE = 0.18f
    const val OVERLAY_MAX = 0.70f
    const val TEXT_FADE_RATE = 0.70f
    const val CHART_FADE_START = 0.30f
    const val CHART_FADE_END = 0.70f
    const val FLATTEN_START = 0.75f
    const val FLATTEN_END = 1.0f
    const val BAND_START = 0.85f
    const val BAND_END = 1.0f

    private fun ramp(value: Float, start: Float, end: Float): Float = ((value - start) / (end - start)).coerceIn(0f, 1f)

    fun progress(scrollPx: Float, cardHeightPx: Float, statusPx: Float): Float {
        val distance = (cardHeightPx - statusPx).coerceAtLeast(1f)
        return (scrollPx / distance).coerceIn(0f, 1f)
    }

    fun backgroundShift(scrollPx: Float): Float = BACKGROUND_RATE * max(scrollPx, 0f)
    fun textShift(scrollPx: Float): Float = TEXT_RATE * max(scrollPx, 0f)
    fun chartShift(scrollPx: Float): Float = CHART_RATE * max(scrollPx, 0f)

    /** The #05070B darkening laid over the gradient and under the text. */
    fun overlayAlpha(p: Float): Float = OVERLAY_MAX * p

    /**
     * One text line fades so it is gone just before it reaches the status-bar glyphs: it has scrolled up by `s`, so its top
     * is at `restTop - s` less what the text group's own drift gives back; zero when that is [marginPx] inside the status inset.
     */
    fun lineAlpha(scrollPx: Float, restTopPx: Float, statusPx: Float, marginPx: Float): Float {
        val room = (restTopPx - statusPx + marginPx).coerceAtLeast(1f)
        return (1f - TEXT_FADE_RATE * max(scrollPx, 0f) / room).coerceIn(0f, 1f)
    }

    fun chartAlpha(p: Float): Float = 1f - ramp(p, CHART_FADE_START, CHART_FADE_END)

    /** The card's bottom corner radius: full until 75% of the collapse, flat at the end. */
    fun cornerRadius(p: Float, fullRadius: Float): Float = fullRadius * (1f - ramp(p, FLATTEN_START, FLATTEN_END))

    /** The pinned status band that takes over from the card; stays at 1 once the card has gone. */
    fun bandAlpha(p: Float): Float = ramp(p, BAND_START, BAND_END)

    /** Text under a line is not tappable once it has faded this far. */
    const val INERT_BELOW = 0.1f
}

/** Where a collapsing card's lines rest, measured once so each can fade by its own distance from the status bar. */
@Stable
class CollapseLine {
    var restTopPx: Float = 0f
}

@Stable
class HomeCollapseSpec(
    val scroll: ScrollState,
    val cardHeightPx: Float,
    val statusPx: Float,
    val marginPx: Float,
    val spatial: Boolean,
) {
    fun p(): Float = HomeCollapse.progress(scroll.value.toFloat(), cardHeightPx, statusPx)
    fun lineAlpha(line: CollapseLine): Float = HomeCollapse.lineAlpha(scroll.value.toFloat(), line.restTopPx, statusPx, marginPx)
}

@Composable
fun rememberHomeCollapse(scroll: ScrollState, cardHeight: Dp, fadeMargin: Dp): HomeCollapseSpec {
    val density = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
    val spatial = LocalZarMotion.current.allowsSpatialMotion
    return remember(scroll, density, statusPx, spatial, cardHeight, fadeMargin) {
        HomeCollapseSpec(scroll, with(density) { cardHeight.toPx() }, statusPx, with(density) { fadeMargin.toPx() }, spatial)
    }
}

/** Translates by [rate] of the scroll in the layer phase; nothing at all under reduced motion (spatial zero). */
fun Modifier.collapseShift(spec: HomeCollapseSpec, rate: Float): Modifier =
    graphicsLayer { translationY = if (spec.spatial) rate * max(spec.scroll.value.toFloat(), 0f) else 0f }

/**
 * One line of the text group: it fades by its own distance from the status bar, and stops taking taps and reading out
 * to accessibility once it has faded. [line] records where the line rests inside the card.
 */
fun Modifier.collapseLine(spec: HomeCollapseSpec, line: CollapseLine): Modifier = composed {
    val inert by remember(spec, line) { derivedStateOf { spec.lineAlpha(line) < HomeCollapse.INERT_BELOW } }
    this
        .onPlaced { line.restTopPx = it.positionInParent().y }
        .graphicsLayer { alpha = spec.lineAlpha(line) }
        .then(
            if (inert) {
                Modifier
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                    .clearAndSetSemantics {}
            } else {
                Modifier
            },
        )
}

/** The chart group fades over the middle of the collapse; it has no controls to disable. */
fun Modifier.collapseChartFade(spec: HomeCollapseSpec): Modifier =
    graphicsLayer { alpha = HomeCollapse.chartAlpha(spec.p()) }
