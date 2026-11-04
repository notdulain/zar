package com.example.zar.features.plans.domain.model

import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.calculation.active
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
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
    /** One goal in isolation: what [calculateAll] gives when it is the only plan. */
    fun calculate(goal: SavingsGoal, accounts: List<Account>, entries: List<LedgerEntry>, today: LocalDate): BackedGoalProgress =
        calculateAll(listOf(goal), accounts, entries, today).getValue(goal.id)

    /**
     * Backing for every plan at once, conserving money. When the plans sharing an account ask for more than its usable
     * balance, each plan is backed by its proportional share, rounded down to the currency's smallest unit, so the same funds are
     * never counted as backing two plans and the result does not depend on plan order. Unknown, mismatched or future
     * balances stay unknown as before; a plan's [BackedGoalProgress.allocationGap] shows the shortfall.
     */
    fun calculateAll(goals: List<SavingsGoal>, accounts: List<Account>, entries: List<LedgerEntry>, today: LocalDate): Map<GoalId, BackedGoalProgress> {
        val byId = accounts.associateBy { it.id }
        val balances = HashMap<AccountId, AccountBalance?>()
        fun balanceOf(account: Account): AccountBalance = balances.getOrPut(account.id) {
            AccountBalanceCalculator.calculate(account, entries, today)
        }!!
        fun usable(goal: SavingsGoal, allocation: GoalAllocation): Account? =
            byId[allocation.accountId]?.takeIf { it.currency == goal.target.currency && it.openingDate <= today }

        // What every plan together asks of each account, counting only requests the account can actually serve.
        val requested = HashMap<AccountId, Money>()
        goals.forEach { goal ->
            goal.allocations.forEach { allocation ->
                val account = usable(goal, allocation) ?: return@forEach
                requested[account.id] = (requested[account.id] ?: Money.zero(account.currency)) + allocation.amount
            }
        }

        return goals.associate { goal -> goal.id to progressFor(goal, requested, ::usable, ::balanceOf, entries, today) }
    }

    private fun progressFor(
        goal: SavingsGoal,
        requested: Map<AccountId, Money>,
        usable: (SavingsGoal, GoalAllocation) -> Account?,
        balanceOf: (Account) -> AccountBalance,
        entries: List<LedgerEntry>,
        today: LocalDate,
    ): BackedGoalProgress {
        var backed = Money.zero(goal.target.currency)
        var unknown = false
        var approximate = false
        var mismatched = false
        val balanceDates = mutableListOf<LocalDate>()

        goal.allocations.forEach { allocation ->
            val account = usable(goal, allocation)
            if (account == null) {
                unknown = true
                return@forEach
            }
            val balance = balanceOf(account)
            mismatched = mismatched || balance.mismatchedCount > 0
            if (balance.current == null || balance.mismatchedCount > 0) {
                unknown = true
                return@forEach
            }
            approximate = approximate || balance.isApproximate
            backed += share(allocation.amount, balance.current.coerceAtLeastZero(), requested.getValue(account.id))
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

    /** This allocation's slice of [available] when everyone asked for [totalRequested]: whole when it fits, else proportional, rounded down. */
    private fun share(amount: Money, available: Money, totalRequested: Money): Money {
        if (available >= totalRequested) return amount
        if (!available.isPositive) return Money.zero(amount.currency)
        val scaled = amount.toDecimal().multiply(available.toDecimal())
            .divide(totalRequested.toDecimal(), amount.currency.fractionDigits, RoundingMode.DOWN)
        return Money.parse(scaled.toPlainString(), amount.currency)?.let { if (it > amount) amount else it }
            ?: Money.zero(amount.currency)
    }
}
