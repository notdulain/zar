package com.example.zar.features.transactions

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.manual.screen.AddTransactionHostContent
import com.example.zar.features.transactions.presentation.manual.screen.AddTransactionTab
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Expense, Income and Transfer share one sheet height: the sheet's top edge never moves when the tab changes. */
class AddSheetHeightUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, opened)

    @Test
    fun switchingTabsKeepsTheSheetTopAndTheLongTabScrolls() {
        val tab = mutableStateOf(AddTransactionTab.EXPENSE)
        compose.setContent {
            ZarTheme {
                AddTransactionHostContent(
                    tab = tab.value,
                    manualUi = ManualEntryUiState(
                        isLoading = false,
                        kind = if (tab.value == AddTransactionTab.TRANSFER) ManualEntryKind.TRANSFER else ManualEntryKind.EXPENSE,
                        accounts = listOf(bank, cash), accountId = bank.id, fromId = bank.id, toId = cash.id,
                    ),
                    incomeUi = IncomeFormUiState(isLoading = false, accounts = listOf(bank, cash), accountId = bank.id),
                    focusNote = false,
                    manualActions = ManualEntryActions(
                        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = {}, onAmount = {}, onDate = {}, onTime = {},
                        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
                        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
                        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
                    ),
                    incomeActions = IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        compose.waitForIdle()
        val expenseTop = top("manual-kind-expense")
        // The longest field is reachable by scrolling, so a fixed sheet never hides it.
        compose.onNodeWithTag("manual-description").performScrollTo()

        compose.runOnIdle { tab.value = AddTransactionTab.INCOME }
        compose.waitForIdle()
        assertEquals(expenseTop.value, top("income-kind-expense").value, 0.5f)

        compose.runOnIdle { tab.value = AddTransactionTab.TRANSFER }
        compose.waitForIdle()
        assertEquals(expenseTop.value, top("manual-kind-expense").value, 0.5f)
        compose.onNodeWithTag("manual-save").assertIsDisplayed()
    }
}
