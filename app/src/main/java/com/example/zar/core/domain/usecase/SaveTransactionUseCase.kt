package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.repository.LedgerRepository
import javax.inject.Inject

/** Saves a new actual transaction after checking it fits its account. */
class SaveTransactionUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(transaction: Transaction, account: Account): Result<Unit> = resultOf {
        if (account.id != transaction.accountId) throw LedgerError.InvalidRecord("Transaction is for another account")
        if (account.isArchived) throw LedgerError.AccountArchived(account.id)
        if (transaction.amount.currency != account.currency) {
            throw LedgerError.CurrencyMismatch(account.currency.code, transaction.amount.currency.code)
        }
        ledger.saveTransaction(transaction)
    }
}
