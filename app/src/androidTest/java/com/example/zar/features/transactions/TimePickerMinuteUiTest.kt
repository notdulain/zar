package com.example.zar.features.transactions

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.manual.screen.ManualTimePickerSheet
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The time dial picks any single minute, not just multiples of five. */
class TimePickerMinuteUiTest {
    @get:Rule val compose = createComposeRule()

    private var set: LocalTime? = null

    private fun show(start: LocalTime) {
        compose.setContent { ZarTheme { ManualTimePickerSheet(time = start, onCancel = {}, onSetTime = { set = it }) } }
        // Choose the hour so the dial switches to minutes.
        compose.onNodeWithTag("time-dial").performTouchInput { click(center + Offset(width * 0.4f, 0f)) }
        compose.waitForIdle()
    }

    private fun Offset.onDial(degrees: Float, radius: Float) =
        Offset(x + radius * sin(degrees * PI / 180).toFloat(), y - radius * cos(degrees * PI / 180).toFloat())

    @Test
    fun tappingBetweenTheFiveMinuteLabelsPicksThatExactMinute() {
        show(LocalTime.of(9, 0))
        // 17 minutes is 102 degrees clockwise from 12.
        compose.onNodeWithTag("time-dial").performTouchInput { click(center.onDial(102f, width * 0.39f)) }
        compose.waitForIdle()
        com.example.zar.core.presentation.evidence.PrScreenshots.capture("time-picker-17-minutes")
        compose.onNodeWithText("Set time").performClick()
        compose.runOnIdle { assertEquals(LocalTime.of(3, 17), set) }
    }

    @Test
    fun draggingTheArmLandsOnAnExactMinute() {
        show(LocalTime.of(9, 0))
        compose.onNodeWithTag("time-dial").performTouchInput {
            // From 12 o'clock round to 44 minutes (264 degrees).
            swipe(center.onDial(0f, width * 0.39f), center.onDial(264f, width * 0.39f), durationMillis = 400)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Set time").performClick()
        compose.runOnIdle { assertEquals(44, set!!.minute) }
    }
}
