package com.example.zar.features.plans.domain.model

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.coerceAtLeastZero

@JvmInline
value class GoalId(val value: String)

/** Money the user explicitly reserved from one account for one goal. */
data class GoalAllocation(
    val accountId: AccountId,
    val amount: Money,
) {
    init {
        require(amount.isPositive) { "An allocation must be positive" }
    }
}

/** A target and its explicit allocations. Account balances alone never increase progress. */
data class SavingsGoal(
    val id: GoalId,
    val name: String,
    val target: Money,
    val allocations: List<GoalAllocation> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Goal name is required" }
        require(name.length <= 80) { "Goal name must be 80 characters or fewer" }
        require(target.isPositive) { "Goal target must be positive" }
        require(allocations.map { it.accountId }.distinct().size == allocations.size) {
            "A goal can have only one allocation per account"
        }
        require(allocations.all { it.amount.currency == target.currency }) {
            "Goal allocations must use ${target.currency.code}"
        }
        require(allocated <= target) { "Allocated money cannot exceed the goal target" }
    }

    val allocated: Money
        get() = allocations.fold(Money.zero(target.currency)) { total, allocation -> total + allocation.amount }

    val remaining: Money get() = (target - allocated).coerceAtLeastZero()

    val progress: Float
        get() = if (target.isZero) 0f else {
            allocated.toDecimal().divide(target.toDecimal(), 6, java.math.RoundingMode.HALF_UP)
                .toFloat()
                .coerceIn(0f, 1f)
        }
}

const val MACBOOK_GOAL_ID = "macbook-fund"
