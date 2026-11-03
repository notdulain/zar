package com.example.zar.features.home.domain.model

import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.LedgerEntry
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything Home shows for one month, built only from saved records.
 *
 * [summary] holds Received (income only) and Spent split into committed, discretionary and
 * unknown. Expected payments, drafts and transfers are excluded; refunds reduce spending. [recent] is the
 * latest activity overall, not limited to [month].
 */
data class HomeOverview(
    val month: YearMonth,
    val summary: PeriodSummary,
    val recent: List<LedgerEntry>,
    val hasRecordsInMonth: Boolean,
    /** One income total per calendar day, starting on day 1; missing rates remain unresolved. */
    val receivedByDay: List<ReportingTotal> = emptyList(),
) {
    /** Cumulative Received from day 1, one slot per calendar day; days after [today] are placeholders. */
    fun cumulativeIncome(today: LocalDate): CumulativeIncomeSeries =
        CumulativeIncomeSeries.from(month, receivedByDay, today)

    /** Spent / For plans / Still free of this month's Received. */
    val donut: HomeDonut get() = HomeDonut.from(summary)

    /** Records in this month that could not be converted to LKR and so are missing from totals. */
    val missingRateCount: Int
        get() = summary.received.unresolvedCount + summary.spent.total.unresolvedCount

    /** Cash catch-up or reconciliation adjustments in either direction. */
    val adjustmentCount: Int get() = summary.unclassifiedAdjustmentCount

    val hasUnknownSpending: Boolean get() = summary.spent.unknown.amount.isPositive

    /** True when any total may be missing something the user should look at. */
    val hasIncompleteData: Boolean get() = summary.hasIncompleteData || hasUnknownSpending
}
