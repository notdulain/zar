package com.example.zar.features.transactions.presentation.manual.screen

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Angle maths for the 12-detent time dial. Angles are degrees clockwise from 12 o'clock. */
internal object ClockGeometry {
    const val DETENTS = 12
    const val DETENT_DEGREES = 360f / DETENTS

    /** Pointer angle for an offset from the dial centre (y grows downward), in [0, 360). */
    fun angleOf(dx: Float, dy: Float): Float =
        normalize((atan2(dx.toDouble(), -dy.toDouble()) * 180.0 / PI).toFloat())

    fun normalize(angle: Float): Float {
        val wrapped = angle % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    /** Signed shortest rotation from [from] to [to], in (-180, 180]. */
    fun signedDelta(from: Float, to: Float): Float {
        val delta = normalize(to - from)
        return if (delta > 180f) delta - 360f else delta
    }

    fun distance(a: Float, b: Float): Float = abs(signedDelta(a, b))

    fun detentOf(angle: Float): Int = (normalize(angle) / DETENT_DEGREES).roundToInt() % DETENTS

    fun angleOfDetent(detent: Int): Float = detent * DETENT_DEGREES

    /** Distance from a point to the arm segment running from the centre at [armAngle] for [armLength]. */
    fun distanceToArm(dx: Float, dy: Float, armAngle: Float, armLength: Float): Float {
        val radians = armAngle * PI / 180.0
        val ax = kotlin.math.sin(radians).toFloat()
        val ay = -kotlin.math.cos(radians).toFloat()
        val along = (dx * ax + dy * ay).coerceIn(0f, armLength)
        return hypot(dx - ax * along, dy - ay * along)
    }
}

/** Dial dimensions in pixels plus the tuned gesture thresholds. */
internal data class ClockDialConfig(
    val armLength: Float,
    /** Pointer samples closer to the centre than this are ignored, because their angle is noise. */
    val centerDeadzone: Float,
    /** A press this close to the arm grabs it and keeps its offset from the pointer. */
    val armGrabRadius: Float,
    /** Extra degrees past a detent boundary before the selection switches. */
    val hysteresisDegrees: Float = 3f,
)

/**
 * Turns controlled pointer input into dial selection. It knows nothing about Compose, so the same
 * calls drive the composable and the unit tests.
 *
 * Mode (hour or minute) is not an input here: the caller keeps it fixed for the whole gesture and
 * advances it only from [onFinished], which fires once per completed tap or drag.
 */
internal class ClockDialInput(
    private val config: ClockDialConfig,
    /** Angle the arm is resting at, which for precise typed minutes is not on a detent. */
    private val restingArmAngle: () -> Float,
    private val currentDetent: () -> Int,
    private val onSelectDetent: (Int) -> Unit,
    private val onFinished: () -> Unit,
    /** Continuous arm angle while dragging, then null so the arm returns to its detent. */
    private val onArmAngle: (Float?) -> Unit,
) {
    private var grabOffset = 0f
    private var detent = 0
    private var dragging = false

    /** Pointer went down. Call before the slop decision so an arm grab remembers its offset. */
    fun press(dx: Float, dy: Float) {
        grabOffset = 0f
        val arm = restingArmAngle()
        if (hypot(dx, dy) >= config.centerDeadzone &&
            ClockGeometry.distanceToArm(dx, dy, arm, config.armLength) <= config.armGrabRadius
        ) {
            grabOffset = ClockGeometry.signedDelta(ClockGeometry.angleOf(dx, dy), arm)
        }
    }

    /** Released without crossing touch slop. Tapping the current detent still applies its exact value. */
    fun tap(dx: Float, dy: Float) {
        if (hypot(dx, dy) < config.centerDeadzone) return
        onSelectDetent(ClockGeometry.detentOf(ClockGeometry.angleOf(dx, dy)))
        onFinished()
    }

    fun clickDetent(detent: Int) {
        onSelectDetent(detent)
        onFinished()
    }

    fun dragStart(dx: Float, dy: Float) {
        dragging = true
        detent = currentDetent()
        dragMove(dx, dy)
    }

    fun dragMove(dx: Float, dy: Float) {
        if (!dragging || hypot(dx, dy) < config.centerDeadzone) return
        val arm = ClockGeometry.normalize(ClockGeometry.angleOf(dx, dy) + grabOffset)
        onArmAngle(arm)
        val candidate = ClockGeometry.detentOf(arm)
        val pastBoundary = ClockGeometry.distance(arm, ClockGeometry.angleOfDetent(detent)) >=
            ClockGeometry.DETENT_DEGREES / 2 + config.hysteresisDegrees
        if (candidate != detent && pastBoundary) {
            detent = candidate
            onSelectDetent(candidate)
        }
    }

    /** Pointer lifted after a drag. Advances the mode at most once. */
    fun dragEnd() {
        if (!dragging) return
        dragging = false
        onArmAngle(null)
        onFinished()
    }

    /** Gesture was taken away. The draft stays valid; nothing advances. */
    fun dragCancel() {
        if (!dragging) return
        dragging = false
        onArmAngle(null)
    }
}
