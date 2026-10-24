package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger.usdRate
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.ReportingConversion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerUseCasesTest {

    private val repo = FakeLedgerRepository()

    private val transfer = Transfer(
        id = TransferId("t1"), fromAccountId = bank.id, toAccountId = cash.id,
        sent = lkr(5_000), received = lkr(5_000), date = OCT_10,
        sentConversion = ReportingConversion.SameCurrency, receivedConversion = ReportingConversion.SameCurrency,
        createdAt = T0,
    )

    @Test fun accountCorrectionsRejectMissingArchivedAndForeignCurrencyAccounts() = runBlocking {
        val accounts = com.example.zar.core.domain.FakeAccountRepository(cash, bank.copy(isArchived = true), payoneer)
        val correct = CorrectTransactionUseCase(repo, accounts)
        val entry = entry(tx("account-check", TransactionType.EXPENSE, lkr(850), cash))
        for ((id, expected) in listOf(
            com.example.zar.core.domain.model.AccountId("missing") to LedgerError.InvalidRecord::class.java,
            bank.id to LedgerError.AccountArchived::class.java,
            payoneer.id to LedgerError.CurrencyMismatch::class.java,
        )) {
            val result = correct(entry, TransactionChanges(accountId = id), CorrectionId(id.value), T0)
            assertTrue(expected.isInstance(result.exceptionOrNull()))
        }
        assertTrue(repo.corrections.isEmpty())
    }

    @Test fun expenseWithRefundsMustKeepItsAccount() = runBlocking {
        val purchase = tx("purchase", TransactionType.EXPENSE, lkr(850), cash)
        repo.saveTransaction(purchase)
        repo.saveTransaction(tx("refund", TransactionType.REFUND, lkr(100), cash, refundOf = "purchase"))
        val correct = CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(cash, bank))
        val result = correct(entry(purchase), TransactionChanges(accountId = bank.id), CorrectionId("move"), T0)
        assertTrue(result.exceptionOrNull() is LedgerError.HasActiveRefunds)
        assertTrue(repo.corrections.isEmpty())
    }

    @Test
    fun rejectsAmountInAnotherCurrency() = runBlocking {
        val result = SaveTransactionUseCase(repo)(tx("x", TransactionType.EXPENSE, usd(5)), bank)
        assertTrue(result.exceptionOrNull() is LedgerError.CurrencyMismatch)
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun retriedSaveDoesNotOverwrite() = runBlocking {
        val save = SaveTransactionUseCase(repo)
        save(tx("x", TransactionType.EXPENSE, lkr(100)), bank)
        save(tx("x", TransactionType.EXPENSE, lkr(999)), bank)
        assertEquals(lkr(100), repo.transactions.getValue(TransactionId("x")).amount)
    }

    @Test
    fun transferSavesBothLegs() = runBlocking {
        SaveTransferUseCase(repo)(transfer, bank, cash).getOrThrow()
        assertEquals(setOf("t1-out", "t1-in"), repo.transactions.keys.map { it.value }.toSet())
        assertEquals(TransactionType.TRANSFER_OUT, repo.transactions.getValue(TransactionId("t1-out")).type)
    }

    @Test
    fun undoingOneTransferLegVoidsBoth() = runBlocking {
        val void = VoidTransactionUseCase(repo)
        void(entry(transfer.inLeg()), T0).getOrThrow()
        void(entry(transfer.inLeg()), T0).getOrThrow()
        assertEquals(setOf("t1-out", "t1-in"), repo.voids.keys.map { it.value }.toSet())
    }

    @Test
    fun correctingAForeignAmountReusesTheHistoricalRate() = runBlocking {
        val income = tx("usd", TransactionType.INCOME, usd(100), account = payoneer)
        CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, com.example.zar.core.domain.TestLedger.cash, payoneer))(entry(income), TransactionChanges(amount = usd(120)), CorrectionId("c1"), T0).getOrThrow()
        val corrected = LedgerProjector.project(repo.transactions.values + income, repo.corrections, emptyList()).single().current
        val conversion = corrected.reportingConversion as ReportingConversion.Converted
        assertEquals(usdRate, conversion.rate)
        assertEquals(lkr(36_000), conversion.amount)
    }

    @Test
    fun cannotCorrectAnUndoneEntry() = runBlocking {
        val result = CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, com.example.zar.core.domain.TestLedger.cash, payoneer))(
            entry(tx("x", TransactionType.EXPENSE), voided = true), TransactionChanges(amount = lkr(5)), CorrectionId("c"), T0,
        )
        assertTrue(result.exceptionOrNull() is LedgerError.AlreadyVoided)
        assertTrue(repo.corrections.isEmpty())
    }

    @Test
    fun emptyCorrectionIsRejected() = runBlocking {
        val result = CorrectTransactionUseCase(repo, com.example.zar.core.domain.FakeAccountRepository(bank, com.example.zar.core.domain.TestLedger.cash, payoneer))(entry(tx("x", TransactionType.EXPENSE)), TransactionChanges(), CorrectionId("c"), T0)
        assertTrue(result.exceptionOrNull() is LedgerError.NothingToChange)
    }
}
