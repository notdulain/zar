package com.example.zar.features.quicknote.domain.usecase

import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.quicknote.domain.review.ConfirmableRecord
import com.example.zar.features.quicknote.domain.review.ProposalInput
import com.example.zar.features.quicknote.domain.review.QuickNoteReviewRules
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Saves one reviewed proposal of a quick-note draft. Nothing is posted before this runs, and only the proposal
 * the person confirmed is saved. The record's ID comes from the draft and the proposal's position, so confirming
 * again (a retry, a replay after reconnecting) saves it once.
 */
class ConfirmQuickNoteProposalUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
    private val expected: ExpectedPaymentRepository,
    private val saveTransaction: SaveTransactionUseCase,
    private val saveTransfer: SaveTransferUseCase,
    private val categories: ExpenseCategoryRepository,
) {
    suspend operator fun invoke(draft: Draft, index: Int, input: ProposalInput, today: LocalDate, now: Instant): Result<Unit> = try {
        val active = accounts.observeAccounts().first().filterNot { it.isArchived }
        // Only a refund needs the ledger, to find the purchase it returns money for.
        val entries = if (input is ProposalInput.Actual && input.refundOf != null) ledger.observeLedger().first() else emptyList()
        val catalog = if (input is ProposalInput.Actual && input.categoryId != null) {
            ExpenseCategoryCatalog(categories.currentCustomCategories())
        } else {
            ExpenseCategoryCatalog.BUILT_IN_ONLY
        }
        val check = QuickNoteReviewRules.check(draft, index, input, active, entries, today, now, catalog)
        when (val record = check.record) {
            null -> Result.failure(LedgerError.InvalidRecord("The proposal still needs ${check.blockers.joinToString { it.name.lowercase() }}"))
            is ConfirmableRecord.Ledger -> saveTransaction(record.transaction, record.account)
            is ConfirmableRecord.Moved -> saveTransfer(record.transfer, record.from, record.to)
            is ConfirmableRecord.Expectation -> resultOf { expected.saveExpectedPayment(record.payment) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
