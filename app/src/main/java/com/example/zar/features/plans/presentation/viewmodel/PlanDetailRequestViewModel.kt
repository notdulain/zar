package com.example.zar.features.plans.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.usecase.PlanDetailRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Lets the Plans tab pick up a request, for example from Home, to open one plan's detail. */
@HiltViewModel
class PlanDetailRequestViewModel @Inject constructor(
    private val requests: PlanDetailRequests,
) : ViewModel() {
    val pending: StateFlow<GoalId?> = requests.pending

    fun consume(id: GoalId) = requests.consume(id)
}
