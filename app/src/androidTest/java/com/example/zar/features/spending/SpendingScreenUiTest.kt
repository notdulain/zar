package com.example.zar.features.spending

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.spending.domain.model.BreakdownRow
import com.example.zar.features.spending.domain.model.ClassBreakdown
import com.example.zar.features.spending.domain.model.SpendingBreakdownReport
import com.example.zar.features.spending.presentation.screen.SpendingBody
import com.example.zar.features.spending.presentation.viewmodel.SpendingContent
import com.example.zar.features.spending.presentation.viewmodel.SpendingUiState
import com.example.zar.features.transactions.domain.category.SpendingKeys
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpendingScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 9)

    private fun total(major: Long, unresolved: Int = 0) = ReportingTotal(FiatMoney(major * 100, Currencies.LKR), unresolved)
    private fun row(key: String, major: Long, count: Int = 1, unresolved: Int = 0) = BreakdownRow(key, total(major, unresolved), count)
    private fun cls(c: SpendingClass, vararg rows: BreakdownRow) =
        ClassBreakdown(c, rows.fold(ReportingTotal.zero()) { s, r -> s + r.total }, rows.toList())

    private val report = SpendingBreakdownReport(
        month,
        committed = cls(SpendingClass.COMMITTED, row("rent", 300), row("bills", 100)),
        discretionary = cls(SpendingClass.DISCRETIONARY, row("groceries", 80)),
        unknown = cls(SpendingClass.UNKNOWN, row(SpendingKeys.UNCATEGORISED, 20, count = 2)),
    )

    private fun show(
        content: SpendingContent,
        isCurrent: Boolean = false,
        onOpen: (SpendingClass, String?) -> Unit = { _, _ -> },
        onReview: () -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        ZarTheme {
            Box(Modifier.fillMaxSize()) {
                SpendingBody(
                    state = SpendingUiState(month, canGoForward = !isCurrent, isCurrentMonth = isCurrent, content = content),
                    onBack = onBack, onPreviousMonth = {}, onNextMonth = {}, onRetry = onRetry,
                    onOpenEntries = onOpen, onOpenReview = onReview,
                )
            }
        }
    }

    @Test
    fun summaryClassesSharesAndUnknownReviewReconcileWithTheReport() {
        show(SpendingContent.Ready(report))
        compose.onNodeWithTag("spending-total").assertTextContains("LKR 500", substring = true)
        compose.onNodeWithTag("spending-summary-committed").assertTextContains("LKR 400", substring = true)
        compose.onNodeWithTag("spending-row-COMMITTED-rent").assertTextContains("75%", substring = true)
        compose.onNodeWithTag("spending-row-COMMITTED-bills").assertTextContains("25%", substring = true)
        compose.onNodeWithTag("spending-row-DISCRETIONARY-groceries").performScrollTo().assertTextContains("100%", substring = true)
        compose.onNodeWithTag("spending-review-count").performScrollTo().assertTextContains("2 entries need review")
    }

    @Test
    fun rowsFixNowAndBackAreWiredAndWriteNothing() {
        var opened: Pair<SpendingClass, String?>? = null
        var review = 0
        var back = 0
        show(SpendingContent.Ready(report), onOpen = { c, k -> opened = c to k }, onReview = { review++ }, onBack = { back++ })
        compose.onNodeWithTag("spending-row-COMMITTED-rent").performScrollTo().performClick()
        assertEquals(SpendingClass.COMMITTED to "rent", opened)
        compose.onNodeWithTag("spending-row-UNKNOWN-${SpendingKeys.UNCATEGORISED}").performScrollTo().performClick()
        assertEquals(SpendingClass.UNKNOWN to SpendingKeys.UNCATEGORISED, opened)
        compose.onNodeWithTag("spending-fix-now").performScrollTo().performClick()
        assertEquals(1, review)
        compose.onNodeWithTag("spending-screen").assertIsDisplayed()
    }

    @Test
    fun missingRateRowsNeverClaimAShareAndTheTotalsAreFlagged() {
        val partial = report.copy(discretionary = cls(SpendingClass.DISCRETIONARY, row("groceries", 0, unresolved = 1)))
        show(SpendingContent.Ready(partial))
        compose.onNodeWithTag("spending-unresolved").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("spending-row-DISCRETIONARY-groceries").performScrollTo().assertTextContains("rate missing", substring = true)
    }

    @Test
    fun emptyAndErrorStatesAreExplicit() {
        val empty = SpendingBreakdownReport(
            month, cls(SpendingClass.COMMITTED), cls(SpendingClass.DISCRETIONARY), cls(SpendingClass.UNKNOWN),
        )
        show(SpendingContent.Ready(empty))
        compose.onNodeWithTag("spending-empty").assertIsDisplayed()
    }

    @Test
    fun aFailedLoadOffersRetry() {
        var retries = 0
        show(SpendingContent.Error, onRetry = { retries++ })
        compose.onNodeWithTag("spending-error").assertIsDisplayed()
        compose.onNodeWithTag("spending-retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun theCurrentMonthCannotStepIntoTheFuture() {
        show(SpendingContent.Loading, isCurrent = true)
        compose.onNodeWithTag("spending-month-label").assertTextContains("September")
        compose.onNodeWithTag("spending-month-next").assertIsNotEnabled()
    }
}
