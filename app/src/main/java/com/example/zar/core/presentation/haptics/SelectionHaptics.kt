package com.example.zar.core.presentation.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Where a selection tick physically goes. Tests substitute a fake. */
fun interface SelectionHapticSink {
    fun tick()
}

/**
 * Gentle semantic tick for stepping through discrete values, such as clock minutes.
 *
 * API 34+ has a feedback constant made for this ("minutes on a clock face"). Older releases use the
 * clock-tick constant. Both go through [View.performHapticFeedback] with no flags, so the system
 * touch-feedback setting and device support decide whether anything is felt. No vibration
 * permission, override flag, repeating effect or sound is involved.
 */
internal fun selectionTickConstant(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    else HapticFeedbackConstants.CLOCK_TICK

class ViewSelectionHapticSink(
    private val view: View,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : SelectionHapticSink {
    override fun tick() {
        // Unsupported or disabled feedback must leave time input untouched.
        runCatching { view.performHapticFeedback(selectionTickConstant(sdkInt)) }
    }
}

/**
 * Caps physical ticks to one per [minIntervalMillis]. A tick that arrives too soon is dropped, not
 * queued, so a fast scrub never leaves a burst behind. The caller still updates visuals every time.
 */
class SelectionTicker(
    private val sink: SelectionHapticSink,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private var lastTickMillis: Long? = null

    /** Returns true if a physical tick was requested. [timeMillis] is the input event's timestamp. */
    fun tick(timeMillis: Long): Boolean {
        val last = lastTickMillis
        if (last != null) {
            val elapsed = timeMillis - last
            if (elapsed in 0 until minIntervalMillis) return false
        }
        lastTickMillis = timeMillis
        sink.tick()
        return true
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 40L
    }
}

@Composable
fun rememberSelectionTicker(): SelectionTicker {
    val view = LocalView.current
    return remember(view) { SelectionTicker(ViewSelectionHapticSink(view)) }
}
