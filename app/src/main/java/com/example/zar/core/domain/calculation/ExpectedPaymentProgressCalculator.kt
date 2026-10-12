package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentProgress
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.coerceAtLeastZero
import java.time.LocalDate

/**
 * Matched/remaining/overdue for expected payments (freelance milestones, bill occurrences).
 * A transaction's allocations are capped at its amount, oldest match first, so one payment
 * is never counted twice. Undone or corrected transactions update the result automatically.
 */
object ExpectedPaymentProgressCalculator {

    fun calculate(
        expectedPayments: List<ExpectedPayment>,
        matches: List<PaymentMatch>,
        entries: List<LedgerEntry>,
        today: LocalDate,
    ): List<ExpectedPaymentProgress> {
        val expectedById = expectedPayments.associateBy { it.id }
        val transactions = entries.active().associate { it.id to it.current }

        // Effective allocation per match after capping each transaction.
        val effective = mutableMapOf<String, Money>()
        matches.distinctBy { it.id }.groupBy { it.transactionId }.forEach { (txId, txMatches) ->
            val tx = transactions[txId] ?: return@forEach
            if (tx.type == TransactionType.TRANSFER_IN || tx.type == TransactionType.TRANSFER_OUT) return@forEach
            var available = tx.amount
            for (match in txMatches.sortedWith(compareBy<PaymentMatch>({ it.matchedAt }, { it.id }))) {
                val expected = expectedById[match.expectedPaymentId] ?: continue
                if (expected.direction != tx.direction) continue
                if (match.amount.currency != tx.amount.currency || match.amount.currency != expected.amount.currency) continue
                val allocated = minOf(match.amount, available)
                if (allocated.isPositive) {
                    effective[match.id] = allocated
                    available -= allocated
                }
            }
        }

        val matchesByExpected = matches.distinctBy { it.id }.groupBy { it.expectedPaymentId }
        return expectedPayments.map { expected ->
            var matched = Money.zero(expected.amount.currency)
            matchesByExpected[expected.id].orEmpty().forEach { m -> effective[m.id]?.let { matched += it } }
            matched = minOf(matched, expected.amount)
            val remaining = (expected.amount - matched).coerceAtLeastZero()
            val isCancelled = expected.status == ExpectedPaymentStatus.CANCELLED
            val overdue = if (!isCancelled && expected.dueDate < today) remaining else Money.zero(remaining.currency)
            val state = when {
                isCancelled -> MatchState.CANCELLED
                remaining.isZero -> MatchState.MATCHED
                matched.isPositive -> MatchState.PARTIALLY_MATCHED
                else -> MatchState.NOT_MATCHED
            }
            ExpectedPaymentProgress(expected, matched, remaining, overdue, state)
        }
    }
}
