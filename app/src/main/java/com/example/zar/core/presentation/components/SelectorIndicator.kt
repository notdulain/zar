package com.example.zar.core.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarSprings
import kotlin.math.max
import kotlin.math.min

/**
 * The moving selected surface of an Add transaction selector. Its position is counted in slots (0 = first slot), so it
 * stays valid when the track is re-measured and when the same state is shown by two different bodies: the Expense /
 * Transfer body and the Income body both draw the type selector from the one state hoisted above them.
 *
 * It is presentation only. Selection is decided by the caller's `selectedIndex` and takes effect at once; this follows it.
 */
@Stable
class SelectorIndicatorState internal constructor(initialIndex: Int) {
    internal val position = Animatable(initialIndex.toFloat())
    private var target = initialIndex
    private var origin = initialIndex.toFloat()
    private var placed: Rect? = null

    /** Where the indicator was last placed inside its track, in pixels. Null before the first placement. */
    fun indicatorBounds(): Rect? = placed

    internal fun placedAt(bounds: Rect) {
        placed = bounds
    }

    /** Move toward [index] under [policy], retargeting from the current position and velocity. */
    internal suspend fun follow(index: Int, policy: MotionPolicy) {
        if (policy.allowsSpatialMotion) {
            if (index == target && !position.isRunning && position.value == index.toFloat()) return
            origin = position.value
            target = index
            position.animateTo(index.toFloat(), policy.spring(ZarSprings.AddSelector, visibilityThreshold = SETTLED_SLOTS))
        } else {
            // Reduced and Instant show the final slot directly: no travel, no bounce, nothing left running.
            target = index
            origin = index.toFloat()
            position.snapTo(index.toFloat())
        }
    }

    /**
     * The drawn position in slots. A spring may swing past its target by a small, fixed distance ([capSlots]) and never
     * out of the track ([lastIndex] slots from the first).
     */
    internal fun presentation(lastIndex: Int, capSlots: Float): Float {
        val value = position.value
        val t = target.toFloat()
        val capped = when {
            origin < t && value > t -> min(value, t + capSlots)
            origin > t && value < t -> max(value, t - capSlots)
            else -> value
        }
        return capped.coerceIn(0f, lastIndex.toFloat())
    }

    private companion object {
        const val SETTLED_SLOTS = 0.001f
    }
}

/**
 * Remembers an indicator that starts at [selectedIndex] with no entrance motion and follows later changes under the
 * shared motion policy. Hoist it above anything that swaps the composables drawing the selector.
 */
@Composable
fun rememberSelectorIndicator(selectedIndex: Int): SelectorIndicatorState {
    val policy = LocalZarMotion.current
    val state = remember { SelectorIndicatorState(selectedIndex) }
    // Keyed on the level too: switching to Reduced or Instant mid-spring resolves straight to the selected slot.
    LaunchedEffect(selectedIndex, policy.level) { state.follow(selectedIndex, policy) }
    return state
}
