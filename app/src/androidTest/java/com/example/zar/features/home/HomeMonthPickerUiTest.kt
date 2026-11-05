package com.example.zar.features.home

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.screen.HomeScreenContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeMonthPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val current = YearMonth.from(ZarTime.today())
    private var selected by mutableStateOf(current)
    private var commits = 0

    private fun show(reduced: Boolean = false) {
        compose.setContent {
            val zero = ReportingTotal.zero()
            val overview = HomeOverview(selected, PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), 0),
                emptyList(), true, receivedByDay = List(selected.lengthOfMonth()) { zero })
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides MotionPolicy(reduced, true)) {
                    HomeScreenContent(
                        HomeUiState(selected, selected < current, HomeContent.Ready(overview)),
                        { selected = selected.minusMonths(1); commits++ },
                        { selected = selected.plusMonths(1); commits++ }, {}, {}, {},
                    )
                }
            }
        }
    }

    private fun assertCardsDoNotOverlap() {
        val hero = compose.onNodeWithTag("received-card").getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag("nav-more").getUnclippedBoundsInRoot()
        val spending = compose.onNodeWithTag("spent-card").getUnclippedBoundsInRoot()
        assertTrue("navigation stays below hero", navigation.top >= hero.bottom)
        assertTrue("spending stays below navigation", spending.top >= navigation.bottom)
    }

    private fun screenshot(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun swipeTracksFingerCommitsOnceAndKeepsHomeLayout() {
        show()
        assertCardsDoNotOverlap()
        compose.onNodeWithTag("home-month-selector").performClick()
        compose.onNodeWithTag("month-pager").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            repeat(7) { moveBy(Offset(width * 0.1f, 0f), delayMillis = 30) }
        }
        compose.waitForIdle()
        assertEquals("query waits for release", current, selected)
        compose.onNodeWithTag("month-pager").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(current.minusMonths(1), selected)
        assertEquals(1, commits)
        compose.onNodeWithTag("month-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(current, selected)
        assertEquals(2, commits)
        screenshot("month-picker-open.png")
        compose.onNodeWithTag("month-next").assertIsNotEnabled()
        compose.onNodeWithTag("month-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(2, commits)
        androidx.test.espresso.Espresso.pressBack()
        assertCardsDoNotOverlap()
        compose.onNodeWithTag("received-amount").assertIsDisplayed()
        screenshot("home-after-month.png")
    }

    @Test fun arrowsWorkWithReducedMotionWithoutMovingHomeCards() {
        show(reduced = true)
        compose.onNodeWithTag("home-month-selector").performClick()
        compose.onNodeWithTag("month-previous").performClick()
        compose.waitForIdle()
        assertEquals(current.minusMonths(1), selected)
        compose.onNodeWithTag("month-next").performClick()
        compose.waitForIdle()
        assertEquals(current, selected)
        androidx.test.espresso.Espresso.pressBack()
        assertCardsDoNotOverlap()
    }
}
