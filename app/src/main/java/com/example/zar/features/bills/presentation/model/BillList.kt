package com.example.zar.features.bills.presentation.model

import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.model.BillOccurrence
import com.example.zar.features.bills.domain.model.BillSummary
import com.example.zar.features.bills.domain.model.BillsOverview
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Something on a bill the user should look at (Figma Z22 "Needs a look"). */
sealed interface NeedsLook {
    val summary: BillSummary

    /** Past due dates with no payment, or only part of one, linked. [latest] is the newest. */
    data class Missed(override val summary: BillSummary, val latest: BillOccurrence, val count: Int) : NeedsLook

    /** Charges that look like a bill but arrived after it was confirmed cancelled. */
    data class ChargedAfterCancelling(override val summary: BillSummary, val latest: LedgerEntry, val count: Int) : NeedsLook
}

/** A due date still expected: this week or later. */
data class DueRow(val summary: BillSummary, val occurrence: BillOccurrence)

/** A due date with its payment matched; [paidOn] is the latest linked expense's date. */
data class PaidRow(val summary: BillSummary, val occurrence: BillOccurrence, val paidOn: LocalDate)

/** The Bills list (Z22), worked out from the overview for one day. */
data class BillList(
    val needsLook: List<NeedsLook>,
    val thisWeek: List<DueRow>,
    val later: List<DueRow>,
    val paidThisMonth: List<PaidRow>,
    /** Running bills with no row in another section. Keep early-paid and future-ending bills editable. */
    val otherRunning: List<BillSummary>,
    /** Stopped bills and bills cancelled before today. */
    val ended: List<BillSummary>,
    /** The Sunday that ends this week (today when today is Sunday). */
    val weekEnd: LocalDate,
    /** Still expected this week, one total per currency. */
    val thisWeekTotal: List<Money>,
    /** Estimated yearly cost of running bills, one total per currency. */
    val annual: List<Money>,
    val hasBills: Boolean,
    val hasRunningBills: Boolean,
) {
    companion object {
        fun from(overview: BillsOverview, today: LocalDate): BillList {
            val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val month = YearMonth.from(today)
            val summaries = overview.bills

            val needsLook = summaries.flatMap { summary ->
                val missed = summary.occurrences.filter { it.dueDate < today && it.needsPayment }
                val charges = summary.chargesAfterCancellation
                listOfNotNull(
                    missed.maxByOrNull { it.dueDate }?.let { NeedsLook.Missed(summary, it, missed.size) },
                    charges.maxByOrNull { it.current.date }?.let { NeedsLook.ChargedAfterCancelling(summary, it, charges.size) },
                )
            }

            val upcoming = summaries
                .filter { it.bill.runsOn(today) }
                .mapNotNull { summary -> summary.next?.takeIf { it.needsPayment }?.let { DueRow(summary, it) } }
                .sortedWith(compareBy<DueRow> { it.occurrence.dueDate }.thenBy { it.summary.bill.name.lowercase() })
            val (thisWeek, later) = upcoming.partition { it.occurrence.dueDate <= weekEnd }

            val paid = summaries.flatMap { summary ->
                summary.occurrences
                    .filter { it.progress.state == MatchState.MATCHED }
                    .map { PaidRow(summary, it, it.payments.maxOfOrNull { p -> p.entry.current.date } ?: it.dueDate) }
                    .filter { YearMonth.from(it.paidOn) == month }
            }.sortedByDescending { it.paidOn }

            val visibleIds = (needsLook.map { it.summary.bill.id } + upcoming.map { it.summary.bill.id } + paid.map { it.summary.bill.id }).toSet()
            val otherRunning = summaries.filter { it.bill.runsOn(today) && it.bill.id !in visibleIds }

            return BillList(
                needsLook = needsLook,
                thisWeek = thisWeek,
                later = later,
                paidThisMonth = paid,
                otherRunning = otherRunning,
                ended = summaries.filterNot { it.bill.runsOn(today) },
                weekEnd = weekEnd,
                thisWeekTotal = thisWeek.map { it.occurrence.progress.remaining }.totals(),
                annual = overview.annual,
                hasBills = summaries.isNotEmpty(),
                hasRunningBills = summaries.any { it.bill.runsOn(today) },
            )
        }

        private val BillOccurrence.needsPayment: Boolean
            get() = progress.state == MatchState.NOT_MATCHED || progress.state == MatchState.PARTIALLY_MATCHED

        private fun List<Money>.totals(): List<Money> = groupBy { it.currency }
            .map { (currency, amounts) -> amounts.fold(Money.zero(currency)) { total, next -> total + next } }
            .filter { it.isPositive }
            .sortedWith(compareBy<Money> { it.currency != Currencies.REPORTING }.thenBy { it.currency.code })
    }
}
