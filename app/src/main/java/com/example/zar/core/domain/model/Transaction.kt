package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.Instant
import java.time.LocalDate

enum class Direction { IN, OUT }

enum class TransactionType(val direction: Direction) {
    /** Money that actually arrived. The only type counted in Received. */
    INCOME(Direction.IN),
    EXPENSE(Direction.OUT),

    /** Transfer legs move money between owned accounts: never income or spending. */
    TRANSFER_IN(Direction.IN),
    TRANSFER_OUT(Direction.OUT),

    /** Money returned for an earlier expense; linked via [Transaction.refundOf]. Not income. */
    REFUND(Direction.IN),

    /** Explicit reconciliation or cash catch-up corrections. Never categorised spending. */
    ADJUSTMENT_IN(Direction.IN),
    ADJUSTMENT_OUT(Direction.OUT),
}

enum class SpendingClass { COMMITTED, DISCRETIONARY, UNKNOWN }

enum class RecordSource { MANUAL, SMS, SHARED_TEXT, QUICK_NOTE, CASH_CATCH_UP, RECONCILIATION }

/**
 * An actual money movement that already happened. Saved once and never changed in place:
 * edits are [Correction]s and undo is a [VoidMarker].
 */
data class Transaction(
    val id: TransactionId,
    val accountId: AccountId,
    val type: TransactionType,
    /** Always positive. The direction comes from [type]. */
    val amount: Money,
    val date: LocalDate,
    val reportingConversion: ReportingConversion,
    /** Required for expenses, and only for expenses. */
    val spendingClass: SpendingClass? = null,
    val categoryId: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val source: RecordSource = RecordSource.MANUAL,
    /** Shared by both legs of a transfer. */
    val transferId: TransferId? = null,
    /** The expense this refund belongs to. */
    val refundOf: TransactionId? = null,
    val createdAt: Instant,
) {
    init {
        require(amount.isPositive) { "Amount must be positive" }
        require((type == TransactionType.EXPENSE) == (spendingClass != null)) {
            "Spending class is required for expenses and only for expenses"
        }
        require((type == TransactionType.REFUND) == (refundOf != null)) { "Refunds must link to the original expense" }
        val isTransferLeg = type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT
        require(isTransferLeg == (transferId != null)) { "Transfer legs must carry a transfer ID" }
    }

    val direction: Direction get() = type.direction
}
