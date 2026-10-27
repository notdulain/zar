package com.example.zar.core.presentation.components

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.coroutines.coroutineContext

/**
 * Owns the one selected-capsule mover for the full navbar. It is created once per navigation session (beside
 * [NavSession]) and passed down to every navbar composition, so the capsule's position survives a screen being
 * created again. The navbar only reports the measured tab slots and the current destination; this class decides
 * where the capsule is drawn.
 *
 * The capsule is a measured rectangle (x and width) that springs toward [targetRect], the slot of [targetIndex] in the
 * latest measured layout. Springing the rectangle rather than a slot index keeps it continuous when the selected tab's
 * slot widens at selection time, retargets from the on-screen position and velocity, and bounces a little when it
 * lands on an interior tab. It never goes past Home or More: the outer slots are the capsule's hard stops. Because the value is snapshot state read in the navbar's draw phase, a moving capsule invalidates drawing
 * only, never the screen. The first placement snaps, so a reveal never flies in from Home or x = 0, and RTL mirrors
 * because the slots are measured, not computed.
 */
@Stable
class NavCapsuleController {

    /** The destination the capsule should settle on, as a slot index. */
    var targetIndex by mutableIntStateOf(0)
        private set

    /** The measured tab slots in the navbar's own coordinate space (physical order, so RTL is already applied). */
    private var slots by mutableStateOf<List<Rect>>(emptyList())

    /** The pill's interior: the capsule is never drawn outside it. */
    private var bounds by mutableStateOf(Rect.Zero)

    /**
     * How far past an interior target the spring may carry the capsule, in pixels (`ZarMotion.NavMaxOvershoot`). The
     * outer edges of the first and last slots are hard stops, so this never lets the capsule leave Home or More.
     */
    var maxOvershootPx by mutableStateOf(0f)

    /** False until the tab slots have been measured once, so the first reveal can snap instead of flying. */
    var revealed by mutableStateOf(false)
        private set

    /** The rectangle drawn right now. A snapshot state, so only the navbar's draw phase reads it. */
    private var presentation by mutableStateOf(Rect.Zero)
    private var placed = false

    /** Capsule velocity in px/s per edge, and the display time of the frame that last moved it while it is in flight. */
    private var velocity = AnimationVector4D(0f, 0f, 0f, 0f)
    private var lastFrameNanos = NO_FRAME

    private var moving by mutableStateOf(false)

    /** Where the flight in progress started, so the bounce past the target is only allowed in the direction of travel. */
    private var flightOrigin: Rect? = null

    val slotCount: Int get() = slots.size

    /** True while the capsule is moving. */
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
     * Springs the capsule to [target] with [spec], continuing from its current position and velocity. The first call
     * places it directly, so a first reveal has no flight. A newer call cancels this one and takes over.
     *
     * The spring is evaluated from the clock rather than stepped by an `Animatable`: a retarget starts its new curve at
     * the last frame this capsule was drawn, so the first frame after a tap already moves. An `Animatable` restarts its
     * clock on the next frame and the capsule would hold still for that one frame, a visible hitch during rapid taps.
     */
    suspend fun moveTo(target: Rect, spec: FiniteAnimationSpec<Rect>) {
        if (!placed) {
            snapTo(target)
            return
        }
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            snapTo(target)
            return
        }
        val converter = Rect.VectorConverter
        val vectorized = spec.vectorize(converter)
        val from = converter.convertToVector(presentation)
        val to = converter.convertToVector(target)
        val initialVelocity = AnimationVector4D(velocity.v1, velocity.v2, velocity.v3, velocity.v4)
        val duration = vectorized.getDurationNanos(from, to, initialVelocity)
        var start = lastFrameNanos
        moving = true
        flightOrigin = presentation
        try {
            while (true) {
                val frame = withFrameNanos { it }
                if (start == NO_FRAME) start = frame
                val play = ((frame - start) / scale).toLong().coerceAtLeast(0L)
                if (play >= duration) {
                    presentation = target
                    velocity = AnimationVector4D(0f, 0f, 0f, 0f)
                    lastFrameNanos = NO_FRAME
                    return
                }
                presentation = converter.convertFromVector(vectorized.getValueFromNanos(play, from, to, initialVelocity))
                val v = vectorized.getVelocityFromNanos(play, from, to, initialVelocity)
                velocity = AnimationVector4D(v.v1, v.v2, v.v3, v.v4)
                lastFrameNanos = frame
            }
        } finally {
            moving = false
            flightOrigin = null
        }
    }

    /** Resolves the capsule on [target] at once, for reduced motion and disabled system animations. */
    @Suppress("RedundantSuspendModifier")
    suspend fun snapTo(target: Rect) {
        placed = true
        presentation = target
        velocity = AnimationVector4D(0f, 0f, 0f, 0f)
        lastFrameNanos = NO_FRAME
    }

    /** Where the capsule is drawn right now, or null before the first placement. */
    fun capsuleBounds(): Rect? {
        if (!placed || slots.isEmpty()) return null
        val r = presentation
        // Home and More are hard stops: the slots are in physical order, so the leftmost and rightmost slot are the
        // ends in both LTR and RTL, and the pill interior (which has room for an overshoot) is never used to pass them.
        var minLeft = maxOf(bounds.left, slots.minOf { it.left })
        var maxRight = minOf(bounds.right, slots.maxOf { it.right })
        // Between interior tabs the spring may carry the capsule a little past its target in the direction of travel.
        val target = targetRect
        val origin = flightOrigin
        if (target != null && origin != null) {
            if (target.left >= origin.left) maxRight = minOf(maxRight, target.right + maxOvershootPx)
            if (target.left <= origin.left) minLeft = maxOf(minLeft, target.left - maxOvershootPx)
        }
        // Slide the whole capsule back rather than clipping one edge, so the bounce never squashes its width.
        val shift = when {
            r.right > maxRight -> maxRight - r.right
            r.left < minLeft -> minLeft - r.left
            else -> 0f
        }
        return if (shift == 0f) r else r.translate(shift, 0f)
    }

    /** Velocity of the capsule's left edge in pixels per second, for retargeting checks. */
    val velocityLeftEdge: Float get() = velocity.v1

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
    }
}
