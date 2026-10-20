package com.example.zar.features.plans.domain.model

import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.coerceAtLeastZero
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A conservative view of a goal based only on recorded account funds. */
data class BackedGoalProgress(
    val allocated: Money,
    /** Known backing; excludes any allocation whose balance cannot be valued reliably. */
    val backed: Money,
    val allocationGap: Money?,
    val targetShortfall: Money?,
    val weeklyPace: Money?,
    val daysRemaining: Long?,
    /** Oldest last-recorded date among backing accounts. This is not a bank sync timestamp. */
    val balanceAsOf: LocalDate?,
    val hasUnknownBalance: Boolean,
    val isApproximate: Boolean,
    val hasMismatchedEntries: Boolean,
) {
    fun backedFraction(target: Money): Float = fraction(backed, target)
    fun allocatedFraction(target: Money): Float = fraction(allocated, target)

    private fun fraction(amount: Money, target: Money): Float =
        amount.toDecimal().divide(target.toDecimal(), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}

object GoalBackingCalculator {
    fun calculate(goal: SavingsGoal, accounts: List<Account>, entries: List<LedgerEntry>, today: LocalDate): BackedGoalProgress {
        val byId = accounts.associateBy { it.id }
        var backed = Money.zero(goal.target.currency)
        var unknown = false
        var approximate = false
        var mismatched = false
        val balanceDates = mutableListOf<LocalDate>()

        goal.allocations.forEach { allocation ->
            val account = byId[allocation.accountId]
            if (account == null || account.currency != goal.target.currency || account.openingDate > today) {
                unknown = true
                return@forEach
            }
            val balance = AccountBalanceCalculator.calculate(account, entries, today)
            mismatched = mismatched || balance.mismatchedCount > 0
            if (balance.current == null || balance.mismatchedCount > 0) {
                unknown = true
                return@forEach
            }
            approximate = approximate || balance.isApproximate
            val available = balance.current.coerceAtLeastZero()
            backed += if (available < allocation.amount) available else allocation.amount
            val latestEntryDate = entries.active().asSequence()
                .map { it.current }
                .filter { it.accountId == account.id && it.date in account.openingDate..today }
                .maxOfOrNull { it.date }
            balanceDates += maxOf(account.openingDate, latestEntryDate ?: account.openingDate)
        }

        val daysRemaining = goal.targetDate?.let { ChronoUnit.DAYS.between(today, it) }
        val shortfall = if (unknown) null else (goal.target - backed).coerceAtLeastZero()
        val pace = if (shortfall == null || daysRemaining == null || daysRemaining <= 0 || shortfall.isZero) null else {
            val perWeek = shortfall.toDecimal()
                .multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(daysRemaining), goal.target.currency.fractionDigits, RoundingMode.CEILING)
            Money.parse(perWeek.toPlainString(), goal.target.currency)
        }
        return BackedGoalProgress(
            allocated = goal.allocated,
            backed = backed,
            allocationGap = if (unknown) null else (goal.allocated - backed).coerceAtLeastZero(),
            targetShortfall = shortfall,
            weeklyPace = pace,
            daysRemaining = daysRemaining,
            balanceAsOf = if (unknown) null else balanceDates.minOrNull(),
            hasUnknownBalance = unknown,
            isApproximate = approximate,
            hasMismatchedEntries = mismatched,
        )
    }
}
