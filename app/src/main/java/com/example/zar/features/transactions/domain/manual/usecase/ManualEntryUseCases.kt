package com.example.zar.features.transactions.domain.manual.usecase

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.repository.ConnectivityMonitor
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.manual.EntrySuggestions
import com.example.zar.features.transactions.domain.manual.ManualEntryRules
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion
import com.example.zar.features.transactions.domain.manual.model.ExpenseDetails
import com.example.zar.features.transactions.domain.manual.model.TransferDetails
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Saves a manual expense. Reuse the same [id] when retrying so a repeated save can't duplicate. */
class RecordExpenseUseCase @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
) {
    suspend operator fun invoke(id: TransactionId, details: ExpenseDetails, createdAt: Instant): Result<Unit> {
        val expense = runCatching { ManualEntryRules.newExpense(id, details, createdAt) }
            .getOrElse { return Result.failure(LedgerError.InvalidRecord(it.message ?: "Invalid expense")) }
        return saveTransaction(expense, details.account)
    }
}

/** Saves both transfer legs and any separate fee expense in one atomic write. */
class RecordTransferUseCase @Inject constructor(
    private val saveTransfer: SaveTransferUseCase,
) {
    suspend operator fun invoke(id: TransferId, details: TransferDetails, createdAt: Instant): Result<Unit> {
        val transfer = runCatching { ManualEntryRules.newTransfer(id, details, createdAt) }
            .getOrElse { return Result.failure(LedgerError.InvalidRecord(it.message ?: "Invalid transfer")) }
        return saveTransfer(transfer, details.from, details.to)
    }
}

/** The user's recent expenses, for suggesting a quick note. */
class ObserveEntrySuggestionsUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val categories: ObserveExpenseCategoryCatalogUseCase,
) {
    operator fun invoke(): Flow<List<EntrySuggestion>> =
        combine(ledger.observeLedger(), categories()) { entries, catalog -> EntrySuggestions.recent(entries, catalog) }
}

/** Whether the phone is online. Saving still works offline; this only lets the form say so. */
class ObserveOnlineStatusUseCase @Inject constructor(
    private val connectivity: ConnectivityMonitor,
) {
    operator fun invoke(): Flow<Boolean> = connectivity.observeOnline()
}
