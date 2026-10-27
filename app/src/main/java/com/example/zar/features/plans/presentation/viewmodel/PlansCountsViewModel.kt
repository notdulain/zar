package com.example.zar.features.plans.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.features.bills.domain.usecase.ObserveBillsUseCase
import com.example.zar.features.freelance.domain.usecase.ObserveFreelanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What exists behind the Plans list rows. `null` while a count is still loading or could not be read. */
data class PlansCounts(val bills: Int? = null, val projects: Int? = null)

/** Real counts for the "Other plans" rows, from the same use cases the Bills and Freelance screens read. */
@HiltViewModel
class PlansCountsViewModel @Inject constructor(
    observeBills: ObserveBillsUseCase,
    observeFreelance: ObserveFreelanceUseCase,
) : ViewModel() {
    val counts: StateFlow<PlansCounts> = combine(
        observeBills(ZarTime.today()).map<_, Int?> { it.bills.size }.catch { emit(null) },
        observeFreelance(ZarTime.today()).map<_, Int?> { it.projects.size }.catch { emit(null) },
    ) { bills, projects -> PlansCounts(bills, projects) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansCounts())
}
