package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.FiatMoney
import java.time.LocalDate

/** A total in the reporting currency plus how many records could not be converted. */
data class ReportingTotal(val amount: FiatMoney, val unresolvedCount: Int = 0) {
    val isComplete: Boolean get() = unresolvedCount == 0

    operator fun plus(other: ReportingTotal) =
        ReportingTotal(amount + other.amount, unresolvedCount + other.unresolvedCount)

    companion object {
        fun zero(currency: Currency = Currencies.REPORTING) = ReportingTotal(FiatMoney.zero(currency))
    }
}

data class SpendingBreakdown(
    val committed: ReportingTotal,
    val discretionary: ReportingTotal,
    /** Unclassified expenses plus explicit outgoing adjustments (e.g. cash catch-up). */
    val unknown: ReportingTotal,
) {
    val total: ReportingTotal get() = committed + discretionary + unknown
}

data class PeriodSummary(
    val received: ReportingTotal,
    val spent: SpendingBreakdown,
    /** Outgoing adjustments in the period. Any adjustment means the category picture is incomplete. */
    val unclassifiedAdjustmentCount: Int,
) {
    val hasIncompleteData: Boolean
        get() = !received.isComplete || !spent.total.isComplete || unclassifiedAdjustmentCount > 0
}

/**
 * Shared Received/Spent rules for Home, Activity and exports:
 * - Received = INCOME only. Expected payments, drafts, refunds, transfers and adjustments are excluded.
 * - Spent = EXPENSE by class, net of linked refunds (capped at the purchase, counted in the
 *   purchase's period), plus ADJUSTMENT_OUT under unknown.
 * - Transfers are neither. Voided entries are ignored. Missing rates are counted, not guessed.
 */
object LedgerSummaryCalculator {

    fun summarize(
        entries: List<LedgerEntry>,
        period: ClosedRange<LocalDate>,
        reporting: Currency = Currencies.REPORTING,
    ): PeriodSummary {
        val active = entries.active().map { it.current }
        val refundsByPurchase = active
            .filter { it.type == TransactionType.REFUND }
            .groupBy { it.refundOf!! }

        var received = ReportingTotal.zero(reporting)
        var committed = ReportingTotal.zero(reporting)
        var discretionary = ReportingTotal.zero(reporting)
        var unknown = ReportingTotal.zero(reporting)
        var adjustments = 0

        for (tx in active.filter { it.date in period }) {
            when (tx.type) {
                TransactionType.INCOME -> received += reportingTotal(tx, reporting)
                TransactionType.EXPENSE -> {
                    val net = netOfRefunds(tx, refundsByPurchase, reporting)
                    when (tx.spendingClass) {
                        SpendingClass.COMMITTED -> committed += net
                        SpendingClass.DISCRETIONARY -> discretionary += net
                        SpendingClass.UNKNOWN, null -> unknown += net
                    }
                }
                TransactionType.ADJUSTMENT_OUT -> {
                    unknown += reportingTotal(tx, reporting)
                    adjustments++
                }
                TransactionType.ADJUSTMENT_IN -> adjustments++
                TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT, TransactionType.REFUND -> Unit
            }
        }
        return PeriodSummary(received, SpendingBreakdown(committed, discretionary, unknown), adjustments)
    }

    /** Expense value after linked refunds, never below zero. */
    fun netOfRefunds(
        expense: Transaction,
        refundsByPurchase: Map<TransactionId, List<Transaction>>,
        reporting: Currency = Currencies.REPORTING,
    ): ReportingTotal {
        val gross = reportingTotal(expense, reporting)
        if (!gross.isComplete) return gross
        var refunded = ReportingTotal.zero(reporting)
        for (refund in refundsByPurchase[expense.id].orEmpty()) refunded += reportingTotal(refund, reporting)
        if (!refunded.isComplete) return gross.copy(unresolvedCount = refunded.unresolvedCount)
        val net = gross.amount - minOf(refunded.amount, gross.amount)
        return ReportingTotal(net)
    }

    private fun reportingTotal(tx: Transaction, reporting: Currency): ReportingTotal {
        val value = CurrencyConverter.reportingAmount(tx.amount, tx.reportingConversion)
        return if (value != null && value.currency == reporting) ReportingTotal(value)
        else ReportingTotal(FiatMoney.zero(reporting), unresolvedCount = 1)
    }
}
