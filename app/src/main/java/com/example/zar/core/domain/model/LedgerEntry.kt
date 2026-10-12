package com.example.zar.core.domain.model

/** A transaction as the user sees it now: original facts plus applied corrections and links. */
data class LedgerEntry(
    val original: Transaction,
    val current: Transaction,
    /** Applied corrections, oldest first. */
    val corrections: List<Correction>,
    val voidMarker: VoidMarker?,
    val evidenceIds: List<EvidenceId>,
) {
    val id: TransactionId get() = original.id
    val isVoided: Boolean get() = voidMarker != null
    val isCorrected: Boolean get() = corrections.isNotEmpty()
}
