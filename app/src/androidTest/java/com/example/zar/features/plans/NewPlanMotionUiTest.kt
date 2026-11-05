package com.example.zar.features.plans

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zar.core.presentation.components.SheetBackHost
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.plans.presentation.screen.NewPlanSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NewPlanMotionUiTest {
    @get:Rule val compose = createComposeRule()
    private var closes = 0
    private fun show(saving: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides MotionPolicy(false, true)) {
                    Dialog({}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                        SheetBackHost(onPop = { closes++ }, slideUpOnEnter = true) { dismiss ->
                            NewPlanSheet(false, saving, dismiss, { _, _, _ -> }, {}, {}, {})
                        }
                    }
                }
            }
        }
        frames(2)
    }
    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun top() = compose.onNodeWithTag("new-plan-sheet").getUnclippedBoundsInRoot().top.value

    @Test fun sheetRisesThenShortPullReturnsAndLongPullClosesDownward() {
        show()
        val entering = top()
        frames(50)
        val rest = top()
        com.example.zar.core.presentation.evidence.PrScreenshots.capture("new-plan-open")
        assertTrue("New plan enters from below", entering > rest + 20f)
        val sheet = compose.onNodeWithTag("new-plan-sheet")
        sheet.performTouchInput { swipe(Offset(centerX, 12f), Offset(centerX, 50f), 500) }
        frames(60)
        assertEquals(0, closes)
        assertEquals(rest, top(), 0.5f)
        sheet.performTouchInput { swipe(Offset(centerX, 12f), Offset(centerX, 500f), 500) }
        frames(80)
        assertEquals(1, closes)
        assertTrue("dismissal follows the downward gesture", top() > rest + 100f)
    }

    @Test fun savingPreventsSwipeDismissal() {
        show(saving = true)
        frames(50)
        val rest = top()
        compose.onNodeWithTag("new-plan-sheet").performTouchInput {
            swipe(Offset(centerX, 12f), Offset(centerX, 500f), 500)
        }
        frames(80)
        assertEquals(0, closes)
        assertEquals(rest, top(), 0.5f)
    }
}
