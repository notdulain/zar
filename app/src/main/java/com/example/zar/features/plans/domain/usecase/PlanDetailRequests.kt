package com.example.zar.features.plans.domain.usecase

import com.example.zar.features.plans.domain.model.GoalId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-shot ask from another screen, such as Home, to open one plan's detail on the Plans tab. The detail itself keeps
 * its own saved state by plan ID; this only carries the ID across the tab switch until Plans takes it.
 */
@Singleton
class PlanDetailRequests @Inject constructor() {
    private val _pending = MutableStateFlow<GoalId?>(null)
    val pending: StateFlow<GoalId?> = _pending.asStateFlow()

    fun request(id: GoalId) {
        _pending.value = id
    }

    /** Clears the request only if it is still [id], so a newer request is never lost. */
    fun consume(id: GoalId) {
        _pending.compareAndSet(id, null)
    }
}
