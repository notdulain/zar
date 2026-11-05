package com.example.zar.features.transactions

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryContent
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import com.example.zar.features.transactions.presentation.manual.viewmodel.NewCategoryState
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Add category sheet's icon picker: one icon selected at a time, picking reports it and the sheet stays usable. */
class CategoryIconPickerUiTest {
    @get:Rule val compose = createComposeRule()

    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val picked = mutableListOf<CategoryIcon>()

    private fun capture(name: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle(); SystemClock.sleep(500)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.filesDir, "icon-picker").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun show(dark: Boolean = false) {
        val form = mutableStateOf(NewCategoryState(name = "Pets"))
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                ManualEntryContent(
                    ManualEntryUiState(isLoading = false, kind = ManualEntryKind.EXPENSE, accounts = listOf(bank), accountId = bank.id, newCategory = form.value),
                    focusNote = false,
                    actions = ManualEntryActions(
                        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = {}, onAmount = {}, onDate = {}, onTime = {},
                        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
                        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
                        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
                        onNewCategoryIcon = { picked += it; form.value = form.value.copy(icon = it) },
                    ),
                )
            }
        }
    }

    @Test
    fun theDefaultIconIsSelectedAndPickingAnotherMovesTheSelection() {
        show()
        compose.onNodeWithTag("manual-new-category-icon-receipt").assertIsSelected()
        compose.onNodeWithTag("manual-new-category-icon-piggy_bank").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(CategoryIcon.PIGGY_BANK), picked) }
        compose.onNodeWithTag("manual-new-category-icon-piggy_bank").assertIsSelected()
        compose.onNodeWithTag("manual-new-category-icon-receipt").assertIsNotSelected()
    }

    @Test
    fun everyIconHasATile() {
        show()
        CategoryIcon.entries.forEach { compose.onNodeWithTag("manual-new-category-icon-${it.key}").performScrollTo().assertExists() }
    }

    @Test
    fun saveStaysReachableWithTheIconsShown() {
        show()
        compose.onNodeWithTag("manual-new-category-save").assertExists()
        capture("light")
    }

    @Test
    fun darkSheet() {
        show(dark = true)
        compose.onNodeWithTag("manual-new-category-icon-car").performScrollTo().performClick()
        capture("dark-car")
    }
}
