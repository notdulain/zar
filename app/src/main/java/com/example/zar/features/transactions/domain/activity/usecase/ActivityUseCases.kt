package com.example.zar.features.transactions.domain.activity.usecase

import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.feeId
import com.example.zar.core.domain.model.inLegId
import com.example.zar.core.domain.model.outLegId
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.transactions.domain.activity.EntryHistory
import com.example.zar.features.transactions.domain.activity.model.ActivitySnapshot
import com.example.zar.features.transactions.domain.activity.model.EditKind
import com.example.zar.features.transactions.domain.activity.model.EntryDetail
import com.example.zar.features.transactions.domain.refund.RefundRules
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Every entry and account, live. It re-emits on any save, correction or undo, including offline ones. */
class ObserveActivityUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val accounts: AccountRepository,
) {
    operator fun invoke(): Flow<ActivitySnapshot> =
        combine(ledger.observeLedger(), accounts.observeAccounts()) { entries, list -> ActivitySnapshot(entries, list) }
}

/** One entry with its account, transfer partner, fee and correction history. Null if it doesn't exist. */
class ObserveEntryDetailUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val accounts: AccountRepository,
) {
    operator fun invoke(id: TransactionId): Flow<EntryDetail?> =
        combine(ledger.observeLedger(), accounts.observeAccounts()) { entries, list ->
            val entry = entries.firstOrNull { it.id == id } ?: return@combine null
            val byId = entries.associateBy { it.id }
            val transferId = entry.original.transferId
            val counterpart = transferId?.let {
                byId[if (entry.id == it.outLegId()) it.inLegId() else it.outLegId()]
            }
            val tx = entry.current
            val account = list.firstOrNull { it.id == tx.accountId }
            val refunds = if (entry.current.type == TransactionType.EXPENSE) RefundRules.summary(entry, entries) else null
            val purchase = if (entry.current.type == TransactionType.REFUND) entry.current.refundOf?.let { byId[it] } else null
            EntryDetail(
                entry = entry,
                account = account,
                counterpart = counterpart,
                counterpartAccount = counterpart?.let { c -> list.firstOrNull { it.id == c.current.accountId } },
                fee = transferId?.let { byId[it.feeId()] },
                history = EntryHistory.build(entry, list),
                accounts = list,
                canChangeAccount = tx.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME) && refunds?.hasRefunds != true && tx.transferId == null && entries.none { it.original.transferId?.feeId() == entry.id },
                editKind = if (entry.isVoided) {
                    EditKind.NONE
                } else {
                    when (tx.type) {
                        TransactionType.EXPENSE -> EditKind.EXPENSE
                        TransactionType.INCOME -> EditKind.INCOME
                        else -> EditKind.NONE
                    }
                },
                // A purchase with active refunds must have them undone first, or they would keep adding money back.
                canVoid = !entry.isVoided && refunds?.hasRefunds != true,
                refunds = refunds,
                canRefund = refunds?.canRefund == true && account != null && !account.isArchived,
                refundedPurchase = purchase,
                refundedPurchaseAccount = purchase?.let { p -> list.firstOrNull { it.id == p.current.accountId } },
            )
        }
}

/** Corrects an expense by appending a correction. The original and every earlier correction are kept. */
class CorrectExpenseUseCase @Inject constructor(
    private val correctTransaction: CorrectTransactionUseCase,
) {
    suspend operator fun invoke(
        entry: LedgerEntry,
        changes: TransactionChanges,
        correctionId: CorrectionId,
        correctedAt: Instant,
    ): Result<Unit> = resultOf {
        if (entry.original.type != TransactionType.EXPENSE) {
            throw LedgerError.InvalidRecord("Only expenses can be corrected here")
        }
        correctTransaction(entry, changes, correctionId, correctedAt).getOrThrow()
    }
}
