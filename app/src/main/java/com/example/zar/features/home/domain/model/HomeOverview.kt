package com.example.zar.features.home.domain.model

import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.model.LedgerEntry
import java.time.YearMonth

/**
 * Everything Home shows for one month, built only from saved records.
 *
 * [summary] holds Received (income only) and Spent split into committed, discretionary and
 * unknown. Expected payments, drafts, transfers and refunds never reach it. [recent] is the
 * latest activity overall, not limited to [month].
 */
data class HomeOverview(
    val month: YearMonth,
    val summary: PeriodSummary,
    val recent: List<LedgerEntry>,
    val hasRecordsInMonth: Boolean,
) {
    /** Records in this month that could not be converted to LKR and so are missing from totals. */
    val missingRateCount: Int
        get() = summary.received.unresolvedCount + summary.spent.total.unresolvedCount

    /** Cash catch-up or reconciliation outflows, counted as unknown spending. */
    val adjustmentCount: Int get() = summary.unclassifiedAdjustmentCount

    val hasUnknownSpending: Boolean get() = summary.spent.unknown.amount.isPositive

    /** True when any total may be missing something the user should look at. */
    val hasIncompleteData: Boolean get() = summary.hasIncompleteData || hasUnknownSpending
}
