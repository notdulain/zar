package com.example.zar.features.transactions.domain.refund

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.coerceAtLeastZero
import java.time.Instant
import java.time.LocalDate

/**
 * Pure rules for linking a refund to the purchase it belongs to. A refund is money coming back for an
 * earlier expense: it is never income, it goes back to the purchase's account, and it cannot add up to
 * more than the purchase. The Spent totals pick it up through `LedgerSummaryCalculator`, which counts it
 * in the purchase's own month.
 */
object RefundRules {
    // Limit matches firestore.rules.
    const val MAX_NOTE = 500

    /** The purchase with its active refunds. [entries] is the whole ledger. */
    fun summary(purchase: LedgerEntry, entries: List<LedgerEntry>): RefundSummary {
        val currency = purchase.current.amount.currency
        val refunds = entries
            .filter { !it.isVoided && it.current.type == TransactionType.REFUND && it.current.refundOf == purchase.id }
            .filter { it.current.amount.currency == currency }
            .sortedWith(compareBy<LedgerEntry> { it.current.date }.thenBy { it.current.createdAt })
        val refunded = refunds.fold(Money.zero(currency)) { sum, refund -> sum + refund.current.amount }
        val remaining = (purchase.current.amount - refunded).coerceAtLeastZero()
        return RefundSummary(purchase, refunds, refunded, remaining)
    }

    fun validate(summary: RefundSummary, account: Account, input: RefundFormInput, today: LocalDate): RefundFormResult {
        val problems = mutableSetOf<RefundProblem>()
        if (!summary.canRefund || account.isArchived) problems += RefundProblem.NOTHING_LEFT

        val amount = if (input.amount.isBlank()) {
            problems += RefundProblem.AMOUNT_MISSING
            null
        } else {
            Money.parse(input.amount, account.currency)?.takeIf { it.isPositive }
                ?: null.also { problems += RefundProblem.AMOUNT_INVALID }
        }
        if (amount != null && summary.canRefund && amount > summary.remaining) problems += RefundProblem.AMOUNT_TOO_HIGH
        if (input.date.isAfter(today)) problems += RefundProblem.DATE_IN_FUTURE
        val note = input.note.trim()
        if (note.length > MAX_NOTE) problems += RefundProblem.NOTE_TOO_LONG

        if (problems.isNotEmpty() || amount == null) return RefundFormResult.Invalid(problems)
        return RefundFormResult.Valid(
            RefundDetails(summary.purchase, account, amount, input.date, note.ifEmpty { null }),
        )
    }

    /**
     * The refund as a record. It uses the purchase's own historical rate, so it takes back exactly what
     * the purchase added to Spent, and it carries the purchase's title so lists can tell what it was for.
     */
    fun newRefund(id: TransactionId, details: RefundDetails, createdAt: Instant): Transaction {
        val purchase = details.purchase.current
        return Transaction(
            id = id,
            accountId = purchase.accountId,
            type = TransactionType.REFUND,
            amount = details.amount,
            date = details.date,
            reportingConversion = CurrencyConverter.reconvert(details.amount, purchase.reportingConversion),
            merchant = purchase.merchant,
            note = details.note,
            refundOf = purchase.id,
            createdAt = createdAt,
        )
    }
}
