package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivityList
import com.example.zar.features.transactions.domain.activity.model.ActivityRow
import com.example.zar.features.transactions.domain.activity.model.ActivitySnapshot
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import java.time.LocalDate
import java.time.YearMonth

/** Pure rules for what the Activity list shows. Dates are calendar days (Asia/Colombo), passed in as [today]. */
object ActivityQuery {

    /** Newest day first, then newest record first. The ID makes the order stable. */
    private val newestFirst = compareByDescending<LedgerEntry> { it.current.date }
        .thenByDescending { it.original.createdAt }
        .thenBy { it.id.value }

    fun rangeOf(filter: ActivityFilter, today: LocalDate): ClosedRange<LocalDate> = when (filter.preset) {
        DatePreset.THIS_MONTH -> YearMonth.from(today).toDateRange()
        DatePreset.LAST_MONTH -> YearMonth.from(today).minusMonths(1).toDateRange()
        DatePreset.LAST_7_DAYS -> today.minusDays(6)..today
        DatePreset.CUSTOM -> {
            val from = filter.customFrom
            val to = filter.customTo
            // An unfinished custom range falls back to this month instead of showing nothing.
            if (from == null || to == null) YearMonth.from(today).toDateRange() else minOf(from, to)..maxOf(from, to)
        }
    }

    fun apply(snapshot: ActivitySnapshot, filter: ActivityFilter, today: LocalDate): ActivityList {
        val range = rangeOf(filter, today)
        val accounts = snapshot.accounts.associateBy { it.id }
        val onAccount = snapshot.entries.filter { filter.accountId == null || it.current.accountId == filter.accountId }
        val needle = filter.query.trim().lowercase()

        val rows = onAccount
            .filter { it.current.date in range }
            .filter { filter.showVoided || !it.isVoided }
            .filter { needle.isEmpty() || matches(it, accounts[it.current.accountId]?.name, needle) }
            .sortedWith(newestFirst)
            .map { ActivityRow(it, accounts[it.current.accountId]) }

        return ActivityList(
            range = range,
            rows = rows,
            summary = LedgerSummaryCalculator.summarize(onAccount, range),
            hasAnyEntries = snapshot.entries.isNotEmpty(),
        )
    }

    /**
     * Case-insensitive match on what a row shows: the title, description, category, account name and the kind of
     * entry ("Transfer in" has no title of its own, so people search for it by that name).
     */
    private fun matches(entry: LedgerEntry, accountName: String?, needle: String): Boolean {
        val tx = entry.current
        val category = tx.categoryId?.replace('-', ' ')
        return listOfNotNull(tx.merchant, tx.note, category, accountName, kindWords(tx.type))
            .any { it.lowercase().contains(needle) }
    }

    private fun kindWords(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "income"
        TransactionType.EXPENSE -> "expense"
        TransactionType.TRANSFER_IN -> "transfer in"
        TransactionType.TRANSFER_OUT -> "transfer out"
        TransactionType.REFUND -> "refund"
        TransactionType.ADJUSTMENT_IN -> "adjustment in"
        TransactionType.ADJUSTMENT_OUT -> "adjustment out"
    }
}
