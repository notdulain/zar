package com.example.zar.core.presentation.components

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A dialog route's sheet is dismissed by sliding right, driven by the activity's own back dispatcher (the system button and
 * the predictive edge gesture both arrive there). The sheet never scales or drops, the scrim fades, the backdrop does not move,
 * and cancelling restores it with no pop.
 */
class SheetBackHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var bodyScroll: ScrollState? = null
    private var entranceReady = false
    private var pops = 0
    private var dismiss: (() -> Unit)? = null
    private var widthDp = 0f
    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)

    private fun show(policy: MotionPolicy, slideUpOnEnter: Boolean = false, scrollable: Boolean = false) {
        pops = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
                    SheetBackHost(onPop = { pops++ }, slideUpOnEnter = slideUpOnEnter) { close ->
                        dismiss = close
                        val sheet = LocalSheetBack.current
                        androidx.compose.runtime.LaunchedEffect(sheet) { sheet?.awaitEntrance(); entranceReady = true }
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().sheetBackScrim().background(Color.Black.copy(alpha = 0.64f)).testTag("scrim"))
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxSize().sheetBackSlide().background(Color.White)
                                .pointerInput(Unit) { detectTapGestures { } }.testTag("sheet")) {
                                if (scrollable) {
                                    val scroll = androidx.compose.foundation.rememberScrollState()
                                    bodyScroll = scroll
                                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) { Box(Modifier.height(3000.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        frame(3)
    }

    private fun frame(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun sheetLeft() = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().left.value
    private fun scrimLeft() = compose.onNodeWithTag("scrim").getUnclippedBoundsInRoot().left.value

    @Test
    fun entryRisesFromBelowWithoutMovingTheBackdrop() {
        show(full, slideUpOnEnter = true)
        org.junit.Assert.assertFalse("IME focus waits while the sheet is rising", entranceReady)
        val tops = ArrayList<Float>()
        repeat(35) {
            tops += compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().top.value
            assertEquals("the scrim stays stationary", 0f, compose.onNodeWithTag("scrim").getUnclippedBoundsInRoot().top.value, .5f)
            frame()
        }
        assertTrue("starts below its final position: $tops", tops.first() > 20f)
        assertTrue("continuous upward entrance: $tops", tops.zipWithNext().all { (a, b) -> b <= a + .1f })
        assertEquals(0f, tops.last(), .5f)
        assertTrue("automatic focus is released after arrival", entranceReady)
        assertEquals(0, pops)
    }

    @Test
    fun shortPullReturnsAndLongPullDismissesDownExactlyOnce() {
        show(full, slideUpOnEnter = true)
        frame(50)
        val sheet = compose.onNodeWithTag("sheet")
        sheet.performTouchInput { swipe(Offset(centerX, 200f), Offset(centerX, 240f), 500) }
        frame(60)
        assertEquals("a short pull returns without closing", 0, pops)
        assertEquals(0f, sheet.getUnclippedBoundsInRoot().top.value, .5f)
        sheet.performTouchInput { swipe(Offset(centerX, 200f), Offset(centerX, 700f), 500) }
        frame(80)
        assertEquals("one downward dismissal", 1, pops)
        assertTrue("leaves below the window", sheet.getUnclippedBoundsInRoot().top.value > 300f)
        assertEquals("does not slide sideways", 0f, sheetLeft(), .5f)
        assertEquals("backdrop stays stationary", 0f, compose.onNodeWithTag("scrim").getUnclippedBoundsInRoot().top.value, .5f)
    }

    @Test
    fun touchingAgainDuringDownwardExitStillPopsExactlyOnce() {
        show(full, slideUpOnEnter = true)
        frame(50)
        val sheet = compose.onNodeWithTag("sheet")
        sheet.performTouchInput { swipe(Offset(centerX, 200f), Offset(centerX, 700f), 500) }
        frame(2)
        sheet.performTouchInput { down(Offset(centerX, 100f)) }
        frame(2)
        sheet.performTouchInput { up() }
        frame(100)
        assertEquals("a new touch must not cancel an already committed dismissal", 1, pops)
    }

    @Test
    fun contentScrollsFirstAndPullingDownAtItsTopDismisses() {
        show(full, slideUpOnEnter = true, scrollable = true)
        frame(50)
        compose.runOnIdle { runBlocking { bodyScroll!!.scrollTo(500) } }
        val sheet = compose.onNodeWithTag("sheet")
        sheet.performTouchInput { swipe(Offset(centerX, 200f), Offset(centerX, 300f), 500) }
        frame(20)
        assertEquals("content scrolling does not close the sheet", 0, pops)
        assertTrue("the content scrolled toward its top", bodyScroll!!.value in 1..499)
        assertEquals(0f, sheet.getUnclippedBoundsInRoot().top.value, .5f)
        compose.runOnIdle { runBlocking { bodyScroll!!.scrollTo(0) } }
        sheet.performTouchInput { swipe(Offset(centerX, 200f), Offset(centerX, 700f), 500) }
        frame(80)
        assertEquals("unconsumed downward pull dismisses", 1, pops)
    }

    @Test
    fun theCloseButtonSlidesTheSheetRightAndPopsOnce() {
        show(full)

        compose.runOnUiThread { dismiss!!.invoke() }
        val lefts = ArrayList<Float>()
        repeat(80) { frame(); lefts += sheetLeft(); assertEquals("the backdrop never moves", 0f, scrimLeft(), 0.5f) }

        assertTrue("the sheet moves toward the end only: $lefts", lefts.zipWithNext().all { (a, b) -> b >= a - 0.01f })
        assertTrue("it travels off screen: ${lefts.last()} of $widthDp", lefts.last() > widthDp * 0.95f)
        assertEquals("exactly one pop", 1, pops)
        assertEquals(widthDp, compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().width.value, 0.6f)
    }

    @Test
    fun aPredictiveGestureFollowsAtHalfProgressAndCancelRestoresWithoutAPop() {
        show(full)
        val dispatcher = compose.activity.onBackPressedDispatcher

        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(160f, 400f, 0.6f, BackEventCompat.EDGE_LEFT)) }
        frame(2)

        assertEquals("the sheet is at half the gesture's progress", widthDp * 0.3f, sheetLeft(), widthDp * 0.02f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frame(80)

        assertEquals("cancel springs the sheet back", 0f, sheetLeft(), 0.6f)
        assertEquals("cancel does not pop", 0, pops)
    }

    @Test
    fun aCommittedGestureCompletesTheSlideAndPopsExactlyOnce() {
        show(full)
        val dispatcher = compose.activity.onBackPressedDispatcher

        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 400f, 0.8f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        frame(120)

        assertEquals(1, pops)
        assertTrue("the sheet left to the right", sheetLeft() > widthDp * 0.95f)
    }

    @Test
    fun reducedMotionDismissesAtOnceWithoutTranslatingOrFading() {
        show(reduced)

        compose.runOnUiThread { dismiss!!.invoke() }
        val lefts = ArrayList<Float>()
        repeat(12) { frame(); lefts += sheetLeft() }

        assertTrue("no translation under reduced motion: $lefts", lefts.all { kotlin.math.abs(it) < 0.5f })
        assertEquals("pops once, immediately, with no fade first", 1, pops)
    }
}
