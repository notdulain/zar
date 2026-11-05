package com.example.zar.features.transactions

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategoryChoice
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryContent
import com.example.zar.features.transactions.presentation.manual.viewmodel.DeleteCategoryState
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Removing a category from the All categories grid: every tile has a cross, and it asks first. */
class CategoryDeleteUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val pets = CustomExpenseCategory("custom-pets-1", "Pets", "pets", Instant.ofEpochMilli(1_000), CategoryIcon.PIGGY_BANK)

    private val started = mutableListOf<ExpenseCategoryChoice>()
    private var cancelled = 0
    private var confirmed = 0

    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(500)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.filesDir, "cat-delete").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun show(deleting: Boolean = false, failed: Boolean = false, hidden: Set<String> = emptySet(), withPets: Boolean = true) {
        val state = mutableStateOf(
            ManualEntryUiState(
                isLoading = false, kind = ManualEntryKind.EXPENSE, accounts = listOf(bank), accountId = bank.id,
                categoryCatalog = ExpenseCategoryCatalog(if (withPets) listOf(pets) else emptyList(), hiddenBuiltIns = hidden),
                deletingCategory = if (deleting) DeleteCategoryState(pets, failed = failed) else null,
            ),
        )
        compose.setContent {
            ZarTheme {
                ManualEntryContent(
                    state.value, focusNote = false,
                    actions = ManualEntryActions(
                        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = {}, onAmount = {}, onDate = {}, onTime = {},
                        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
                        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
                        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
                        onDeleteCategoryStart = { started += it },
                        onDeleteCategoryCancel = { cancelled++ },
                        onDeleteCategoryConfirm = { confirmed++ },
                    ),
                )
            }
        }
    }

    @Test
    fun everyTileHasACrossAndTappingItAsksToDelete() {
        show()
        compose.onNodeWithText("All categories →").performScrollTo().performClick()
        compose.onNodeWithTag("manual-category-delete-custom-pets-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manual-category-delete-groceries").performScrollTo().assertIsDisplayed()
        capture("grid")
        compose.onNodeWithTag("manual-category-delete-custom-pets-1").performClick()
        compose.onNodeWithTag("manual-category-delete-groceries").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf<ExpenseCategoryChoice>(pets, ExpenseCategory.GROCERIES), started) }
    }

    @Test
    fun theLastRemainingCategoryHasNoCross() {
        // Everything but Groceries is removed; the one left cannot be.
        show(hidden = ExpenseCategory.entries.map { it.id }.toSet() - "groceries", withPets = false)
        compose.onNodeWithText("All categories →").performScrollTo().performClick()
        compose.onNodeWithTag("manual-category-groceries").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("manual-category-delete-groceries").assertDoesNotExist()
    }

    @Test
    fun theConfirmationNamesTheCategoryAndOffersCancelAndDelete() {
        show(deleting = true)
        compose.onNodeWithTag("manual-delete-category-sheet").assertIsDisplayed()
        compose.onNodeWithText("Delete category?").assertIsDisplayed()
        capture("confirm")
        compose.onNodeWithTag("manual-delete-category-cancel").performClick()
        compose.onNodeWithTag("manual-delete-category-confirm").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(1, confirmed) }
    }

    @Test
    fun aFailedDeleteShowsAnError() {
        show(deleting = true, failed = true)
        compose.onNodeWithTag("manual-delete-category-error").assertIsDisplayed()
    }
}
