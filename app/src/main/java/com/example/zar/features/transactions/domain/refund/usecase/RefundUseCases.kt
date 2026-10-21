package com.example.zar.features.transactions.domain.refund.usecase

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.transactions.domain.refund.RefundContext
import com.example.zar.features.transactions.domain.refund.RefundDetails
import com.example.zar.features.transactions.domain.refund.RefundRules
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** A purchase with its refunds and the account a refund would go back to. Null if the purchase doesn't exist. */
class ObserveRefundContextUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val accounts: AccountRepository,
) {
    operator fun invoke(purchaseId: TransactionId): Flow<RefundContext?> =
        combine(ledger.observeLedger(), accounts.observeAccounts()) { entries, list ->
            val purchase = entries.firstOrNull { it.id == purchaseId } ?: return@combine null
            RefundContext(
                summary = RefundRules.summary(purchase, entries),
                account = list.firstOrNull { it.id == purchase.current.accountId },
            )
        }
}

/**
 * Saves a refund linked to its purchase. The cap is checked against the ledger as it is now, not as the form
 * last saw it, so two refunds can never add up to more than the purchase. Reuse the same [id] when retrying:
 * a refund that is already stored is not saved a second time.
 */
class RecordRefundUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val saveTransaction: SaveTransactionUseCase,
) {
    suspend operator fun invoke(id: TransactionId, details: RefundDetails, createdAt: Instant): Result<Unit> = resultOf {
        val entries = ledger.observeLedger().first()
        if (entries.any { it.id == id }) return@resultOf

        val purchase = entries.firstOrNull { it.id == details.purchase.id }
            ?: throw LedgerError.InvalidRecord("The purchase no longer exists")
        if (purchase.isVoided) throw LedgerError.AlreadyVoided(purchase.id)
        if (purchase.current.type != TransactionType.EXPENSE) throw LedgerError.InvalidRecord("Only an expense can be refunded")
        val currency = purchase.current.amount.currency
        if (details.amount.currency != currency) throw LedgerError.CurrencyMismatch(currency.code, details.amount.currency.code)

        if (details.amount > RefundRules.summary(purchase, entries).remaining) throw LedgerError.RefundTooHigh()

        val refund = RefundRules.newRefund(id, details.copy(purchase = purchase), createdAt)
        saveTransaction(refund, details.account).getOrThrow()
    }
}
