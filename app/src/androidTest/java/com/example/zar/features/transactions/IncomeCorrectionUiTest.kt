package com.example.zar.features.transactions

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormContent
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncomeCorrectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun incomeCanChooseAnotherSameCurrencyAccountAndEditTime() {
        val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))
        val bank = cash.copy(id = AccountId("bank"), name = "Bank", type = AccountType.BANK)
        val usd = bank.copy(id = AccountId("usd"), name = "USD bank", currency = Currencies.USD)
        val state = mutableStateOf(IncomeFormUiState(isLoading = false, isEditing = true, accounts = listOf(cash, bank, usd),
            accountId = cash.id, lockedCurrency = Currencies.LKR, amount = "500", time = LocalTime.of(18, 30)))
        compose.setContent {
            ZarTheme(darkTheme = false) {
                IncomeFormContent(state.value, IncomeFormActions({}, {},
                    { id -> state.value = state.value.copy(accountId = id) }, {}, {}, {}, {}, {}, {}, {},
                    onTimeSelect = { time -> state.value = state.value.copy(time = time) }))
            }
        }
        compose.onAllNodesWithText("USD bank · USD").assertCountEquals(0)
        compose.onNodeWithTag("income-account-usd").assertDoesNotExist()
        compose.onNodeWithTag("income-account-bank").performScrollTo().performClick()
        compose.onNodeWithTag("income-time").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("AM").performClick()
        compose.onNodeWithText("Set time").performClick()
        compose.runOnIdle {
            assertEquals(bank.id, state.value.accountId)
            assertEquals(LocalTime.of(6, 30), state.value.time)
        }
    }
}
