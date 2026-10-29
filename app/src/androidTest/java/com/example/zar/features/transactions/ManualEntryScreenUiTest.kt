package com.example.zar.features.transactions

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryContent
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryMessage
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import com.example.zar.features.transactions.presentation.manual.viewmodel.NewCategoryState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManualEntryScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, opened)

    private fun state(
        kind: ManualEntryKind = ManualEntryKind.EXPENSE,
        accounts: List<Account> = listOf(bank, cash),
        block: ManualEntryUiState.() -> ManualEntryUiState = { this },
    ) = ManualEntryUiState(
        isLoading = false,
        kind = kind,
        accounts = accounts,
        accountId = accounts.firstOrNull()?.id,
        fromId = accounts.getOrNull(0)?.id,
        toId = accounts.getOrNull(1)?.id,
    ).block()

    private class Recorder {
        var saves = 0
        var amount: String? = null
        var kind: ManualEntryKind? = null
        var income = 0
        var addAccount = 0
        var category: com.example.zar.features.transactions.domain.manual.model.ExpenseCategoryChoice? = null
        var time: java.time.LocalTime? = null
        var suggestion: EntrySuggestion? = null
        var closed = 0
        var addCategoryStart = 0
        var newCategoryName: String? = null
        var addCategoryCancel = 0
        var addCategorySave = 0
    }

    private fun show(state: ManualEntryUiState, rec: Recorder = Recorder(), focusNote: Boolean = false) {
        compose.setContent {
            ZarTheme {
                ManualEntryContent(
                    uiState = state,
                    focusNote = focusNote,
                    actions = ManualEntryActions(
                        onClose = { rec.closed++ },
                        onOpenIncome = { rec.income++ },
                        onAddAccount = { rec.addAccount++ },
                        onKind = { rec.kind = it },
                        onAmount = { rec.amount = it },
                        onDate = {},
                        onTime = { rec.time = it },
                        onAccount = {},
                        onCategory = { rec.category = it },
                        onAddCategoryStart = { rec.addCategoryStart++ },
                        onNewCategoryName = { rec.newCategoryName = it },
                        onAddCategoryCancel = { rec.addCategoryCancel++ },
                        onAddCategorySave = { rec.addCategorySave++ },
                        onSpendingClass = {},
                        onTitle = {},
                        onDescription = {},
                        onSuggestion = { rec.suggestion = it },
                        onFee = {},
                        onFrom = {},
                        onTo = {},
                        onSwap = {},
                        onTransferNote = {},
                        onSave = { rec.saves++ },
                    ),
                )
            }
        }
    }

    @Test
    fun expenseFormShowsItsFieldsAndTheAccountCurrency() {
        show(state())

        compose.onNodeWithTag("manual-amount").assertIsDisplayed()
        compose.onNodeWithTag("manual-currency").assertIsDisplayed()
        compose.onNodeWithTag("manual-time").assertExists()
        compose.onNodeWithTag("manual-account-bank").assertIsDisplayed()
        compose.onNodeWithTag("manual-category-groceries").assertExists()
        compose.onNodeWithTag("manual-class-discretionary").assertExists()
        compose.onNodeWithTag("manual-save").assertIsEnabled()
        compose.onNodeWithText("LKR").assertExists()
    }

    @Test
    fun savingAndTypingAndPickingCategoryAreReported() {
        val rec = Recorder()
        show(state(), rec)

        compose.onNodeWithTag("manual-amount").performTextInput("850")
        compose.onNodeWithTag("manual-category-transport").performScrollTo().performClick()
        compose.onNodeWithTag("manual-save").performClick()

        assertEquals("850", rec.amount)
        assertEquals(ExpenseCategory.TRANSPORT, rec.category)
        assertEquals(1, rec.saves)
    }

    @Test
    fun expenseTimePickerReturnsTheSelectedTime() {
        val rec = Recorder()
        show(state(), rec)

        compose.onNodeWithTag("manual-time").performScrollTo().performClick()
        compose.onNodeWithText("Time").assertIsDisplayed()
        compose.onNodeWithText("Set time").performClick()

        assertTrue(rec.time != null)
    }

    @Test
    fun everyMissingFieldShowsItsOwnMessage() {
        show(
            state {
                copy(
                    expenseProblems = setOf(
                        ExpenseProblem.AMOUNT_MISSING,
                        ExpenseProblem.ACCOUNT_MISSING,
                        ExpenseProblem.CATEGORY_MISSING,
                    ),
                )
            },
        )

        compose.onNodeWithText("Enter an amount above 0").assertExists()
        compose.onNodeWithText("Choose an account").assertExists()
        compose.onNodeWithText("Choose a category").assertExists()
    }

    @Test
    fun typeSwitchMovesToTransferAndOpensIncome() {
        val rec = Recorder()
        show(state(), rec)

        compose.onNodeWithTag("manual-kind-transfer").performClick()
        compose.onNodeWithTag("manual-kind-income").performClick()

        assertEquals(ManualEntryKind.TRANSFER, rec.kind)
        assertEquals(1, rec.income)
    }

    @Test
    fun noAccountsShowsTheEmptyStateAndDisablesSave() {
        val rec = Recorder()
        show(state(accounts = emptyList()), rec)

        compose.onNodeWithTag("manual-no-accounts").assertIsDisplayed()
        compose.onNodeWithText("No accounts yet").assertIsDisplayed()
        compose.onNodeWithTag("manual-save").assertIsNotEnabled()

        compose.onNodeWithTag("manual-add-account").performClick()
        assertEquals(1, rec.addAccount)
    }

    @Test
    fun offlineBannerIsShownOnlyWhenOffline() {
        show(state { copy(isOnline = false) })

        compose.onNodeWithTag("manual-offline").assertIsDisplayed()
        compose.onNodeWithText("You're offline").assertIsDisplayed()
    }

    @Test
    fun noOfflineBannerWhileOnline() {
        show(state())

        compose.onNodeWithTag("manual-offline").assertDoesNotExist()
    }

    @Test
    fun savingIsDisabledWhileASaveIsRunning() {
        show(state { copy(isSaving = true) })

        compose.onNodeWithTag("manual-save").assertIsNotEnabled()
    }

    @Test
    fun aFailedSaveShowsAMessage() {
        show(state { copy(message = ManualEntryMessage.FAILED) })

        compose.onNodeWithText("Could not save. Please try again.").assertExists()
    }

    @Test
    fun suggestionsListRecentEntriesAndPickingOneIsReported() {
        val rec = Recorder()
        val suggestion = EntrySuggestion("Bus to Kandy", ExpenseCategory.TRANSPORT, cash.id, FiatMoney(85_000, Currencies.LKR), SpendingClass.DISCRETIONARY)
        show(state { copy(title = "Bu", suggestions = listOf(suggestion)) }, rec)

        compose.onNodeWithTag("manual-suggestions").assertExists()
        compose.onNodeWithText("Bus to Kandy").performScrollTo().performClick()

        assertEquals(suggestion, rec.suggestion)
    }

    @Test
    fun transferShowsBothLegsWithTheFeeOnTheSendingSide() {
        show(
            state(ManualEntryKind.TRANSFER) {
                copy(amount = "5000", fee = "30", fromId = bank.id, toId = cash.id)
            },
        )

        compose.onNodeWithTag("manual-transfer-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("− 5,030").assertExists()
        compose.onNodeWithText("+ 5,000").assertExists()
    }

    @Test
    fun transferErrorsAreShownUnderTheirFields() {
        show(
            state(ManualEntryKind.TRANSFER) {
                copy(
                    transferProblems = setOf(
                        TransferProblem.AMOUNT_MISSING,
                        TransferProblem.SAME_ACCOUNT,
                        TransferProblem.FEE_INVALID,
                    ),
                )
            },
        )

        compose.onNodeWithText("Enter an amount above 0").assertExists()
        compose.onNodeWithText("Choose two different accounts").assertExists()
        compose.onNodeWithText("Enter a fee of 0 or more").assertExists()
    }

    @Test
    fun transferFormHasNoPreviewUntilTheAmountIsValid() {
        show(state(ManualEntryKind.TRANSFER) { copy(amount = "") })

        compose.onNodeWithTag("manual-transfer-preview").assertDoesNotExist()
    }

    @Test
    fun closeButtonLeavesTheScreen() {
        val rec = Recorder()
        show(state(), rec)

        compose.onNodeWithTag("manual-close").performClick()

        assertTrue(rec.closed == 1)
    }
    @Test
    fun entryKindAndCloseExposeTheirAccessibleStateAndName() {
        show(state())
        compose.onNodeWithTag("manual-kind-expense").assertIsSelected()
        compose.onNodeWithContentDescription("Close").assertExists()
    }


    @Test
    fun theMissingEntryFormExplainsTheGapAndLocksTheAccount() {
        show(state { copy(amount = "450", gapAmount = "450", accountId = cash.id, accountLocked = true) })

        compose.onNodeWithText("Add the missing entry").assertIsDisplayed()
        compose.onNodeWithTag("manual-gap-banner").assertIsDisplayed()
        compose.onNodeWithText("Explaining the LKR 450 gap").assertIsDisplayed()
        compose.onNodeWithTag("manual-account-locked").assertIsDisplayed()
        compose.onNodeWithTag("manual-account-bank").assertDoesNotExist()
        compose.onNodeWithTag("manual-kind-transfer").assertDoesNotExist()
    }

    @Test
    fun anOrdinaryFormHasNoGapBannerAndKeepsItsAccountChoice() {
        show(state())

        compose.onNodeWithTag("manual-gap-banner").assertDoesNotExist()
        compose.onNodeWithTag("manual-account-locked").assertDoesNotExist()
        compose.onNodeWithTag("manual-account-bank").assertIsDisplayed()
    }

    @Test
    fun allCategoriesOpensTheThreeColumnGridWithAnAddAction() {
        val rec = Recorder()
        show(state(), rec)

        compose.onNodeWithText("All categories →").performClick()
        // Every built-in is offered as a tile, and the visible Add category action starts creation.
        compose.onNodeWithTag("manual-category-groceries").assertExists()
        compose.onNodeWithTag("manual-category-subscriptions").assertExists()
        compose.onNodeWithTag("manual-add-category").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, rec.addCategoryStart)
    }

    @Test
    fun addCategorySheetReportsTheTypedName() {
        val rec = Recorder()
        show(state { copy(newCategory = NewCategoryState()) }, rec)

        compose.onNodeWithTag("manual-new-category-name").performTextInput("Pets")

        assertEquals("Pets", rec.newCategoryName)
    }

    @Test
    fun addCategorySaveIsEnabledWithANameAndReportsTheSave() {
        val rec = Recorder()
        show(state { copy(newCategory = NewCategoryState(name = "Pets")) }, rec)

        compose.onNodeWithTag("manual-new-category-save").assertIsEnabled().performClick()

        assertEquals(1, rec.addCategorySave)
    }
}
