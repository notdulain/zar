package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AddButtonPressUiTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private val pulses = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
    }
    private fun show(reduced: Boolean = false, pill: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides MotionPolicy(reduced, true), LocalHapticFeedback provides haptics) {
                    Box(Modifier.padding(40.dp)) {
                        ZarAddButton("Add entry", "New plan", stretch = { if (pill) 1f else 0f }) { clicks++ }
                    }
                }
            }
        }
        frames(3)
    }
    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun width() = compose.onNodeWithTag("add-entry-content", useUnmergedTree = true).getBoundsInRoot().width.value
    private fun fill() = compose.onNodeWithTag("add-entry").captureToImage().toPixelMap().let {
        it[it.width / 4, it.height / 4]
    }

    @Test fun pressShrinksWithoutRippleAndReleaseHasASmallSpringRecovery() {
        show()
        val rest = width()
        val originalFill = fill()
        compose.onNodeWithTag("add-entry").performTouchInput { down(center) }
        frames(5)
        assertTrue("compression responds within about 80ms", width() < rest * 0.95f)
        frames(15)
        assertTrue("pressed=${width()}, rest=$rest", width() < rest * 0.94f)
        assertTrue(width() > rest * 0.90f)
        assertEquals("holding keeps the fill unchanged without a ripple", originalFill, fill())
        assertEquals(0, clicks)
        assertTrue("no haptic until the click commits", pulses.isEmpty())
        compose.onNodeWithTag("add-entry").performTouchInput { up() }
        val widths = (1..50).map { frames(1); width() }
        assertTrue("a little overshoot gives the release its soft recovery", widths.max() > rest)
        assertTrue("the overshoot stays subtle", widths.max() < rest * 1.02f)
        assertEquals(rest, widths.last(), 0.2f)
        assertEquals(1, clicks)
        assertEquals(listOf(HapticFeedbackType.ContextClick), pulses)
    }

    @Test fun cancellingTheNewPlanPillRestoresItsSizeWithoutAClick() {
        show(pill = true)
        val rest = width()
        compose.onNodeWithTag("add-entry").performTouchInput { down(center) }
        frames(20)
        assertTrue("pressed=${width()}, rest=$rest", width() < rest)
        compose.onNodeWithTag("add-entry").performTouchInput { cancel() }
        frames(50)
        assertEquals(rest, width(), 0.2f)
        assertEquals(0, clicks)
        assertTrue("a cancelled press does not buzz", pulses.isEmpty())
    }

    @Test fun reducedMotionKeepsTheButtonStillAndRippleFree() {
        show(reduced = true)
        val rest = width()
        val originalFill = fill()
        compose.onNodeWithTag("add-entry").performTouchInput { down(center) }
        frames(20)
        assertEquals(rest, width(), 0.1f)
        assertEquals(originalFill, fill())
        compose.onNodeWithTag("add-entry").performTouchInput { up() }
        frames(3)
        assertEquals(1, clicks)
        assertEquals(listOf(HapticFeedbackType.ContextClick), pulses)
    }
}
