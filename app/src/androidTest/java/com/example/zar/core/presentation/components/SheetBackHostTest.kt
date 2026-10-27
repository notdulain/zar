package com.example.zar.core.presentation.components

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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

    private var pops = 0
    private var dismiss: (() -> Unit)? = null
    private var widthDp = 0f
    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)

    private fun show(policy: MotionPolicy) {
        pops = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
                    SheetBackHost(onPop = { pops++ }) { close ->
                        dismiss = close
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().sheetBackScrim().background(Color.Black.copy(alpha = 0.64f)).testTag("scrim"))
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxSize().sheetBackSlide().background(Color.White).testTag("sheet"))
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
    fun reducedMotionCrossfadesInsteadOfTranslating() {
        show(reduced)

        compose.runOnUiThread { dismiss!!.invoke() }
        val lefts = ArrayList<Float>()
        repeat(12) { frame(); lefts += sheetLeft() }

        assertTrue("no translation under reduced motion: $lefts", lefts.all { kotlin.math.abs(it) < 0.5f })
        assertEquals("pops once the short fade is done", 1, pops)
    }
}
