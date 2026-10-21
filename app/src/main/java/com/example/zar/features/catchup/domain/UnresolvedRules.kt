package com.example.zar.features.catchup.domain

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.catchup.domain.model.UnresolvedItem
import com.example.zar.features.catchup.domain.model.UnresolvedKind
import java.time.LocalDate

/**
 * Which saved entries still need a look. Nothing is guessed: an entry is open only because of something it
 * visibly lacks (a kind of spending, an exchange rate) or because it is an unexplained gap.
 */
object UnresolvedRules {

    /** Open entries, newest first. Voided entries are never listed. */
    fun find(entries: List<LedgerEntry>, accounts: List<Account>): List<UnresolvedItem> =
        entries.active()
            .mapNotNull { entry ->
                kindOf(entry.current)?.let { UnresolvedItem(it, entry.current, accounts.firstOrNull { a -> a.id == entry.current.accountId }) }
            }
            .sortedWith(compareByDescending<UnresolvedItem> { it.transaction.date }.thenByDescending { it.transaction.createdAt })

    /** Active entries dated in the seven days up to and including [today]. */
    fun recordedInLastWeek(entries: List<LedgerEntry>, today: LocalDate): Int {
        val from = today.minusDays(6)
        return entries.active().count { it.current.date in from..today }
    }

    private fun kindOf(tx: Transaction): UnresolvedKind? = when {
        tx.type == TransactionType.ADJUSTMENT_OUT -> UnresolvedKind.UNEXPLAINED_ADJUSTMENT
        tx.type in RATE_DEPENDENT && CurrencyConverter.reportingAmount(tx.amount, tx.reportingConversion) == null ->
            UnresolvedKind.MISSING_RATE
        tx.type == TransactionType.EXPENSE && tx.spendingClass == SpendingClass.UNKNOWN -> UnresolvedKind.UNCATEGORISED
        else -> null
    }

    // The types whose reporting value feeds Received or Spent.
    private val RATE_DEPENDENT = setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.REFUND)
}
