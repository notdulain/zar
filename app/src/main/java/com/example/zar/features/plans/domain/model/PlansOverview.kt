package com.example.zar.features.plans.domain.model

import com.example.zar.core.domain.calculation.AccountBalance

/**
 * Every saved plan plus the one the user picked as main. [goal], [allocationAccounts] and [progress] describe the main
 * goal, as before. [goals] is the whole catalog in a stable order (name, then ID), so a plan never vanishes from the list
 * because another one is main.
 */
data class PlansOverview(
    val goal: SavingsGoal?,
    val allocationAccounts: List<AccountBalance>,
    val progress: BackedGoalProgress? = null,
    val goals: List<SavingsGoal> = listOfNotNull(goal),
    val progressByGoal: Map<GoalId, BackedGoalProgress> = if (goal != null && progress != null) mapOf(goal.id to progress) else emptyMap(),
    val accountsByGoal: Map<GoalId, List<AccountBalance>> = if (goal != null) mapOf(goal.id to allocationAccounts) else emptyMap(),
) {
    val mainGoalId: GoalId? get() = goal?.id

    fun goalById(id: GoalId): SavingsGoal? = goals.firstOrNull { it.id == id }
    fun progressFor(id: GoalId): BackedGoalProgress? = progressByGoal[id]
    fun allocationAccountsFor(id: GoalId): List<AccountBalance> = accountsByGoal[id].orEmpty()
}
