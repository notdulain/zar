package com.example.zar.features.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height as dpHeight
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormContent
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Income sheet visual parity with Figma Z162/Z171 on synthetic fixtures: compact lower sheet, inline amount and currency,
 * account strip, date and time row, 48dp targets and large-text reflow. Pure Compose: no Firebase, no network.
 */
class IncomeSheetParityUiTest {
    @get:Rule val compose = createComposeRule()

    private val sampath = Account(AccountId("sampath"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))
    private val cash = sampath.copy(id = AccountId("cash"), name = "Cash", type = AccountType.CASH)
    private val usd = sampath.copy(id = AccountId("usd"), name = "Payoneer", type = AccountType.FREELANCE, currency = Currencies.USD)
    private val eurArchived = usd.copy(id = AccountId("eur"), name = "Old euro", currency = Currencies.EUR, isArchived = true)

    private fun add(accounts: List<Account> = listOf(sampath, cash, usd)) =
        IncomeFormUiState(isLoading = false, accounts = accounts, accountId = accounts.firstOrNull()?.id, amount = "125000")

    private fun show(
        state: IncomeFormUiState,
        fontScale: Float = 1f,
        height: androidx.compose.ui.unit.Dp? = null,
        actions: IncomeFormActions = IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
    ) {
        compose.setContent {
            ZarTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    if (height == null) IncomeFormContent(state, actions)
                    else Box(Modifier.fillMaxWidth().height(height)) { IncomeFormContent(state, actions) }
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test fun defaultSheetIsCompactAndSitsInTheLowerTwoThirds() {
        show(add())
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        // The type switch is the sheet's first control; with Figma's header above it the sheet starts near 30% of the height.
        assertTrue("sheet too tall: switch at ${bounds("income-kind-income").top} of ${root.dpHeight}", bounds("income-kind-income").top >= root.dpHeight * 0.33f)
        assertTrue(bounds("income-save").bottom <= root.dpHeight)
        compose.onNodeWithTag("income-note").assertIsDisplayed()
    }

    @Test fun amountAndCurrencyShareOneRow() {
        show(add())
        val amount = bounds("income-amount")
        val currency = bounds("income-currency")
        assertTrue("amount $amount, currency $currency", kotlin.math.abs((amount.top + amount.bottom).value - (currency.top + currency.bottom).value) < 24f)
        assertTrue(currency.left > amount.left)
    }

    @Test fun allAccountsStacksTheChipsAndTheStripReturnsOnPick() {
        show(add())
        val strip = listOf("sampath", "cash", "usd").map { bounds("income-account-$it") }
        assertTrue("strip is one row", strip.all { kotlin.math.abs(it.top.value - strip[0].top.value) < 2f })
        compose.onNodeWithText("All accounts →").performClick()
        val stacked = listOf("sampath", "cash", "usd").map { bounds("income-account-$it") }
        assertTrue("stacked: $stacked", stacked[1].top > stacked[0].bottom - 2.dp && stacked[2].top > stacked[1].bottom - 2.dp)
    }

    @Test fun currencyMenuOffersOnlyUsableAccountCurrenciesAndRoutesThroughTheAccountAction() {
        val picked = mutableListOf<AccountId>()
        show(add(listOf(sampath, cash, usd, eurArchived)), actions = IncomeFormActions({}, {}, { picked += it }, {}, {}, {}, {}, {}, {}, {}))
        compose.onNodeWithTag("income-currency").performClick()
        compose.onNodeWithTag("income-currency-LKR").assertIsDisplayed()
        compose.onNodeWithTag("income-currency-USD").assertIsDisplayed()
        compose.onNodeWithTag("income-currency-EUR").assertDoesNotExist()
        compose.onNodeWithTag("income-currency-USD").performClick()
        assertEquals(listOf(usd.id), picked)
    }

    @Test fun choosingTheCurrentCurrencyChangesNothing() {
        val picked = mutableListOf<AccountId>()
        show(add(), actions = IncomeFormActions({}, {}, { picked += it }, {}, {}, {}, {}, {}, {}, {}))
        compose.onNodeWithTag("income-currency").performClick()
        compose.onNodeWithTag("income-currency-LKR").performClick()
        assertTrue(picked.isEmpty())
    }

    @Test fun anEditKeepsItsCurrencyAndOffersNoMenu() {
        show(add().copy(isEditing = true, lockedCurrency = Currencies.LKR))
        compose.onNodeWithTag("income-currency").performClick()
        compose.onNodeWithTag("income-currency-USD").assertDoesNotExist()
        compose.onNodeWithTag("income-currency-LKR").assertDoesNotExist()
    }

    @Test fun aSingleCurrencyIsShownWithoutAMenu() {
        show(add(listOf(sampath, cash)))
        compose.onNodeWithTag("income-currency").performClick()
        compose.onNodeWithTag("income-currency-LKR").assertDoesNotExist()
    }

    @Test fun dateReadsTodayAndAnUnsetTimeSaysNotSet() {
        show(add())
        compose.onNodeWithText(IncomeFormat.dateLabel(ZarTime.today())).assertIsDisplayed()
        assertTrue(IncomeFormat.dateLabel(ZarTime.today()).startsWith("Today, "))
        compose.onNodeWithText("Not set").assertIsDisplayed()
    }

    @Test fun removeTimeIsOfferedOnlyWhenATimeIsSet() {
        var time: LocalTime? = LocalTime.of(9, 55)
        val state = mutableStateOf(add().copy(time = time))
        compose.setContent {
            ZarTheme(darkTheme = false) {
                IncomeFormContent(state.value, IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onTimeSelect = { time = it; state.value = state.value.copy(time = it) }))
            }
        }
        compose.onNodeWithTag("income-time").performClick()
        compose.onNodeWithTag("time-clear").performClick()
        compose.runOnIdle { assertNull(time) }
        compose.onNodeWithTag("income-time").performClick()
        compose.onNodeWithTag("time-clear").assertDoesNotExist()
    }

    @Test fun datePickerOpensAndBackToTheSheetKeepsTheDraft() {
        show(add().copy(receivedFrom = "Acmes"))
        compose.onNodeWithTag("income-date").performClick()
        compose.onNodeWithText("Set date").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("income-received-from").assertIsDisplayed()
        compose.onNodeWithText("Acmes").assertIsDisplayed()
    }

    @Test fun targetsAreAtLeast48dp() {
        show(add())
        listOf("income-close", "income-date", "income-time", "income-save").forEach { tag ->
            val b = bounds(tag)
            assertTrue("$tag is ${b.dpHeight} tall", b.dpHeight >= 47.5.dp)
        }
        // The currency chip's visible box is smaller; its touch target is the 48dp interactive area around it.
        compose.onNodeWithTag("income-currency").performClick()
        compose.onNodeWithTag("income-currency-USD").assertIsDisplayed()
    }

    private fun checkLargeText(scale: Float) {
        show(add(), fontScale = scale)
        val label = compose.onNodeWithText("DATE").getUnclippedBoundsInRoot()
        val value = bounds("income-date")
        assertTrue("scale $scale: value below its label", value.top >= label.bottom - 2.dp)
        assertEquals("scale $scale: value starts under the label", label.left.value, value.left.value, 2f)
        compose.onNodeWithTag("income-note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("income-save").assertIsDisplayed()
        assertTrue(bounds("income-save").dpHeight >= 47.5.dp)
    }

    @Test fun textAt1_3StacksTheLabelsAndKeepsSaveReachable() = checkLargeText(1.3f)

    @Test fun textAt1_5StacksTheLabelsAndKeepsSaveReachable() = checkLargeText(1.5f)

    @Test fun textAt2_0StacksTheLabelsAndKeepsSaveReachable() = checkLargeText(2.0f)

    @Test fun enlargedTextStacksEditActionsWithSaveFirst() {
        show(add().copy(isEditing = true, lockedCurrency = Currencies.LKR), fontScale = 2.0f)
        val save = bounds("income-save")
        val undo = bounds("income-undo")
        assertTrue("save $save above undo $undo", save.bottom <= undo.top + 1.dp)
        assertEquals(save.left.value, undo.left.value, 1f)
        compose.onNodeWithText("Undo this income").assertIsDisplayed()
    }

    @Test fun defaultTextKeepsEditActionsSideBySideWithUndoFirst() {
        show(add().copy(isEditing = true, lockedCurrency = Currencies.LKR))
        assertTrue(bounds("income-undo").left < bounds("income-save").left)
        assertEquals(bounds("income-undo").top.value, bounds("income-save").top.value, 1f)
    }

    @Test fun aShortScreenScrollsTheFormAndKeepsSaveVisible() {
        show(add().copy(isEditing = true, lockedCurrency = Currencies.LKR), fontScale = 1.5f, height = 560.dp)
        compose.onNodeWithTag("income-save").assertIsDisplayed()
        compose.onNodeWithTag("income-note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("income-close").assertIsDisplayed()
    }

    @Test fun noAccountsShowsTheNoAccountsNoteAndNoCurrencyChip() {
        show(IncomeFormUiState(isLoading = false, accounts = emptyList(), amount = "5"))
        compose.onNodeWithTag("income-no-accounts").assertIsDisplayed()
        compose.onNodeWithTag("income-currency").assertDoesNotExist()
        compose.onNodeWithTag("income-save").assertIsDisplayed()
    }
}
