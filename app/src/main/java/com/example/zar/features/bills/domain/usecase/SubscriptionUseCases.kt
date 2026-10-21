package com.example.zar.features.bills.domain.usecase

import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.RecurrenceDetector
import com.example.zar.features.bills.domain.RecurringSuggestion
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.repository.BillRepository
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Suggestions the user dismissed, kept on this phone. */
interface SuggestionPreferencesRepository {
    fun observeDismissed(): Flow<Set<String>>
    suspend fun dismiss(key: String)
    suspend fun clear()
}

/** Possible monthly subscriptions, for the weekly review and Bills. */
class ObserveRecurringSuggestionsUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val bills: BillRepository,
    private val accounts: AccountRepository,
    private val preferences: SuggestionPreferencesRepository,
) {
    operator fun invoke(): Flow<List<RecurringSuggestion>> = combine(
        ledger.observeLedger(),
        bills.observeBills(),
        accounts.observeAccounts(),
        preferences.observeDismissed(),
    ) { entries, billList, accountList, dismissed -> RecurrenceDetector.suggest(entries, billList, accountList, dismissed) }
}

/**
 * Turns a confirmed suggestion into a monthly bill due on the latest charge's day, and links
 * that charge as its first payment. The bill ID comes from the suggestion, so confirming twice
 * creates one bill.
 */
class ConfirmSuggestionUseCase @Inject constructor(
    private val bills: BillRepository,
    private val expected: ExpectedPaymentRepository,
) {
    suspend operator fun invoke(suggestion: RecurringSuggestion, now: Instant): Result<BillId> = attemptResult {
        val bill = Bill(
            id = billIdFor(suggestion),
            name = suggestion.merchant,
            accountId = suggestion.accountId,
            amount = suggestion.amount,
            frequency = BillFrequency.MONTHLY,
            firstDue = suggestion.lastDate,
            status = BillStatus.ACTIVE,
            createdAt = now,
        )
        bills.saveBill(bill)
        val due = BillSchedule.expectedPayment(bill, suggestion.lastDate)
        expected.saveExpectedPayment(due)
        expected.saveMatch(PaymentMatch(due.id, suggestion.lastEntryId, suggestion.amount, now))
        bill.id
    }

    fun billIdFor(suggestion: RecurringSuggestion): BillId {
        val digest = MessageDigest.getInstance("SHA-256").digest(suggestion.key.toByteArray(Charsets.UTF_8))
        return BillId("sub-" + digest.take(12).joinToString("") { "%02x".format(it) })
    }
}

class DismissSuggestionUseCase @Inject constructor(
    private val preferences: SuggestionPreferencesRepository,
) {
    suspend operator fun invoke(suggestion: RecurringSuggestion): Result<Unit> = attemptResult { preferences.dismiss(suggestion.key) }
}

/** Intend to cancel, confirm a cancellation from a date, or keep the bill after all. */
class SetBillCancellationUseCase @Inject constructor(
    private val bills: BillRepository,
) {
    suspend fun intend(bill: Bill): Result<Unit> = attemptResult { bills.saveBill(bill.copy(cancellation = BillCancellation.Intended)) }

    suspend fun confirm(bill: Bill, effectiveFrom: LocalDate): Result<Unit> =
        attemptResult { bills.saveBill(bill.copy(cancellation = BillCancellation.Confirmed(effectiveFrom))) }

    suspend fun keep(bill: Bill): Result<Unit> = attemptResult { bills.saveBill(bill.copy(cancellation = null)) }
}

private inline fun <T> attemptResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
