package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.repository.LedgerRepository
import javax.inject.Inject

/** Saves both legs of a transfer atomically. Transfers are never income or spending. */
class SaveTransferUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(transfer: Transfer, from: Account, to: Account): Result<Unit> = resultOf {
        if (from.id != transfer.fromAccountId || to.id != transfer.toAccountId) {
            throw LedgerError.InvalidRecord("Accounts do not match the transfer")
        }
        listOf(from, to).firstOrNull { it.isArchived }?.let { throw LedgerError.AccountArchived(it.id) }
        if (transfer.sent.currency != from.currency) {
            throw LedgerError.CurrencyMismatch(from.currency.code, transfer.sent.currency.code)
        }
        if (transfer.received.currency != to.currency) {
            throw LedgerError.CurrencyMismatch(to.currency.code, transfer.received.currency.code)
        }
        ledger.saveTransfer(transfer)
    }
}
