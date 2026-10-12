package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.Money
import java.time.Instant
import java.time.LocalDate

/** Where an expectation came from. Features own the details behind these IDs. */
sealed interface ExpectedSource {
    data class FreelanceMilestone(val projectId: String, val milestoneId: String) : ExpectedSource
    data class BillOccurrence(val billId: String, val occurrenceDate: LocalDate) : ExpectedSource
    data object QuickNote : ExpectedSource
    data object Manual : ExpectedSource
}

enum class ExpectedPaymentStatus { OPEN, CANCELLED }

/**
 * Money expected to arrive (IN) or be paid (OUT) but not yet confirmed. Never counted in
 * Received, Spent or balances. Settled only by matching actual transactions.
 */
data class ExpectedPayment(
    val id: ExpectedPaymentId,
    val direction: Direction,
    val amount: Money,
    val dueDate: LocalDate,
    val title: String,
    val source: ExpectedSource,
    val accountId: AccountId? = null,
    val status: ExpectedPaymentStatus = ExpectedPaymentStatus.OPEN,
    val createdAt: Instant,
) {
    init {
        require(amount.isPositive) { "Expected amount must be positive" }
        require(title.isNotBlank()) { "Title is required" }
    }
}

/**
 * Allocates part of an actual transaction to an expected payment (a partial freelance receipt,
 * a bill payment). Same currency as the expectation. Keyed by both IDs, so it is idempotent.
 */
data class PaymentMatch(
    val expectedPaymentId: ExpectedPaymentId,
    val transactionId: TransactionId,
    val amount: Money,
    val matchedAt: Instant,
) {
    init {
        require(amount.isPositive) { "Matched amount must be positive" }
    }

    val id: String get() = "${expectedPaymentId.value}_${transactionId.value}"
}

/** "Not matched" means no evidence was linked; it never claims the payment definitely did not happen. */
enum class MatchState { NOT_MATCHED, PARTIALLY_MATCHED, MATCHED, CANCELLED }

data class ExpectedPaymentProgress(
    val expected: ExpectedPayment,
    val matched: Money,
    val remaining: Money,
    /** Remaining amount once the due date has passed; zero otherwise. */
    val overdue: Money,
    val state: MatchState,
)
