package com.example.zar.features.bills.domain

import com.example.zar.core.domain.calculation.ExpectedPaymentProgressCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillOccurrence
import com.example.zar.features.bills.domain.model.BillSummary
import com.example.zar.features.bills.domain.model.BillsOverview
import com.example.zar.features.bills.domain.model.LinkedPayment
import com.example.zar.features.bills.domain.model.NextBill
import com.example.zar.features.bills.domain.model.PaymentCandidate
import java.time.LocalDate
import kotlin.math.abs

/**
 * Bills built on the shared expected-payment rules. Due dates are worked out from each
 * schedule; a due date is only saved once a payment is linked to it. Payments are capped per
 * expense, so one payment is never counted twice, and undone or corrected expenses update the
 * result automatically.
 */
object BillsCalculator {

    /** How far back unmatched due dates stay visible. */
    const val HISTORY_DAYS = 92L
    const val DUE_SOON_DAYS = 30L

    /** Expenses this many days either side of a due date are offered as its payment. */
    const val MATCH_WINDOW_DAYS = 31L

    fun overview(
        bills: List<Bill>,
        accounts: List<Account>,
        expectedPayments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        entries: List<LedgerEntry>,
        today: LocalDate,
    ): BillsOverview {
        val all = withVirtualOccurrences(bills, expectedPayments, today)
        val progressById = ExpectedPaymentProgressCalculator.calculate(all, matches, entries, today).associateBy { it.expected.id }
        val effective = ExpectedPaymentProgressCalculator.effectiveAllocations(all, matches, entries)
        val entriesById = entries.associateBy { it.id }
        val matchesByExpected = matches.distinctBy { it.id }.groupBy { it.expectedPaymentId }
        val accountsById = accounts.associateBy { it.id }
        val occurrencesByBill = all.mapNotNull { payment ->
            (payment.source as? ExpectedSource.BillOccurrence)?.let { it.billId to payment }
        }.groupBy({ it.first }, { it.second })

        val summaries = bills.map { bill ->
            val occurrences = occurrencesByBill[bill.id.value].orEmpty()
                .mapNotNull { payment ->
                    val progress = progressById[payment.id] ?: return@mapNotNull null
                    val payments = matchesByExpected[payment.id].orEmpty().mapNotNull { match ->
                        val counted = effective[match.id] ?: return@mapNotNull null
                        val entry = entriesById[match.transactionId] ?: return@mapNotNull null
                        LinkedPayment(match, entry, counted)
                    }
                    BillOccurrence(progress, payments)
                }
                .sortedByDescending { it.dueDate }
            BillSummary(
                bill = bill,
                account = accountsById[bill.accountId],
                annualCost = BillSchedule.annualCost(bill),
                occurrences = occurrences,
                next = if (bill.isActive) occurrences.filter { it.dueDate >= today }.minByOrNull { it.dueDate } else null,
            )
        }.sortedWith(
            compareBy<BillSummary> { !it.bill.isActive }
                .thenBy { it.next?.dueDate ?: LocalDate.MAX }
                .thenBy { it.bill.name.lowercase() },
        )

        val next = summaries.mapNotNull { summary -> summary.next?.let { summary.bill to it } }
            .minByOrNull { it.second.dueDate }
            ?.let { (bill, occurrence) -> NextBill(bill.id, bill.name, occurrence.dueDate, occurrence.progress.expected.amount) }
        val soonEnd = today.plusDays(DUE_SOON_DAYS)
        return BillsOverview(
            bills = summaries,
            next = next,
            dueSoon = summaries.filter { it.bill.isActive }
                .flatMap { stillDue(it, today, soonEnd) }
                .totalsByCurrency(),
            annual = summaries.filter { it.bill.isActive }.map { it.annualCost }.totalsByCurrency(),
        )
    }

    /**
     * Saved expected payments plus the bills' recent and next due dates that aren't saved yet.
     * A saved due date (one with a linked payment) always wins over the worked-out one.
     */
    fun withVirtualOccurrences(bills: List<Bill>, saved: List<ExpectedPayment>, today: LocalDate): List<ExpectedPayment> {
        val savedIds = saved.map { it.id }.toSet()
        val virtual = bills.flatMap { bill ->
            val from = maxOf(bill.firstDue, today.minusDays(HISTORY_DAYS))
            val to = if (bill.isActive) BillSchedule.nextDueDate(bill, today) else today.minusDays(1)
            BillSchedule.dueDates(bill, from, to)
                .map { BillSchedule.expectedPayment(bill, it) }
                .filterNot { it.id in savedIds }
        }
        return saved + virtual
    }

    /**
     * Active expenses in the due date's currency that still have an unlinked amount, are not
     * already linked to it, and fall near its date. Closest first.
     */
    fun paymentCandidates(
        occurrence: ExpectedPayment,
        expectedPayments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        entries: List<LedgerEntry>,
    ): List<PaymentCandidate> {
        val all = if (expectedPayments.any { it.id == occurrence.id }) expectedPayments else expectedPayments + occurrence
        val effective = ExpectedPaymentProgressCalculator.effectiveAllocations(all, matches, entries)
        val unique = matches.distinctBy { it.id }
        val usedByTransaction = unique.groupBy { it.transactionId }
        val alreadyLinked = unique.filter { it.expectedPaymentId == occurrence.id }.map { it.transactionId }.toSet()
        val window = occurrence.dueDate.minusDays(MATCH_WINDOW_DAYS)..occurrence.dueDate.plusDays(MATCH_WINDOW_DAYS)
        return entries.active()
            .filter { it.current.type == TransactionType.EXPENSE && it.current.amount.currency == occurrence.amount.currency }
            .filter { it.id !in alreadyLinked && it.current.date in window }
            .map { entry ->
                val used = usedByTransaction[entry.id].orEmpty().mapNotNull { effective[it.id] }
                    .fold(Money.zero(entry.current.amount.currency)) { total, next -> total + next }
                PaymentCandidate(entry, entry.current.amount - used)
            }
            .filter { it.available.isPositive }
            .sortedWith(
                compareBy<PaymentCandidate> { abs(it.entry.current.date.toEpochDay() - occurrence.dueDate.toEpochDay()) }
                    .thenBy { if (it.entry.current.accountId == occurrence.accountId) 0 else 1 },
            )
    }

    /**
     * What a bill still expects in [today, end]. Only the next due date is listed, so a weekly
     * bill's later dates in the window are worked out from the schedule at the full amount.
     */
    private fun stillDue(summary: BillSummary, today: LocalDate, end: LocalDate): List<Money> {
        val listed = summary.occurrences.filter { it.dueDate in today..end }.associateBy { it.progress.expected.id }
        val scheduled = BillSchedule.dueDates(summary.bill, today, end).map { BillSchedule.occurrenceId(summary.bill, it) }
        return (listed.keys + scheduled).mapNotNull { id ->
            val progress = listed[id]?.progress ?: return@mapNotNull summary.bill.amount
            progress.remaining.takeUnless { progress.state == MatchState.CANCELLED }
        }
    }

    private fun List<Money>.totalsByCurrency(): List<Money> = groupBy { it.currency }
        .map { (currency, amounts) -> amounts.fold(Money.zero(currency)) { total, next -> total + next } }
        .filter { it.isPositive }
        .sortedWith(compareBy<Money> { it.currency != Currencies.REPORTING }.thenBy { it.currency.code })
}
