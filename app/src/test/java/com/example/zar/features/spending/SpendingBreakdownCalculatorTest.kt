package com.example.zar.features.spending

import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.spending.domain.model.SpendingBreakdownCalculator
import com.example.zar.features.spending.domain.model.SpendingBreakdownCalculator.ADJUSTMENTS
import com.example.zar.features.spending.domain.model.SpendingBreakdownCalculator.UNCATEGORISED
import com.example.zar.features.spending.domain.model.SpendingBreakdownReport
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingBreakdownCalculatorTest {
    private val october = YearMonth.of(2026, 10)

    private fun expense(id: String, major: Long, spendingClass: SpendingClass?, category: String?, date: LocalDate = OCT_10): Transaction =
        tx(id, TransactionType.EXPENSE, lkr(major), date = date, spendingClass = spendingClass ?: SpendingClass.UNKNOWN)
            .copy(categoryId = category, spendingClass = spendingClass ?: SpendingClass.UNKNOWN)

    private fun report(
        vararg txs: Transaction,
        corrections: List<Correction> = emptyList(),
        voids: List<VoidMarker> = emptyList(),
        catalog: ExpenseCategoryCatalog = ExpenseCategoryCatalog.BUILT_IN_ONLY,
    ): Pair<SpendingBreakdownReport, com.example.zar.core.domain.calculation.PeriodSummary> {
        val entries = LedgerProjector.project(txs.toList(), corrections, voids)
        return SpendingBreakdownCalculator.calculate(entries, october, catalog) to
            LedgerSummaryCalculator.summarize(entries, october.toDateRange())
    }

    @Test
    fun groupsByClassThenCategoryLargestFirstWithSharesOfTheClassSubtotal() {
        val (r, summary) = report(
            expense("rent", 30_000, SpendingClass.COMMITTED, "rent"),
            expense("bills1", 6_000, SpendingClass.COMMITTED, "bills"),
            expense("bills2", 4_000, SpendingClass.COMMITTED, "bills"),
            expense("food", 8_000, SpendingClass.DISCRETIONARY, "groceries"),
        )
        assertEquals(listOf("rent", "bills"), r.committed.rows.map { it.key })
        assertEquals(lkr(10_000), r.committed.rows[1].total.amount)
        assertEquals(2, r.committed.rows[1].entryCount)
        assertEquals(lkr(40_000), r.committed.subtotal.amount)
        assertEquals(75, r.committed.shareOf(r.committed.rows[0]))
        assertEquals(25, r.committed.shareOf(r.committed.rows[1]))
        assertEquals(100, r.discretionary.shareOf(r.discretionary.rows[0]))
        assertEquals(summary.spent.total, r.total)
        assertEquals(summary.spent.committed, r.committed.subtotal)
        assertEquals(summary.spent.discretionary, r.discretionary.subtotal)
    }

    @Test
    fun rowsReconcileToEachClassSubtotalAfterRefundsFeesAdjustmentsCorrectionsAndVoids() {
        val refund = tx("refund", TransactionType.REFUND, lkr(1_000), refundOf = "coffee")
        val (r, summary) = report(
            expense("coffee", 3_000, SpendingClass.DISCRETIONARY, "coffee-dining"),
            refund,
            expense("fee", 150, SpendingClass.COMMITTED, "transfer-fee"),
            tx("cash", TransactionType.ADJUSTMENT_OUT, lkr(500)),
            expense("fixed", 900, SpendingClass.DISCRETIONARY, "groceries"),
            expense("gone", 7_000, SpendingClass.COMMITTED, "rent"),
            expense("outside", 4_000, SpendingClass.COMMITTED, "rent", date = LocalDate.of(2026, 9, 30)),
            corrections = listOf(TestLedger.correction("c", "fixed", TransactionChanges(amount = lkr(1_200), reportingConversion = ReportingConversion.SameCurrency))),
            voids = listOf(TestLedger.void("gone")),
        )
        // The refund lowers the coffee line, the correction replaces the grocery amount, the void and September drop out.
        assertEquals(lkr(2_000), r.discretionary.rows.first { it.key == "coffee-dining" }.total.amount)
        assertEquals(lkr(1_200), r.discretionary.rows.first { it.key == "groceries" }.total.amount)
        assertEquals(lkr(500), r.unknown.rows.single { it.key == ADJUSTMENTS }.total.amount)
        for (cls in r.classes) assertEquals(cls.subtotal, cls.rows.fold(com.example.zar.core.domain.calculation.ReportingTotal.zero()) { s, row -> s + row.total })
        assertEquals(summary.spent.total, r.total)
        assertEquals(summary.spent.unknown, r.unknown.subtotal)
        assertEquals(1, r.needsReviewCount)
    }

    @Test
    fun uncategorisedAndUnknownClassLandInUnknownWithTheReviewCount() {
        val (r, _) = report(
            expense("a", 100, SpendingClass.UNKNOWN, null),
            expense("b", 200, SpendingClass.UNKNOWN, "groceries"),
            expense("c", 300, SpendingClass.DISCRETIONARY, null),
        )
        assertEquals(setOf(UNCATEGORISED, "groceries"), r.unknown.rows.map { it.key }.toSet())
        assertEquals(2, r.needsReviewCount)
        assertEquals(UNCATEGORISED, r.discretionary.rows.single().key)
    }

    @Test
    fun customCategoriesGroupByTheirCanonicalIdAndDuplicatesMerge() {
        val first = CustomExpenseCategory("custom-1", "Gym", "gym", Instant.parse("2026-01-01T00:00:00Z"))
        val dupe = CustomExpenseCategory("custom-2", "gym", "gym", Instant.parse("2026-02-01T00:00:00Z"))
        val catalog = ExpenseCategoryCatalog(stored = listOf(first, dupe))
        val (r, _) = report(
            expense("a", 1_000, SpendingClass.DISCRETIONARY, "custom-1"),
            expense("b", 500, SpendingClass.DISCRETIONARY, "custom-2"),
            catalog = catalog,
        )
        val row = r.discretionary.rows.single()
        assertEquals("custom-1", row.key)
        assertEquals(lkr(1_500), row.total.amount)
        assertEquals(2, row.entryCount)
    }

    @Test
    fun missingRatesStayUnresolvedAndNeverClaimAShare() {
        val noRate = tx("noRate", TransactionType.EXPENSE, usd(10), account = TestLedger.payoneer, conversion = ReportingConversion.Missing(Currencies.LKR))
            .copy(categoryId = "groceries", spendingClass = SpendingClass.DISCRETIONARY)
        val (r, summary) = report(noRate, expense("ok", 400, SpendingClass.DISCRETIONARY, "bills"))
        val row = r.discretionary.rows.first { it.key == "groceries" }
        assertEquals(1, row.total.unresolvedCount)
        assertNull(r.discretionary.shareOf(row))
        assertTrue(r.hasUnresolved)
        assertEquals(summary.spent.total, r.total)
    }

    @Test
    fun emptyMonthHasNoRowsAndSafeShares() {
        val (r, _) = report()
        assertFalse(r.hasAnySpending)
        assertTrue(r.classes.all { it.rows.isEmpty() && it.subtotal.amount.isZero })
        assertEquals(0, r.needsReviewCount)
    }
}
