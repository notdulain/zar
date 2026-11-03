package com.example.zar.features.spending.domain.model

import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.SpendingKeys
import java.time.YearMonth

/** One category line inside a class. [key] is a canonical category ID or one of [SpendingBreakdownCalculator]'s markers. */
data class BreakdownRow(val key: String, val total: ReportingTotal, val entryCount: Int)

/** One spending class: its subtotal and the category rows that add up to it, largest first. */
data class ClassBreakdown(val spendingClass: SpendingClass, val subtotal: ReportingTotal, val rows: List<BreakdownRow>) {
    val entryCount: Int get() = rows.sumOf { it.entryCount }

    /**
     * [row]'s whole-number share of this class's subtotal. Null when the subtotal is zero or the row has no known amount,
     * so a missing-rate row never claims a share. Shares are of the class, never of the whole month.
     */
    fun shareOf(row: BreakdownRow): Int? {
        val subtotalMinor = subtotal.amount.minorUnits
        val rowMinor = row.total.amount.minorUnits
        if (subtotalMinor <= 0L || rowMinor <= 0L) return null
        return Math.round(rowMinor.toDouble() / subtotalMinor * 100.0).toInt().coerceIn(0, 100)
    }
}

/**
 * Where one month's spending went, by class then category. Built by the same rules as [LedgerSummaryCalculator], so the
 * class subtotals add up to the Home Spent total: refunds reduce the purchase, voids are ignored, and a missing rate is
 * counted as unresolved instead of guessed.
 */
data class SpendingBreakdownReport(
    val month: YearMonth,
    val committed: ClassBreakdown,
    val discretionary: ClassBreakdown,
    val unknown: ClassBreakdown,
) {
    val classes: List<ClassBreakdown> get() = listOf(committed, discretionary, unknown)

    val total: ReportingTotal get() = committed.subtotal + discretionary.subtotal + unknown.subtotal

    /** Entries whose class or category still needs the user's call. */
    val needsReviewCount: Int get() = unknown.entryCount

    val hasAnySpending: Boolean get() = classes.any { it.rows.isNotEmpty() }
    val hasUnresolved: Boolean get() = !total.isComplete
}

object SpendingBreakdownCalculator {
    const val UNCATEGORISED = SpendingKeys.UNCATEGORISED
    const val ADJUSTMENTS = SpendingKeys.ADJUSTMENTS

    fun calculate(
        entries: List<LedgerEntry>,
        month: YearMonth,
        catalog: ExpenseCategoryCatalog,
        reporting: Currency = Currencies.REPORTING,
    ): SpendingBreakdownReport {
        val period = month.toDateRange()
        val active = entries.active().map { it.current }
        val refundsByPurchase = active.filter { it.type == TransactionType.REFUND }.groupBy { it.refundOf!! }

        class Acc(var total: ReportingTotal, var count: Int)
        val byClass = SpendingClass.entries.associateWith { LinkedHashMap<String, Acc>() }

        for (tx in active) {
            if (tx.date !in period) continue
            val spendingClass = SpendingKeys.classOf(tx) ?: continue
            val net = when (tx.type) {
                TransactionType.EXPENSE -> LedgerSummaryCalculator.netOfRefunds(tx, refundsByPurchase, reporting)
                else -> LedgerSummaryCalculator.netOfRefunds(tx, emptyMap(), reporting)
            }
            val acc = byClass.getValue(spendingClass).getOrPut(SpendingKeys.keyOf(tx, catalog)) { Acc(ReportingTotal.zero(reporting), 0) }
            acc.total += net
            acc.count++
        }

        fun build(spendingClass: SpendingClass): ClassBreakdown {
            val rows = byClass.getValue(spendingClass).map { (key, acc) -> BreakdownRow(key, acc.total, acc.count) }
                .sortedWith(compareByDescending<BreakdownRow> { it.total.amount.minorUnits }.thenBy { it.key })
            return ClassBreakdown(spendingClass, rows.fold(ReportingTotal.zero(reporting)) { sum, row -> sum + row.total }, rows)
        }
        return SpendingBreakdownReport(month, build(SpendingClass.COMMITTED), build(SpendingClass.DISCRETIONARY), build(SpendingClass.UNKNOWN))
    }
}
