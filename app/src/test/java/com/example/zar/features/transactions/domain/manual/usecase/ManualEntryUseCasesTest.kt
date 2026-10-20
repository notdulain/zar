package com.example.zar.features.transactions.domain.manual.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseDetails
import com.example.zar.features.transactions.domain.manual.model.TransferDetails
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryUseCasesTest {
    private val repo = FakeLedgerRepository()
    private val saveTransaction = SaveTransactionUseCase(repo)
    private val recordExpense = RecordExpenseUseCase(saveTransaction)
    private val recordTransfer = RecordTransferUseCase(SaveTransferUseCase(repo))
    private val oct10 = LocalDate.of(2026, 10, 10)

    private fun expense(account: com.example.zar.core.domain.model.Account = cash, amount: Long = 850) = ExpenseDetails(
        account = account,
        amount = lkr(amount),
        date = oct10,
        category = ExpenseCategory.TRANSPORT,
        spendingClass = SpendingClass.DISCRETIONARY,
        title = "Bus to Kandy",
        description = null,
    )

    private fun transfer(fee: Long? = null) = TransferDetails(
        from = bank,
        to = cash,
        amount = lkr(5_000),
        fee = fee?.let(::lkr),
        date = oct10,
        note = null,
    )

    @Test
    fun savingAnExpenseStoresOneExpenseOnItsAccount() = runBlocking {
        recordExpense(TransactionId("e1"), expense(), T0).getOrThrow()

        val saved = repo.transactions.getValue(TransactionId("e1"))
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(cash.id, saved.accountId)
        assertEquals(lkr(850), saved.amount)
        assertEquals(1, repo.transactions.size)
    }

    @Test
    fun retryingAnExpenseWithTheSameIdDoesNotDuplicateIt() = runBlocking {
        recordExpense(TransactionId("e1"), expense(), T0).getOrThrow()
        recordExpense(TransactionId("e1"), expense(amount = 99_999), T0.plusSeconds(5)).getOrThrow()

        assertEquals(1, repo.transactions.size)
        assertEquals(lkr(850), repo.transactions.getValue(TransactionId("e1")).amount)
    }

    @Test
    fun anArchivedAccountCannotReceiveAnExpense() = runBlocking {
        val result = recordExpense(TransactionId("e1"), expense(account = cash.copy(isArchived = true)), T0)

        assertTrue(result.exceptionOrNull() is LedgerError.AccountArchived)
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun savingATransferStoresBothLegsTogether() = runBlocking {
        recordTransfer(TransferId("t1"), transfer(), T0).getOrThrow()

        assertEquals(
            setOf(TransactionId("t1-out"), TransactionId("t1-in")),
            repo.transactions.keys,
        )
        assertEquals(TransactionType.TRANSFER_OUT, repo.transactions.getValue(TransactionId("t1-out")).type)
        assertEquals(TransactionType.TRANSFER_IN, repo.transactions.getValue(TransactionId("t1-in")).type)
    }

    @Test
    fun aTransferFeeIsSavedAsAnExtraExpense() = runBlocking {
        recordTransfer(TransferId("t1"), transfer(fee = 30), T0).getOrThrow()

        assertEquals(3, repo.transactions.size)
        val fee = repo.transactions.getValue(TransactionId("t1-fee"))
        assertEquals(TransactionType.EXPENSE, fee.type)
        assertEquals(lkr(30), fee.amount)
        assertEquals(bank.id, fee.accountId)
    }

    @Test
    fun retryingATransferWithFeeDoesNotDuplicateAnything() = runBlocking {
        repeat(2) { recordTransfer(TransferId("t1"), transfer(fee = 30), T0).getOrThrow() }

        assertEquals(3, repo.transactions.size)
    }

    @Test
    fun aTransferToAnArchivedAccountSavesNothing() = runBlocking {
        val result = recordTransfer(TransferId("t1"), transfer().copy(to = cash.copy(isArchived = true)), T0)

        assertTrue(result.exceptionOrNull() is LedgerError.AccountArchived)
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun transfersAreNeitherReceivedNorSpendingButTheFeeIsSpending() = runBlocking {
        recordTransfer(TransferId("t1"), transfer(fee = 30), T0).getOrThrow()
        recordExpense(TransactionId("e1"), expense(), T0).getOrThrow()

        val summary = LedgerSummaryCalculator.summarize(
            repo.transactions.values.map { TestLedger.entry(it) },
            YearMonth.of(2026, 10).toDateRange(),
        )

        assertEquals(lkr(0), summary.received.amount)
        // 850 discretionary expense + 30 committed fee. The 5,000 transferred is not spending.
        assertEquals(lkr(850), summary.spent.discretionary.amount)
        assertEquals(lkr(30), summary.spent.committed.amount)
        assertEquals(lkr(0), summary.spent.unknown.amount)
    }

    @Test
    fun foreignCurrencyExpenseIsReportedAsUnresolvedNotGuessed() = runBlocking {
        val usdExpense = expense(account = payoneer).copy(amount = TestLedger.usd(12))
        recordExpense(TransactionId("e1"), usdExpense, T0).getOrThrow()

        val summary = LedgerSummaryCalculator.summarize(
            repo.transactions.values.map { TestLedger.entry(it) },
            YearMonth.of(2026, 10).toDateRange(),
        )

        assertEquals(1, summary.spent.total.unresolvedCount)
        assertEquals(lkr(0), summary.spent.total.amount)
    }
    @Test
    fun invalidFeeCannotLeaveTransferLegsStored() = runBlocking {
        val result = recordTransfer(TransferId("invalid"), transfer().copy(fee = TestLedger.usd(30)), T0)
        assertTrue(result.isFailure)
        assertTrue(repo.transactions.isEmpty())
    }

    @Test
    fun retryingWithDifferentFeeCannotAddToAnAlreadyStoredTransfer() = runBlocking {
        recordTransfer(TransferId("t1"), transfer(), T0).getOrThrow()
        recordTransfer(TransferId("t1"), transfer(fee = 30), T0).getOrThrow()
        assertEquals(2, repo.transactions.size)
    }

}
