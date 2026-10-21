package com.example.zar.features.catchup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.accounts.domain.reconcile.BalanceCheck
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.catchup.domain.model.CatchUpOverview
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.catchup.domain.model.UnresolvedItem
import com.example.zar.features.catchup.domain.model.UnresolvedKind
import com.example.zar.features.catchup.presentation.screen.CatchUpActions
import com.example.zar.features.catchup.presentation.screen.CatchUpBody
import com.example.zar.features.catchup.presentation.screen.NeedsReviewBody
import com.example.zar.features.catchup.presentation.screen.ReminderActions
import com.example.zar.features.catchup.presentation.screen.ReminderBody
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpMessage
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpStep
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpUiState
import com.example.zar.features.catchup.presentation.viewmodel.NeedsReviewUiState
import com.example.zar.features.catchup.presentation.viewmodel.ReminderUiState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Needs review, the weekly catch-up steps and the reminder settings (Figma Z73 to Z85). */
class CatchUpUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val t0 = Instant.parse("2026-10-21T04:00:00Z")
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Known(lkr(3_450)), LocalDate.of(2026, 10, 1))
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(lkr(10_000)), LocalDate.of(2026, 10, 1))

    private fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)

    private fun expense(id: String, amount: Long, merchant: String, account: Account = cash) = Transaction(
        id = TransactionId(id),
        accountId = account.id,
        type = TransactionType.EXPENSE,
        amount = lkr(amount),
        date = today,
        reportingConversion = ReportingConversion.SameCurrency,
        spendingClass = SpendingClass.UNKNOWN,
        merchant = merchant,
        createdAt = t0,
    )

    private fun adjustment(id: String, amount: Long) = Transaction(
        id = TransactionId(id),
        accountId = cash.id,
        type = TransactionType.ADJUSTMENT_OUT,
        amount = lkr(amount),
        date = today,
        reportingConversion = ReportingConversion.SameCurrency,
        createdAt = t0,
    )

    private val pickMe = UnresolvedItem(UnresolvedKind.UNCATEGORISED, expense("pickme", 1_250, "PickMe"), cash)
    private val gap = UnresolvedItem(UnresolvedKind.UNEXPLAINED_ADJUSTMENT, adjustment("gap", 450), cash)

    private fun balance(account: Account = cash): AccountBalance = AccountBalanceCalculator.calculate(account, emptyList())

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent { ZarTheme { content() } }

    /** A note card is plain text in a row, so check the row is shown and that the words are on screen. */
    private fun bannerHas(tag: String, text: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    // --- Needs review (Z73, Z84) ---------------------------------------------------------------

    @Test
    fun needsReviewListsOpenItemsAndOpensOneOnTap() {
        var opened: String? = null
        setContent {
            NeedsReviewBody(
                state = NeedsReviewUiState(isLoading = false, items = listOf(pickMe, gap), daysSinceCatchUp = 9),
                onBack = {},
                onOpenEntry = { opened = it },
                onStartCatchUp = {},
            )
        }

        compose.onNodeWithTag("review-list").assertIsDisplayed()
        compose.onNodeWithText("2 to sort out", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("unresolved-pickme").assertIsDisplayed()
        compose.onNodeWithText("Unexplained gap", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Last catch-up 9 days ago").assertIsDisplayed()
        compose.onNodeWithText("2 items to sort out").assertIsDisplayed()
        compose.onNodeWithTag("unresolved-pickme").performClick()
        assertEquals("pickme", opened)
    }

    @Test
    fun needsReviewStartsTheCatchUp() {
        var started = 0
        setContent {
            NeedsReviewBody(NeedsReviewUiState(isLoading = false, items = listOf(pickMe)), {}, {}, { started++ })
        }

        compose.onNodeWithTag("review-start").assertTextContains("Start catch-up", substring = true).performClick()

        assertEquals(1, started)
    }

    @Test
    fun needsReviewWithNothingOpenSaysAllClearButStillOffersACashCheck() {
        setContent {
            NeedsReviewBody(NeedsReviewUiState(isLoading = false, daysSinceCatchUp = 1), {}, {}, {})
        }

        compose.onNodeWithTag("review-clear").assertIsDisplayed()
        compose.onNodeWithText("Nothing to sort out right now.").assertIsDisplayed()
        compose.onNodeWithText("Last catch-up yesterday").assertIsDisplayed()
        compose.onNodeWithText("Nothing open").assertIsDisplayed()
        compose.onNodeWithTag("review-start").assertTextContains("Check my cash anyway")
    }

    @Test
    fun needsReviewSaysWhenThereHasBeenNoCatchUpYet() {
        setContent { NeedsReviewBody(NeedsReviewUiState(isLoading = false, items = listOf(pickMe)), {}, {}, {}) }

        compose.onNodeWithText("No catch-up yet").assertIsDisplayed()
    }

    @Test
    fun needsReviewShowsALoadFailure() {
        setContent { NeedsReviewBody(NeedsReviewUiState(isLoading = false, loadFailed = true), {}, {}, {}) }

        compose.onNodeWithTag("review-failed").assertIsDisplayed()
        compose.onAllNodesWithTag("review-start").assertCountEquals(0)
    }

    // --- Catch-up steps (Z74 to Z77, Z83, Z85) ----------------------------------------------------

    private class Taps {
        var continued = 0
        var skippedWeek = 0
        var closed = 0
        var home = 0
        var addAccount = 0
        var reviewed = 0
        var saved = 0
        var back = 0
        var counted: String? = null
        var opened: String? = null
    }

    private fun actions(taps: Taps) = CatchUpActions(
        onBack = { taps.back++ },
        onClose = { taps.closed++ },
        onGoHome = { taps.home++ },
        onOpenEntry = { taps.opened = it },
        onAddCashAccount = { taps.addAccount++ },
        onStartCounting = { taps.continued++ },
        onSkipWeek = { taps.skippedWeek++ },
        onAccount = {},
        onCounted = { taps.counted = it },
        onReview = { taps.reviewed++ },
        onSave = { taps.saved++ },
    )

    private fun overview(items: List<UnresolvedItem> = listOf(pickMe, gap), cashAccounts: List<AccountBalance> = listOf(balance()), recorded: Int = 5) =
        CatchUpOverview(items, cashAccounts, lastCatchUp = null, recordedThisWeek = recorded)

    private fun check(counted: Long, account: Account = cash) = BalanceCheck(account, lkr(3_450), lkr(counted))

    private fun catchUp(
        step: CatchUpStep,
        overview: CatchUpOverview = overview(),
        block: CatchUpUiState.() -> CatchUpUiState = { this },
    ) = CatchUpUiState(
        isLoading = false,
        step = step,
        overview = overview,
        balance = overview.cashAccounts.firstOrNull(),
        availability = overview.cashAccounts.firstOrNull()?.let { CheckAvailability.AVAILABLE },
    ).block()

    @Test
    fun theReviewStepListsOpenItemsAndSaysItDoesNotNeedThemAllFixed() {
        val taps = Taps()
        setContent { CatchUpBody(catchUp(CatchUpStep.REVIEW), actions(taps)) }

        compose.onNodeWithText("Step 1 of 3 · Review", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag("catchup-items").assertIsDisplayed()
        bannerHas("catchup-honest-note", "A quiet week")
        compose.onNodeWithTag("unresolved-pickme").performClick()
        assertEquals("pickme", taps.opened)
        compose.onNodeWithTag("catchup-continue").assertTextContains("Continue to counting").performClick()
        assertEquals(1, taps.continued)
    }

    @Test
    fun skippingTheWeekIsOfferedOnTheReviewStep() {
        val taps = Taps()
        setContent { CatchUpBody(catchUp(CatchUpStep.REVIEW), actions(taps)) }

        compose.onNodeWithTag("catchup-skip-week").assertTextContains("Skip catch-up this week").performClick()

        assertEquals(1, taps.skippedWeek)
        assertEquals(0, taps.closed)
    }

    @Test
    fun aFirstCatchUpWithNothingRecordedNeverSaysTheDataIsComplete() {
        val taps = Taps()
        setContent { CatchUpBody(catchUp(CatchUpStep.REVIEW, overview(items = emptyList(), recorded = 0)), actions(taps)) }

        compose.onNodeWithTag("catchup-none").assertIsDisplayed()
        bannerHas("catchup-honest-note", "Quiet doesn't mean complete")
        compose.onNodeWithTag("catchup-continue").assertTextContains("Count my cash")
        // "Not now" only closes: it does not skip the week's reminder.
        compose.onNodeWithTag("catchup-skip-week").assertTextContains("Not now").performClick()
        assertEquals(1, taps.closed)
        assertEquals(0, taps.skippedWeek)
    }

    @Test
    fun theCountStepShowsTheRecordedCashAndTakesTheCount() {
        val taps = Taps()
        setContent { CatchUpBody(catchUp(CatchUpStep.COUNT), actions(taps)) }

        compose.onNodeWithText("Step 2 of 3 · Count", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag("catchup-recorded").assertTextContains("LKR 3,450")
        compose.onNodeWithTag("catchup-not-saved").assertIsDisplayed()
        compose.onNodeWithTag("catchup-amount").performTextInput("3000")
        assertEquals("3000", taps.counted)
        compose.onNodeWithTag("catchup-review").performClick()
        assertEquals(1, taps.reviewed)
    }

    @Test
    fun theCountStepShowsTheGapAsSoonAsTheCountCanBeRead() {
        setContent { CatchUpBody(catchUp(CatchUpStep.COUNT) { copy(counted = "3000", check = check(3_000)) }, actions(Taps())) }

        compose.onNodeWithTag("catchup-difference").assertTextContains("450", substring = true)
        compose.onNodeWithText("LKR 3,000").assertIsDisplayed()
    }

    @Test
    fun aMissingCountShowsItsError() {
        setContent { CatchUpBody(catchUp(CatchUpStep.COUNT) { copy(problems = setOf(CheckProblem.AMOUNT_MISSING)) }, actions(Taps())) }

        compose.onNodeWithTag("catchup-error-amount").assertTextContains("Enter the amount, or 0")
    }

    @Test
    fun withoutACashAccountTheCountStepOffersToAddOne() {
        val taps = Taps()
        setContent { CatchUpBody(catchUp(CatchUpStep.COUNT, overview(cashAccounts = emptyList())), actions(taps)) }

        compose.onNodeWithTag("catchup-no-cash").assertIsDisplayed()
        compose.onAllNodesWithTag("catchup-review").assertCountEquals(0)
        compose.onNodeWithTag("catchup-add-account").performClick()
        compose.onNodeWithTag("catchup-done-empty").performClick()
        assertEquals(1, taps.addAccount)
        assertEquals(1, taps.closed)
    }

    @Test
    fun anAccountThatCannotBeCountedSaysWhyAndOffersNoReview() {
        setContent {
            CatchUpBody(catchUp(CatchUpStep.COUNT) { copy(availability = CheckAvailability.BALANCE_UNKNOWN) }, actions(Taps()))
        }

        bannerHas("catchup-unavailable", "opening value")
        compose.onAllNodesWithTag("catchup-review").assertCountEquals(0)
        compose.onNodeWithTag("catchup-skip-counting").assertIsDisplayed()
    }

    @Test
    fun theSaveStepShowsTheOneAdjustmentAndWarnsTheTotalsStayIncomplete() {
        val taps = Taps()
        setContent { CatchUpBody(catchUp(CatchUpStep.SAVE) { copy(check = check(3_000)) }, actions(taps)) }

        compose.onNodeWithText("Save the gap?").assertIsDisplayed()
        compose.onNodeWithTag("catchup-adjustment-amount").assertTextContains("450", substring = true)
        compose.onNodeWithText("Unclassified · no category · dated today").assertIsDisplayed()
        bannerHas("catchup-incomplete-note", "not counted as food")
        compose.onNodeWithTag("catchup-save").assertTextContains("Save adjustment").performClick()
        assertEquals(1, taps.saved)
        compose.onNodeWithTag("catchup-back").performClick()
        assertEquals(1, taps.back)
    }

    @Test
    fun anExtraIsSavedAsAnAdjustmentThatIsNotIncome() {
        setContent { CatchUpBody(catchUp(CatchUpStep.SAVE) { copy(check = check(3_800)) }, actions(Taps())) }

        bannerHas("catchup-extra-note", "isn't counted as received money")
    }

    @Test
    fun aMatchSavesNothingAndSaysSo() {
        setContent { CatchUpBody(catchUp(CatchUpStep.SAVE) { copy(check = check(3_450)) }, actions(Taps())) }

        compose.onNodeWithText("Your cash matches").assertIsDisplayed()
        compose.onAllNodesWithTag("catchup-adjustment-amount").assertCountEquals(0)
        compose.onNodeWithTag("catchup-save").assertTextContains("Finish catch-up")
    }

    @Test
    fun theSaveStepSaysSoWhenOfflineAndWhenSavingFails() {
        setContent {
            CatchUpBody(catchUp(CatchUpStep.SAVE) { copy(check = check(3_000), isOnline = false, message = CatchUpMessage.FAILED) }, actions(Taps()))
        }

        bannerHas("catchup-offline", "syncs when you're back online")
        compose.onNodeWithTag("catchup-message").assertTextContains("Could not save", substring = true)
    }

    @Test
    fun theSavedStepShowsWhatWasSavedAndKeepsTheTotalsMarkedIncomplete() {
        val taps = Taps()
        val next = ZonedDateTime.of(2026, 10, 25, 19, 0, 0, 0, ZoneId.of("Asia/Colombo"))
        setContent {
            CatchUpBody(catchUp(CatchUpStep.DONE) { copy(result = check(3_000), nextReminder = next) }, actions(taps))
        }

        compose.onNodeWithText("Catch-up saved").assertIsDisplayed()
        compose.onNodeWithText("Cash is LKR 3,000").assertIsDisplayed()
        compose.onNodeWithTag("catchup-result-adjustment").assertTextContains("1 · unclassified")
        compose.onNodeWithTag("catchup-result-left").assertTextContains("2")
        compose.onNodeWithTag("catchup-result-next").assertTextContains("Sun 25 Oct · 7:00 pm")
        bannerHas("catchup-result-note", "show as incomplete")
        compose.onNodeWithTag("catchup-home").performClick()
        assertEquals(1, taps.home)
    }

    @Test
    fun aMatchingCatchUpSavesNoAdjustmentAndOnlyClaimsTheCash() {
        setContent {
            CatchUpBody(catchUp(CatchUpStep.DONE) { copy(result = check(3_450), nextReminder = null) }, actions(Taps()))
        }

        compose.onNodeWithTag("catchup-result-adjustment").assertTextContains("None needed")
        compose.onNodeWithTag("catchup-result-next").assertTextContains("Off")
        bannerHas("catchup-result-note", "says nothing about spending")
    }

    @Test
    fun aLoadFailureShowsAMessageAndNoActions() {
        setContent { CatchUpBody(CatchUpUiState(isLoading = false, loadFailed = true), actions(Taps())) }

        compose.onNodeWithTag("catchup-failed").assertIsDisplayed()
        compose.onAllNodesWithTag("catchup-continue").assertCountEquals(0)
    }

    // --- Reminder settings (Z78 to Z80, Z82) -------------------------------------------------------

    private class ReminderTaps {
        var enabled: Boolean? = null
        var moved: Pair<DayOfWeek, LocalTime>? = null
        var skipped = 0
        var undone = 0
        var openedSettings = 0
        var back = 0
    }

    private fun reminderActions(taps: ReminderTaps) = ReminderActions(
        onBack = { taps.back++ },
        onEnabled = { taps.enabled = it },
        onMove = { day, time -> taps.moved = day to time },
        onSkip = { taps.skipped++ },
        onUndoSkip = { taps.undone++ },
        onOpenNotificationSettings = { taps.openedSettings++ },
    )

    private val nextSunday = ZonedDateTime.of(2026, 10, 25, 19, 0, 0, 0, ZoneId.of("Asia/Colombo"))

    private fun reminder(block: ReminderUiState.() -> ReminderUiState = { this }) =
        ReminderUiState(isLoading = false, settings = ReminderSettings(), nextReminder = nextSunday).block()

    @Test
    fun theReminderDefaultsToSundayAtSevenPmInColombo() {
        val taps = ReminderTaps()
        setContent { ReminderBody(reminder(), notificationsAllowed = true, actions = reminderActions(taps)) }

        compose.onNodeWithTag("reminder-day").assertTextContains("Sunday")
        compose.onNodeWithTag("reminder-time").assertTextContains("7:00 pm")
        compose.onNodeWithTag("reminder-zone").assertTextContains("Asia/Colombo", substring = true)
        bannerHas("reminder-note-next", "Sun 25 Oct · 7:00 pm")
        compose.onNodeWithTag("reminder-skip").performScrollTo().performClick()
        assertEquals(1, taps.skipped)
    }

    @Test
    fun theReminderCanBeTurnedOff() {
        val taps = ReminderTaps()
        setContent { ReminderBody(reminder(), notificationsAllowed = true, actions = reminderActions(taps)) }

        compose.onNodeWithTag("reminder-switch").performClick()

        assertEquals(false, taps.enabled)
    }

    @Test
    fun aSkippedWeekIsSaidPlainlyAndCanBeUndone() {
        val taps = ReminderTaps()
        val settings = ReminderSettings(skippedOccurrence = LocalDate.of(2026, 10, 25))
        setContent {
            ReminderBody(
                reminder { copy(settings = settings, nextReminder = nextSunday.plusWeeks(1), isNextSkipped = true) },
                notificationsAllowed = true,
                actions = reminderActions(taps),
            )
        }

        bannerHas("reminder-note-skipped", "Next reminder: Sun 1 Nov · 7:00 pm")
        compose.onAllNodesWithTag("reminder-skip").assertCountEquals(0)
        compose.onNodeWithTag("reminder-undo-skip").performScrollTo().performClick()
        assertEquals(1, taps.undone)
    }

    @Test
    fun aReminderThatIsOffHasNoNextTimeAndNoSkip() {
        setContent {
            ReminderBody(
                reminder { copy(settings = ReminderSettings(enabled = false), nextReminder = null) },
                notificationsAllowed = true,
                actions = reminderActions(ReminderTaps()),
            )
        }

        compose.onNodeWithTag("reminder-note-off").assertIsDisplayed()
        compose.onAllNodesWithTag("reminder-skip").assertCountEquals(0)
        compose.onAllNodesWithTag("reminder-note-next").assertCountEquals(0)
    }

    @Test
    fun whenNotificationsAreOffTheScreenSaysSoAndOpensTheSystemSettings() {
        val taps = ReminderTaps()
        setContent { ReminderBody(reminder(), notificationsAllowed = false, actions = reminderActions(taps)) }

        compose.onNodeWithTag("reminder-blocked-card").assertIsDisplayed()
        bannerHas("reminder-note-blocked", "No reminder will be sent")
        compose.onAllNodesWithTag("reminder-skip").assertCountEquals(0)
        compose.onNodeWithTag("reminder-open-settings").performClick()
        assertEquals(1, taps.openedSettings)
    }

    @Test
    fun movingTheReminderPicksADayAndATimeInTheSheet() {
        val taps = ReminderTaps()
        setContent { ReminderBody(reminder(), notificationsAllowed = true, actions = reminderActions(taps)) }

        compose.onNodeWithTag("reminder-day").performClick()
        compose.onNodeWithTag("reminder-sheet").assertIsDisplayed()
        compose.onNodeWithTag("reminder-day-FRIDAY").performClick()
        compose.onNodeWithTag("reminder-time-20").performClick()
        compose.onNodeWithTag("reminder-sheet-save").performClick()

        assertEquals(DayOfWeek.FRIDAY to LocalTime.of(20, 0), taps.moved)
    }

    @Test
    fun cancellingTheSheetMovesNothing() {
        val taps = ReminderTaps()
        setContent { ReminderBody(reminder(), notificationsAllowed = true, actions = reminderActions(taps)) }

        compose.onNodeWithTag("reminder-time").performClick()
        compose.onNodeWithTag("reminder-day-MONDAY").performClick()
        compose.onNodeWithTag("reminder-sheet-cancel").performClick()

        assertNull(taps.moved)
        compose.onAllNodesWithTag("reminder-sheet").assertCountEquals(0)
    }
}
