package com.example.zar.features.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/**
 * The one Add-transaction session's tab switch, driven through the real host composable. The two drafts are held
 * outside the switched body (as the two ViewModels hold them in production), so a value typed on one tab must still
 * be there after a round trip, and Save is the only thing that reports a write.
 */
class AddTransactionSessionUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)

    @Test
    fun switchingTabsKeepsEachDraftAndOnlySaveReportsAWrite() {
        var tab by mutableStateOf(AddTransactionTab.EXPENSE)
        var manual by mutableStateOf(
            ManualEntryUiState(isLoading = false, kind = ManualEntryKind.EXPENSE, accounts = listOf(bank), accountId = bank.id),
        )
        var income by mutableStateOf(
            IncomeFormUiState(isLoading = false, accounts = listOf(bank), accountId = bank.id),
        )
        var saves = 0

        compose.setContent {
            ZarTheme {
                AddTransactionHostContent(
                    tab = tab,
                    manualUi = manual,
                    incomeUi = income,
                    focusNote = false,
                    manualActions = ManualEntryActions(
                        onClose = {}, onOpenIncome = { tab = AddTransactionTab.INCOME }, onAddAccount = {},
                        onKind = { tab = if (it == ManualEntryKind.TRANSFER) AddTransactionTab.TRANSFER else AddTransactionTab.EXPENSE },
                        onAmount = { manual = manual.copy(amount = it) }, onDate = {}, onTime = {}, onAccount = {},
                        onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
                        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {},
                        onFee = {}, onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = { saves++ },
                    ),
                    incomeActions = IncomeFormActions(
                        onBack = {}, onAmountChange = { income = income.copy(amount = it) }, onAccountSelect = {},
                        onDateSelect = {}, onRateChange = {}, onReceivedFromChange = {}, onCategorySelect = {},
                        onNoteChange = {}, onSave = { saves++ }, onUndo = {},
                        onSelectKind = { index ->
                            tab = when (index) {
                                0 -> AddTransactionTab.EXPENSE
                                2 -> AddTransactionTab.TRANSFER
                                else -> AddTransactionTab.INCOME
                            }
                        },
                    ),
                )
            }
        }

        // Expense tab is shown first.
        compose.onNodeWithTag("manual-amount").assertIsDisplayed()
        compose.onNodeWithTag("income-amount").assertDoesNotExist()
        compose.onNodeWithTag("manual-amount").performTextInput("850")

        // Switch to Income, type there.
        compose.onNodeWithTag("manual-kind-income").performClick()
        compose.onNodeWithTag("income-amount").assertIsDisplayed()
        compose.onNodeWithTag("income-amount").performTextInput("12000")

        // Switch to Transfer and back to Expense; the expense draft is intact.
        compose.onNodeWithTag("income-kind-transfer").performClick()
        compose.onNodeWithTag("manual-kind-expense").performClick()
        compose.onNodeWithTag("manual-amount").assertIsDisplayed()

        compose.runOnIdle {
            assertEquals("850", manual.amount)
            assertEquals("12000", income.amount)
            assertEquals("nothing is written until Save", 0, saves)
        }

        // Save on the active (Expense) tab reports exactly one write.
        compose.onNodeWithTag("manual-save").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun openingOnIncomeShowsTheIncomeBodyWithTheSameSwitch() {
        val tab = mutableStateOf(AddTransactionTab.INCOME)
        val manual = mutableStateOf(
            ManualEntryUiState(isLoading = false, kind = ManualEntryKind.EXPENSE, accounts = listOf(bank), accountId = bank.id),
        )
        val income = mutableStateOf(IncomeFormUiState(isLoading = false, accounts = listOf(bank), accountId = bank.id))

        compose.setContent {
            ZarTheme {
                AddTransactionHostContent(
                    tab = tab.value,
                    manualUi = manual.value,
                    incomeUi = income.value,
                    focusNote = false,
                    manualActions = noopManual(),
                    incomeActions = noopIncome(),
                )
            }
        }

        compose.onNodeWithTag("income-amount").assertIsDisplayed()
        compose.onNodeWithTag("manual-amount").assertDoesNotExist()
    }

    private fun noopManual() = ManualEntryActions(
        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = {}, onAmount = {}, onDate = {}, onTime = {},
        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
    )

    private fun noopIncome() = IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}
