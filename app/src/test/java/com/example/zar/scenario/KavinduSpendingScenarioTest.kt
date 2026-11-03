package com.example.zar.scenario

import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kavindu has never seen his committed-to-discretionary split (assignment §3.3). These tests
 * check the split Home shows him, with his rent, gym, rides and the LKR 24,000 coffee month.
 */
class KavinduSpendingScenarioTest {

    private val october = YearMonth.of(2026, 10)
    private val period = october.atDay(1)..october.atEndOfMonth()
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    private fun spend(id: String, amount: Long, cls: SpendingClass, day: Int, fromCash: Boolean = false) =
        entry(tx(id, TransactionType.EXPENSE, lkr(amount), account = if (fromCash) cash else com.example.zar.core.domain.TestLedger.bank,
            date = oct(day), spendingClass = cls))

    @Test
    fun committedAndDiscretionaryAreSeparated() {
        val entries = listOf(
            spend("rent", 34_000, SpendingClass.COMMITTED, 1),
            spend("gym", 6_500, SpendingClass.COMMITTED, 3),
            spend("coffee-and-dining", 24_000, SpendingClass.DISCRETIONARY, 15),
            spend("ubereats", 3_200, SpendingClass.DISCRETIONARY, 18),
        )
        val spent = LedgerSummaryCalculator.summarize(entries, period).spent

        assertEquals(lkr(40_500), spent.committed.amount)
        assertEquals("The coffee month is visible as discretionary", lkr(27_200), spent.discretionary.amount)
        assertEquals(lkr(67_700), spent.total.amount)
    }

    @Test
    fun cashAndUnsortedSpendingStillCountsAsUnknown() {
        // His bank summary missed cash entirely (§3.5). A cash withdrawal spent at a kade still counts.
        val entries = listOf(
            spend("rent", 34_000, SpendingClass.COMMITTED, 1),
            spend("kade-cash", 2_400, SpendingClass.UNKNOWN, 7, fromCash = true),
            spend("pickme", 850, SpendingClass.UNKNOWN, 8),
        )
        val spent = LedgerSummaryCalculator.summarize(entries, period).spent

        assertEquals("Nothing is dropped just because it has no class yet", lkr(3_250), spent.unknown.amount)
        assertEquals(lkr(37_250), spent.total.amount)
    }

    @Test
    fun aRefundReducesTheOriginalPurchaseNotIncome() {
        val order = tx("ubereats", TransactionType.EXPENSE, lkr(3_200), date = oct(18), spendingClass = SpendingClass.DISCRETIONARY)
        val refund = tx("ubereats-refund", TransactionType.REFUND, lkr(1_200), date = oct(19), refundOf = "ubereats")
        val summary = LedgerSummaryCalculator.summarize(listOf(entry(order), entry(refund)), period)

        assertEquals(lkr(2_000), summary.spent.discretionary.amount)
        assertEquals("A refund is never shown as money earned", lkr(0), summary.received.amount)
    }

    @Test
    fun aRefundLargerThanThePurchaseNeverMakesSpendingNegative() {
        val order = tx("pickme", TransactionType.EXPENSE, lkr(850), date = oct(8), spendingClass = SpendingClass.DISCRETIONARY)
        val refund = tx("pickme-refund", TransactionType.REFUND, lkr(1_000), date = oct(9), refundOf = "pickme")
        val spent = LedgerSummaryCalculator.summarize(listOf(entry(order), entry(refund)), period).spent

        assertEquals(lkr(0), spent.discretionary.amount)
    }
}
