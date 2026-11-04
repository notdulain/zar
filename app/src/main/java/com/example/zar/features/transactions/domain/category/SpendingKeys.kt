package com.example.zar.features.transactions.domain.category

import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType

/**
 * How an entry is grouped on the spending breakdown, shared with the Activity filter so a row's drill-down shows
 * exactly the entries that row added up.
 */
object SpendingKeys {
    /** Spending with no category. Prefixed so it can never equal a stored category ID. */
    const val UNCATEGORISED = "~uncategorised"

    /** Outgoing adjustments (cash catch-up and reconciliation), which are never categorised spending. */
    const val ADJUSTMENTS = "~adjustments"

    /** The row key an entry belongs to; custom duplicates resolve to their canonical category. */
    fun keyOf(tx: Transaction, catalog: ExpenseCategoryCatalog): String = when {
        tx.type == TransactionType.ADJUSTMENT_OUT -> ADJUSTMENTS
        tx.categoryId == null -> UNCATEGORISED
        else -> catalog.canonicalId(tx.categoryId)
    }

    /** The class an entry is reported under, or null when it is not spending at all. */
    fun classOf(tx: Transaction): SpendingClass? = when (tx.type) {
        TransactionType.EXPENSE -> tx.spendingClass ?: SpendingClass.UNKNOWN
        TransactionType.ADJUSTMENT_OUT -> SpendingClass.UNKNOWN
        else -> null
    }
}
