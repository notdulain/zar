package com.example.zar.features.transactions.domain.activity.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.transactions.domain.activity.model.EditKind
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityUseCasesTest {
    private class FakeAccounts(vararg initial: Account) : AccountRepository {
        private val accounts = MutableStateFlow(initial.toList())
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override suspend fun saveAccount(account: Account) {
            accounts.value = accounts.value.filterNot { it.id == account.id } + account
        }
    }

    private val repo = FakeLedgerRepository()
    private val accounts = FakeAccounts(bank, cash)
    private val observeDetail = ObserveEntryDetailUseCase(repo, accounts)
    private val correct = CorrectExpenseUseCase(CorrectTransactionUseCase(repo))
    private val void = VoidTransactionUseCase(repo)
    private val period = YearMonth.from(OCT_10).toDateRange()

    private fun fee() = Transaction(
        id = TransactionId("t1-fee"), accountId = bank.id, type = TransactionType.EXPENSE, amount = lkr(30), date = OCT_10,
        reportingConversion = CurrencyConverter.toReporting(lkr(30), null), spendingClass = SpendingClass.COMMITTED,
        categoryId = "transfer-fee", merchant = "Transfer fee", createdAt = T0,
    )

    private fun transfer() = Transfer(
        id = TransferId("t1"), fromAccountId = bank.id, toAccountId = cash.id, sent = lkr(5_000), received = lkr(5_000), date = OCT_10,
        sentConversion = CurrencyConverter.toReporting(lkr(5_000), null),
        receivedConversion = CurrencyConverter.toReporting(lkr(5_000), null), createdAt = T0, fee = fee(),
    )

    private suspend fun entry(id: String) = repo.observeLedger().first().first { it.id.value == id }

    @Test
    fun anExpenseCanBeCorrectedAndShowsItsHistory() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(8_400), cash))

        correct(entry("e1"), TransactionChanges(amount = lkr(8_900)), CorrectionId("c1"), T0.plusSeconds(10)).getOrThrow()

        val detail = observeDetail(TransactionId("e1")).first()!!
        assertEquals(lkr(8_900), detail.entry.current.amount)
        assertEquals(lkr(8_400), detail.entry.original.amount)
        assertEquals(1, detail.history.size)
        assertEquals(EditKind.EXPENSE, detail.editKind)
    }

    @Test
    fun replayingTheSameCorrectionDoesNotOverwriteOrDuplicateIt() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(8_400), cash))
        correct(entry("e1"), TransactionChanges(amount = lkr(8_900)), CorrectionId("c1"), T0.plusSeconds(10)).getOrThrow()

        // Same ID again, with a different amount: the stored correction wins (write-once).
        correct(entry("e1"), TransactionChanges(amount = lkr(1)), CorrectionId("c1"), T0.plusSeconds(20)).getOrThrow()

        assertEquals(1, repo.corrections.size)
        assertEquals(lkr(8_900), entry("e1").current.amount)
    }

    @Test
    fun aCorrectionChangesBalancesAndSpendingExactlyOnce() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(1_000), cash))
        // A replay (for example after reconnecting) carries the entry as it was before the first save.
        val before = entry("e1")
        correct(before, TransactionChanges(amount = lkr(1_500)), CorrectionId("c1"), T0.plusSeconds(10)).getOrThrow()
        correct(before, TransactionChanges(amount = lkr(1_500)), CorrectionId("c1"), T0.plusSeconds(10)).getOrThrow()

        val entries = repo.observeLedger().first()
        assertEquals(lkr(1_500), LedgerSummaryCalculator.summarize(entries, period).spent.total.amount)
        assertEquals(lkr(2_000 - 1_500), AccountBalanceCalculator.calculate(cash, entries).current)
    }

    @Test
    fun aVoidedEntryCannotBeCorrectedOrRevivedByAReplay() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(1_000), cash))
        void(entry("e1"), T0.plusSeconds(30)).getOrThrow()

        val result = correct(entry("e1"), TransactionChanges(amount = lkr(2)), CorrectionId("c9"), T0.plusSeconds(40))
        assertTrue(result.exceptionOrNull() is LedgerError.AlreadyVoided)

        // Replaying the original save later changes nothing: the void marker still wins.
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(1_000), cash))
        assertTrue(entry("e1").isVoided)
        assertEquals(lkr(0), LedgerSummaryCalculator.summarize(repo.observeLedger().first(), period).spent.total.amount)
        assertEquals(EditKind.NONE, observeDetail(TransactionId("e1")).first()!!.editKind)
        assertFalse(observeDetail(TransactionId("e1")).first()!!.canVoid)
    }

    @Test
    fun aCorrectionCannotCrossIntoAnotherCurrency() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(1_000), cash))

        val result = correct(entry("e1"), TransactionChanges(amount = com.example.zar.core.domain.TestLedger.usd(5)), CorrectionId("c1"), T0)

        assertTrue(result.exceptionOrNull() is LedgerError.CurrencyMismatch)
        assertTrue(repo.corrections.isEmpty())
    }

    @Test
    fun onlyExpensesCanBeCorrectedWithThisUseCase() = runBlocking {
        repo.saveTransaction(tx("i1", TransactionType.INCOME, lkr(1_000), bank))

        val result = correct(entry("i1"), TransactionChanges(note = FieldUpdate.Set("x")), CorrectionId("c1"), T0)

        assertTrue(result.exceptionOrNull() is LedgerError.InvalidRecord)
    }

    @Test
    fun incomeIsEditedInItsOwnFormAndOtherTypesAreNotEditable() = runBlocking {
        repo.saveTransaction(tx("i1", TransactionType.INCOME, lkr(1_000), bank))
        repo.saveTransaction(tx("a1", TransactionType.ADJUSTMENT_OUT, lkr(100), cash))

        assertEquals(EditKind.INCOME, observeDetail(TransactionId("i1")).first()!!.editKind)
        assertEquals(EditKind.NONE, observeDetail(TransactionId("a1")).first()!!.editKind)
    }

    @Test
    fun aTransferLegShowsItsPartnerAndFeeAndCannotBeEdited() = runBlocking {
        repo.saveTransfer(transfer())

        val out = observeDetail(TransactionId("t1-out")).first()!!
        assertTrue(out.isTransfer)
        assertEquals("t1-in", out.counterpart!!.id.value)
        assertEquals(cash, out.counterpartAccount)
        assertEquals("t1-fee", out.fee!!.id.value)
        assertEquals(bank, out.account)
        assertEquals(EditKind.NONE, out.editKind)
        assertTrue(out.canVoid)

        val inLeg = observeDetail(TransactionId("t1-in")).first()!!
        assertEquals("t1-out", inLeg.counterpart!!.id.value)
    }

    @Test
    fun anOrdinaryEntryHasNoPartnerOrFee() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(1_000), cash))

        val detail = observeDetail(TransactionId("e1")).first()!!

        assertNull(detail.counterpart)
        assertNull(detail.fee)
        assertFalse(detail.isTransfer)
        assertNotNull(detail.account)
    }

    @Test
    fun anUnknownEntryHasNoDetail() = runBlocking {
        assertNull(observeDetail(TransactionId("missing")).first())
    }

    @Test
    fun activitySnapshotCarriesEntriesAndAccountsTogether() = runBlocking {
        repo.saveTransaction(tx("e1", TransactionType.EXPENSE, lkr(1_000), cash))

        val snapshot = ObserveActivityUseCase(repo, accounts)().first()

        assertEquals(listOf("e1"), snapshot.entries.map { it.id.value })
        assertEquals(setOf("bank", "cash"), snapshot.accounts.map { it.id.value }.toSet())
    }
}
