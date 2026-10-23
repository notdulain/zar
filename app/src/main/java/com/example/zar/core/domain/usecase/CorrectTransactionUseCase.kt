package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.feeId
import com.example.zar.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Appends a correction. The original record is kept. When the amount changes, the stored
 * historical rate is reused rather than today's rate.
 */
class CorrectTransactionUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(
        entry: LedgerEntry,
        changes: TransactionChanges,
        correctionId: CorrectionId,
        correctedAt: Instant,
        reason: String? = null,
    ): Result<Unit> = resultOf {
        if (entry.isVoided) throw LedgerError.AlreadyVoided(entry.id)
        val current = entry.current
        if (changes.accountId != null && changes.accountId != current.accountId) {
            if (current.type !in setOf(TransactionType.EXPENSE, TransactionType.INCOME) || current.transferId != null) {
                throw LedgerError.InvalidRecord("Linked entries must keep their account")
            }
            val entries = ledger.observeLedger().first()
            if (entries.any { !it.isVoided && it.current.type == TransactionType.REFUND && it.current.refundOf == entry.id }) {
                throw LedgerError.HasActiveRefunds(entry.id)
            }
            if (entries.any { it.original.transferId?.feeId() == entry.id }) {
                throw LedgerError.InvalidRecord("A transfer fee must stay on its sending account")
            }
            val target = accounts.observeAccounts().first().firstOrNull { it.id == changes.accountId }
                ?: throw LedgerError.InvalidRecord("The selected account no longer exists")
            if (target.isArchived) throw LedgerError.AccountArchived(target.id)
            if (target.currency != current.amount.currency) {
                throw LedgerError.CurrencyMismatch(current.amount.currency.code, target.currency.code)
            }
        }
        val newAmount = changes.amount
        if (newAmount != null && newAmount.currency != entry.current.amount.currency) {
            throw LedgerError.CurrencyMismatch(entry.current.amount.currency.code, newAmount.currency.code)
        }
        if (newAmount != null && entry.current.type == TransactionType.EXPENSE) {
            val refunded = ledger.observeLedger().first().asSequence()
                .filter { !it.isVoided && it.current.type == TransactionType.REFUND && it.current.refundOf == entry.id }
                .filter { it.current.amount.currency == newAmount.currency }
                .fold(java.math.BigDecimal.ZERO) { total, refund -> total + refund.current.amount.toDecimal() }
            if (newAmount.toDecimal() < refunded) throw LedgerError.RefundTooHigh()
        }
        val completed = if (newAmount != null && changes.reportingConversion == null) {
            changes.copy(reportingConversion = CurrencyConverter.reconvert(newAmount, entry.current.reportingConversion))
        } else {
            changes
        }
        val updated = resultOf { LedgerProjector.applyChanges(entry.current, completed) }
            .getOrElse { throw LedgerError.InvalidRecord(it.message ?: "Invalid correction") }
        if (completed.isEmpty || updated == entry.current) throw LedgerError.NothingToChange()
        ledger.addCorrection(Correction(correctionId, entry.id, completed, correctedAt, reason))
    }
}
