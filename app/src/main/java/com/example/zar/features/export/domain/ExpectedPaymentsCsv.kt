package com.example.zar.features.export.domain

import com.example.zar.core.domain.calculation.ExpectedPaymentProgressCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.PaymentMatch
import java.time.LocalDate

/**
 * Expected payments only: money that is due or owed but not confirmed. It never mixes with the
 * transactions file, so expected money cannot be mistaken for money that arrived.
 *
 * - `settlement_state` is NOT_MATCHED, PARTIALLY_MATCHED, MATCHED or CANCELLED. "Not matched"
 *   means no payment was linked, not that it is unpaid.
 * - `matched_amount`, `remaining_amount` and `overdue_amount` come from the same calculator the
 *   app uses, so receipts are capped per transaction and undone or corrected ones update them.
 * - `linked_transaction_ids` lists the transactions that count towards this row, separated by `;`.
 * - Bill due dates appear here once a payment is linked to them; later dates are worked out from
 *   the bill's schedule and are not saved records.
 */
object ExpectedPaymentsCsv {

    val HEADER = listOf(
        "expected_payment_id", "status", "settlement_state", "direction", "title",
        "amount", "currency", "matched_amount", "remaining_amount", "overdue_amount", "due_date",
        "source_type", "project_id", "milestone_id", "bill_id", "occurrence_date",
        "account_id", "account_name", "linked_transaction_ids", "created_at",
    )

    fun build(
        payments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        entries: List<LedgerEntry>,
        accounts: List<Account>,
        today: LocalDate,
    ): CsvDocument {
        val accountNames = accounts.associate { it.id to it.name }
        val progress = ExpectedPaymentProgressCalculator.calculate(payments, matches, entries, today)
            .associateBy { it.expected.id }
        val counted = ExpectedPaymentProgressCalculator.effectiveAllocations(payments, matches, entries)
        val linked = matches.distinctBy { it.id }
            .filter { it.id in counted }
            .groupBy({ it.expectedPaymentId }, { it.transactionId.value })

        val rows = payments
            .sortedWith(compareBy<ExpectedPayment>({ it.dueDate }, { it.createdAt }, { it.id.value }))
            .map { payment -> row(payment, progress.getValue(payment.id), linked[payment.id].orEmpty(), accountNames) }
        return Csv.document(HEADER, rows)
    }

    private fun row(
        payment: ExpectedPayment,
        progress: com.example.zar.core.domain.model.ExpectedPaymentProgress,
        linkedTransactions: List<String>,
        accountNames: Map<AccountId, String>,
    ): List<String?> {
        val source = payment.source
        return listOf(
            payment.id.value,
            payment.status.name,
            progress.state.name,
            payment.direction.name,
            payment.title,
            payment.amount.plain(),
            payment.amount.currency.code,
            progress.matched.plain(),
            progress.remaining.plain(),
            progress.overdue.plain(),
            payment.dueDate.toString(),
            when (source) {
                is ExpectedSource.FreelanceMilestone -> "FREELANCE_MILESTONE"
                is ExpectedSource.BillOccurrence -> "BILL_OCCURRENCE"
                ExpectedSource.QuickNote -> "QUICK_NOTE"
                ExpectedSource.Manual -> "MANUAL"
            },
            (source as? ExpectedSource.FreelanceMilestone)?.projectId,
            (source as? ExpectedSource.FreelanceMilestone)?.milestoneId,
            (source as? ExpectedSource.BillOccurrence)?.billId,
            (source as? ExpectedSource.BillOccurrence)?.occurrenceDate?.toString(),
            payment.accountId?.value,
            payment.accountId?.let { accountNames[it] },
            linkedTransactions.sorted().joinToString(";"),
            payment.createdAt.toString(),
        )
    }
}
