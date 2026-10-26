package com.example.zar.core.presentation.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `adaptive-refresh-performance`: animation follows wall-clock time, so a spring settles in the same time on a 60, 90
 * or 120 Hz display. These tests deliver frames at each period to a Compose frame clock and compare the result. They
 * prove the motion tokens are cadence-independent; they do not prove that a physical 120 Hz panel presents frames.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrameCadenceTest {

    private val periods = listOf(FrameDriver.HZ_60, FrameDriver.HZ_90, FrameDriver.HZ_120)

    private data class Run(val settleMillis: Double, val samples: List<Float>, val frames: Int, val overshoot: Float)

    /** Runs [token] from 0 to 1 and samples the value after each of [sampleMillis] of animation time. */
    private fun run(token: SpringToken, period: Long, sampleMillis: List<Int>): Run {
        var result: Run? = null
        runTest {
            val driver = FrameDriver(this, period)
            val value = Animatable(0f)
            val job = launch(driver.clock) { value.animateTo(1f, spring(token.dampingRatio, token.stiffness)) }
            runCurrent()
            driver.step() // the first frame only starts the animation
            val startNanos = driver.nowNanos
            val samples = ArrayList<Float>()
            var peak = 0f
            var pending = sampleMillis.sorted()
            while (job.isActive && driver.frames < 2_000) {
                driver.step()
                val elapsed = (driver.nowNanos - startNanos) / 1_000_000.0
                peak = max(peak, value.value)
                while (pending.isNotEmpty() && elapsed >= pending.first() - 0.5) {
                    samples += value.value
                    pending = pending.drop(1)
                }
            }
            result = Run((driver.nowNanos - startNanos) / 1_000_000.0, samples, driver.frames, peak - 1f)
        }
        return result!!
    }

    @Test
    fun everyTokenSettlesInTheSameWallClockTimeAt60And90And120Hz() {
        val tokens = mapOf(
            "settle" to ZarSprings.Settle,
            "navSelection" to ZarSprings.NavSelection,
            "sheetSettle" to ZarSprings.SheetSettle,
            "route" to ZarSprings.Route,
        )
        tokens.forEach { (name, token) ->
            val runs = periods.map { run(token, it, emptyList()) }
            val reference = runs.first().settleMillis
            runs.forEachIndexed { i, r ->
                // within 10% or one 60 Hz frame, whichever is larger (verification.md)
                val tolerance = max(reference * 0.10, 16.7)
                assertTrue(
                    "$name at ${periods[i]}ns settled in ${r.settleMillis}ms vs ${reference}ms at 60 Hz",
                    abs(r.settleMillis - reference) <= tolerance,
                )
            }
            println("cadence $name settle ms @60/90/120 = ${runs.map { "%.1f".format(it.settleMillis) }}  frames = ${runs.map { it.frames }}")
        }
    }

    @Test
    fun theSpringIsAtTheSamePositionAtTheSameWallClockTimeAcrossCadences() {
        val times = listOf(50, 100, 150, 200, 300)
        val tokens = listOf(ZarSprings.NavSelection, ZarSprings.Settle, ZarSprings.Route)
        tokens.forEach { token ->
            val runs = periods.map { run(token, it, times) }
            val base = runs.first().samples
            runs.drop(1).forEach { r ->
                assertEquals(base.size, r.samples.size)
                base.indices.forEach { i ->
                    // One frame of travel at 60 Hz is the largest sampling skew between cadences.
                    assertEquals("spring sample at ${times[i]}ms", base[i], r.samples[i], 0.08f)
                }
            }
        }
    }

    @Test
    fun navSelectionOvershootsAndLargePageSpringsDoNot() {
        periods.forEach { period ->
            assertTrue("navSelection should bounce a little", run(ZarSprings.NavSelection, period, emptyList()).overshoot > 0.01f)
            assertEquals("route is critically damped", 0f, run(ZarSprings.Route, period, emptyList()).overshoot, 0.002f)
            assertEquals("settle is critically damped", 0f, run(ZarSprings.Settle, period, emptyList()).overshoot, 0.002f)
        }
    }

    @Test
    fun navSelectionSettlesInsideTheQuietSettleWindow() {
        // design.md aims for roughly 250-400 ms; allow the visibility threshold's tail.
        periods.forEach { period ->
            val ms = run(ZarSprings.NavSelection, period, emptyList()).settleMillis
            assertTrue("navSelection settled in ${ms}ms", ms in 150.0..600.0)
        }
    }

    @Test
    fun theFrameCountScalesWithTheCadenceButNotTheDuration() {
        val frames = periods.map { run(ZarSprings.NavSelection, it, emptyList()).frames }
        assertTrue("120 Hz should deliver about twice the frames of 60 Hz: $frames", frames[2] >= frames[0] * 1.7)
    }
}
