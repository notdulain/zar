package com.example.zar.features.transactions.domain.refund.usecase

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
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.transactions.domain.refund.RefundDetails
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundUseCasesTest {
    private class FakeAccounts(vararg initial: Account) : AccountRepository {
        private val accounts = MutableStateFlow(initial.toList())
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override suspend fun saveAccount(account: Account) {
            accounts.value = accounts.value.filterNot { it.id == account.id } + account
        }
    }

    private val repo = FakeLedgerRepository()
    private val accounts = FakeAccounts(bank, cash, payoneer)
    private val observeContext = ObserveRefundContextUseCase(repo, accounts)
    private val record = RecordRefundUseCase(repo, SaveTransactionUseCase(repo))
    private val void = VoidTransactionUseCase(repo)
    private val october = YearMonth.from(OCT_10).toDateRange()

    private suspend fun entry(id: String): LedgerEntry = repo.observeLedger().first().first { it.id.value == id }

    private suspend fun details(purchaseId: String, amount: Money, account: Account = cash) =
        RefundDetails(entry(purchaseId), account, amount, LocalDate.of(2026, 10, 25), note = null)

    private suspend fun summary() = LedgerSummaryCalculator.summarize(repo.observeLedger().first(), october)

    @Test
    fun aRefundIsSavedLinkedToThePurchaseAndOffsetsItsSpending() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(8_400), cash))

        record(TransactionId("r1"), details("p1", lkr(2_000)), T0).getOrThrow()

        val refund = entry("r1").current
        assertEquals(TransactionType.REFUND, refund.type)
        assertEquals(TransactionId("p1"), refund.refundOf)
        assertEquals(lkr(6_400), summary().spent.total.amount)
        assertEquals(lkr(0), summary().received.amount)
    }

    @Test
    fun theRefundOffsetsThePurchaseInThePurchasesMonthNotTheRefundsMonth() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash, date = LocalDate.of(2026, 9, 28)))
        val refundInOctober = RefundDetails(entry("p1"), cash, lkr(400), LocalDate.of(2026, 10, 3), null)

        record(TransactionId("r1"), refundInOctober, T0).getOrThrow()

        val entries = repo.observeLedger().first()
        val september = YearMonth.of(2026, 9).toDateRange()
        assertEquals(lkr(600), LedgerSummaryCalculator.summarize(entries, september).spent.total.amount)
        assertEquals(lkr(0), LedgerSummaryCalculator.summarize(entries, october).spent.total.amount)
    }

    @Test
    fun theAccountBalanceGetsTheMoneyBackExactlyOnce() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        val d = details("p1", lkr(400))

        record(TransactionId("r1"), d, T0).getOrThrow()
        // A replay (for example after reconnecting) carries the same ID and must not add the money twice.
        record(TransactionId("r1"), d, T0.plusSeconds(60)).getOrThrow()

        assertEquals(1, repo.transactions.values.count { it.type == TransactionType.REFUND })
        assertEquals(lkr(2_000 - 1_000 + 400), AccountBalanceCalculator.calculate(cash, repo.observeLedger().first()).current)
    }

    @Test
    fun aReplayCarryingDifferentValuesNeverOverwritesTheStoredRefund() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        record(TransactionId("r1"), details("p1", lkr(400)), T0).getOrThrow()

        record(TransactionId("r1"), details("p1", lkr(900)), T0.plusSeconds(5)).getOrThrow()

        assertEquals(lkr(400), entry("r1").current.amount)
    }

    @Test
    fun twoRefundsCanNeverAddUpToMoreThanThePurchase() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        // Both forms were opened before either was saved, so each looked at "1,000 left".
        val first = details("p1", lkr(700))
        val second = details("p1", lkr(700))

        record(TransactionId("r1"), first, T0).getOrThrow()
        val result = record(TransactionId("r2"), second, T0.plusSeconds(1))

        assertTrue(result.exceptionOrNull() is LedgerError.RefundTooHigh)
        assertNull(repo.transactions[TransactionId("r2")])
        assertEquals(lkr(300), summary().spent.total.amount)
    }

    @Test
    fun theWholePurchaseCanBeRefundedAcrossSeveralRefunds() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))

        record(TransactionId("r1"), details("p1", lkr(600)), T0).getOrThrow()
        record(TransactionId("r2"), details("p1", lkr(400)), T0.plusSeconds(1)).getOrThrow()

        assertEquals(lkr(0), summary().spent.total.amount)
        val context = observeContext(TransactionId("p1")).first()!!
        assertEquals(lkr(0), context.summary.remaining)
        assertFalse(context.canRefund)
        assertTrue(record(TransactionId("r3"), details("p1", lkr(1)), T0.plusSeconds(2)).exceptionOrNull() is LedgerError.RefundTooHigh)
    }

    @Test
    fun aVoidedPurchaseCannotBeRefunded() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        val d = details("p1", lkr(100))
        void(entry("p1"), T0.plusSeconds(5)).getOrThrow()

        val result = record(TransactionId("r1"), d, T0.plusSeconds(10))

        assertTrue(result.exceptionOrNull() is LedgerError.AlreadyVoided)
        assertTrue(repo.transactions.values.none { it.type == TransactionType.REFUND })
    }

    @Test
    fun onlyAnExpenseCanBeRefunded() = runBlocking {
        repo.saveTransaction(tx("i1", TransactionType.INCOME, lkr(1_000), cash))

        val result = record(TransactionId("r1"), details("i1", lkr(100)), T0)

        assertTrue(result.exceptionOrNull() is LedgerError.InvalidRecord)
    }

    @Test
    fun aMissingPurchaseIsRejected() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        val d = details("p1", lkr(100))
        val gone = d.copy(purchase = d.purchase.copy(original = d.purchase.original.copy(id = TransactionId("nope"))))

        assertTrue(record(TransactionId("r1"), gone, T0).exceptionOrNull() is LedgerError.InvalidRecord)
    }

    @Test
    fun theRefundMustUseThePurchasesCurrencyAndAccount() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))

        val wrongCurrency = record(TransactionId("r1"), details("p1", usd(5), payoneer), T0)
        val wrongAccount = record(TransactionId("r2"), details("p1", lkr(100), bank), T0)

        assertTrue(wrongCurrency.exceptionOrNull() is LedgerError.CurrencyMismatch)
        assertTrue(wrongAccount.exceptionOrNull() is LedgerError.InvalidRecord)
        assertTrue(repo.transactions.values.none { it.type == TransactionType.REFUND })
    }

    @Test
    fun anArchivedAccountCannotTakeARefund() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        val archived = cash.copy(isArchived = true)

        val result = record(TransactionId("r1"), details("p1", lkr(100), archived), T0)

        assertTrue(result.exceptionOrNull() is LedgerError.AccountArchived)
        accounts.saveAccount(archived)
        assertFalse(observeContext(TransactionId("p1")).first()!!.canRefund)
    }

    @Test
    fun undoingARefundBringsTheFullPurchaseBackIntoSpendingAndFreesTheCap() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        record(TransactionId("r1"), details("p1", lkr(400)), T0).getOrThrow()

        void(entry("r1"), T0.plusSeconds(30)).getOrThrow()

        assertEquals(lkr(1_000), summary().spent.total.amount)
        assertEquals(lkr(1_000), observeContext(TransactionId("p1")).first()!!.summary.remaining)
        assertEquals(lkr(2_000 - 1_000), AccountBalanceCalculator.calculate(cash, repo.observeLedger().first()).current)
    }

    @Test
    fun aPurchaseWithActiveRefundsCannotBeUndoneUntilTheyAre() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        record(TransactionId("r1"), details("p1", lkr(400)), T0).getOrThrow()

        val blocked = void(entry("p1"), T0.plusSeconds(30))

        assertTrue(blocked.exceptionOrNull() is LedgerError.HasActiveRefunds)
        assertFalse(entry("p1").isVoided)

        void(entry("r1"), T0.plusSeconds(40)).getOrThrow()
        void(entry("p1"), T0.plusSeconds(50)).getOrThrow()
        assertTrue(entry("p1").isVoided)
        assertEquals(lkr(0), summary().spent.total.amount)
    }

    @Test
    fun theContextIsNullForAnUnknownPurchase() = runBlocking {
        assertNull(observeContext(TransactionId("nope")).first())
    }
}
