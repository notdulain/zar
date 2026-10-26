package com.example.zar.core.presentation.motion

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The shared settle spring and press feedback on a controlled clock. 16 ms frames; it proves interruptibility, no
 * overshoot for the critically damped token, the reduced-motion paths and press cancellation, not a refresh rate.
 */
class SettleMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)
    private var expanded by mutableStateOf(false)
    private var clicks by mutableIntStateOf(0)

    private fun frame(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun height() = compose.onNodeWithTag("section").getUnclippedBoundsInRoot().height.value

    private fun showSection(policy: MotionPolicy) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.testTag("section").settleSize()) {
                            Box(Modifier.fillMaxWidth().height(40.dp))
                            if (expanded) Box(Modifier.fillMaxWidth().height(160.dp))
                        }
                        Text("below", Modifier.testTag("below"))
                    }
                }
            }
        }
        frame(3)
    }

    @Test
    fun expandingASectionSettlesToItsNewHeightWithoutOvershoot() {
        expanded = false
        showSection(full)
        val start = height()
        assertEquals(40f, start, 0.5f)

        expanded = true
        frame()
        val samples = ArrayList<Float>()
        repeat(40) { frame(); samples += height() }
        Log.i("ZarSettle", "expand 40dp->200dp height per frame: $samples")

        assertTrue("it grows through intermediate sizes: $samples", samples.any { it in 45f..195f })
        assertTrue("monotonic and never past the target: $samples", samples.zipWithNext().all { (a, b) -> b >= a - 0.01f } && samples.all { it <= 200.5f })
        assertEquals(200f, samples.last(), 0.6f)
    }

    @Test
    fun aSecondChangeMidSettleRetargetsFromTheCurrentSize() {
        expanded = false
        showSection(full)
        expanded = true
        frame(6)
        val mid = height()
        assertTrue("mid-settle $mid", mid in 45f..195f)

        expanded = false
        frame()
        val after = height()

        assertTrue("no jump on reversal: $mid -> $after", kotlin.math.abs(after - mid) < 60f)
        frame(60)
        assertEquals(40f, height(), 0.6f)
    }

    @Test
    fun reducedMotionChangesTheSizeAtOnce() {
        expanded = false
        showSection(reduced)

        expanded = true
        frame(2)

        assertEquals(200f, height(), 0.5f)
    }

    private fun showButton(policy: MotionPolicy) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    Box(Modifier.padding(40.dp)) {
                        ZarButton("Save", onClick = { clicks++ }, modifier = Modifier.fillMaxWidth().testTag("button"))
                    }
                }
            }
        }
        frame(3)
    }

    // The button's clickable sits inside the scaled layer, so its root bounds show the scale; the outer tagged node does not.
    private fun buttonWidth() = compose.onNodeWithText("Save").getBoundsInRoot().width.value

    @Test
    fun aPressedButtonShrinksTwoPercentAtMostAndSpringsBackOnRelease() {
        clicks = 0
        showButton(full)
        val rest = buttonWidth()

        compose.onNodeWithTag("button").performTouchInput { down(center) }
        frame(20)
        val pressed = buttonWidth()
        compose.onNodeWithTag("button").performTouchInput { up() }
        frame(30)

        assertEquals("scaled to 98%", rest * MotionPolicy.PRESSED_SCALE, pressed, 0.6f)
        assertEquals("back to rest", rest, buttonWidth(), 0.3f)
        assertEquals("release commits the click once", 1, clicks)
    }

    @Test
    fun draggingAwayCancelsThePressSoNothingIsClickedAndTheScaleReturns() {
        clicks = 0
        showButton(full)
        val rest = buttonWidth()

        compose.onNodeWithTag("button").performTouchInput {
            down(center)
            moveBy(Offset(0f, 600f))
        }
        frame(30)
        compose.onNodeWithTag("button").performTouchInput { up() }
        frame(5)

        assertEquals(0, clicks)
        assertEquals(rest, buttonWidth(), 0.3f)
    }

    @Test
    fun reducedMotionNeverScalesAPressedButton() {
        showButton(reduced)
        val rest = buttonWidth()

        compose.onNodeWithTag("button").performTouchInput { down(center) }
        frame(10)

        assertEquals(rest, buttonWidth(), 0.01f)
        compose.onNodeWithTag("button").performTouchInput { up() }
    }
}
