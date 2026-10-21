package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.feeId
import com.example.zar.core.domain.model.inLegId
import com.example.zar.core.domain.model.outLegId
import com.example.zar.core.domain.repository.LedgerRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Undo. Voiding a transfer leg voids both legs, and the transfer's separate fee expense when it
 * has one that is still active, in one atomic write. Voiding twice is harmless. A purchase that still has
 * active refunds cannot be undone: its refunds would keep adding money back to the account.
 */
class VoidTransactionUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(entry: LedgerEntry, voidedAt: Instant, reason: String? = null): Result<Unit> =
        resultOf {
            if (entry.isVoided) return@resultOf
            if (entry.original.type == TransactionType.EXPENSE) {
                val hasRefunds = ledger.observeLedger().first().any {
                    !it.isVoided && it.current.type == TransactionType.REFUND && it.current.refundOf == entry.id
                }
                if (hasRefunds) throw LedgerError.HasActiveRefunds(entry.id)
            }
            val transferId = entry.original.transferId
            val ids = if (transferId == null) {
                listOf(entry.id)
            } else {
                // A fee that was already voided on its own must not be written again: voids are write-once.
                val feeId = transferId.feeId()
                val fee = ledger.observeLedger().first().firstOrNull { it.id == feeId }?.takeIf { !it.isVoided }
                listOfNotNull(transferId.outLegId(), transferId.inLegId(), fee?.id)
            }
            ledger.void(ids.map { VoidMarker(it, voidedAt, reason) })
        }
}
