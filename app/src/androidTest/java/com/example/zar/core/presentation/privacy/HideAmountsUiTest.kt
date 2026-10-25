package com.example.zar.core.presentation.privacy

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.NextBill
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.home.domain.model.HomeCommitments
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.screen.HomeScreenContent
import com.example.zar.features.home.presentation.viewmodel.HomeCommitmentsContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.presentation.screen.PlansScreenContent
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import java.time.LocalDate
import java.time.YearMonth
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HideAmountsUiTest {
    @get:Rule val compose = createComposeRule()

    private val month = YearMonth.of(2026, 10)
    private fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)

    @Before fun show() = AmountMask.set(false)
    @After fun reset() = AmountMask.set(false)

    private fun overview(): HomeOverview {
        val received = ReportingTotal(lkr(148_350))
        val spent = SpendingBreakdown(ReportingTotal(lkr(30_000)), ReportingTotal(lkr(8_400)), ReportingTotal(lkr(1_250)))
        return HomeOverview(
            month = month,
            summary = PeriodSummary(received, spent, 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { if (it == 4) received else ReportingTotal.zero() },
        )
    }

    private val commitments = HomeCommitments(
        outstanding = Outstanding(
            owed = listOf(lkr(45_000), FiatMoney(30_000, Currencies.USD)),
            overdue = listOf(lkr(10_000)),
            projectsWithBalance = 2,
        ),
        nextBill = NextBill(BillId("b1"), "Dialog", LocalDate.of(2026, 10, 28), lkr(2_450)),
    )

    private fun showHome(onToggle: () -> Unit = {}) {
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(
                        month, canGoForward = false, HomeContent.Ready(overview()),
                        HomeCommitmentsContent.Ready(commitments),
                    ),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                    onToggleAmounts = onToggle,
                )
            }
        }
    }

    private fun assertNoAmountShown() {
        listOf("148,350", "30,000", "8,400", "1,250", "45,000", "300", "10,000", "2,450").forEach { digits ->
            compose.onAllNodesWithText(digits, substring = true).assertCountEquals(0)
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val target = instrumentation.targetContext.getExternalFilesDir(null)!!
        FileOutputStream(target.resolve(name)).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    @Test
    fun hiddenAmountsShowNoValueOnHomeAndNoChart() {
        AmountMask.set(true)
        showHome()

        compose.onNodeWithTag("received-amount").assertTextContains("LKR ••••")
        compose.onNodeWithTag("spent-amount", useUnmergedTree = true).assertTextContains("LKR ••••")
        compose.onNodeWithTag("received-chart-hidden").assertIsDisplayed()
        // The bar chart, with its per-day description, is not drawn at all while hidden.
        compose.onNodeWithTag("received-chart").assertDoesNotExist()
        compose.onAllNodesWithText("Daily received income", substring = true).assertCountEquals(0)
        assertNoAmountShown()
        capture("u07-home-hidden.png")
    }

    @Test
    fun theOwedAndNextBillRowsAreMaskedToo() {
        AmountMask.set(true)
        showHome()

        compose.onNodeWithTag("clients-owe-amount", useUnmergedTree = true).performScrollTo().assertTextContains("LKR •••• + USD ••••")
        compose.onNodeWithTag("next-bill-detail", useUnmergedTree = true).performScrollTo().assertTextContains("expected LKR ••••", substring = true)
        // The bill's name and date are not amounts, so they stay readable.
        compose.onNodeWithTag("next-bill-name", useUnmergedTree = true).assertTextContains("Dialog")
        assertNoAmountShown()
        capture("u07-commitments-hidden.png")
    }

    @Test
    fun turningTheSwitchRevealsValuesAndTurningItBackHidesThemAgain() {
        showHome()
        compose.onNodeWithTag("received-amount").assertTextContains("LKR 148,350")

        compose.runOnIdle { AmountMask.set(true) }
        compose.onNodeWithTag("received-amount").assertTextContains("LKR ••••")
        assertNoAmountShown()

        compose.runOnIdle { AmountMask.set(false) }
        compose.onNodeWithTag("received-amount").assertTextContains("LKR 148,350")
        compose.onNodeWithTag("clients-owe-amount", useUnmergedTree = true).performScrollTo().assertTextContains("LKR 45,000", substring = true)
    }

    @Test
    fun theControlIsLabelledForTheCurrentState() {
        var toggled = 0
        showHome(onToggle = { toggled++ })
        compose.onNodeWithContentDescription("Hide amounts").assertIsDisplayed().performClick()
        assertEquals(1, toggled)

        compose.runOnIdle { AmountMask.set(true) }
        compose.onNodeWithContentDescription("Show amounts").assertIsDisplayed()
    }

    @Test
    fun emptyPlansHeroMasksItsZeroPlaceholder() {
        AmountMask.set(true)
        compose.setContent {
            ZarTheme {
                PlansScreenContent(
                    state = PlansUiState(isLoading = false, overview = PlansOverview(null, emptyList())),
                    onNavigate = {}, onAddOption = {}, onRetry = {}, onEditGoal = {}, onAllocate = {},
                )
            }
        }
        compose.onNodeWithTag("plans-backed").assertTextContains("LKR ••••")
        compose.onAllNodesWithText("LKR 0", substring = true).assertCountEquals(0)
    }

    @Test
    fun aMaskedAmountFieldStillTakesTypingWithoutShowingItsLength() {
        AmountMask.set(true)
        var typed = ""
        compose.setContent {
            var value by remember { mutableStateOf("1850.50") }
            BasicTextField(
                value = value,
                onValueChange = { value = it; typed = it },
                visualTransformation = amountFieldTransformation(),
                modifier = Modifier.testTag("masked-amount"),
            )
        }
        compose.onNodeWithTag("masked-amount").performClick().performTextInput("9")
        compose.runOnIdle { assertEquals("1850.509", typed) }
        // What is drawn and announced is the fixed mask; only the field's raw value keeps the digits.
        compose.onNodeWithTag("masked-amount").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(AmountMask.HIDDEN_TEXT)))
    }
}
