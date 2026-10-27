package com.example.zar.core.presentation.components

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.coroutines.coroutineContext

/**
 * Owns the one selected-capsule mover for the full navbar. It is created once per navigation session (beside
 * [NavSession]) and passed down to every navbar composition, so the capsule's position survives a screen being
 * created again. The navbar only reports the measured tab slots; this class decides where the capsule is drawn.
 *
 * The four tab slots are equal and never move. The capsule is a single value, [position], in slot units: `0f` is
 * the Home slot and `3f` the More slot, and a fractional value is a point between two neighbours (the capsule's edges
 * are interpolated between the measured slots, so RTL mirrors because the slots are measured, not computed).
 * - While a pager swipe is in progress the host calls [dragTo] every frame with `currentPage + currentPageOffsetFraction`,
 *   so the capsule follows the finger one for one with no spring and no lag.
 * - A tab tap calls [moveTo]: a spring evaluated from the display clock, continuing from the capsule's current position
 *   and velocity, so a second tap mid-flight retargets with no restart and no held frame.
 * Home and More are hard stops: the capsule is never drawn outside the outermost slots. Between interior tabs the spring
 * may carry it a little past its target in the direction of travel ([maxOvershootPx]). The value is snapshot state read in the
 * navbar's draw phase, so a moving capsule invalidates drawing only, never the screen. The first placement snaps, so a
 * reveal never flies in from Home or x = 0.
 */
@Stable
class NavCapsuleController {

    /** The destination the capsule should settle on, as a slot index. */
    var targetIndex by mutableIntStateOf(0)
        private set

    /** The measured tab slots in the navbar's own coordinate space (indexed by destination, so RTL is already applied). */
    private var slots by mutableStateOf<List<Rect>>(emptyList())

    /** The pill's interior: the capsule is never drawn outside it. */
    private var bounds by mutableStateOf(Rect.Zero)

    /**
     * How far past an interior target the spring may carry the capsule, in pixels (`ZarMotion.NavMaxOvershoot`). The
     * outer edges of the first and last slots are hard stops, so this never lets the capsule leave Home or More.
     */
    var maxOvershootPx by mutableFloatStateOf(0f)

    /** The capsule's height in pixels, centred on the slots; zero means "as tall as a slot". */
    var capsuleHeightPx by mutableFloatStateOf(0f)

    /** False until the tab slots have been measured once, so the first reveal can snap instead of flying. */
    var revealed by mutableStateOf(false)
        private set

    /** The capsule's place in slot units. A snapshot state, so only the navbar's draw phase reads it. */
    var position by mutableFloatStateOf(0f)
        private set

    // A first draw can happen before the capsule is placed. Observing this flag lets the first placement invalidate that
    // initially empty draw even when the navbar's layout and selected destination do not change again.
    private var placed by mutableStateOf(false)

    /** Capsule velocity in slot units per second and the display time of the frame that last moved it while in flight. */
    private var velocity = 0f
    private var lastFrameNanos = NO_FRAME

    private var moving by mutableStateOf(false)

    /** Where the flight in progress started, so the bounce past the target is only allowed in the direction of travel. */
    private var flightOrigin: Float? = null

    val slotCount: Int get() = slots.size

    /** True while a tap flight is moving the capsule. */
    val isMoving: Boolean get() = moving

    /** The slot the capsule should settle on, or null before the first measurement. */
    val targetRect: Rect? get() = slots.getOrNull(targetIndex.coerceIn(0, (slots.size - 1).coerceAtLeast(0)))

    /**
     * Reports the measured tab slots, and the pill interior they sit in, in the navbar's own coordinate space.
     * Without [interior] the capsule may only be drawn inside the slots.
     */
    fun onSlotsMeasured(measured: List<Rect>, interior: Rect? = null) {
        if (measured.isEmpty()) return
        val limit = interior ?: Rect(measured.minOf { it.left }, measured.minOf { it.top }, measured.maxOf { it.right }, measured.maxOf { it.bottom })
        if (measured != slots) slots = measured
        if (limit != bounds) bounds = limit
        revealed = true
    }

    /** A new valid destination was selected. The capsule retargets from wherever it currently is. */
    fun select(index: Int) {
        targetIndex = if (slots.isEmpty()) index else index.coerceIn(0, slots.size - 1)
    }

