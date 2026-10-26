package com.example.zar.core.presentation.motion

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Drives a Compose frame clock one frame at a time at a fixed display period, so a test can run the same animation at
 * 60, 90 and 120 Hz and look at it between frames. Frame times are those a display of that rate would deliver; nothing
 * here proves how a physical panel behaves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrameDriver(private val scope: TestScope, val periodNanos: Long) {
    val clock = BroadcastFrameClock()
    var nowNanos = 0L
        private set
    var frames = 0
        private set

    /** Delivers one display frame and lets everything waiting on it run. */
    fun step() {
        nowNanos += periodNanos
        frames++
        clock.sendFrame(nowNanos)
        scope.runCurrent()
    }

    fun stepFor(millis: Double) {
        val frameCount = Math.round(millis * 1_000_000.0 / periodNanos).toInt()
        repeat(frameCount) { step() }
    }

    companion object {
        const val HZ_60 = 16_666_667L
        const val HZ_90 = 11_111_111L
        const val HZ_120 = 8_333_333L
    }
}
