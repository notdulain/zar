package com.example.zar.features.home.domain.model

import com.example.zar.core.domain.calculation.ReportingTotal
import java.time.LocalDate
import java.time.YearMonth

/** Received income for one calendar month. [isPartial] is true while that month is still in progress. */
data class MonthlyIncomePoint(val month: YearMonth, val received: ReportingTotal, val isPartial: Boolean)

/**
 * The six-month Received window ending at the selected month, oldest first. These are monthly totals; they never reuse
 * the one-month cumulative daily bars. Nothing is forecast: an in-progress month is flagged partial and a month with
 * unconvertible income is flagged unresolved rather than counted as confirmed zero.
 */
data class MonthlyIncomeTrend(val points: List<MonthlyIncomePoint>) {
    val latest: MonthlyIncomePoint? get() = points.lastOrNull()
    val previous: MonthlyIncomePoint? get() = points.getOrNull(points.lastIndex - 1)

    /** Mean of every month's known total, in minor units, or null when there are no points. */
    val averageMinor: Long?
        get() = if (points.isEmpty()) null else {
            points.fold(java.math.BigInteger.ZERO) { sum, point -> sum + point.received.amount.minorUnits.toBigInteger() }
                .divide(points.size.toBigInteger()).toLong()
        }

    /** Latest month minus the month before it, in minor units, or null when there is no earlier month. */
    val deltaMinor: Long?
        get() {
            val last = latest ?: return null
            val before = previous ?: return null
            return Math.subtractExact(last.received.amount.minorUnits, before.received.amount.minorUnits)
        }

    val hasPartialMonth: Boolean get() = points.any { it.isPartial }
    val hasUnresolvedMonth: Boolean get() = points.any { !it.received.isComplete }

    /** Largest known monthly total, never below one so scaling cannot divide by zero. */
    val maximumMinor: Long get() = (points.maxOfOrNull { it.received.amount.minorUnits } ?: 1L).coerceAtLeast(1L)

    companion object {
        const val WINDOW = 6

        /** [totals] are the monthly totals for the [WINDOW] months ending at [selected], oldest first. */
        fun from(selected: YearMonth, totals: List<ReportingTotal>, today: LocalDate): MonthlyIncomeTrend {
            val current = YearMonth.from(today)
            val start = selected.minusMonths((totals.size - 1).toLong())
            return MonthlyIncomeTrend(
                totals.mapIndexed { index, total ->
                    val month = start.plusMonths(index.toLong())
                    MonthlyIncomePoint(month, total, isPartial = month == current)
                },
            )
        }
    }
}
