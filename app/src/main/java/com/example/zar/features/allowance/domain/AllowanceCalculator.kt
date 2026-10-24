package com.example.zar.features.allowance.domain

import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.calculation.weekContaining
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceStatus
import java.time.LocalDate

/**
 * This week's allowance from saved records. Only discretionary spending counts, so the rules
 * follow from what is left out:
 *
 * - Bills: an expense linked to a bill due date is a confirmed bill, never allowance spending.
 * - Committed spending, including transfer fees, is not discretionary.
 * - Transfers and goal allocations never reach the ledger as spending, so they never count.
 * - Cash catch-up and reconciliation outflows are not counted; they are reported as coverage.
 * - A refund reduces the original purchase, capped at that purchase, in the purchase's week.
 * - Income never increases the allowance. There is no rollover.
 * - Expenses with no class yet are left out and reported, rather than guessed.
 * - Unvalued expenses are omitted. An unvalued refund leaves the known purchase gross in place,
 *   with a warning that its net total is incomplete.
 */
object AllowanceCalculator {

    fun status(
        settings: AllowanceSettings,
        entries: List<LedgerEntry>,
        expectedPayments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        today: LocalDate,
    ): AllowanceStatus? {
        val allowance = settings.amount?.takeIf { settings.isEnabled } ?: return null
        val week = weekContaining(today, settings.weekStart)
        val current = entries.active().map { it.current }
        val refundsByPurchase = current.filter { it.type == TransactionType.REFUND }.groupBy { it.refundOf!! }
        val billPayments = billPaymentIds(expectedPayments, matches)

        var spent = ReportingTotal.zero()
        var unclassified = 0
        var adjustments = 0
        for (tx in current) {
            if (tx.date !in week) continue
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    if (tx.id in billPayments) continue
                    if (settings.categoryIds.isNotEmpty() && tx.categoryId !in settings.categoryIds) continue
                    when (tx.spendingClass) {
                        SpendingClass.DISCRETIONARY -> spent += LedgerSummaryCalculator.netOfRefunds(tx, refundsByPurchase)
                        SpendingClass.UNKNOWN, null -> unclassified++
                        SpendingClass.COMMITTED -> Unit
                    }
                }
                TransactionType.ADJUSTMENT_OUT -> adjustments++
                else -> Unit
            }
        }
        return AllowanceStatus(week, allowance, spent, unclassified, adjustments)
    }

    /** Transactions the user has linked to a bill's due date. */
    private fun billPaymentIds(expectedPayments: List<ExpectedPayment>, matches: List<PaymentMatch>): Set<TransactionId> {
        val billDueDates = expectedPayments
            .filter { it.source is ExpectedSource.BillOccurrence }
            .map { it.id }
            .toSet()
        return matches.filter { it.expectedPaymentId in billDueDates }.map { it.transactionId }.toSet()
    }
}
