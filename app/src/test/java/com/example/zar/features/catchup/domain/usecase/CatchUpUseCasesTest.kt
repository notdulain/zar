package com.example.zar.features.catchup.domain.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.usecase.RecordBalanceCheckUseCase
import com.example.zar.features.catchup.FakeCatchUpPreferences
import com.example.zar.features.catchup.FakeReminderScheduler
import com.example.zar.features.catchup.domain.model.CatchUpPreferences
import com.example.zar.features.catchup.domain.model.ReminderSettings
import com.example.zar.features.transactions.presentation.activity.FakeAccounts
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpUseCasesTest {
    private val ledger = FakeLedgerRepository()
    private val prefs = FakeCatchUpPreferences()
    private val scheduler = FakeReminderScheduler()
    private val today = LocalDate.of(2026, 10, 25)
    private val october = YearMonth.from(OCT_10).toDateRange()
    private val record = RecordCatchUpUseCase(RecordBalanceCheckUseCase(ledger, SaveTransactionUseCase(ledger)), prefs)

    /** Cash opens at 2,000 and one 500 expense is recorded, so the recorded balance is 1,500. */
    private suspend fun cashAt1500() = ledger.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(500), cash))

    private suspend fun balance() = AccountBalanceCalculator.calculate(cash, ledger.observeLedger().first()).current

    private fun adjustments() = ledger.transactions.values.filter {
        it.type == TransactionType.ADJUSTMENT_OUT || it.type == TransactionType.ADJUSTMENT_IN
    }

    // --- Observing the catch-up ---

    private fun observe(vararg accounts: Account) = ObserveCatchUpUseCase(ledger, FakeAccounts(accounts.toList()), prefs)

    @Test
    fun theOverviewListsOnlyActiveCashAccountsWithTheirRecordedBalance() = runBlocking {
        cashAt1500()
        val archived = cash.copy(id = com.example.zar.core.domain.model.AccountId("old"), name = "Old tin", isArchived = true)

        val overview = observe(bank, cash, archived).invoke().first()

        assertEquals(listOf("Cash"), overview.cashAccounts.map { it.account.name })
        assertEquals(lkr(1_500), overview.cashAccounts.single().current)
    }

    @Test
    fun theOverviewHasNoCashAccountsWhenThereAreNone() = runBlocking {
        assertTrue(observe(bank).invoke().first().cashAccounts.isEmpty())
    }

    @Test
    fun theOverviewListsOpenItemsAndRemembersTheLastCatchUp() = runBlocking {
        ledger.saveTransaction(tx("e1", TransactionType.EXPENSE, account = cash, spendingClass = SpendingClass.UNKNOWN))
        prefs.markCaughtUp(LocalDate.of(2026, 10, 18))

        val overview = observe(cash).invoke().first()

        assertEquals(listOf("e1"), overview.items.map { it.entryId.value })
        assertEquals(LocalDate.of(2026, 10, 18), overview.lastCatchUp)
    }

    @Test
    fun anEmptyLedgerIsQuietNotComplete() = runBlocking {
        val overview = observe(cash).invoke().first()

        assertTrue(overview.items.isEmpty())
        assertTrue(overview.hasNothingRecorded)
        assertNull(overview.lastCatchUp)
    }

    // --- Saving the catch-up ---

    @Test
    fun aShortfallIsSavedAsOneUnclassifiedCashCatchUpAdjustment() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()

        assertEquals(CheckOutcome.SHORTFALL, check.outcome)
        val adjustment = adjustments().single()
        assertEquals(TransactionType.ADJUSTMENT_OUT, adjustment.type)
        assertEquals(lkr(500), adjustment.amount)
        assertEquals(RecordSource.CASH_CATCH_UP, adjustment.source)
        assertNull(adjustment.categoryId)
        assertNull(adjustment.spendingClass)
        assertNull(adjustment.merchant)
        assertEquals(lkr(1_000), balance())
        assertEquals(today, prefs.current.lastCatchUp)
    }

    @Test
    fun theGapStaysUnknownSpendingAndTheCategoryPictureStaysIncomplete() = runBlocking {
        cashAt1500()

        record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()

        val summary = LedgerSummaryCalculator.summarize(ledger.observeLedger().first(), october)
        assertEquals(lkr(500), summary.spent.unknown.amount)
        assertEquals(lkr(500), summary.spent.discretionary.amount)
        assertEquals(lkr(0), summary.received.amount)
        assertTrue(summary.hasIncompleteData)
    }

    @Test
    fun anExtraIsAnIncomingAdjustmentThatIsNotIncome() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(1_800), today, T0).getOrThrow()

        assertEquals(CheckOutcome.SURPLUS, check.outcome)
        assertEquals(TransactionType.ADJUSTMENT_IN, adjustments().single().type)
        assertEquals(RecordSource.CASH_CATCH_UP, adjustments().single().source)
        assertEquals(lkr(0), LedgerSummaryCalculator.summarize(ledger.observeLedger().first(), october).received.amount)
    }

    @Test
    fun aMatchSavesNothingButStillCountsAsACatchUp() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(1_500), today, T0).getOrThrow()

        assertEquals(CheckOutcome.MATCHES, check.outcome)
        assertTrue(adjustments().isEmpty())
        assertEquals(today, prefs.current.lastCatchUp)
    }

    @Test
    fun repeatingTheSameSaveDoesNotAdjustTwice() = runBlocking {
        cashAt1500()

        record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()
        record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()

        assertEquals(1, adjustments().size)
        assertEquals(lkr(1_000), balance())
    }

    @Test
    fun aCheckThatCannotBeMadeIsNotRememberedAsACatchUp() = runBlocking {
        val unknownOpening = cash.copy(openingBalance = OpeningBalance.Unknown)

        val result = record(TransactionId("a1"), unknownOpening, lkr(1_000), today, T0)

        assertTrue(result.isFailure)
        assertTrue(adjustments().isEmpty())
        assertNull(prefs.current.lastCatchUp)
    }

    @Test
    fun aFailureToRememberTheDateDoesNotHideASavedAdjustment() = runBlocking {
        cashAt1500()
        prefs.markError = IOException("disk full")

        val result = record(TransactionId("a1"), cash, lkr(1_000), today, T0)

        assertTrue(result.isSuccess)
        assertEquals(1, adjustments().size)
        assertNull(prefs.current.lastCatchUp)
    }

    // --- The reminder ---

    @Test
    fun savingTheReminderStoresItAndArrangesTheNextOne() = runBlocking {
        val update = UpdateReminderSettingsUseCase(prefs, scheduler)
        val moved = ReminderSettings(day = DayOfWeek.FRIDAY, time = LocalTime.of(8, 30))

        update(moved, Instant.parse("2026-10-21T04:30:00Z"))

        assertEquals(moved, prefs.current.reminder)
        assertEquals(LocalDate.of(2026, 10, 23), scheduler.last!!.toLocalDate())
        assertEquals(LocalTime.of(8, 30), scheduler.last!!.toLocalTime())
    }

    @Test
    fun turningTheReminderOffCancelsIt() = runBlocking {
        val update = UpdateReminderSettingsUseCase(prefs, scheduler)

        update(ReminderSettings(enabled = false), Instant.parse("2026-10-21T04:30:00Z"))

        assertFalse(prefs.current.reminder.enabled)
        assertEquals(1, scheduler.scheduled.size)
        assertNull(scheduler.last)
    }

    @Test
    fun schedulingFromTheSavedSettingsUsesTheDefaultWhenNothingWasSaved() = runBlocking {
        ScheduleReminderUseCase(prefs, scheduler)(Instant.parse("2026-10-21T04:30:00Z"))

        assertEquals(LocalDate.of(2026, 10, 25), scheduler.last!!.toLocalDate())
        assertEquals(LocalTime.of(19, 0), scheduler.last!!.toLocalTime())
    }

    @Test
    fun schedulingHonoursASkippedWeek() = runBlocking {
        val skipped = ReminderSettings(skippedOccurrence = LocalDate.of(2026, 10, 25))
        val withSkip = FakeCatchUpPreferences(CatchUpPreferences(reminder = skipped))

        ScheduleReminderUseCase(withSkip, scheduler)(Instant.parse("2026-10-21T04:30:00Z"))

        assertEquals(LocalDate.of(2026, 11, 1), scheduler.last!!.toLocalDate())
    }

    @Test
    fun aFailedSaveDoesNotArrangeAReminder() = runBlocking {
        prefs.saveError = IOException("disk full")
        val update = UpdateReminderSettingsUseCase(prefs, scheduler)

        val failure = runCatching { update(ReminderSettings(enabled = false)) }

        assertTrue(failure.isFailure)
        assertTrue(scheduler.scheduled.isEmpty())
    }
}
