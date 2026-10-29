package com.example.zar.features.transactions.domain.manual

import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion

/** Suggestions for the quick note: the user's own recent expenses, matched by the start of the title. */
object EntrySuggestions {
    const val MAX_SHOWN = 5

    /**
     * The latest active expense for each distinct title, newest first. A suggestion offers the entry's category
     * only if the user's [catalog] knows it (built-in or their own); a legacy or not-yet-synced ID is left out
     * rather than replaced, so picking the suggestion never invents a category.
     */
    fun recent(entries: List<LedgerEntry>, catalog: ExpenseCategoryCatalog = ExpenseCategoryCatalog.BUILT_IN_ONLY): List<EntrySuggestion> = entries.active()
        .map { it.current }
        .filter { it.type == TransactionType.EXPENSE && !it.merchant.isNullOrBlank() }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
        .distinctBy { it.merchant!!.trim().lowercase() }
        .map { tx ->
            EntrySuggestion(
                title = tx.merchant!!.trim(),
                category = catalog.find(tx.categoryId),
                accountId = tx.accountId,
                amount = tx.amount,
                // Expenses always carry a spending class (Transaction enforces it).
                spendingClass = tx.spendingClass!!,
            )
        }

    /** Suggestions whose title starts with [typed], ignoring case. Nothing is suggested for blank input. */
    fun matching(all: List<EntrySuggestion>, typed: String): List<EntrySuggestion> {
        val prefix = typed.trim()
        if (prefix.isEmpty()) return emptyList()
        return all.filter { it.title.startsWith(prefix, ignoreCase = true) }.take(MAX_SHOWN)
    }
}
