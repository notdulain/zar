package com.example.zar.features.plans.domain.repository

import com.example.zar.features.plans.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

/** User-owned savings goals. Implementations must remain usable from Firestore's offline cache. */
interface GoalRepository {
    fun observeGoals(): Flow<List<SavingsGoal>>
    suspend fun saveGoal(goal: SavingsGoal)
}
