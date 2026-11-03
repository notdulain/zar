package com.example.zar.features.plans.domain.model

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.coerceAtLeastZero
import java.time.LocalDate

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
    val targetDate: LocalDate? = null,
) {
    init {
        require(name.isNotBlank()) { "Goal name is required" }
        require(name.length <= 80) { "Goal name must be 80 characters or fewer" }
        require(target.isPositive) { "Goal target must be positive" }
        require(allocations.size <= 10) { "A goal can allocate from at most 10 accounts" }
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

/** The ID of the single goal Zar created before multiple plans. It is a normal goal now; it keeps its ID forever. */
const val LEGACY_GOAL_ID = "macbook-fund"

/** Kept for existing call sites and tests of the legacy record. New goals get a [newGoalId], never this. */
const val MACBOOK_GOAL_ID = LEGACY_GOAL_ID

/** A fresh opaque ID for a new goal. Generate it once per create attempt and reuse it when the save is retried. */
fun newGoalId(): GoalId = GoalId(java.util.UUID.randomUUID().toString())

/** Which goal is the user's main one, from a saved choice, the legacy goal, or a stable fallback. */
object MainGoalResolver {
    /**
     * [preferredId] wins when that goal exists. Otherwise the legacy goal stays main so upgraded users see no change,
     * then the first goal by name and ID so every device resolves the same one. Never changes because another goal was added
     * while a valid preference exists, and returns null only for an empty catalog.
     */
    fun resolve(goals: List<SavingsGoal>, preferredId: GoalId?): GoalId? =
        goals.firstOrNull { it.id == preferredId }?.id
            ?: goals.firstOrNull { it.id.value == LEGACY_GOAL_ID }?.id
            ?: goals.minWithOrNull(compareBy({ it.name.lowercase() }, { it.id.value }))?.id
}