    /**
     * Springs the capsule to the slot of [index] with [spec], continuing from its current position and velocity. The first
     * call places it directly, so a first reveal has no flight. A newer call cancels this one and takes over.
     *
     * The spring is evaluated from the clock rather than stepped by an `Animatable`: a retarget starts its new curve at
     * the last frame this capsule was drawn, so the first frame after a tap already moves. An `Animatable` restarts its
     * clock on the next frame and the capsule would hold still for that one frame, a visible hitch during rapid taps.
     */
    suspend fun moveTo(index: Int, spec: FiniteAnimationSpec<Float>) {
        val target = clampIndex(index).toFloat()
        if (!placed) {
            snapTo(target)
            return
        }
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            snapTo(target)
            return
        }
        val converter = Float.VectorConverter
        val vectorized = spec.vectorize(converter)
        val from = AnimationVector1D(position)
        val to = AnimationVector1D(target)
        val initialVelocity = AnimationVector1D(velocity)
        val duration = vectorized.getDurationNanos(from, to, initialVelocity)
        var start = lastFrameNanos
        moving = true
        flightOrigin = position
        try {
            while (true) {
                val frame = withFrameNanos { it }
                if (start == NO_FRAME) start = frame
                val play = ((frame - start) / scale).toLong().coerceAtLeast(0L)
                if (play >= duration) {
                    position = target
                    velocity = 0f
                    lastFrameNanos = NO_FRAME
                    return
                }
                position = vectorized.getValueFromNanos(play, from, to, initialVelocity).value
                velocity = vectorized.getVelocityFromNanos(play, from, to, initialVelocity).value
                lastFrameNanos = frame
            }
        } finally {
            moving = false
            flightOrigin = null
        }
    }

    /** Resolves the capsule at [slotPosition] at once, for reduced motion, disabled system animations and the first reveal. */
    @Suppress("RedundantSuspendModifier")
    suspend fun snapTo(slotPosition: Float) = dragTo(slotPosition)

    /** Direct manipulation: the capsule follows [slotPosition] one for one with the pager, with no spring and no lag. */
    fun dragTo(slotPosition: Float) {
        placed = true
        position = slotPosition
        velocity = 0f
        lastFrameNanos = NO_FRAME
    }

    /** Where the capsule is drawn right now, or null before the first placement. */
    fun capsuleBounds(): Rect? {
        if (!placed || slots.isEmpty()) return null
        val p = position.coerceIn(0f, (slots.size - 1).toFloat())
        val lower = p.toInt().coerceIn(0, slots.size - 1)
        val upper = (lower + 1).coerceAtMost(slots.size - 1)
        val fraction = p - lower
        val a = slots[lower]
        val b = slots[upper]
        var left = a.left + (b.left - a.left) * fraction
        var right = a.right + (b.right - a.right) * fraction
        val centerY = a.top + (b.top - a.top) * fraction + (a.height + (b.height - a.height) * fraction) / 2f
        val height = if (capsuleHeightPx > 0f) capsuleHeightPx else a.height
        // Home and More are hard stops: slot rectangles are indexed by destination, so the leftmost and rightmost physical
        // slot are the ends in both LTR and RTL. The pill interior (which has room for an overshoot) never passes them.
        var minLeft = maxOf(bounds.left, slots.minOf { it.left })
        var maxRight = minOf(bounds.right, slots.maxOf { it.right })
        // Between interior tabs the spring may carry the capsule a little past its target in the direction of travel.
        val target = targetRect
        val origin = flightOrigin
        if (target != null && origin != null) {
            val originLeft = leftAt(origin)
            if (target.left >= originLeft) maxRight = minOf(maxRight, target.right + maxOvershootPx)
            if (target.left <= originLeft) minLeft = maxOf(minLeft, target.left - maxOvershootPx)
        }
        // Slide the whole capsule back rather than clipping one edge, so the bounce never squashes its width.
        val shift = when {
            right > maxRight -> maxRight - right
            left < minLeft -> minLeft - left
            else -> 0f
        }
        left += shift
        right += shift
        return Rect(left, centerY - height / 2f, right, centerY + height / 2f)
    }

    /** Velocity of the capsule in slot units per second, for retargeting checks. */
    val velocitySlots: Float get() = velocity

    private fun leftAt(slotPosition: Float): Float {
        val clamped = slotPosition.coerceIn(0f, (slots.size - 1).toFloat())
        val lower = clamped.toInt().coerceIn(0, slots.size - 1)
        val upper = (lower + 1).coerceAtMost(slots.size - 1)
        return slots[lower].left + (slots[upper].left - slots[lower].left) * (clamped - lower)
    }

    private fun clampIndex(index: Int) = if (slots.isEmpty()) index.coerceAtLeast(0) else index.coerceIn(0, slots.size - 1)

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
    }
}
