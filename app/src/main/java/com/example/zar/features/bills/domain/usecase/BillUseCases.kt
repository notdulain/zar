package com.example.zar.features.bills.domain.usecase

import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.bills.domain.BillInput
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillOccurrence
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.model.BillsOverview
import com.example.zar.features.bills.domain.model.NextBill
import com.example.zar.features.bills.domain.model.PaymentCandidate
import com.example.zar.features.bills.domain.repository.BillRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed class BillError(message: String) : Exception(message) {
    class CurrencyMismatch : BillError("The amount must use the bill's currency")
    class NotExpense : BillError("Only an expense can pay a bill")
    class AmountTooHigh : BillError("More than the expense or due date allows")
    class Stopped : BillError("The bill is stopped")
}

/** Every bill with its recent and next due dates, plus the next bill and yearly estimate. */
class ObserveBillsUseCase @Inject constructor(
    private val bills: BillRepository,
    private val accounts: AccountRepository,
    private val expected: ExpectedPaymentRepository,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(today: LocalDate): Flow<BillsOverview> = combine(
        bills.observeBills(),
        accounts.observeAccounts(),
        expected.observeExpectedPayments(),
        expected.observeMatches(),
        ledger.observeLedger(),
    ) { billList, accountList, payments, matches, entries ->
        BillsCalculator.overview(billList, accountList, payments, matches, entries, today)
    }
}

/** The next bill's name, date and expected amount for Home; null when nothing is due. */
class ObserveNextBillUseCase @Inject constructor(
    private val observeBills: ObserveBillsUseCase,
) {
    operator fun invoke(today: LocalDate): Flow<NextBill?> = observeBills(today).map { it.next }
}

/** Expenses that can still pay a due date. Empty if the bill is unknown. */
class ObservePaymentCandidatesUseCase @Inject constructor(
    private val bills: BillRepository,
    private val expected: ExpectedPaymentRepository,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(billId: BillId, dueDate: LocalDate): Flow<List<PaymentCandidate>> = combine(
        bills.observeBills(),
        expected.observeExpectedPayments(),
        expected.observeMatches(),
        ledger.observeLedger(),
    ) { billList, payments, matches, entries ->
        val bill = billList.firstOrNull { it.id == billId } ?: return@combine emptyList()
        val id = BillSchedule.occurrenceId(bill, dueDate)
        val occurrence = payments.firstOrNull { it.id == id } ?: BillSchedule.expectedPayment(bill, dueDate)
        BillsCalculator.paymentCandidates(occurrence, payments, matches, entries)
    }
}

class SaveBillUseCase @Inject constructor(
    private val bills: BillRepository,
) {
    /** Creates a bill. Reuse the same [id] when retrying. */
    suspend fun create(id: BillId, input: BillInput, now: Instant): Result<Unit> = attempt {
        bills.saveBill(Bill(id, input.name, input.account.id, input.amount, input.frequency, input.firstDue, BillStatus.ACTIVE, now))
    }

    /** Edits future due dates. Due dates that already have a payment keep what was saved. */
    suspend fun update(bill: Bill, input: BillInput): Result<Unit> = attempt {
        if (input.amount.currency != bill.amount.currency) throw BillError.CurrencyMismatch()
        bills.saveBill(bill.copy(name = input.name, accountId = input.account.id, amount = input.amount, frequency = input.frequency, firstDue = input.firstDue))
    }

    suspend fun setStopped(bill: Bill, stopped: Boolean): Result<Unit> =
        attempt { bills.saveBill(bill.copy(status = if (stopped) BillStatus.STOPPED else BillStatus.ACTIVE)) }
}

/**
 * Links part or all of an expense to a due date. The due date is saved as an expected payment
 * first (same stable ID every time), then the match.
 */
class LinkBillPaymentUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
) {
    suspend operator fun invoke(bill: Bill, occurrence: BillOccurrence, candidate: PaymentCandidate, amount: Money, now: Instant): Result<Unit> =
        attempt {
            val due = occurrence.progress.expected
            val expense = candidate.entry
            if (due.status == ExpectedPaymentStatus.CANCELLED) throw BillError.Stopped()
            if (expense.isVoided || expense.current.type != TransactionType.EXPENSE) throw BillError.NotExpense()
            if (amount.currency != due.amount.currency || amount.currency != expense.current.amount.currency) throw BillError.CurrencyMismatch()
            if (!amount.isPositive || amount > candidate.available || amount > occurrence.progress.remaining) throw BillError.AmountTooHigh()
            expected.saveExpectedPayment(due.copy(id = BillSchedule.occurrenceId(bill, due.dueDate)))
            expected.saveMatch(PaymentMatch(due.id, expense.id, amount, now))
        }
}

class UnlinkBillPaymentUseCase @Inject constructor(
    private val expected: ExpectedPaymentRepository,
) {
    suspend operator fun invoke(match: PaymentMatch): Result<Unit> =
        attempt { expected.removeMatch(match.expectedPaymentId, match.transactionId) }
}

private inline fun attempt(block: () -> Unit): Result<Unit> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
