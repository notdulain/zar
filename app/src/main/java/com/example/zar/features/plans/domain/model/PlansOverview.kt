package com.example.zar.features.plans.domain.model

import com.example.zar.core.domain.calculation.AccountBalance

data class PlansOverview(
    val goal: SavingsGoal?,
    val allocationAccounts: List<AccountBalance>,
    val progress: BackedGoalProgress? = null,
)
