package com.example.zar.core.presentation.components

import com.example.zar.core.presentation.motion.MotionLevel
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarDimens

internal data class SpeedDialActionPose(val travel: Float, val scale: Float, val alpha: Float)
internal data class SpeedDialPose(
    val rotation: Float,
    val quick: SpeedDialActionPose,
    val manual: SpeedDialActionPose,
    val scrim: Float,
)

/** Allocation-free helpers shared by the graphics layers and the fixture model. */
internal fun speedDialPhase(progress: Float, quick: Boolean): Float {
    val p = progress.coerceIn(0f, 1f + ZarMotion.SPEED_DIAL_MAX_OVERSHOOT_DP / ZarDimens.SpeedDialQuickOffset.value)
    return if (quick || p > 1f) p
    else ((p - ZarMotion.SPEED_DIAL_PHASE_LAG) / (1f - ZarMotion.SPEED_DIAL_PHASE_LAG)).coerceAtLeast(0f)
}

internal fun speedDialScale(phase: Float): Float =
    ZarMotion.SPEED_DIAL_START_SCALE + (1f - ZarMotion.SPEED_DIAL_START_SCALE) * phase.coerceIn(0f, 1f)

internal fun speedDialAlpha(phase: Float): Float = (phase / ZarMotion.SPEED_DIAL_FADE_END).coerceIn(0f, 1f)

internal fun speedDialRotation(progress: Float, level: MotionLevel, expanded: Boolean): Float =
    if (level == MotionLevel.Full) ZarMotion.SPEED_DIAL_ROTATION * speedDialPhase(progress, quick = true)
    else if (expanded) ZarMotion.SPEED_DIAL_ROTATION else 0f

/** One presentation value, including reversals. Only travel and rotation may overshoot. */
internal fun speedDialPose(progress: Float, level: MotionLevel, expanded: Boolean): SpeedDialPose {
    val alpha = progress.coerceIn(0f, 1f)
    if (level != MotionLevel.Full) return SpeedDialPose(
        rotation = speedDialRotation(progress, level, expanded),
        quick = SpeedDialActionPose(ZarDimens.SpeedDialQuickOffset.value, 1f, alpha),
        manual = SpeedDialActionPose(ZarDimens.SpeedDialManualOffset.value, 1f, alpha),
        scrim = alpha,
    )
    fun action(phase: Float, offset: Float) = SpeedDialActionPose(
        travel = offset * phase,
        scale = speedDialScale(phase),
        alpha = speedDialAlpha(phase),
    )
    return SpeedDialPose(
        rotation = speedDialRotation(progress, level, expanded),
        quick = action(speedDialPhase(progress, quick = true), ZarDimens.SpeedDialQuickOffset.value),
        manual = action(speedDialPhase(progress, quick = false), ZarDimens.SpeedDialManualOffset.value),
        scrim = alpha,
    )
}

internal fun speedDialUsesRow(clearHeightDp: Float): Boolean = clearHeightDp < ZarDimens.SpeedDialStackClearance.value

/** The Home row opens toward logical start even when pixel rounding puts its anchor off centre. */
internal fun speedDialRowDirection(centered: Boolean, rtl: Boolean, anchorCenter: Float, windowCenter: Float): Float = when {
    centered || anchorCenter == windowCenter -> if (rtl) 1f else -1f
    anchorCenter > windowCenter -> -1f
    else -> 1f
}
