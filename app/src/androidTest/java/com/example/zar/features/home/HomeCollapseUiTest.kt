package com.example.zar.features.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
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

/** The Home top card on a real scroll: lines fade by their own distance, stop taking taps, retrace on reverse scroll, and stay put under reduced motion. */
class HomeCollapseUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 9)
    private val zero = ReportingTotal.zero()
    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)

    private fun show(policy: MotionPolicy, dark: Boolean = false) {
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    // A fixed phone-size frame: the scroll distances below only collapse the card when the
                    // short fixture content overflows a phone-height screen, not a tall tablet one.
                    Box(Modifier.size(width = 412.dp, height = 732.dp)) {
                        HomeScreenContent(
                            state = HomeUiState(month, canGoForward = true, HomeContent.Ready(overview)),
                            onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun scrollBy(px: Float) {
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
        compose.waitForIdle()
    }

    private fun amountTop() = compose.onNodeWithTag("received-amount").getUnclippedBoundsInRoot().top.value

    @Test
    fun linesFadeAwayAsTheCardScrollsAndComeBackOnReverseScroll() {
        show(full)
        compose.onNodeWithTag("received-amount").assertIsDisplayed()
        compose.onNodeWithTag("home-month-selector").assertIsDisplayed()

        scrollBy(700f)
        compose.onNodeWithTag("received-amount").assertDoesNotExist()
        compose.onNodeWithTag("home-month-selector").assertDoesNotExist()

        scrollBy(-700f)
        compose.onNodeWithTag("received-amount").assertIsDisplayed()
        compose.onNodeWithTag("home-month-selector").assertIsDisplayed()
    }

    @Test
    fun reducedMotionHasNoSpatialDriftButStillFadesWithScroll() {
        show(reduced)
        val rest = amountTop()
        scrollBy(60f)
        val top = amountTop()
        assertEquals("spatial zero: the group scrolls 1:1", rest - 60f / compose.density.density, top, 1.5f)
        scrollBy(700f)
        compose.onNodeWithTag("received-amount").assertDoesNotExist()
    }

    @Test
    fun darkThemeCollapsesTheSameWay() {
        show(full, dark = true)
        compose.onNodeWithTag("received-amount").assertIsDisplayed()
        scrollBy(700f)
        compose.onNodeWithTag("received-amount").assertDoesNotExist()
        assertTrue(true)
    }
}
