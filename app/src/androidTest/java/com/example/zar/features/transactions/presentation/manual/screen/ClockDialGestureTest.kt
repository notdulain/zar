package com.example.zar.features.transactions.presentation.manual.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.haptics.SelectionHapticSink
import com.example.zar.core.presentation.haptics.SelectionTicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Real Compose gesture coverage for the dial: a tap, a drag past slop with a single release, and a cancelled gesture.
 * The pure maths is covered by [ClockDialInputTest]; this exercises the pointer plumbing that feeds it.
 */
class ClockDialGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recorder {
        val selected = mutableListOf<Int>()
        var finished = 0
        var arm: Float? = null
        var ticked = 0
    }

    private fun host(recorder: Recorder, initialDetent: () -> Int): ClockDialInput {
        var detent = initialDetent()
        return ClockDialInput(
            config = ClockDialConfig(armLength = 100f, centerDeadzone = 24f, armGrabRadius = 24f),
            restingArmAngle = { ClockGeometry.angleOfDetent(detent) },
            currentDetent = { detent },
            onSelectDetent = { detent = it; recorder.selected += it },
            onFinished = { recorder.finished++ },
            ticks = SelectionTicker(SelectionHapticSink { recorder.ticked++ }),
            onArmAngle = { recorder.arm = it },
        )
    }

    private fun show(input: ClockDialInput) {
        compose.setContent {
            Box(Modifier.size(256.dp).testTag("dial").pointerInput(input) { detectClockGestures(input) })
        }
    }

    @Test
    fun aTapSelectsTheSectorUnderTheFingerAndFinishesOnce() {
        val recorder = Recorder()
        val input = host(recorder) { 0 }
        show(input)

        // 3 o'clock is detent 3; a tap with no movement must not start a drag.
        compose.onNodeWithTag("dial").performTouchInput { click(center + Offset(width * 0.4f, 0f)) }
        compose.waitForIdle()

        assertEquals(listOf(3), recorder.selected)
        assertEquals(1, recorder.finished)
        assertNull(recorder.arm)
    }

    @Test
    fun aDragPastSlopMovesTheArmAndFinishesOnceOnRelease() {
        val recorder = Recorder()
        val input = host(recorder) { 0 }
        show(input)

        // From 12 o'clock across to 3 o'clock and down to 6 o'clock.
        compose.onNodeWithTag("dial").performTouchInput {
            swipe(
                start = center + Offset(0f, -height * 0.4f),
                end = center + Offset(0f, height * 0.4f),
                durationMillis = 250,
            )
        }
        compose.waitForIdle()

        assertTrue("arm moved during the drag", recorder.selected.isNotEmpty())
        assertEquals(1, recorder.finished)
        assertNull("arm is released back to its detent", recorder.arm)
    }

    @Test
    fun aCancelledGestureEndsDraggingWithoutFinishing() {
        val recorder = Recorder()
        val input = host(recorder) { 0 }
        show(input)

        compose.onNodeWithTag("dial").performTouchInput {
            down(center + Offset(0f, -height * 0.4f))
            moveTo(center + Offset(width * 0.4f, 0f))
            cancel()
        }
        compose.waitForIdle()

        assertEquals("cancel must not advance or commit", 0, recorder.finished)
        assertNull("cancel clears the arm", recorder.arm)
    }
}
