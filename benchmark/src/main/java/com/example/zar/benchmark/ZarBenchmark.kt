package com.example.zar.benchmark

import android.graphics.Point
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import kotlin.math.cos
import kotlin.math.sin
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark journeys for `add-fluid-motion` 6.2 against the profileable `:app` `benchmark` build type.
 *
 * The signed-in journeys run on the demo account that `tools/benchmark-seed.mjs` creates on the LOCAL Firebase
 * emulators (the `benchmark` build must be built with `-Pzar.useEmulators=true`). Nothing in the app is bypassed: the
 * journeys type the demo credentials into the real sign-in screen once, and Firebase Auth keeps the session across the
 * process restarts the benchmark performs. No live Firebase project is contacted.
 *
 * These are **emulator timings**. They show that frames are produced and what the CPU/overrun distribution looks like
 * on this machine, but an emulator cannot prove a physical Pixel 6a 60 Hz feel or any 120 Hz result, so none is claimed.
 */
@RunWith(AndroidJUnit4::class)
class ZarBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    /** `-Pandroid.testInstrumentationRunnerArguments.zarTargetPackage=com.example.zar.perf` measures a side-by-side build. */
    private val pkg = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("zarTargetPackage")
        ?: "com.example.zar"

    /** The animator scale is restored after every test, whether or not a reduced-motion journey changed it. */
    @After fun restoreAnimatorScale() {
        UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("settings put global animator_duration_scale 1")
    }

    /** Cold start to the first frame of the sign-in screen (no session). Clears nothing: it is the signed-out path. */
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

    /** Cold start of the signed-in app to the first Home frame (hero + chart + ledger already in the cache). */
    @Test
    fun coldStartupSignedInHome() = benchmarkRule.measureRepeated(
        packageName = pkg,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
        setupBlock = { openHome() },
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("RECEIVED THIS MONTH")), 10_000)
    }

    /** Home hero scroll: a fast fling down and back, which drives the top-card parallax layers. */
    @Test
    fun homeHeroScrollFrameTiming() = frameJourney(setup = { openHome() }) {
        repeat(4) { device.swipe(device.displayWidth / 2, (device.displayHeight * 0.75).toInt(), device.displayWidth / 2, (device.displayHeight * 0.25).toInt(), 12) }
        repeat(4) { device.swipe(device.displayWidth / 2, (device.displayHeight * 0.25).toInt(), device.displayWidth / 2, (device.displayHeight * 0.75).toInt(), 12) }
    }

    /** Rapid top-level tab retargeting while the shared nav capsule moves. */
    @Test
    fun navRetargetFrameTiming() = frameJourney(setup = { openActivityFromHome() }) { retargetTabs() }

    /** The same retargeting with system animations off, which the app treats as reduced motion (instant, no motion). */
    @Test
    fun navRetargetReducedMotionFrameTiming() = frameJourney(
        setup = { device.executeShellCommand("settings put global animator_duration_scale 0"); openActivityFromHome() },
    ) { retargetTabs() }

    /** Route changes and list scrolling: each top-level tab, scrolled once each way, ending back on Home. */
    @Test
    fun routesAndListScrollFrameTiming() = frameJourney(setup = { openActivityFromHome() }) {
        listOf("Activity", "Plans", "More", "Activity", "Home").forEach { label ->
            tapTab(label)
            device.waitForIdle(800)
            if (label != "Home") {
                device.swipe(device.displayWidth / 2, (device.displayHeight * 0.7).toInt(), device.displayWidth / 2, (device.displayHeight * 0.3).toInt(), 14)
                device.swipe(device.displayWidth / 2, (device.displayHeight * 0.3).toInt(), device.displayWidth / 2, (device.displayHeight * 0.7).toInt(), 14)
            }
        }
    }

    /** Add sheet open, Expense/Income/Transfer tab switching, the time dial dragged around, then closed. */
    @Test
    fun addSheetAndDialFrameTiming() = frameJourney(setup = { openHome() }) { addSheetJourney() }

    /** The sheet and dial with system animations off. */
    @Test
    fun addSheetAndDialReducedMotionFrameTiming() = frameJourney(
        setup = { device.executeShellCommand("settings put global animator_duration_scale 0"); openHome() },
    ) { addSheetJourney() }

    /** One unmeasured pass of the journey has run for this test, so the measured iterations start warm. */
    private var warmedUp = false

    private fun frameJourney(
        setup: MacrobenchmarkScope.() -> Unit,
        measure: MacrobenchmarkScope.() -> Unit,
    ) = benchmarkRule.measureRepeated(
        packageName = pkg,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        // Full AOT approximates the installed app's steady state; CompilationMode.None() would time interpreted code.
        compilationMode = CompilationMode.Full(),
        setupBlock = {
            setup()
            if (!warmedUp) {
                // The same journey once, unmeasured and on the same fixture and compilation, then a clean setup again:
                // first-use class loading, JIT-free AOT page-in and first composition are not part of the five.
                warmedUp = true
                measure()
                setup()
            }
        },
        measureBlock = {
            measure()
            logDisplayCadence()
        },
    )

    /** The display's reported modes while a journey ran. Capability only: the produced cadence comes from the trace. */
    private fun logDisplayCadence() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val modes = display.supportedModes.joinToString { "%.1f".format(it.refreshRate) }
        android.util.Log.i("ZarCadence", "journey display activeHz=%.1f supportedHz=[%s]".format(display.mode.refreshRate, modes))
    }

    // --- Journeys ----------------------------------------------------------------------------

    private fun MacrobenchmarkScope.retargetTabs() {
        repeat(3) {
            listOf("Plans", "More", "Activity", "Home").forEach { label ->
                tapTab(label)
                // A short gap, not waitForIdle: the next tap lands while the capsule is still travelling.
                Thread.sleep(180)
            }
        }
        device.waitForIdle(1_000)
    }

    private fun MacrobenchmarkScope.addSheetJourney() {
        tap(By.desc("Add an entry"))
        device.wait(Until.hasObject(By.text("MANUAL ENTRY")), 3_000)
        tap(By.text("MANUAL ENTRY"))
        device.wait(Until.hasObject(By.text("Add transaction")), 3_000)
        listOf("Income", "Transfer", "Expense").forEach { tab ->
            tap(By.text(tab))
            device.waitForIdle(600)
        }
        tap(By.text(Pattern.compile("\\d{1,2}:\\d{2} [AP]M")))
        if (device.wait(Until.hasObject(By.text("Time")), 3_000)) {
            // The dial centre and radius are measured from where its "12" and "6" numerals sit on this layout.
            val twelve = device.findObject(By.text("12"))?.visibleBounds
            val six = device.findObject(By.text("6"))?.visibleBounds
            if (twelve != null && six != null) {
                val cx = (twelve.centerX() + six.centerX()) / 2
                val cy = (twelve.centerY() + six.centerY()) / 2
                val radius = (six.centerY() - twelve.centerY()) / 2 * 0.8
                val arc = (0..24).map { step ->
                    val angle = Math.toRadians(step * 15.0 - 90.0)
                    Point((cx + radius * cos(angle)).toInt(), (cy + radius * sin(angle)).toInt())
                }
                device.swipe(arc.toTypedArray(), 6)
                device.swipe(arc.reversed().toTypedArray(), 6)
            }
            device.pressBack()
            device.waitForIdle(600)
        }
        tap(By.desc("Close"))
        device.waitForIdle(800)
    }

    // --- Signed-in state ----------------------------------------------------------------------

    /** Launches the app, signs in through the real sign-in screen if needed, and waits for Home. */
    private fun MacrobenchmarkScope.openHome() {
        pressHome()
        startActivityAndWait()
        if (device.wait(Until.hasObject(By.text("SIGN IN")), 2_500)) signIn()
        check(device.wait(Until.hasObject(By.text("RECEIVED THIS MONTH")), 20_000)) {
            "Home never appeared. Seed the demo account with tools/benchmark-seed.mjs and build with -Pzar.useEmulators=true."
        }
        device.waitForIdle(1_500)
    }

    private fun MacrobenchmarkScope.signIn() {
        val fields = device.findObjects(By.clazz("android.widget.EditText"))
        check(fields.size >= 2) { "sign-in fields not found" }
        fields[0].text = DEMO_EMAIL
        fields[1].text = DEMO_PASSWORD
        if (device.findObject(By.text("Sign in")) == null) device.pressBack()
        tap(By.text("Sign in"))
    }

    /** From Home, opens Activity through its tile: after that the full capsule (Home/Activity/Plans/More) is shown. */
    private fun MacrobenchmarkScope.openActivityFromHome() {
        openHome()
        tap(By.text("Activity"))
        device.waitForIdle(1_200)
    }

    /** Finds and taps [selector], looking again if the node went stale because the screen was mid-transition. */
    private fun MacrobenchmarkScope.tap(selector: androidx.test.uiautomator.BySelector) {
        repeat(3) {
            try { device.findObject(selector)?.click(); return } catch (_: androidx.test.uiautomator.StaleObjectException) { device.waitForIdle(300) }
        }
    }

    private fun MacrobenchmarkScope.tapTab(name: String) {
        repeat(3) {
            try { tab(name)?.click(); return } catch (_: androidx.test.uiautomator.StaleObjectException) { device.waitForIdle(300) }
        }
    }

    /** The nav capsule's label for [name]; on Home the lower one, because the tiles reuse the same words above it. */
    private fun MacrobenchmarkScope.tab(name: String) =
        device.findObjects(By.text(name)).maxByOrNull { it.visibleBounds.centerY() }

    private companion object {
        const val DEMO_EMAIL = "benchmark@zar.test"
        const val DEMO_PASSWORD = "benchmark-pass-1"
    }
}
