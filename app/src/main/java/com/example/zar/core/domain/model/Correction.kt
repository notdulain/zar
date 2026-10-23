package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate

/** Change to an optional field: set a new value or clear it. */
sealed interface FieldUpdate<out T> {
    data class Set<T>(val value: T) : FieldUpdate<T>
    data object Clear : FieldUpdate<Nothing>
}

/** Fields a user may correct. A null property means "unchanged". */
data class TransactionChanges(
    val amount: Money? = null,
    /** Required whenever [amount] changes; see CorrectTransactionUseCase. */
    val reportingConversion: ReportingConversion? = null,
    val date: LocalDate? = null,
    val spendingClass: SpendingClass? = null,
    val categoryId: FieldUpdate<String>? = null,
    val merchant: FieldUpdate<String>? = null,
    val note: FieldUpdate<String>? = null,
    val time: FieldUpdate<LocalTime>? = null,
) {
    val isEmpty: Boolean
        get() = amount == null && reportingConversion == null && date == null && spendingClass == null &&
            categoryId == null && merchant == null && note == null && time == null
}

/**
 * An append-only edit. The original (including imported facts) is kept, and corrections are
 * applied oldest first, so the full history stays visible.
 */
data class Correction(
    val id: CorrectionId,
    val transactionId: TransactionId,
    val changes: TransactionChanges,
    val correctedAt: Instant,
    val reason: String? = null,
)

/**
 * Durable undo. Once a void marker exists the transaction is excluded for good; replaying the
 * original save, or a later correction, cannot bring it back. Re-adding needs a new ID.
 */
data class VoidMarker(
    val transactionId: TransactionId,
    val voidedAt: Instant,
    val reason: String? = null,
)

/**
 * Connects captured evidence (SMS, shared text) to an existing transaction, e.g. the bank
 * message for a manual transfer leg, without posting money again. One evidence item links to
 * one transaction, so a payment reported by several sources settles once.
 */
data class EvidenceLink(
    val evidenceId: EvidenceId,
    val transactionId: TransactionId,
    val linkedAt: Instant,
)
