package com.example.zar.features.transactions

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.manual.screen.AddTransactionHostContent
import com.example.zar.features.transactions.presentation.manual.screen.AddTransactionTab
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * Emulator-rendered screenshots of the real Add-transaction session composable, one per tab, in Light and Dark.
 * Emulator evidence only: no physical-device claim. PNGs land in the app's external files dir for `adb pull`.
 */
class AddSessionEvidenceTest {
    @get:Rule val compose = createComposeRule()

    private val opened = LocalDate.of(2026, 10, 1)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, opened)

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(700)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.filesDir, "add-session").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun manualExpense() = ManualEntryUiState(
        isLoading = false, kind = ManualEntryKind.EXPENSE, accounts = listOf(bank, cash), accountId = bank.id,
        amount = "850", category = ExpenseCategory.GROCERIES, title = "Keells Super", time = java.time.LocalTime.of(18, 30),
    )
    private fun manualTransfer() = ManualEntryUiState(
        isLoading = false, kind = ManualEntryKind.TRANSFER, accounts = listOf(bank, cash),
        fromId = bank.id, toId = cash.id, amount = "5000", fee = "30",
    )
    private fun income() = IncomeFormUiState(
        isLoading = false, accounts = listOf(bank, cash), accountId = bank.id, amount = "12,500",
        receivedFrom = "Upwork client", category = IncomeCategory.FREELANCE,
    )

    @Test
    fun addSessionTabsLightAndDark() {
        val dark = mutableStateOf(false)
        val tab = mutableStateOf(AddTransactionTab.EXPENSE)
        compose.setContent {
            ZarTheme(darkTheme = dark.value) {
                AddTransactionHostContent(
                    tab = tab.value,
                    manualUi = if (tab.value == AddTransactionTab.TRANSFER) manualTransfer() else manualExpense(),
                    incomeUi = income(),
                    focusNote = false,
                    manualActions = noopManual(),
                    incomeActions = noopIncome(),
                )
            }
        }
        compose.waitForIdle()

        capture("01-expense-light")
        compose.runOnIdle { dark.value = true }
        capture("02-expense-dark")

        compose.runOnIdle { dark.value = false; tab.value = AddTransactionTab.INCOME }
        capture("03-income-light")
        compose.runOnIdle { dark.value = true }
        capture("04-income-dark")

        compose.runOnIdle { dark.value = false; tab.value = AddTransactionTab.TRANSFER }
        capture("05-transfer-light")
        compose.runOnIdle { dark.value = true }
        capture("06-transfer-dark")
    }

    private fun noopManual() = ManualEntryActions(
        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = {}, onAmount = {}, onDate = {}, onTime = {},
        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
    )

    private fun noopIncome() = IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}
