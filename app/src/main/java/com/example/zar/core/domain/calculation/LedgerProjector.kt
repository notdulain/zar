package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.FieldUpdate
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.VoidMarker

/**
 * Builds the user's current view from write-once records. Pure and order-independent, so the
 * same records always give the same result regardless of retries or sync order.
 */
object LedgerProjector {

    fun project(
        transactions: Collection<Transaction>,
        corrections: Collection<Correction>,
        voids: Collection<VoidMarker>,
        evidenceLinks: Collection<EvidenceLink> = emptyList(),
    ): List<LedgerEntry> {
        val correctionsByTransaction = corrections
            .distinctBy { it.id }
            .sortedWith(compareBy<Correction>({ it.correctedAt }, { it.id.value }))
            .groupBy { it.transactionId }
        val voidByTransaction = voids
            .sortedBy { it.voidedAt }
            .associateBy { it.transactionId }
        // Each evidence item belongs to the first transaction it was linked to.
        val evidenceByTransaction = evidenceLinks
            .sortedBy { it.linkedAt }
            .distinctBy { it.evidenceId }
            .groupBy({ it.transactionId }, { it.evidenceId })

        return transactions
            .distinctBy { it.id }
            .map { original ->
                var current = original
                val applied = mutableListOf<Correction>()
                for (correction in correctionsByTransaction[original.id].orEmpty()) {
                    // A correction that would make the record invalid is ignored, not fatal.
                    val next = runCatching { applyChanges(current, correction.changes) }.getOrNull() ?: continue
                    current = next
                    applied += correction
                }
                LedgerEntry(
                    original = original,
                    current = current,
                    corrections = applied,
                    voidMarker = voidByTransaction[original.id],
                    evidenceIds = evidenceByTransaction[original.id].orEmpty(),
                )
            }
            .sortedWith(compareByDescending<LedgerEntry> { it.current.date }.thenByDescending { it.original.createdAt })
    }

    fun applyChanges(transaction: Transaction, changes: TransactionChanges): Transaction = transaction.copy(
        amount = changes.amount ?: transaction.amount,
        reportingConversion = changes.reportingConversion ?: transaction.reportingConversion,
        date = changes.date ?: transaction.date,
        time = changes.time.applyTo(transaction.time),
        spendingClass = changes.spendingClass ?: transaction.spendingClass,
        categoryId = changes.categoryId.applyTo(transaction.categoryId),
        merchant = changes.merchant.applyTo(transaction.merchant),
        note = changes.note.applyTo(transaction.note),
    )

    private fun <T> FieldUpdate<T>?.applyTo(current: T?): T? = when (this) {
        null -> current
        FieldUpdate.Clear -> null
        is FieldUpdate.Set -> value
    }
}

/** Entries that still count: not undone. */
fun List<LedgerEntry>.active(): List<LedgerEntry> = filterNot { it.isVoided }
