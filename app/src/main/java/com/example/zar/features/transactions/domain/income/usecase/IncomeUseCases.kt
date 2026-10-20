package com.example.zar.features.transactions.domain.income.usecase

import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.calculation.toDateRange
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.features.transactions.domain.income.IncomeRules
import com.example.zar.features.transactions.domain.income.model.IncomeDetails
import com.example.zar.features.transactions.domain.income.model.IncomeOverview
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Received income and the month's Received total, also used by Home. */
class ObserveIncomeUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(month: YearMonth): Flow<IncomeOverview> = ledger.observeLedger().map { entries ->
        IncomeOverview(
            receivedThisMonth = LedgerSummaryCalculator.summarize(entries, month.toDateRange()).received,
            entries = entries.active().filter { it.current.type == TransactionType.INCOME },
        )
    }
}

/** One active income entry, or null once it is undone or if the ID is not income. */
class ObserveIncomeEntryUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(id: TransactionId): Flow<LedgerEntry?> = ledger.observeLedger().map { entries ->
        entries.firstOrNull { it.id == id && !it.isVoided && it.current.type == TransactionType.INCOME }
    }
}

/** Accounts income can be recorded into. */
class ObserveIncomeAccountsUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    operator fun invoke(): Flow<List<Account>> = accounts.observeAccounts().map { list -> list.filterNot { it.isArchived } }
}

/** Saves new received income. Reuse the same [id] when retrying. */
class RecordIncomeUseCase @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
) {
    suspend operator fun invoke(
        id: TransactionId,
        account: Account,
        details: IncomeDetails,
        createdAt: Instant,
    ): Result<Unit> {
        val income = runCatching { IncomeRules.newIncome(id, account, details, createdAt) }
            .getOrElse { return Result.failure(LedgerError.InvalidRecord(it.message ?: "Invalid income")) }
        return saveTransaction(income, account)
    }
}

/** Edits income by appending a correction; the original record is kept. */
class EditIncomeUseCase @Inject constructor(
    private val correctTransaction: CorrectTransactionUseCase,
) {
    suspend operator fun invoke(
        entry: LedgerEntry,
        edited: IncomeDetails,
        correctionId: CorrectionId,
        correctedAt: Instant,
    ): Result<Unit> {
        if (entry.current.type != TransactionType.INCOME) {
            return Result.failure(LedgerError.InvalidRecord("Only income can be edited here"))
        }
        val changes = IncomeRules.changesBetween(entry.current, edited, correctedAt)
        return correctTransaction(entry, changes, correctionId, correctedAt)
    }
}
