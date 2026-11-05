package com.example.zar.features.home

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.*
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.presentation.privacy.AmountMask
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.accounts.domain.model.AccountsOverview
import com.example.zar.features.accounts.presentation.viewmodel.AccountsUiState
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.screen.HomeScreenContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeAccountsUiTest {
    @get:Rule val compose = createComposeRule()
    private var openedAccount: String? = null
    private var openedAccounts = false
    @After fun restorePrivacy() { AmountMask.set(false) }

    private fun show() {
        AmountMask.set(false)
        val date = LocalDate.of(2026, 11, 5)
        val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR,
            OpeningBalance.Known(FiatMoney(2500000, Currencies.LKR)), date.minusYears(1))
        val wallet = Account(AccountId("wallet"), "USD wallet", AccountType.CASH, Currencies.USD,
            OpeningBalance.Known(FiatMoney(12050, Currencies.USD)), date.minusYears(1))
        val unknown = bank.copy(id = AccountId("unknown"), name = "New account", openingBalance = OpeningBalance.Unknown)
        val balances = listOf(bank, wallet, unknown).map { AccountBalanceCalculator.calculate(it, emptyList(), date) }
        val zero = ReportingTotal.zero()
        val month = YearMonth.from(date)
        val home = HomeOverview(month, PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), 0), emptyList(), true)
        compose.setContent {
            ZarTheme {
                HomeScreenContent(HomeUiState(month, false, HomeContent.Ready(home)), {}, {}, {}, {}, {},
                    accounts = AccountsUiState(false, AccountsOverview(balances, emptyList(), null, 0, 1)),
                    onOpenAccounts = { openedAccounts = true }, onOpenAccount = { openedAccount = it })
            }
        }
    }

    @Test fun accountsShowNativeBalancesOpenDetailsAndClientsOweIsLast() {
        show()
        compose.onNodeWithTag("home-accounts").performScrollTo()
        compose.onNodeWithTag("home-account-balance-bank", useUnmergedTree = true).assertTextEquals("LKR 25,000")
        compose.onNodeWithTag("home-account-balance-wallet", useUnmergedTree = true).assertTextEquals("USD 120.50")
        compose.onNodeWithTag("home-account-bank").performClick()
        assertEquals("bank", openedAccount)
        compose.onNodeWithTag("home-open-accounts").performClick()
        assertTrue(openedAccounts)
        screenshot("home-accounts-list.png")
        compose.onNodeWithTag("clients-owe-row").performScrollTo()
        val accounts = compose.onNodeWithTag("home-accounts").getUnclippedBoundsInRoot()
        val bill = compose.onNodeWithTag("next-bill-card").getUnclippedBoundsInRoot()
        val weekly = compose.onNodeWithTag("weekly-limit-card").getUnclippedBoundsInRoot()
        val owed = compose.onNodeWithTag("clients-owe-row").getUnclippedBoundsInRoot()
        assertTrue(accounts.bottom <= bill.top)
        assertTrue(bill.bottom <= owed.top)
        assertTrue(weekly.bottom <= owed.top)
        screenshot("home-clients-owe-bottom.png")
    }

    @Test fun balancesRespectPrivacyAndUnknownBalancesStayUnknown() {
        show()
        compose.onNodeWithTag("home-accounts").performScrollTo()
        compose.onNodeWithTag("home-account-balance-unknown", useUnmergedTree = true).assertTextEquals("Unknown")
        compose.runOnIdle { AmountMask.set(true) }
        compose.onNodeWithTag("home-account-balance-bank", useUnmergedTree = true).assertTextEquals("LKR ${AmountMask.HIDDEN_TEXT}")
        compose.onNodeWithTag("home-account-balance-wallet", useUnmergedTree = true).assertTextEquals("USD ${AmountMask.HIDDEN_TEXT}")
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
