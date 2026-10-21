package com.example.zar.features.transactions.domain.refund.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.features.transactions.domain.activity.model.EditKind
import com.example.zar.features.transactions.domain.activity.usecase.ObserveEntryDetailUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the entry detail needs to know about refunds and adjustments. */
class RefundDetailTest {
    private class FakeAccounts(vararg initial: Account) : AccountRepository {
        val accounts = MutableStateFlow(initial.toList())
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override suspend fun saveAccount(account: Account) = Unit
    }

    private val repo = FakeLedgerRepository()
    private val accounts = FakeAccounts(bank, cash)
    private val observeDetail = ObserveEntryDetailUseCase(repo, accounts)

    private suspend fun detail(id: String) = observeDetail(TransactionId(id)).first()!!

    @Test
    fun anUnrefundedExpenseCanTakeARefund() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))

        val detail = detail("p1")

        assertTrue(detail.canRefund)
        assertFalse(detail.hasActiveRefunds)
        assertTrue(detail.canVoid)
        assertEquals(lkr(1_000), detail.refunds!!.remaining)
    }

    @Test
    fun aRefundedExpenseShowsItsRefundsAndCannotBeUndoneYet() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        repo.saveTransaction(tx("r1", TransactionType.REFUND, lkr(300), cash, refundOf = "p1"))

        val detail = detail("p1")

        assertEquals(listOf("r1"), detail.refunds!!.refunds.map { it.id.value })
        assertEquals(lkr(700), detail.refunds!!.remaining)
        assertTrue(detail.canRefund)
        assertTrue(detail.hasActiveRefunds)
        assertFalse(detail.canVoid)
    }

    @Test
    fun aFullyRefundedExpenseCannotTakeAnotherRefund() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        repo.saveTransaction(tx("r1", TransactionType.REFUND, lkr(1_000), cash, refundOf = "p1"))

        assertFalse(detail("p1").canRefund)
    }

    @Test
    fun anExpenseOnAnArchivedAccountCannotTakeARefund() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        accounts.accounts.value = listOf(bank, cash.copy(isArchived = true))

        assertFalse(detail("p1").canRefund)
    }

    @Test
    fun aRefundLinksBackToItsPurchaseAndHasNothingToEditOrRefund() = runBlocking {
        repo.saveTransaction(tx("p1", TransactionType.EXPENSE, lkr(1_000), cash))
        repo.saveTransaction(tx("r1", TransactionType.REFUND, lkr(300), cash, refundOf = "p1"))

        val detail = detail("r1")

        assertEquals("p1", detail.refundedPurchase!!.id.value)
        assertEquals(cash, detail.refundedPurchaseAccount)
        assertNull(detail.refunds)
        assertFalse(detail.canRefund)
        assertEquals(EditKind.NONE, detail.editKind)
        assertTrue(detail.canVoid)
    }

    @Test
    fun aRefundWhosePurchaseIsMissingStillOpens() = runBlocking {
        repo.saveTransaction(tx("r1", TransactionType.REFUND, lkr(300), cash, refundOf = "gone"))

        val detail = detail("r1")

        assertNull(detail.refundedPurchase)
        assertTrue(detail.canVoid)
    }

    @Test
    fun anAdjustmentCanOnlyBeUndone() = runBlocking {
        repo.saveTransaction(tx("a1", TransactionType.ADJUSTMENT_OUT, lkr(450), cash))

        val detail = detail("a1")

        assertEquals(EditKind.NONE, detail.editKind)
        assertTrue(detail.canVoid)
        assertFalse(detail.canRefund)
        assertNull(detail.refunds)
    }
}
