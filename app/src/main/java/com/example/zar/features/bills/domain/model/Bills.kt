package com.example.zar.features.bills.domain.model

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.ExpectedPaymentProgress
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.money.Money
import java.time.Instant
import java.time.LocalDate

@JvmInline value class BillId(val value: String)

enum class BillFrequency { WEEKLY, MONTHLY, YEARLY }

/** The choices for a bill's single reminder. */
enum class BillReminder(val daysBefore: Int) {
    ON_THE_DAY(0),
    ONE_DAY_BEFORE(1),
    THREE_DAYS_BEFORE(3),
    ONE_WEEK_BEFORE(7),
    ;

    companion object {
        fun fromDays(days: Int?): BillReminder? = entries.firstOrNull { it.daysBefore == days }
    }
}

enum class BillStatus {
    ACTIVE,

    /** No further due dates. History and linked payments are kept. */
    STOPPED,
}

/**
 * A repeating bill. It only describes what is expected: due dates are expected payments and
 * never post an expense by themselves.
 */
data class Bill(
    val id: BillId,
    val name: String,
    val accountId: AccountId,
    val amount: Money,
    val frequency: BillFrequency,
    /** The first due date. Later dates keep its day (month ends clamp, e.g. 31st → 28 Feb → 31 Mar). */
    val firstDue: LocalDate,
    val status: BillStatus,
    val createdAt: Instant,
    /** The bill's one reminder: days before a due date to notify, or null for none. */
    val reminder: BillReminder? = null,
    /** Null while the bill runs normally. */
    val cancellation: BillCancellation? = null,
) {
    init {
        require(name.isNotBlank()) { "Bill name is required" }
        require(amount.isPositive) { "Bill amount must be positive" }
    }

    /** Still producing due dates: not stopped and not confirmed cancelled. */
    val isActive: Boolean get() = status == BillStatus.ACTIVE && cancellation !is BillCancellation.Confirmed

    /**
     * Still has due dates on or after [day]: not stopped, and not cancelled from [day] or earlier.
     * A cancellation confirmed from a later date keeps the due dates before it.
     */
    fun runsOn(day: LocalDate): Boolean = status == BillStatus.ACTIVE && lastDueDay.let { it == null || it >= day }

    /** The last day a cancelled bill can be due, or null when it isn't cancelled. */
    val lastDueDay: LocalDate? get() = (cancellation as? BillCancellation.Confirmed)?.effectiveFrom?.minusDays(1)
}

/**
 * Cancelling is two steps so the record stays honest. Intending to cancel changes nothing:
 * due dates keep coming. Only a confirmed cancellation stops them, from its date; earlier due
 * dates and their payments are kept.
 */
sealed interface BillCancellation {
    data object Intended : BillCancellation

    /** No due dates on or after [effectiveFrom]. */
    data class Confirmed(val effectiveFrom: LocalDate) : BillCancellation
}

/** An actual expense linked to a due date, and how much of it counts there. */
data class LinkedPayment(
    val match: PaymentMatch,
    val entry: LedgerEntry,
    val counted: Money,
)

data class BillOccurrence(
    val progress: ExpectedPaymentProgress,
    val payments: List<LinkedPayment>,
) {
    val dueDate: LocalDate get() = progress.expected.dueDate
}

data class BillSummary(
    val bill: Bill,
    val account: Account?,
    /** Estimated cost over a year: weekly × 52, monthly × 12, yearly × 1. */
    val annualCost: Money,
    /** Recent and next due dates, newest first. */
    val occurrences: List<BillOccurrence>,
    /** The next due date on or after today; null for a stopped or cancelled bill. */
    val next: BillOccurrence?,
    /** Expenses that look like this bill but arrived after it was confirmed cancelled. */
    val chargesAfterCancellation: List<LedgerEntry> = emptyList(),
)

/** What Home shows for the next bill, with the IDs needed to open its detail. */
data class NextBill(
    val billId: BillId,
    val name: String,
    val dueDate: LocalDate,
    val amount: Money,
)

data class BillsOverview(
    /** Active bills first, by next due date; then stopped bills. */
    val bills: List<BillSummary>,
    val next: NextBill?,
    /** Still expected in the next 30 days, one total per currency. */
    val dueSoon: List<Money>,
    /** Estimated yearly cost of active bills, one total per currency. */
    val annual: List<Money>,
)

/** An expense that can still be linked to a due date. */
data class PaymentCandidate(val entry: LedgerEntry, val available: Money)
