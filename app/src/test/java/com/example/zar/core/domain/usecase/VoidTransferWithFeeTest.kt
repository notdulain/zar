package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voiding a transfer takes its separate fee expense with it, atomically, and never writes a void twice. */
class VoidTransferWithFeeTest {
    private val repo = FakeLedgerRepository()
    private val void = VoidTransactionUseCase(repo)
    private val voidedAt = T0.plusSeconds(500)

    private fun fee() = Transaction(
        id = TransactionId("t1-fee"),
        accountId = bank.id,
        type = TransactionType.EXPENSE,
        amount = lkr(30),
        date = OCT_10,
        reportingConversion = CurrencyConverter.toReporting(lkr(30), null),
        spendingClass = SpendingClass.COMMITTED,
        categoryId = "transfer-fee",
        merchant = "Transfer fee",
        createdAt = T0,
    )

    private fun transfer(withFee: Boolean) = Transfer(
        id = TransferId("t1"),
        fromAccountId = bank.id,
        toAccountId = cash.id,
        sent = lkr(5_000),
        received = lkr(5_000),
        date = OCT_10,
        sentConversion = CurrencyConverter.toReporting(lkr(5_000), null),
        receivedConversion = CurrencyConverter.toReporting(lkr(5_000), null),
        createdAt = T0,
        fee = if (withFee) fee() else null,
    )

    private suspend fun entry(id: String): LedgerEntry = repo.observeLedger().first().first { it.id.value == id }

    private suspend fun voided(): Set<String> = repo.voids.keys.map { it.value }.toSet()

    @Test
    fun voidingATransferWithAFeeVoidsBothLegsAndTheFee() = runBlocking {
        repo.saveTransfer(transfer(withFee = true))

        void(entry("t1-in"), voidedAt).getOrThrow()

        assertEquals(setOf("t1-out", "t1-in", "t1-fee"), voided())
    }

    @Test
    fun voidingATransferWithoutAFeeVoidsOnlyTheLegs() = runBlocking {
        repo.saveTransfer(transfer(withFee = false))

        void(entry("t1-out"), voidedAt).getOrThrow()

        assertEquals(setOf("t1-out", "t1-in"), voided())
    }

    @Test
    fun voidingTheFeeAloneLeavesTheTransferActive() = runBlocking {
        repo.saveTransfer(transfer(withFee = true))

        void(entry("t1-fee"), voidedAt).getOrThrow()

        assertEquals(setOf("t1-fee"), voided())
        assertTrue(!entry("t1-out").isVoided && !entry("t1-in").isVoided)
    }

    @Test
    fun aFeeVoidedOnItsOwnIsNotWrittenAgainWhenTheTransferIsVoided() = runBlocking {
        repo.saveTransfer(transfer(withFee = true))
        val firstVoidAt: Instant = T0.plusSeconds(100)
        void(entry("t1-fee"), firstVoidAt).getOrThrow()

        void(entry("t1-out"), voidedAt).getOrThrow()

        assertEquals(setOf("t1-fee", "t1-out", "t1-in"), voided())
        assertEquals(firstVoidAt, repo.voids.getValue(TransactionId("t1-fee")).voidedAt)
    }

    @Test
    fun voidingTwiceChangesNothing() = runBlocking {
        repo.saveTransfer(transfer(withFee = true))
        void(entry("t1-in"), voidedAt).getOrThrow()

        void(entry("t1-in"), voidedAt.plusSeconds(60)).getOrThrow()

        assertEquals(3, repo.voids.size)
        assertTrue(repo.voids.values.all { it.voidedAt == voidedAt })
    }

    @Test
    fun balancesAndSpendingReturnToWhatTheyWereAfterTheVoid() = runBlocking {
        val period = YearMonth.from(OCT_10).toDateRange()
        repo.saveTransfer(transfer(withFee = true))
        val before = repo.observeLedger().first()
        assertEquals(lkr(30), LedgerSummaryCalculator.summarize(before, period).spent.total.amount)
        assertEquals(lkr(10_000 - 5_000 - 30), AccountBalanceCalculator.calculate(bank, before).current)

        void(entry("t1-out"), voidedAt).getOrThrow()

        val after = repo.observeLedger().first()
        assertEquals(lkr(0), LedgerSummaryCalculator.summarize(after, period).spent.total.amount)
        assertEquals(lkr(10_000), AccountBalanceCalculator.calculate(bank, after).current)
        assertEquals(lkr(2_000), AccountBalanceCalculator.calculate(cash, after).current)
    }
}
