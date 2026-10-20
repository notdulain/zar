package com.example.zar.features.home

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.domain.usecase.ObserveHomeOverviewUseCase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveHomeOverviewUseCaseTest {

    private val october = YearMonth.of(2026, 10)

    private fun compute(
        vararg transactions: Transaction,
        corrections: List<Correction> = emptyList(),
        voids: List<VoidMarker> = emptyList(),
        month: YearMonth = october,
    ): HomeOverview {
        var result: HomeOverview? = null
        runTest {
            val ledger = StaticLedgerRepository(LedgerProjector.project(transactions.toList(), corrections, voids))
            result = ObserveHomeOverviewUseCase(ledger)(month).first()
        }
        return result!!
    }

    @Test
    fun receivedCountsOnlyIncome() {
        val o = compute(
            tx("salary", TransactionType.INCOME, lkr(50_000)),
            tx("refund", TransactionType.REFUND, lkr(1_000), refundOf = "shop"),
            tx("shop", TransactionType.EXPENSE, lkr(3_000)),
            tx("out", TransactionType.TRANSFER_OUT, lkr(5_000), transferId = "t1"),
            tx("in", TransactionType.TRANSFER_IN, lkr(5_000), account = TestLedger.cash, transferId = "t1"),
            tx("adj", TransactionType.ADJUSTMENT_IN, lkr(700)),
        )
        assertEquals(lkr(50_000), o.summary.received.amount)
    }

    @Test
    fun spentIsSplitIntoCommittedDiscretionaryAndUnknown() {
        val o = compute(
            tx("rent", TransactionType.EXPENSE, lkr(30_000), spendingClass = SpendingClass.COMMITTED),
            tx("coffee", TransactionType.EXPENSE, lkr(800), spendingClass = SpendingClass.DISCRETIONARY),
            tx("mystery", TransactionType.EXPENSE, lkr(1_200), spendingClass = SpendingClass.UNKNOWN),
        )
        assertEquals(lkr(30_000), o.summary.spent.committed.amount)
        assertEquals(lkr(800), o.summary.spent.discretionary.amount)
        assertEquals(lkr(1_200), o.summary.spent.unknown.amount)
        assertEquals(lkr(32_000), o.summary.spent.total.amount)
        assertTrue(o.hasUnknownSpending)
        assertTrue(o.hasIncompleteData)
    }

    @Test
    fun refundReducesTheSpendingOfThePurchase() {
        val o = compute(
            tx("shop", TransactionType.EXPENSE, lkr(3_000)),
            tx("refund", TransactionType.REFUND, lkr(1_000), refundOf = "shop"),
        )
        assertEquals(lkr(2_000), o.summary.spent.total.amount)
    }

    @Test
    fun correctionsAndUndoUpdateTheTotals() {
        val o = compute(
            tx("a", TransactionType.INCOME, lkr(10_000)),
            tx("b", TransactionType.INCOME, lkr(4_000)),
            tx("c", TransactionType.INCOME, lkr(999)),
            corrections = listOf(TestLedger.correction("fix", "a", TransactionChanges(amount = lkr(12_000)))),
            voids = listOf(TestLedger.void("c")),
        )
        assertEquals(lkr(16_000), o.summary.received.amount)
    }

    @Test
    fun missingRateIsCountedNotGuessed() {
        val o = compute(
            tx("paid", TransactionType.INCOME, usd(100), account = TestLedger.payoneer, conversion = ReportingConversion.Missing(Currencies.LKR)),
            tx("ok", TransactionType.INCOME, lkr(1_000)),
        )
        assertEquals(lkr(1_000), o.summary.received.amount)
        assertEquals(1, o.summary.received.unresolvedCount)
        assertEquals(1, o.missingRateCount)
        assertTrue(o.hasIncompleteData)
    }

    @Test
    fun cashCatchUpCountsAsUnknownAndIsFlagged() {
        val o = compute(tx("catch", TransactionType.ADJUSTMENT_OUT, lkr(2_500)))
        assertEquals(lkr(2_500), o.summary.spent.unknown.amount)
        assertEquals(1, o.adjustmentCount)
        assertTrue(o.hasIncompleteData)
    }

    @Test
    fun otherMonthsAreExcludedButRecentActivityIsNot() {
        val o = compute(
            tx("sep", TransactionType.INCOME, lkr(9_000), date = LocalDate.of(2026, 9, 20)),
            tx("oct", TransactionType.INCOME, lkr(1_000), date = OCT_10),
        )
        assertEquals(lkr(1_000), o.summary.received.amount)
        assertEquals(2, o.recent.size)
    }

    @Test
    fun recentActivityIsTheNewestThreeAndSkipsUndoneEntries() {
        val o = compute(
            tx("1", TransactionType.INCOME, date = LocalDate.of(2026, 10, 1)),
            tx("2", TransactionType.INCOME, date = LocalDate.of(2026, 10, 2)),
            tx("3", TransactionType.INCOME, date = LocalDate.of(2026, 10, 3)),
            tx("4", TransactionType.INCOME, date = LocalDate.of(2026, 10, 4)),
            tx("5", TransactionType.INCOME, date = LocalDate.of(2026, 10, 5)),
            voids = listOf(TestLedger.void("5")),
        )
        assertEquals(listOf("4", "3", "2"), o.recent.map { it.id.value })
    }

    @Test
    fun emptyMonthHasNoRecordsAndNoWarnings() {
        val o = compute()
        assertFalse(o.hasRecordsInMonth)
        assertFalse(o.hasIncompleteData)
        assertTrue(o.summary.received.amount.isZero)
    }

    @Test
    fun overviewUpdatesWhenTheLedgerChanges() = runTest {
        val ledger = StaticLedgerRepository()
        val useCase = ObserveHomeOverviewUseCase(ledger)
        assertTrue(useCase(october).first().summary.received.amount.isZero)
        ledger.entries.value = LedgerProjector.project(listOf(tx("new", TransactionType.INCOME, lkr(2_000))), emptyList(), emptyList())
        assertEquals(lkr(2_000), useCase(october).first().summary.received.amount)
    }
    @Test
    fun dailyIncomeMatchesTheMonthAfterCorrectionsUndoAndMissingRates() {
        val o = compute(
            tx("paid", TransactionType.INCOME, lkr(1_000), date = october.atDay(1)),
            tx("fixed", TransactionType.INCOME, lkr(400), date = october.atDay(2)),
            tx("voided", TransactionType.INCOME, lkr(999), date = october.atDay(3)),
            tx("no-rate", TransactionType.INCOME, usd(10), account = TestLedger.payoneer,
                conversion = ReportingConversion.Missing(Currencies.LKR), date = october.atDay(4)),
            tx("expense", TransactionType.EXPENSE, lkr(500), date = october.atDay(5)),
            tx("other-month", TransactionType.INCOME, lkr(999), date = october.minusMonths(1).atEndOfMonth()),
            corrections = listOf(TestLedger.correction("move", "fixed", TransactionChanges(amount = lkr(600), date = october.atDay(10)))),
            voids = listOf(TestLedger.void("voided")),
        )
        assertEquals(31, o.receivedByDay.size)
        assertEquals(lkr(1_000), o.receivedByDay[0].amount)
        assertTrue(o.receivedByDay[1].amount.isZero)
        assertTrue(o.receivedByDay[2].amount.isZero)
        assertEquals(1, o.receivedByDay[3].unresolvedCount)
        assertTrue(o.receivedByDay[4].amount.isZero)
        assertEquals(lkr(600), o.receivedByDay[9].amount)
        assertEquals(o.summary.received, o.receivedByDay.fold(com.example.zar.core.domain.calculation.ReportingTotal.zero()) { total, day -> total + day })
    }

    @Test
    fun expenseOnlyMonthsHaveNoIncomeBarsAndRespectLeapYearLength() {
        val february = YearMonth.of(2028, 2)
        val o = compute(tx("expense", TransactionType.EXPENSE, lkr(500), date = february.atDay(29)), month = february)
        assertTrue(o.hasRecordsInMonth)
        assertEquals(29, o.receivedByDay.size)
        assertTrue(o.receivedByDay.all { it.amount.isZero && it.isComplete })
    }

    @Test
    fun incomingAdjustmentsWarnWithoutBecomingSpending() {
        val o = compute(tx("cash-found", TransactionType.ADJUSTMENT_IN, lkr(800)))
        assertEquals(1, o.adjustmentCount)
        assertTrue(o.summary.spent.total.amount.isZero)
        assertTrue(o.hasIncompleteData)
    }

}
