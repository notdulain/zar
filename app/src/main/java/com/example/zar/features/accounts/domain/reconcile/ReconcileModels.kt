package com.example.zar.features.accounts.domain.reconcile

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.money.Money

/** Whether an account's recorded balance is trustworthy enough to compare with a count or a statement. */
enum class CheckAvailability {
    AVAILABLE,

    /** The account started with an unknown value, so there is no recorded balance to compare. */
    BALANCE_UNKNOWN,

    /** Some entries use another currency and are skipped, so the recorded balance is incomplete. */
    ENTRIES_NEED_FIXING,

    ARCHIVED,
}

enum class CheckOutcome {
    MATCHES,

    /** Less money than recorded: something left the account that was never recorded. */
    SHORTFALL,

    /** More money than recorded: something arrived that was never recorded. */
    SURPLUS,
}

/** The recorded balance next to what the person counted (cash) or read on a statement (bank and others). */
data class BalanceCheck(val account: Account, val recorded: Money, val counted: Money) {
    /** Counted minus recorded. Negative when money is missing. */
    val difference: Money get() = counted - recorded

    val outcome: CheckOutcome
        get() = when {
            difference.isZero -> CheckOutcome.MATCHES
            difference.isNegative -> CheckOutcome.SHORTFALL
            else -> CheckOutcome.SURPLUS
        }

    /** The size of the gap, always positive when there is one. */
    val gap: Money get() = if (difference.isNegative) Money.zero(difference.currency) - difference else difference
}

enum class CheckProblem { AMOUNT_MISSING, AMOUNT_INVALID }

sealed interface CheckResult {
    data class Valid(val check: BalanceCheck) : CheckResult

    data class Invalid(val problems: Set<CheckProblem>) : CheckResult
}
