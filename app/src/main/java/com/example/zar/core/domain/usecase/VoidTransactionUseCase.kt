package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.inLegId
import com.example.zar.core.domain.model.outLegId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.LedgerRepository
import java.time.Instant
import javax.inject.Inject

/** Undo. Voiding a transfer leg voids both legs together. Voiding twice is harmless. */
class VoidTransactionUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(entry: LedgerEntry, voidedAt: Instant, reason: String? = null): Result<Unit> =
        resultOf {
            if (entry.isVoided) return@resultOf
            val transferId = entry.original.transferId
            val ids = if (transferId == null) {
                listOf(entry.id)
            } else {
                listOf(transferId.outLegId(), transferId.inLegId())
            }
            ledger.void(ids.map { VoidMarker(it, voidedAt, reason) })
        }
}
