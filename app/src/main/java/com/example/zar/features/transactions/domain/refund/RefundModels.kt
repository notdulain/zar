package com.example.zar.features.transactions.domain.refund

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Money
import java.time.LocalDate

/** A purchase with the refunds linked to it and what can still be refunded. */
data class RefundSummary(
    val purchase: LedgerEntry,
    /** Active refunds linked to the purchase, oldest first. */
    val refunds: List<LedgerEntry>,
    val refunded: Money,
    /** The purchase amount minus [refunded], never below zero. */
    val remaining: Money,
) {
    /** Only an active expense with something left can take a refund. */
    val canRefund: Boolean
        get() = !purchase.isVoided && purchase.current.type == TransactionType.EXPENSE && remaining.isPositive

    val hasRefunds: Boolean get() = refunds.isNotEmpty()
}

/** What the refund form needs: the purchase and the account the money comes back to. */
data class RefundContext(val summary: RefundSummary, val account: Account?) {
    /** A refund goes back to the purchase's own account, so it needs that account to still accept entries. */
    val canRefund: Boolean get() = summary.canRefund && account != null && !account.isArchived
}

/** What the user typed into the refund form. */
data class RefundFormInput(
    val amount: String,
    val date: LocalDate,
    val note: String,
)

enum class RefundProblem {
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    AMOUNT_TOO_HIGH,
    NOTHING_LEFT,
    DATE_IN_FUTURE,
    NOTE_TOO_LONG,
}

/** A validated refund, ready to become a record. */
data class RefundDetails(
    val purchase: LedgerEntry,
    val account: Account,
    val amount: Money,
    val date: LocalDate,
    val note: String?,
)

sealed interface RefundFormResult {
    data class Valid(val details: RefundDetails) : RefundFormResult

    data class Invalid(val problems: Set<RefundProblem>) : RefundFormResult
}
