package com.example.zar.features.allowance.domain.model

import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The user's optional weekly allowance for discretionary spending. It starts switched off.
 *
 * - [amount] is in the reporting currency (LKR) and is required once the allowance is on.
 * - [categoryIds] limits which expense categories count. Empty means every discretionary category.
 * - [weekStart] is the first day of each week, Monday by default (Asia/Colombo calendar days).
 *
 * There is no rollover and no carried-over debt: every week starts again from [amount].
 */
data class AllowanceSettings(
    val isEnabled: Boolean = false,
    val amount: FiatMoney? = null,
    val categoryIds: Set<String> = emptySet(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    init {
        require(amount == null || (amount.isPositive && amount.currency == Currencies.REPORTING)) {
            "The allowance must be a positive ${Currencies.REPORTING.code} amount"
        }
        require(!isEnabled || amount != null) { "An allowance that is on needs an amount" }
        require(categoryIds.size <= MAX_CATEGORIES) { "Choose at most $MAX_CATEGORIES categories" }
    }

    companion object {
        const val MAX_CATEGORIES = 8
        val Disabled = AllowanceSettings()
    }
}

/**
 * How the current week is going. [remaining] is negative when the allowance is overspent.
 * The coverage counts say what could not be included, so a total is never presented as more
 * complete than it is.
 */
data class AllowanceStatus(
    val week: ClosedRange<LocalDate>,
    val allowance: FiatMoney,
    /** Eligible discretionary spending this week, net of linked refunds. */
    val spent: ReportingTotal,
    /** Expenses this week with no spending class yet. They are left out until classified. */
    val unclassifiedExpenseCount: Int,
    /** Cash catch-up or reconciliation outflows this week. They are never counted here. */
    val adjustmentCount: Int,
) {
    val remaining: FiatMoney get() = allowance - spent.amount
    val isOverspent: Boolean get() = remaining.isNegative
    val overspentBy: FiatMoney
        get() = FiatMoney.zero(allowance.currency).let { zero -> if (isOverspent) zero - remaining else zero }
    val missingRateCount: Int get() = spent.unresolvedCount
    val hasCoverageWarning: Boolean
        get() = missingRateCount > 0 || unclassifiedExpenseCount > 0 || adjustmentCount > 0
}

/** The saved settings and, while the allowance is on, this week's status. */
data class AllowanceOverview(
    val settings: AllowanceSettings,
    val status: AllowanceStatus?,
)
