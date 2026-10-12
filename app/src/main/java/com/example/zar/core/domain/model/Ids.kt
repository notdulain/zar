package com.example.zar.core.domain.model

import java.util.UUID

@JvmInline value class UserId(val value: String)
@JvmInline value class AccountId(val value: String)
@JvmInline value class TransactionId(val value: String)
@JvmInline value class TransferId(val value: String)
@JvmInline value class CorrectionId(val value: String)
@JvmInline value class ExpectedPaymentId(val value: String)
@JvmInline value class DraftId(val value: String)
@JvmInline value class EvidenceId(val value: String)

/**
 * Stable IDs for saved records. Create the ID once, when the user starts an action, and reuse
 * it for every retry of that action. Saves are keyed by ID, so a replay can never duplicate,
 * overwrite or revive a record.
 */
object RecordIds {
    fun newId(): String = UUID.randomUUID().toString()
}
