package com.example.zar.features.home

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.NextBill
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.home.domain.model.HomeCommitments
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.viewmodel.HomeCommitmentsContent
import com.example.zar.features.home.presentation.screen.HomeScreenContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 9)
    private val zero = ReportingTotal.zero()

    @Test
    fun narrowScreenAndLargeTextKeepWarningsAndNavigationReachable() {
        var destination: TopLevelDestination? = null
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(zero.copy(unresolvedCount = 1), SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ZarTheme {
                    Box(Modifier.width(320.dp)) {
                        HomeScreenContent(
                            state = HomeUiState(month, canGoForward = false, HomeContent.Ready(overview)),
                            onPreviousMonth = {}, onNextMonth = {}, onRetry = {},
                            onNavigate = { destination = it }, onAddOption = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("received-warning").performScrollTo().assertIsDisplayed().assertTextContains("Check:", substring = true)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("month-label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue("Month must fit in at most two lines", layouts.single().lineCount <= 2)
        saveScreenshot("u02-large-text-warning.png")
        compose.onNodeWithContentDescription("Next month").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("nav-more").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(TopLevelDestination.More, destination)
        compose.onNodeWithTag("spent-card").performScrollTo().performClick()
        assertEquals(TopLevelDestination.Activity, destination)
    }

    @Test
    fun theReviewPromptOpensNeedsReview() {
        var opened = 0
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(zero.copy(unresolvedCount = 1), SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = false, HomeContent.Ready(overview)),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                    onOpenReview = { opened++ },
                )
            }
        }

        compose.onNodeWithTag("home-review").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun chartDescribesCumulativeIncomeAndTheSelectedMonth() {
        val paid = ReportingTotal(FiatMoney(125_050, Currencies.LKR))
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(paid, SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { if (it == 9) paid else zero },
        )
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = true, HomeContent.Ready(overview)),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Cumulative received income in LKR for September 2026, one bar per day. Received so far by Sep 30: LKR 1,250.50.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("received-amount").assertTextContains("LKR 1,250.50")
        saveScreenshot("u02-home-reviewed.png")
    }
    @Test
    fun cumulativeBarsCarryIncomeForwardAndTapShowsTheRunningTotal() {
        val paid = ReportingTotal(FiatMoney(10_000, Currencies.LKR))
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(paid, SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { if (it == 0) paid else zero },
        )
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = true, HomeContent.Ready(overview)),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                )
            }
        }
        compose.onNodeWithTag("received-chart").performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag("received-chart-caption").assertTextContains("LKR 100", substring = true)
    }

    @Test
    fun emptyMonthStillDrawsEverySlotWithAnEmptyCaption() {
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = true, HomeContent.Ready(emptyOverview())),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                )
            }
        }
        compose.onNodeWithTag("received-chart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("received-chart-caption").assertTextContains("No income", substring = true)
    }

    @Test
    fun clientsOweAndNextBillRowsShowTheirDataAndOpenDetails() {
        var freelanceOpened = 0
        var openedBill: String? = null
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        val commitments = HomeCommitments(
            outstanding = Outstanding(
                owed = listOf(FiatMoney(4_500_000, Currencies.LKR), FiatMoney(30_000, Currencies.USD)),
                overdue = listOf(FiatMoney(1_000_000, Currencies.LKR)),
                projectsWithBalance = 2,
            ),
            nextBill = NextBill(BillId("b1"), "Dialog", LocalDate.now().plusDays(3), FiatMoney(245_000, Currencies.LKR)),
        )
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = true, HomeContent.Ready(overview), HomeCommitmentsContent.Ready(commitments)),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                    onOpenFreelance = { freelanceOpened++ },
                    onOpenBill = { openedBill = it },
                )
            }
        }
        compose.onNodeWithTag("clients-owe-amount", useUnmergedTree = true).performScrollTo().assertTextContains("LKR 45,000 + USD 300")
        compose.onNodeWithTag("clients-owe-caption", useUnmergedTree = true).assertTextContains("LKR 10,000 overdue", substring = true)
        compose.onNodeWithTag("next-bill-name", useUnmergedTree = true).performScrollTo().assertTextContains("Dialog")
        compose.onNodeWithTag("next-bill-detail", useUnmergedTree = true).assertTextContains("expected LKR 2,450", substring = true)
        compose.onNodeWithTag("next-bill-card").performScrollTo()
        scrollHomeToBottom()
        saveScreenshot("u03-home-commitments.png")

        compose.onNodeWithTag("clients-owe-row").performScrollTo().performClick()
        assertEquals(1, freelanceOpened)
        compose.onNodeWithTag("next-bill-card").performScrollTo().performClick()
        assertEquals("b1", openedBill)
    }

    @Test
    fun commitmentsFailureShowsRetryWhileTotalsStayVisible() {
        var retried = 0
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(ReportingTotal(FiatMoney(125_050, Currencies.LKR)), SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = true, HomeContent.Ready(overview), HomeCommitmentsContent.Error),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = { retried++ }, onNavigate = {}, onAddOption = {},
                )
            }
        }
        compose.onNodeWithTag("received-amount").assertTextContains("LKR 1,250.50")
        compose.onNodeWithTag("commitments-retry", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("next-bill-card").performScrollTo()
        scrollHomeToBottom()
        saveScreenshot("u03-home-error.png")
        compose.onNodeWithTag("clients-owe-row").performScrollTo().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun emptyCommitmentsOpenFreelanceAndBills() {
        var clientsOpened = 0
        var billsOpened = 0
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, true, HomeContent.Ready(emptyOverview()),
                        HomeCommitmentsContent.Ready(HomeCommitments(Outstanding(emptyList(), emptyList(), 0), null))),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                    onOpenFreelance = { clientsOpened++ }, onOpenBills = { billsOpened++ },
                )
            }
        }
        compose.onNodeWithTag("clients-owe-amount", useUnmergedTree = true)
            .performScrollTo().assertTextContains("Nothing owed right now")
        compose.onNodeWithTag("next-bill-name", useUnmergedTree = true)
            .performScrollTo().assertTextContains("No bills yet")
        scrollHomeToBottom()
        saveScreenshot("u03-home-empty.png")
        compose.onNodeWithTag("clients-owe-row").performScrollTo().performClick()
        compose.onNodeWithTag("next-bill-card").performScrollTo().performClick()
        assertEquals(1, clientsOpened)
        assertEquals(1, billsOpened)
    }

    @Test
    fun nextBillAmountAndActionFitAtLargeTextOnANarrowScreen() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ZarTheme {
                    Box(Modifier.width(320.dp)) {
                        HomeScreenContent(
                            state = HomeUiState(month, true, HomeContent.Ready(emptyOverview()),
                                HomeCommitmentsContent.Ready(HomeCommitments(
                                    Outstanding(emptyList(), emptyList(), 0),
                                    NextBill(BillId("b1"), "Dialog", LocalDate.now().plusDays(3), FiatMoney(245_000, Currencies.LKR)),
                                ))),
                            onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("next-bill-card").performScrollTo()
        scrollHomeToBottom()
        saveScreenshot("u03-home-large-text.png")
        for (node in listOf(
            compose.onNodeWithTag("next-bill-detail", useUnmergedTree = true),
            compose.onNodeWithText("View bill →", useUnmergedTree = true),
        )) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            org.junit.Assert.assertFalse("Bill amount and action must fit without clipping or ellipsis", layouts.single().hasVisualOverflow)
        }
    }

    private fun scrollHomeToBottom() {
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.waitForIdle()
    }

    private fun emptyOverview() = HomeOverview(
        month = month,
        summary = PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), 0),
        recent = emptyList(), hasRecordsInMonth = false,
        receivedByDay = List(month.lengthOfMonth()) { zero },
    )

    /** Keep deterministic emulator images available for visual review. */
    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}
