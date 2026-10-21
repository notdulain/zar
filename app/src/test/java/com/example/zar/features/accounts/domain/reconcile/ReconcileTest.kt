package com.example.zar.features.accounts.domain.reconcile

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.accounts.domain.usecase.RecordBalanceCheckUseCase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileTest {
    private val repo = FakeLedgerRepository()
    private val record = RecordBalanceCheckUseCase(repo, SaveTransactionUseCase(repo))
    private val october = YearMonth.from(OCT_10).toDateRange()
    private val today = LocalDate.of(2026, 10, 25)

    /** Cash opens at 2,000 and one 500 expense is recorded, so the recorded balance is 1,500. */
    private suspend fun cashAt1500() = repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(500), cash))

    private suspend fun balance(account: Account = cash) = AccountBalanceCalculator.calculate(account, repo.observeLedger().first())

    private suspend fun summary() = LedgerSummaryCalculator.summarize(repo.observeLedger().first(), october)

    @Test
    fun aWeeklyCatchUpCanMarkItsAdjustmentWithItsOwnSource() = runBlocking {
        cashAt1500()

        record(TransactionId("a1"), cash, lkr(1_000), today, T0, RecordSource.CASH_CATCH_UP).getOrThrow()

        val adjustment = repo.transactions.getValue(TransactionId("a1"))
        assertEquals(RecordSource.CASH_CATCH_UP, adjustment.source)
        // Only the source differs: it is still an unclassified adjustment and still unknown spending.
        assertNull(adjustment.categoryId)
        assertEquals(lkr(500), summary().spent.unknown.amount)
    }

    @Test
    fun aShortfallIsSavedAsAnOutgoingAdjustmentWithNoCategory() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()

        assertEquals(CheckOutcome.SHORTFALL, check.outcome)
        val adjustment = repo.transactions.getValue(TransactionId("a1"))
        assertEquals(TransactionType.ADJUSTMENT_OUT, adjustment.type)
        assertEquals(lkr(500), adjustment.amount)
        assertNull(adjustment.categoryId)
        assertNull(adjustment.spendingClass)
        assertNull(adjustment.merchant)
        assertEquals(RecordSource.RECONCILIATION, adjustment.source)
        assertEquals(today, adjustment.date)
        assertEquals(lkr(1_000), balance().current)
    }

    @Test
    fun anUnexplainedShortfallStaysUnknownAndKeepsTheIncompleteWarning() = runBlocking {
        cashAt1500()

        record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()

        val result = summary()
        // The 500 expense is discretionary; the 500 adjustment is unknown and never discretionary.
        assertEquals(lkr(500), result.spent.discretionary.amount)
        assertEquals(lkr(500), result.spent.unknown.amount)
        assertEquals(lkr(0), result.spent.committed.amount)
        assertTrue(result.hasIncompleteData)
        assertEquals(1, result.unclassifiedAdjustmentCount)
        assertEquals(lkr(0), result.received.amount)
    }

    @Test
    fun aSurplusIsAnIncomingAdjustmentThatMovesTheBalanceButIsNeitherIncomeNorSpending() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(1_800), today, T0).getOrThrow()

        assertEquals(CheckOutcome.SURPLUS, check.outcome)
        assertEquals(TransactionType.ADJUSTMENT_IN, repo.transactions.getValue(TransactionId("a1")).type)
        assertEquals(lkr(1_800), balance().current)
        val result = summary()
        assertEquals(lkr(0), result.received.amount)
        assertEquals(lkr(500), result.spent.total.amount)
    }

    @Test
    fun aMatchingCountSavesNothing() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(1_500), today, T0).getOrThrow()

        assertEquals(CheckOutcome.MATCHES, check.outcome)
        assertTrue(repo.transactions.keys.none { it.value == "a1" })
        assertFalse(summary().hasIncompleteData)
    }

    @Test
    fun repeatingTheSameConfirmDoesNotAdjustTwice() = runBlocking {
        cashAt1500()

        record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()
        // The acknowledgement was lost and the person taps Confirm again with the same ID.
        val again = record(TransactionId("a1"), cash, lkr(1_000), today, T0.plusSeconds(30)).getOrThrow()

        assertEquals(CheckOutcome.MATCHES, again.outcome)
        assertEquals(1, repo.transactions.values.count { it.type == TransactionType.ADJUSTMENT_OUT })
        assertEquals(lkr(1_000), balance().current)
    }

    @Test
    fun anEmptyCashTinIsAValidCount() = runBlocking {
        cashAt1500()

        val check = record(TransactionId("a1"), cash, lkr(0), today, T0).getOrThrow()

        assertEquals(lkr(1_500), check.gap)
        assertEquals(lkr(0), balance().current)
    }

    @Test
    fun theAdjustmentUsesTheStoredBalanceAsItIsNowNotWhatTheSheetLastShowed() = runBlocking {
        cashAt1500()
        // The sheet was opened at 1,500; before confirming, another 200 expense was recorded: now 1,300.
        repo.saveTransaction(tx("e2", TransactionType.EXPENSE, lkr(200), cash))

        val check = record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()

        assertEquals(lkr(300), check.gap)
        assertEquals(lkr(1_000), balance().current)
    }

    @Test
    fun voidingTheAdjustmentRestoresTheRecordedBalance() = runBlocking {
        cashAt1500()
        record(TransactionId("a1"), cash, lkr(1_000), today, T0).getOrThrow()
        val entry = repo.observeLedger().first().first { it.id.value == "a1" }

        VoidTransactionUseCase(repo)(entry, T0.plusSeconds(30)).getOrThrow()

        assertEquals(lkr(1_500), balance().current)
        assertFalse(summary().hasIncompleteData)
    }

    @Test
    fun anAccountWithAnUnknownOpeningBalanceCannotBeChecked() = runBlocking {
        val unknown = cash.copy(openingBalance = OpeningBalance.Unknown)

        val result = record(TransactionId("a1"), unknown, lkr(1_000), today, T0)

        assertTrue(result.exceptionOrNull() is LedgerError.InvalidRecord)
        assertEquals(CheckAvailability.BALANCE_UNKNOWN, ReconcileRules.availability(AccountBalanceCalculator.calculate(unknown, emptyList())))
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun anApproximateOpeningBalanceCanStillBeChecked() = runBlocking {
        val approximate = bank.copy(openingBalance = OpeningBalance.Approximate(lkr(10_000)))

        val check = record(TransactionId("a1"), approximate, lkr(9_000), today, T0).getOrThrow()

        assertEquals(lkr(1_000), check.gap)
        assertEquals(TransactionType.ADJUSTMENT_OUT, repo.transactions.getValue(TransactionId("a1")).type)
    }

    @Test
    fun entriesInAnotherCurrencyBlockTheCheckUntilTheyAreFixed() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, usd(5), cash))
        val balance = balance()

        assertEquals(CheckAvailability.ENTRIES_NEED_FIXING, ReconcileRules.availability(balance))
        assertTrue(record(TransactionId("a1"), cash, lkr(1_000), today, T0).exceptionOrNull() is LedgerError.InvalidRecord)
    }

    @Test
    fun anArchivedAccountCannotBeChecked() = runBlocking {
        val archived = cash.copy(isArchived = true)

        assertEquals(CheckAvailability.ARCHIVED, ReconcileRules.availability(AccountBalanceCalculator.calculate(archived, emptyList())))
        assertTrue(record(TransactionId("a1"), archived, lkr(1), today, T0).isFailure)
    }

    @Test
    fun theCountMustBeInTheAccountsCurrency() = runBlocking {
        val result = record(TransactionId("a1"), payoneer, lkr(5), today, T0)

        assertTrue(result.exceptionOrNull() is LedgerError.CurrencyMismatch)
    }

    @Test
    fun aForeignCurrencyGapIsSavedWithAMissingRateSoTotalsCountItAsUnresolved() = runBlocking {
        val result = record(TransactionId("a1"), payoneer, usd(0), today, T0)
        // Payoneer opens at USD 0 so counting zero matches.
        assertEquals(CheckOutcome.MATCHES, result.getOrThrow().outcome)

        val withMoney = payoneer.copy(openingBalance = OpeningBalance.Known(usd(50)))
        val check = record(TransactionId("a2"), withMoney, usd(40), today, T0).getOrThrow()

        assertEquals(usd(10), check.gap)
        val adjustment = repo.transactions.getValue(TransactionId("a2"))
        assertEquals(TransactionType.ADJUSTMENT_OUT, adjustment.type)
        val unresolved = LedgerSummaryCalculator.summarize(repo.observeLedger().first(), october).spent.unknown
        assertEquals(1, unresolved.unresolvedCount)
    }

    @Test
    fun theFormNeedsAnAmountItCanRead() {
        val balance = AccountBalanceCalculator.calculate(cash, emptyList())

        fun problems(input: String) = (ReconcileRules.evaluate(balance, input) as CheckResult.Invalid).problems

        assertEquals(setOf(CheckProblem.AMOUNT_MISSING), problems(""))
        assertEquals(setOf(CheckProblem.AMOUNT_INVALID), problems("abc"))
        assertEquals(setOf(CheckProblem.AMOUNT_INVALID), problems("1.234"))
        val valid = ReconcileRules.evaluate(balance, "1,500") as CheckResult.Valid
        assertEquals(lkr(500), valid.check.gap)
        assertEquals(lkr(-500), valid.check.difference)
    }
}
