package com.example.zar.core.presentation.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How much movement the app may use right now. Resolved once from the app switch and Android's animation setting. */
enum class MotionLevel {
    /** Springs, parallax, bounce, press scaling and spatial travel. */
    Full,

    /** The app's Reduce motion switch is on: no spatial travel or bounce; direct changes or a crossfade of 100ms or less. */
    Reduced,

    /** Android's animations are off (animator duration scale 0): every change lands on its final state at once. */
    Instant,
}

/** A named spring from `add-fluid-motion` design.md. Tune the numbers here once rather than per screen. */
@Immutable
class SpringToken(val dampingRatio: Float, val stiffness: Float)

/** The shared spring tokens. They are starting values to tune in the interactive prototype, not measured shipping values. */
object ZarSprings {
    val SpeedDialOpen = SpringToken(dampingRatio = 0.72f, stiffness = 320f)
    /** Panel reflow, ordinary content expansion and reversible state settling. */
    val Settle = SpringToken(dampingRatio = 1f, stiffness = 400f)

    /** The selected navbar capsule: a deliberately small bounce. */
    val NavSelection = SpringToken(dampingRatio = 0.75f, stiffness = 500f)

    /**
     * The sliding indicator of the Add transaction selectors (type and classification). Starts from [NavSelection]'s
     * values; tune both selectors here.
     */
    val AddSelector = SpringToken(dampingRatio = 0.75f, stiffness = 500f)

    /** Release of a custom sheet only; never add it around a Material sheet's own spring. */
    val SheetSettle = SpringToken(dampingRatio = 0.9f, stiffness = 450f)

    /** Full-screen push/pop and peer-tab travel: critically damped, no overshoot on large pages. */
    val Route = SpringToken(dampingRatio = 1f, stiffness = 500f)
}

/**
 * The non-spring numbers from `add-fluid-motion` (mirrored by the Figma `Motion` collection). Starting values to tune in
 * the interactive prototype, not measured shipping values. Offsets and caps are multiplied through [MotionPolicy.travel],
 * so they collapse to zero when motion is reduced.
 */
object ZarMotion {
    const val SPEED_DIAL_PHASE_LAG = 0.18f
    const val SPEED_DIAL_START_SCALE = 0.72f
    const val SPEED_DIAL_FADE_END = 0.35f
    const val SPEED_DIAL_ROTATION = 45f
    const val SPEED_DIAL_MAX_OVERSHOOT_DP = 6f
    /** Peer-tab content travel between Home / Activity / Plans / More. */
    val TabTravel = 24.dp

    /** First reveal of the full navbar: a quiet rise, no spring pop. */
    val NavRevealRise = 8.dp
    const val NAV_REVEAL_MILLIS = 160

    /** The incoming page of a tab jump over one or more pages: TabTravel slide and this fade. */
    const val TAB_ARRIVAL_MILLIS = 160

    /** Every navbar slot has the same weight: selecting a tab never reshapes the row. */
    const val NAV_SLOT_WEIGHT = 1f

    /** Furthest the selected capsule may overshoot its target when there is room. */
    val NavMaxOvershoot = 6.dp

    /** Furthest an Add selector indicator may overshoot its target slot. */
    val AddSelectorMaxOvershoot = 6.dp

    /** Fraction of the hero's scroll displacement each layer is compensated by, and its cap. */
    const val PARALLAX_BACKGROUND = 0.20f
    val ParallaxBackgroundCap = 24.dp
    const val PARALLAX_TEXT = 0.06f
    val ParallaxTextCap = 8.dp
    const val PARALLAX_CHART = 0.12f
    val ParallaxChartCap = 16.dp

    /** Length of a Back transition. A tween, because the predictive gesture scrubs it and a spring has no fixed length. */
    const val POP_MILLIS = 300

    /** A parent screen recedes by at most this fraction of the window width while a child enters. */
    const val ROUTE_PARENT_RECEDE = 0.2f

    /** Clock dial: one haptic tick per changed detent, no more often than this; hysteresis against boundary flicker. */
    const val HAPTIC_MIN_TICK_MILLIS = 40
    const val DIAL_HYSTERESIS_DEGREES = 3f
}

/**
 * The one answer to "may this move, and how?" for the whole app. Read it from [LocalZarMotion]; never read the
 * Reduce motion setting or Android's animation scale directly in a screen.
 *
 * Effective reduction is the app switch OR disabled system animations; Android has no single Reduce Motion flag, so
 * the animator duration scale is the system signal (it is also the scale Compose applies to its own animations).
 * The policy never affects navigation, scroll, selection or haptics: it only decides how a change is presented.
 */
@Immutable
class MotionPolicy(val reduceMotionSetting: Boolean, val systemAnimationsEnabled: Boolean) {

    val level: MotionLevel = when {
        !systemAnimationsEnabled -> MotionLevel.Instant
        reduceMotionSetting -> MotionLevel.Reduced
        else -> MotionLevel.Full
    }

    /** Parallax, bounce, press scaling and large spatial travel are allowed only at [MotionLevel.Full]. */
    val allowsSpatialMotion: Boolean get() = level == MotionLevel.Full

    /** Scale for a pressed non-text action. 1 means no scaling. */
    val pressScale: Float get() = if (allowsSpatialMotion) PRESSED_SCALE else 1f

    /** [distance] when travel is allowed, otherwise zero: use it to size parallax offsets and peer-tab travel. */
    fun travel(distance: Dp): Dp = if (allowsSpatialMotion) distance else 0.dp

    /** The spring for [token], or an immediate change when motion is reduced or off. */
    fun <T> spring(token: SpringToken, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (allowsSpatialMotion) spring(token.dampingRatio, token.stiffness, visibilityThreshold) else snap()

    /**
     * A crossfade. Full motion uses [fullMillis]; Reduced caps it at [REDUCED_FADE_MAX_MILLIS]; Instant is immediate.
     */
    fun <T> fade(fullMillis: Int = DEFAULT_FADE_MILLIS): FiniteAnimationSpec<T> = when (level) {
        MotionLevel.Full -> tween(fullMillis)
        MotionLevel.Reduced -> tween(minOf(fullMillis, REDUCED_FADE_MAX_MILLIS))
        MotionLevel.Instant -> snap()
    }

    override fun equals(other: Any?): Boolean =
        other is MotionPolicy && other.reduceMotionSetting == reduceMotionSetting && other.systemAnimationsEnabled == systemAnimationsEnabled

    override fun hashCode(): Int = 31 * reduceMotionSetting.hashCode() + systemAnimationsEnabled.hashCode()

    override fun toString(): String = "MotionPolicy($level, reduceMotionSetting=$reduceMotionSetting, systemAnimationsEnabled=$systemAnimationsEnabled)"

    companion object {
        const val PRESSED_SCALE = 0.98f
        const val REDUCED_FADE_MAX_MILLIS = 100
        const val DEFAULT_FADE_MILLIS = 160

        /** Android reports disabled animations as an animator duration scale of exactly zero. */
        fun from(reduceMotionSetting: Boolean, animatorDurationScale: Float): MotionPolicy =
            MotionPolicy(reduceMotionSetting, systemAnimationsEnabled = animatorDurationScale != 0f)
    }
}

/** Full motion unless a [ProvideZarMotion] ancestor says otherwise, so previews and tests behave as before. */
val LocalZarMotion = staticCompositionLocalOf { MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true) }
