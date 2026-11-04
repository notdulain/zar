package com.example.zar.features.plans.domain.repository

import com.example.zar.features.plans.domain.model.GoalId
import kotlinx.coroutines.flow.Flow

/** The signed-in user's synced Plans choices. Never global: a different user resolves their own. */
interface PlanPreferencesRepository {
    /** The saved main-goal ID, or null when none was chosen (or while signed out). The goal itself may no longer exist. */
    fun observeMainGoalId(): Flow<GoalId?>
    suspend fun setMainGoalId(id: GoalId)
}
