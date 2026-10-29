package com.example.zar.features.transactions

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormContent
import com.example.zar.features.transactions.presentation.income.screen.IncomeListContent
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeMonth
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeRow
import com.example.zar.features.transactions.presentation.manual.screen.ManualTimePickerSheet
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Final Figma alignment (Z162 field order, Z169-Z171 received list, Z191 typed time) on synthetic data. Pure Compose
 * fixtures: no Firebase, no network.
 */
class FinalFigmaAlignmentUiTest {
    @get:Rule val compose = createComposeRule()

    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))
    private val usd = cash.copy(id = AccountId("usd"), name = "USD bank", type = AccountType.BANK, currency = Currencies.USD)

    private fun form(state: IncomeFormUiState, fontScale: Float = 1f) {
        compose.setContent {
            ZarTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    IncomeFormContent(state, IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
                }
            }
        }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test fun foreignCurrencyIncomeFollowsTheZ162FieldOrder() {
        form(IncomeFormUiState(isLoading = false, accounts = listOf(cash, usd), accountId = usd.id, amount = "100"))
        val order = listOf("income-amount", "income-rate", "income-account-usd", "income-category-freelance", "income-date", "income-received-from", "income-note")
            .also { compose.onNodeWithTag("income-rate").assertExists() }
        val tops = order.map { tag -> tag to top(tag) }
        assertEquals("Z162 order: $tops", tops.sortedBy { it.second }.map { it.first }, order)
        compose.onNodeWithTag("income-save").assertExists()
    }

    @Test fun lkrIncomeHasNoRateRowAndNoProjectOrQuickNoteRows() {
        form(IncomeFormUiState(isLoading = false, accounts = listOf(cash, usd), accountId = cash.id, amount = "100"))
        compose.onNodeWithTag("income-rate").assertDoesNotExist()
        compose.onAllNodesWithText("Project").assertCountEquals(0)
        compose.onAllNodesWithText("Quick note").assertCountEquals(0)
    }

    @Test fun editModeKeepsUndoAndSaveAndTheEditedAccountCurrency() {
        form(IncomeFormUiState(isLoading = false, isEditing = true, accounts = listOf(cash, usd), accountId = usd.id, lockedCurrency = Currencies.USD, amount = "100", rate = "300"))
        compose.onNodeWithTag("income-undo").assertExists()
        compose.onNodeWithTag("income-save").assertExists()
        compose.onNodeWithTag("income-rate").assertExists()
    }

    private val row = IncomeRow("r1", "Acme Ltd", IncomeCategory.FREELANCE, "USD bank", LocalDate.of(2026, 10, 12),
        checkNotNull(FiatMoney.ofMajor(BigDecimal("100.00"), Currencies.USD)), missingRate = true, isEdited = true)

    private fun list(state: IncomeListUiState, retry: () -> Unit = {}, fontScale: Float = 1f) {
        compose.setContent {
            ZarTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    IncomeListContent(state, {}, {}, {}, retry)
                }
            }
        }
    }

    @Test fun receivedListShowsSameCurrencyAmountEditedMarkerAndNoLkrRateLabel() {
        list(IncomeListUiState(isLoading = false, receivedThisMonth = ReportingTotal.zero(), months = listOf(IncomeMonth(YearMonth.of(2026, 10), listOf(row)))))
        compose.onNodeWithTag("income-row-r1").assertIsDisplayed()
            .assertTextContains("+ USD 100", substring = true)
            .assertTextContains("Acme Ltd", substring = true)
            .assertTextContains("Edited", substring = true)
            .assertTextContains("No LKR rate", substring = true)
        compose.onNodeWithTag("income-add").assertIsDisplayed()
    }

    @Test fun emptyReceivedListUsesTheAddIncomeCopy() {
        list(IncomeListUiState(isLoading = false, receivedThisMonth = ReportingTotal.zero()))
        compose.onNodeWithText("No income yet. Tap Add income to record money you received.").assertIsDisplayed()
        compose.onNodeWithTag("income-add").assertIsDisplayed()
    }

    @Test fun couldNotLoadOffersARetry() {
        var retried = 0
        list(IncomeListUiState(isLoading = false, loadFailed = true), retry = { retried++ })
        compose.onNodeWithTag("income-load-failed").assertIsDisplayed()
        compose.onNodeWithTag("income-retry").performClick()
        assertEquals(1, retried)
    }

    @Test fun receivedListStaysUsableAtTwiceTheFontSize() {
        list(IncomeListUiState(isLoading = false, receivedThisMonth = ReportingTotal.zero(), months = listOf(IncomeMonth(YearMonth.of(2026, 10), listOf(row)))), fontScale = 2f)
        compose.onNodeWithTag("income-row-r1").assertIsDisplayed()
        compose.onNodeWithTag("income-add").assertIsDisplayed()
    }

    @Test fun typingTheTimeCollapsesTheDialBoxSoTheActionsMoveUp() {
        compose.setContent { ZarTheme { ManualTimePickerSheet(time = LocalTime.of(9, 30), onCancel = {}, onSetTime = {}) } }
        val before = compose.onNodeWithText("Set time").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Type the time").performClick()
        compose.onNodeWithTag("time-typing-hint").assertIsDisplayed()
        val after = compose.onNodeWithText("Set time").fetchSemanticsNode().boundsInRoot.top
        assertTrue("actions should rise by about the dial height: $before -> $after", before - after > 150f)
    }
}
