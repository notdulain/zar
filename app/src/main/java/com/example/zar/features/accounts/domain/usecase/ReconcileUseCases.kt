package com.example.zar.features.accounts.domain.usecase

import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.accounts.domain.reconcile.BalanceCheck
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.ReconcileRules
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Confirms a count or statement balance. A gap is saved as one explicit adjustment; a match saves nothing.
 * The balance is recalculated from the ledger as it is now, so the adjustment always closes the real gap.
 * Reuse the same [id] when retrying: once the adjustment is stored the balance matches, so a repeated
 * confirm saves nothing more.
 */
class RecordBalanceCheckUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val saveTransaction: SaveTransactionUseCase,
) {
    suspend operator fun invoke(
        id: TransactionId,
        account: Account,
        counted: Money,
        date: LocalDate,
        createdAt: Instant,
        source: RecordSource = RecordSource.RECONCILIATION,
    ): Result<BalanceCheck> = resultOf {
        if (counted.currency != account.currency) throw LedgerError.CurrencyMismatch(account.currency.code, counted.currency.code)
        val balance = AccountBalanceCalculator.calculate(account, ledger.observeLedger().first())
        if (ReconcileRules.availability(balance) != CheckAvailability.AVAILABLE) {
            throw LedgerError.InvalidRecord("This balance cannot be checked yet")
        }
        val check = BalanceCheck(account, balance.current!!, counted)
        ReconcileRules.newAdjustment(id, check, date, createdAt, source)?.let { saveTransaction(it, account).getOrThrow() }
        check
    }
}
