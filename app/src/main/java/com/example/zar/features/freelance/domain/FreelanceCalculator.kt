package com.example.zar.features.freelance.domain

import com.example.zar.core.domain.calculation.ExpectedPaymentProgressCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.freelance.domain.model.FreelanceOverview
import com.example.zar.features.freelance.domain.model.LinkedReceipt
import com.example.zar.features.freelance.domain.model.MilestoneSummary
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectSummary
import com.example.zar.features.freelance.domain.model.ReceiptCandidate
import java.time.LocalDate

/**
 * Freelance totals built on the shared expected-payment rules: receipts are capped per
 * transaction, undone receipts drop out and corrected amounts apply automatically, because
 * everything is recomputed from the current ledger.
 */
object FreelanceCalculator {

    fun overview(
        projects: List<Project>,
        expectedPayments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        entries: List<LedgerEntry>,
        today: LocalDate,
    ): FreelanceOverview {
        val progressById = ExpectedPaymentProgressCalculator.calculate(expectedPayments, matches, entries, today)
            .associateBy { it.expected.id }
        val effective = ExpectedPaymentProgressCalculator.effectiveAllocations(expectedPayments, matches, entries)
        val entriesById = entries.associateBy { it.id }
        val matchesByMilestone = matches.distinctBy { it.id }.groupBy { it.expectedPaymentId }
        val milestonesByProject = expectedPayments
            .filter { it.direction == Direction.IN }
            .mapNotNull { payment -> (payment.source as? ExpectedSource.FreelanceMilestone)?.let { it.projectId to payment } }
            .groupBy({ it.first }, { it.second })

        val summaries = projects.map { project ->
            val milestones = milestonesByProject[project.id.value].orEmpty()
                .filter { it.amount.currency == project.currency }
                .sortedWith(compareBy<ExpectedPayment>({ it.dueDate }, { it.createdAt }))
                .mapNotNull { milestone ->
                    val progress = progressById[milestone.id] ?: return@mapNotNull null
                    val receipts = matchesByMilestone[milestone.id].orEmpty().mapNotNull { match ->
                        val counted = effective[match.id] ?: return@mapNotNull null
                        val entry = entriesById[match.transactionId] ?: return@mapNotNull null
                        LinkedReceipt(match, entry, counted)
                    }.sortedBy { it.entry.current.date }
                    MilestoneSummary(progress, receipts)
                }
            val counted = milestones.filter { it.progress.state != MatchState.CANCELLED }
            ProjectSummary(
                project = project,
                milestones = milestones,
                expected = counted.map { it.milestone.amount }.sum(project.currency),
                received = counted.map { it.progress.matched }.sum(project.currency),
                remaining = counted.map { it.progress.remaining }.sum(project.currency),
                overdue = counted.map { it.progress.overdue }.sum(project.currency),
            )
        }.sortedWith(compareBy<ProjectSummary> { it.project.isArchived }.thenByDescending { it.project.createdAt })

        val active = summaries.filterNot { it.project.isArchived }
        return FreelanceOverview(
            projects = summaries,
            outstanding = Outstanding(
                owed = active.map { it.remaining }.totalsByCurrency(),
                overdue = active.map { it.overdue }.totalsByCurrency(),
                projectsWithBalance = active.count { it.remaining.isPositive },
            ),
        )
    }

    /**
     * Active income in the milestone's currency that still has an unlinked amount and is not
     * already linked to this milestone. Newest first.
     */
    fun receiptCandidates(
        milestone: ExpectedPayment,
        expectedPayments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        entries: List<LedgerEntry>,
    ): List<ReceiptCandidate> {
        val effective = ExpectedPaymentProgressCalculator.effectiveAllocations(expectedPayments, matches, entries)
        val uniqueMatches = matches.distinctBy { it.id }
        val usedByTransaction = uniqueMatches.groupBy { it.transactionId }
        val alreadyLinked = uniqueMatches.filter { it.expectedPaymentId == milestone.id }.map { it.transactionId }.toSet()
        return entries.active()
            .filter { it.current.type == TransactionType.INCOME }
            .filter { it.current.amount.currency == milestone.amount.currency && it.id !in alreadyLinked }
            .map { entry ->
                val used = usedByTransaction[entry.id].orEmpty().mapNotNull { effective[it.id] }.sum(entry.current.amount.currency)
                ReceiptCandidate(entry, entry.current.amount - used)
            }
            .filter { it.available.isPositive }
    }

    private fun List<Money>.sum(currency: Currency): Money = fold(Money.zero(currency)) { total, next -> total + next }

    /** Non-zero totals per currency, reporting currency first. */
    private fun List<Money>.totalsByCurrency(): List<Money> = groupBy { it.currency }
        .map { (currency, amounts) -> amounts.sum(currency) }
        .filter { it.isPositive }
        .sortedWith(compareBy<Money> { it.currency != Currencies.REPORTING }.thenBy { it.currency.code })
}
