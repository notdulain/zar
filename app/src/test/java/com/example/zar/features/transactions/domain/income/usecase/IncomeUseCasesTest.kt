package com.example.zar.features.transactions.domain.income.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.transactions.domain.income.model.IncomeDetails
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeUseCasesTest {

    private val repo = FakeLedgerRepository()
    private val oct = YearMonth.of(2026, 10)

    private fun details(amount: com.example.zar.core.domain.model.money.Money = lkr(50_000), rate: String? = null) =
        IncomeDetails(amount, LocalDate.of(2026, 10, 10), rate?.toBigDecimal(), "Client", null, null)

    /** A ledger that just replays fixed entries. */
    private class FixedLedger(private val entries: List<LedgerEntry>) : LedgerRepository {
        override fun observeLedger(): Flow<List<LedgerEntry>> = flowOf(entries)
        override suspend fun saveTransaction(transaction: Transaction) = Unit
        override suspend fun saveTransfer(transfer: Transfer) = Unit
        override suspend fun addCorrection(correction: Correction) = Unit
        override suspend fun void(markers: List<VoidMarker>) = Unit
        override suspend fun linkEvidence(link: EvidenceLink) = Unit
    }

    @Test fun incomeCanMoveAccountsWithoutChangingItsAmountOrTime() = runBlocking {
        val cash = com.example.zar.core.domain.TestLedger.cash
        val original = tx("income-move", TransactionType.INCOME, lkr(500), bank).copy(time = java.time.LocalTime.of(18, 30))
        repo.saveTransaction(original)
        val edit = EditIncomeUseCase(CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, cash)))
        edit(entry(original), IncomeDetails(original.amount, original.date, null, null, null, null, original.time), CorrectionId("move"), T0, accountId = cash.id).getOrThrow()
        val updated = repo.observeLedger().first().single()
        assertEquals(cash.id, updated.current.accountId)
        assertEquals(original.amount, updated.current.amount)
        assertEquals(original.time, updated.current.time)
    }

    @Test
    fun receivedCountsOnlyIncomeAndFlagsMissingRates() = runBlocking {
        val ledger = FixedLedger(
            listOf(
                entry(tx("salary", TransactionType.INCOME, lkr(100_000))),
                entry(tx("usd-known", TransactionType.INCOME, usd(100), account = payoneer)),
                entry(tx("usd-unknown", TransactionType.INCOME, usd(50), account = payoneer, conversion = ReportingConversion.Missing(lkr(0).currency))),
                entry(tx("undone", TransactionType.INCOME, lkr(9_999)), voided = true),
                entry(tx("other-month", TransactionType.INCOME, lkr(7_000), date = LocalDate.of(2026, 9, 30))),
                entry(tx("refund", TransactionType.REFUND, lkr(500), refundOf = "x")),
                entry(tx("transfer-in", TransactionType.TRANSFER_IN, lkr(5_000), transferId = "t1")),
                entry(tx("adjustment", TransactionType.ADJUSTMENT_IN, lkr(1_000))),
                entry(tx("coffee", TransactionType.EXPENSE, lkr(800))),
            ),
        )
        val overview = ObserveIncomeUseCase(ledger)(oct).first()

        // 100,000 + 100 USD at 300 = 130,000. The rate-less USD income is reported, not guessed.
        assertEquals(lkr(130_000), overview.receivedThisMonth.amount)
        assertEquals(1, overview.receivedThisMonth.unresolvedCount)
        assertEquals(
            listOf("salary", "usd-known", "usd-unknown", "other-month"),
            overview.entries.map { it.id.value },
        )
    }

    @Test
    fun entryLookupIgnoresUndoneAndNonIncome() = runBlocking {
        val ledger = FixedLedger(
            listOf(
                entry(tx("salary", TransactionType.INCOME)),
                entry(tx("undone", TransactionType.INCOME), voided = true),
                entry(tx("coffee", TransactionType.EXPENSE)),
            ),
        )
        val observe = ObserveIncomeEntryUseCase(ledger)
        assertEquals("salary", observe(TransactionId("salary")).first()?.id?.value)
        assertNull(observe(TransactionId("undone")).first())
        assertNull(observe(TransactionId("coffee")).first())
    }

    @Test
    fun recordingTwiceWithTheSameIdSavesOnce() = runBlocking {
        val record = RecordIncomeUseCase(SaveTransactionUseCase(repo))
        assertTrue(record(TransactionId("i1"), bank, details(), T0).isSuccess)
        assertTrue(record(TransactionId("i1"), bank, details(lkr(60_000)), T0).isSuccess)

        assertEquals(1, repo.transactions.size)
        assertEquals(lkr(50_000), repo.transactions.getValue(TransactionId("i1")).amount)
    }

    @Test
    fun recordingIntoTheWrongCurrencyAccountFails() = runBlocking {
        val result = RecordIncomeUseCase(SaveTransactionUseCase(repo))(TransactionId("i1"), bank, details(usd(10)), T0)
        assertTrue(result.exceptionOrNull() is LedgerError.CurrencyMismatch)
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun editingAmountKeepsTheHistoricalRate() = runBlocking {
        val original = tx("i1", TransactionType.INCOME, usd(100), account = payoneer)
        val edit = EditIncomeUseCase(CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, com.example.zar.core.domain.TestLedger.cash, payoneer)))

        assertTrue(edit(entry(original), details(usd(120), rate = "300"), CorrectionId("c1"), T0).isSuccess)

        val projected = LedgerProjector.project(listOf(original), repo.corrections, emptyList()).single()
        assertEquals(usd(120), projected.current.amount)
        assertEquals(lkr(36_000), (projected.current.reportingConversion as ReportingConversion.Converted).amount)
        assertEquals(usd(100), projected.original.amount)
    }

    @Test
    fun editingWithoutChangesIsRejected() = runBlocking {
        val original = tx("i1", TransactionType.INCOME, lkr(50_000)).copy(merchant = "Client")
        val result = EditIncomeUseCase(CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, com.example.zar.core.domain.TestLedger.cash, payoneer)))(entry(original), details(), CorrectionId("c1"), T0)
        assertTrue(result.exceptionOrNull() is LedgerError.NothingToChange)
    }

    @Test
    fun editingUndoneOrNonIncomeIsRejected() = runBlocking {
        val edit = EditIncomeUseCase(CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, com.example.zar.core.domain.TestLedger.cash, payoneer)))
        val undone = edit(entry(tx("i1", TransactionType.INCOME), voided = true), details(lkr(1)), CorrectionId("c1"), T0)
        val expense = edit(entry(tx("e1", TransactionType.EXPENSE)), details(), CorrectionId("c2"), T0)

        assertTrue(undone.exceptionOrNull() is LedgerError.AlreadyVoided)
        assertTrue(expense.exceptionOrNull() is LedgerError.InvalidRecord)
        assertTrue(repo.corrections.isEmpty())
    }
}
