package com.example.zar.features.transactions

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
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
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryContent
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import com.example.zar.features.transactions.presentation.manual.viewmodel.NewCategoryState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * Emulator-rendered screenshots of the real production composables for the three-column category picker and the
 * Add category sheet, in Light and Dark. This is emulator evidence only: no physical-device or performance claim.
 */
class CategoryGridEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val custom = listOf(
        CustomExpenseCategory("custom-a", "Pet care", "pet care", Instant.parse("2026-09-01T00:00:00Z")),
        CustomExpenseCategory("custom-b", "Tuition for my youngest child", "tuition for my youngest child", Instant.parse("2026-09-02T00:00:00Z")),
        CustomExpenseCategory("custom-c", "Data reload", "data reload", Instant.parse("2026-09-03T00:00:00Z")),
    )
    private val catalog = com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog(stored = custom)

    private fun state(newCategory: NewCategoryState? = null) = ManualEntryUiState(
        isLoading = false,
        kind = ManualEntryKind.EXPENSE,
        accounts = listOf(bank),
        accountId = bank.id,
        categoryCatalog = catalog,
        newCategory = newCategory,
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(700)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "pixel6a-categories").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private var noop = false
    private fun actions() = ManualEntryActions(
        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = {}, onAmount = {}, onDate = {}, onTime = {},
        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = { noop = true },
    )

    private fun showSheet(dark: Boolean, newCategory: NewCategoryState? = null) {
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                ManualEntryContent(state(newCategory), focusNote = false, actions = actions())
            }
        }
    }

    @Test
    fun lightAndDarkGridAndSheet() {
        val dark = mutableStateOf(false)
        val sheet = mutableStateOf<NewCategoryState?>(null)
        compose.setContent {
            ZarTheme(darkTheme = dark.value) {
                ManualEntryContent(state(sheet.value), focusNote = false, actions = actions())
            }
        }
        // 01: the form's inline category row, before the grid is opened.
        compose.onNodeWithText("All categories →").performScrollTo()
        capture("01-category-row-light")
        compose.onNodeWithText("All categories →").performClick()
        // 02: the grid at the built-ins (top of the list).
        compose.onNodeWithTag("manual-category-groceries").performScrollTo()
        capture("02-grid-built-ins-light")
        // 03: scrolled to the user's own categories; the long name must wrap inside its column, not shrink.
        compose.onNodeWithTag("manual-category-custom-c").performScrollTo().assertExists()
        compose.onNodeWithTag("manual-category-custom-b").assertExists()
        capture("03-grid-custom-long-name-light")
        // 04: the same grid scrolled in dark.
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        capture("04-grid-custom-long-name-dark")

        compose.runOnIdle { sheet.value = NewCategoryState(name = "Pet care") }
        compose.waitForIdle()
        compose.onNodeWithTag("manual-new-category-name").assertExists()
        capture("05-add-sheet-dark")
        compose.runOnIdle { dark.value = false }
        compose.waitForIdle()
        capture("06-add-sheet-light")
    }

    @Test
    fun incomeSheetLightAndDark() {
        val dark = mutableStateOf(false)
        compose.setContent {
            ZarTheme(darkTheme = dark.value) {
                com.example.zar.features.transactions.presentation.income.screen.IncomeFormContent(
                    com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState(
                        isLoading = false,
                        accounts = listOf(bank),
                        accountId = bank.id,
                        amount = "12,500",
                        receivedFrom = "Upwork client",
                        category = com.example.zar.features.transactions.domain.income.model.IncomeCategory.FREELANCE,
                    ),
                    com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions(
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                    ),
                )
            }
        }
        compose.waitForIdle()
        capture("income-01-light")
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        capture("income-02-dark")
    }
}
