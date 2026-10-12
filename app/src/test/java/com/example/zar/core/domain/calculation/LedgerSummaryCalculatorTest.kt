package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.model.money.Currencies
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSummaryCalculatorTest {

    private val october = YearMonth.of(2026, 10).toDateRange()

    @Test
    fun receivedCountsOnlyActualIncome() {
        val summary = LedgerSummaryCalculator.summarize(
            listOf(
                entry(tx("salary", TransactionType.INCOME, lkr(50_000))),
                entry(tx("freelance", TransactionType.INCOME, usd(100), account = payoneer)), // LKR 30,000
                entry(tx("t-in", TransactionType.TRANSFER_IN, transferId = "t")),
                entry(tx("adj", TransactionType.ADJUSTMENT_IN)),
                entry(tx("undone", TransactionType.INCOME, lkr(9_999)), voided = true),
                entry(tx("september", TransactionType.INCOME, lkr(7_000), date = LocalDate.of(2026, 9, 30))),
            ),
            october,
        )
        assertEquals(lkr(80_000), summary.received.amount)
        assertTrue(summary.received.isComplete)
    }

    @Test
    fun missingRatesAreReportedNotGuessed() {
        val noRate = tx("usd", TransactionType.INCOME, usd(100), account = payoneer, conversion = ReportingConversion.Missing(Currencies.LKR))
        val summary = LedgerSummaryCalculator.summarize(listOf(entry(noRate)), october)
        assertEquals(lkr(0), summary.received.amount)
        assertEquals(1, summary.received.unresolvedCount)
        assertTrue(summary.hasIncompleteData)
    }

    @Test
    fun spendingSplitsByClassAndExcludesTransfers() {
        val summary = LedgerSummaryCalculator.summarize(
            listOf(
                entry(tx("rent", TransactionType.EXPENSE, lkr(20_000), spendingClass = SpendingClass.COMMITTED)),
                entry(tx("movie", TransactionType.EXPENSE, lkr(1_500), spendingClass = SpendingClass.DISCRETIONARY)),
                entry(tx("misc", TransactionType.EXPENSE, lkr(300), spendingClass = SpendingClass.UNKNOWN)),
                entry(tx("t-out", TransactionType.TRANSFER_OUT, lkr(5_000), transferId = "t")),
            ),
            october,
        )
        assertEquals(lkr(20_000), summary.spent.committed.amount)
        assertEquals(lkr(1_500), summary.spent.discretionary.amount)
        assertEquals(lkr(300), summary.spent.unknown.amount)
        assertFalse(summary.hasIncompleteData)
    }

    @Test
    fun refundsOffsetThePurchaseCappedAndAreNotIncome() {
        val summary = LedgerSummaryCalculator.summarize(
            listOf(
                entry(tx("shoes", TransactionType.EXPENSE, lkr(4_000))),
                entry(tx("refund1", TransactionType.REFUND, lkr(3_000), refundOf = "shoes")),
                entry(tx("refund2", TransactionType.REFUND, lkr(3_000), refundOf = "shoes")),
            ),
            october,
        )
        assertEquals(lkr(0), summary.spent.discretionary.amount)
        assertEquals(lkr(0), summary.received.amount)
    }

    @Test
    fun cashCatchUpAdjustmentIsUnknownAndFlagged() {
        val summary = LedgerSummaryCalculator.summarize(listOf(entry(tx("catch-up", TransactionType.ADJUSTMENT_OUT, lkr(2_500)))), october)
        assertEquals(lkr(2_500), summary.spent.unknown.amount)
        assertEquals(lkr(0), summary.spent.discretionary.amount)
        assertEquals(1, summary.unclassifiedAdjustmentCount)
        assertTrue(summary.hasIncompleteData)
    }
}
