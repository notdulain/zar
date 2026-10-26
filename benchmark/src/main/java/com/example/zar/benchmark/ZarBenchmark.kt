package com.example.zar.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark journeys for `add-fluid-motion` 6.2, run against the profileable `:app` `benchmark` build type.
 *
 * These are **emulator timings**: they verify that frames are produced and deadlines are mostly met, but an emulator
 * cannot prove the physical Pixel 6a 60 Hz feel or a physical 120 Hz pass, so no such claim is made. Journeys that need
 * a signed-in, seeded account are skipped with a stated reason unless the run is pointed at the local Firebase
 * emulators (`-Pzar.useEmulators=true`) with a seeded test user; the sign-in-screen journeys always run.
 */
@RunWith(AndroidJUnit4::class)
class ZarBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    private val pkg = "com.example.zar"

    /** Cold start: the app's first frame after process launch. */
    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = pkg,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** Warm start and a scroll on the first screen the app shows (sign-in without a seeded account). */
    @Test
    fun firstScreenScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = pkg,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
        repeat(3) { device.swipeDownSmooth() }
        repeat(3) { device.swipeUpSmooth() }
    }

    /** The signed-in Home hero scroll journey (Home → fast fling → reverse). Needs a seeded signed-in session. */
    @Test
    fun homeHeroScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = pkg,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
        assumeTrue("needs a seeded signed-in session (run with -Pzar.useEmulators=true)", isSignedIn())
        repeat(4) { device.swipeDownSmooth() }
        repeat(4) { device.swipeUpSmooth() }
    }

    /** Rapid top-level tab retargeting while the nav capsule moves. Needs a seeded signed-in session. */
    @Test
    fun navRetargetFrameTiming() = benchmarkRule.measureRepeated(
        packageName = pkg,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
        assumeTrue("needs a seeded signed-in session (run with -Pzar.useEmulators=true)", isSignedIn())
        val tabs = listOf("Activity", "Plans", "More", "Home")
        repeat(3) { tabs.forEach { label -> device.findObject(By.text(label))?.click(); device.waitForIdle() } }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.isSignedIn(): Boolean =
        device.wait(Until.hasObject(By.text("Add")), 2_000)
}

/** Scrolls down a screenful (finger up) and up a screenful (finger down), independent of uiautomator version helpers. */
private fun androidx.test.uiautomator.UiDevice.swipeUpSmooth() =
    swipe(displayWidth / 2, (displayHeight * 0.28).toInt(), displayWidth / 2, (displayHeight * 0.72).toInt(), 20)

private fun androidx.test.uiautomator.UiDevice.swipeDownSmooth() =
    swipe(displayWidth / 2, (displayHeight * 0.72).toInt(), displayWidth / 2, (displayHeight * 0.28).toInt(), 20)
