package com.example.zar.features.rules

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.presentation.screen.CategoryRulesBody
import com.example.zar.features.rules.presentation.screen.RuleOfferContent
import com.example.zar.features.rules.presentation.viewmodel.CategoryRulesUiState
import com.example.zar.features.rules.presentation.viewmodel.RuleDraft
import com.example.zar.features.rules.presentation.viewmodel.RuleOfferUiState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The Remember card and sheet, and the Category rules list and editor (Figma Z115 to Z118, Z121, Z123). */
class RulesUiTest {
    @get:Rule val compose = createComposeRule()

    private val t0 = Instant.parse("2026-10-23T04:00:00Z")
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Known(FiatMoney(200_000, Currencies.LKR)), LocalDate.of(2026, 10, 1))
    private val bank = Account(AccountId("bank"), "Commercial Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(FiatMoney(1_000_000, Currencies.LKR)), LocalDate.of(2026, 10, 1))

    private val offer = RuleOffer("Keells Super", "keells super", "groceries", cash.id, existing = null)

    private fun rule(name: String, category: String, id: String = name.lowercase().replace(' ', '-'), enabled: Boolean = true, version: Int = 1, account: AccountId? = null) =
        CategoryRule(CategoryRuleId(id), name.lowercase(), name, category, account, enabled, version, t0, t0)

    private class OfferRecorder {
        var opened = 0
        var dismissed = 0
        var remembered: RuleScope? = null
    }

    private fun showOffer(state: RuleOfferUiState, rec: OfferRecorder = OfferRecorder()) {
        compose.setContent {
            ZarTheme {
                RuleOfferContent(state, onOpen = { rec.opened++ }, onDismiss = { rec.dismissed++ }, onRemember = { rec.remembered = it })
            }
        }
    }

    // --- The card (Z116) ---

    @Test
    fun theCardNamesTheMerchantAndCategoryAndOpensTheSheetOrGoesAway() {
        val rec = OfferRecorder()
        showOffer(RuleOfferUiState(offer = offer, accountName = "Cash"), rec)

        compose.onNodeWithTag("rule-offer-card").assertIsDisplayed()
        compose.onNodeWithText("Saved · Keells Super").assertIsDisplayed()
        compose.onNodeWithText("Always file it as Groceries?").assertIsDisplayed()
        compose.onNodeWithTag("rule-offer-remember").performClick()
        assertEquals(1, rec.opened)
        compose.onNodeWithTag("rule-offer-dismiss").performClick()
        assertEquals(1, rec.dismissed)
        assertNull(rec.remembered)
    }

    @Test
    fun withoutAnOfferNothingIsShown() {
        showOffer(RuleOfferUiState())

        compose.onAllNodesWithTag("rule-offer-card").assertCountEquals(0)
        compose.onAllNodesWithTag("rule-sheet").assertCountEquals(0)
    }

    // --- The sheet (Z115, Z123) ---

    @Test
    fun theSheetDefaultsToAnyAccountAndSaysWhatTheRuleDoes() {
        val rec = OfferRecorder()
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = "Cash"), rec)

        compose.onNodeWithText("Remember this choice").assertIsDisplayed()
        compose.onNodeWithText("Every future “Keells Super” expense").assertIsDisplayed()
        compose.onNodeWithText("“Keells Super”, same name only").assertIsDisplayed()
        compose.onNodeWithText("Category: Groceries").assertIsDisplayed()
        compose.onNodeWithTag("rule-sheet-note").assertIsDisplayed()
        compose.onNodeWithText("Future expenses only. Nothing already saved changes.").assertIsDisplayed()
        compose.onNodeWithTag("rule-sheet-remember").performClick()

        assertEquals(RuleScope.ANY_ACCOUNT, rec.remembered)
    }

    @Test
    fun choosingOnlyThisAccountRemembersForThatAccount() {
        val rec = OfferRecorder()
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = "Cash"), rec)

        compose.onNodeWithText("Only in Cash").assertIsDisplayed()
        compose.onNodeWithTag("rule-scope-account").performClick()
        compose.onNodeWithTag("rule-sheet-remember").performClick()

        assertEquals(RuleScope.THIS_ACCOUNT, rec.remembered)
    }

    @Test
    fun notNowClosesWithoutSaving() {
        val rec = OfferRecorder()
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = "Cash"), rec)

        compose.onNodeWithTag("rule-sheet-not-now").performClick()

        assertEquals(1, rec.dismissed)
        assertNull(rec.remembered)
    }

    @Test
    fun offlineTheSheetSaysTheRuleIsKeptOnThePhoneAndStillSaves() {
        val rec = OfferRecorder()
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = "Cash", isOnline = false), rec)

        compose.onNodeWithText("Offline: saved on this phone, syncs when you’re back.").assertIsDisplayed()
        compose.onNodeWithTag("rule-sheet-remember").performClick()

        assertEquals(RuleScope.ANY_ACCOUNT, rec.remembered)
    }

    @Test
    fun aFailedSaveShowsAMessage() {
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = "Cash", failed = true))
        compose.onNodeWithText("That did not save. Try again.").assertIsDisplayed()
    }

    @Test
    fun whileSavingTheRememberButtonIsDisabled() {
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = "Cash", isSaving = true))
        compose.onNodeWithTag("rule-sheet-remember").assertIsNotEnabled()
    }

    @Test
    fun withoutAnAccountNameThereIsNoAccountChoice() {
        val rec = OfferRecorder()
        showOffer(RuleOfferUiState(offer = offer, sheetOpen = true, accountName = null), rec)

        compose.onAllNodesWithTag("rule-scope-account").assertCountEquals(0)
        compose.onNodeWithTag("rule-sheet-remember").performClick()
        assertEquals(RuleScope.ANY_ACCOUNT, rec.remembered)
    }

    // --- The list (Z117, Z121) ---

    private class RulesRecorder {
        var opened: CategoryRule? = null
        var toggled: Pair<CategoryRule, Boolean>? = null
        var closed = 0
        var category: String? = null
        var account: AccountId? = AccountId("unset")
        var enabled: Boolean? = null
        var saved = 0
        var back = 0
    }

    private fun showRules(state: CategoryRulesUiState, rec: RulesRecorder = RulesRecorder()) {
        compose.setContent {
            ZarTheme {
                CategoryRulesBody(
                    state = state,
                    onBack = { rec.back++ },
                    onOpen = { rec.opened = it },
                    onToggle = { rule, on -> rec.toggled = rule to on },
                    onCloseEditor = { rec.closed++ },
                    onCategory = { rec.category = it },
                    onAccount = { rec.account = it },
                    onEnabled = { rec.enabled = it },
                    onSave = { rec.saved++ },
                )
            }
        }
    }

    private val listState = CategoryRulesUiState(
        isLoading = false,
        rules = listOf(
            rule("Keells Super", "groceries", version = 2),
            rule("Dialog", "bills", account = bank.id),
            rule("Pilawoos kottu", "coffee-dining", enabled = false, version = 3),
        ),
        accounts = listOf(cash, bank),
    )

    @Test
    fun theListShowsEachRuleWithItsCategoryScopeAndVersion() {
        showRules(listState)

        compose.onNodeWithText("Category rules").assertIsDisplayed()
        compose.onNodeWithText("Keells Super → Groceries").assertIsDisplayed()
        compose.onNodeWithText("Any account · version 2").assertIsDisplayed()
        compose.onNodeWithText("Dialog → Bills").assertIsDisplayed()
        compose.onNodeWithText("Only Commercial Bank · version 1").assertIsDisplayed()
        compose.onNodeWithText("Any account · version 3 · off").assertIsDisplayed()
        compose.onNodeWithTag("rules-note").assertIsDisplayed()
    }

    @Test
    fun theSwitchTurnsARuleOffOrOnAndATapOnTheRowOpensTheEditor() {
        val rec = RulesRecorder()
        showRules(listState, rec)

        compose.onNodeWithTag("rule-switch-keells-super").performClick()
        assertEquals(listState.rules[0] to false, rec.toggled)
        compose.onNodeWithTag("rule-switch-pilawoos-kottu").performClick()
        assertEquals(listState.rules[2] to true, rec.toggled)
        compose.onNodeWithTag("rule-row-dialog").performClick()
        assertEquals(listState.rules[1], rec.opened)
    }

    @Test
    fun withoutRulesTheScreenExplainsHowToMakeOne() {
        showRules(CategoryRulesUiState(isLoading = false))

        compose.onNodeWithTag("rules-empty").assertIsDisplayed()
        compose.onNodeWithText("No rules yet").assertIsDisplayed()
        compose.onAllNodesWithTag("rules-list").assertCountEquals(0)
    }

    @Test
    fun aLoadFailureIsSaidInPlainWords() {
        showRules(CategoryRulesUiState(isLoading = false, hasError = true))
        compose.onNodeWithTag("rules-error").assertIsDisplayed()
    }

    @Test
    fun backGoesBack() {
        val rec = RulesRecorder()
        showRules(listState, rec)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, rec.back)
    }

    // --- The editor (Z118) ---

    private fun editing(rule: CategoryRule, draft: RuleDraft = RuleDraft(rule.categoryId, rule.accountId, rule.enabled), failed: Boolean = false) =
        listState.copy(rules = listOf(rule), editing = rule, draft = draft, failed = failed)

    @Test
    fun theEditorShowsTheRuleAndWhatSavingDoes() {
        showRules(editing(rule("Keells Super", "groceries", version = 2)))

        compose.onNodeWithText("Edit rule · Keells Super").assertIsDisplayed()
        compose.onNodeWithTag("rule-edit-note").assertIsDisplayed()
        compose.onNodeWithText("Matches “Keells Super” exactly. Saving makes version 3. Only future expenses change; amounts and history never do.").assertIsDisplayed()
    }

    @Test
    fun theEditorReportsCategoryAccountSwitchAndSave() {
        val rec = RulesRecorder()
        showRules(editing(rule("Keells Super", "groceries")), rec)

        compose.onNodeWithTag("rule-category-coffee-dining").performScrollTo().performClick()
        assertEquals("coffee-dining", rec.category)
        compose.onNodeWithTag("rule-account-bank").performScrollTo().performClick()
        assertEquals(bank.id, rec.account)
        compose.onNodeWithTag("rule-account-any").performScrollTo().performClick()
        assertNull(rec.account)
        compose.onNodeWithTag("rule-edit-toggle").performScrollTo().performClick()
        assertEquals(false, rec.enabled)
        compose.onNodeWithTag("rule-edit-save").performScrollTo().performClick()
        assertEquals(1, rec.saved)
    }

    @Test
    fun aSwitchedOffRuleOffersToSwitchOn() {
        val off = rule("Keells Super", "groceries", enabled = false)
        showRules(editing(off))
        compose.onNodeWithText("Switch on").assertIsDisplayed()
    }

    @Test
    fun aFailedEditSaysSo() {
        val r = rule("Keells Super", "groceries")
        showRules(editing(r, failed = true))
        compose.onNodeWithText("That did not save. Try again.").assertIsDisplayed()
    }
}
