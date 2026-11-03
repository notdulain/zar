package com.example.zar.features.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.features.allowance.domain.model.AllowanceOverview
import com.example.zar.features.allowance.domain.usecase.ObserveAllowanceUseCase
import com.example.zar.features.home.domain.model.HomeCommitments
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.domain.usecase.ObserveHomeCommitmentsUseCase
import com.example.zar.features.home.domain.usecase.ObserveHomeOverviewUseCase
import com.example.zar.features.plans.domain.model.BackedGoalProgress
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.usecase.ObservePlansOverviewUseCase
import com.example.zar.features.plans.domain.usecase.PlanDetailRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface HomeContent {
    data object Loading : HomeContent
    data object Error : HomeContent
    data class Ready(val overview: HomeOverview) : HomeContent
}

/** Clients owed and the next bill. Loaded on its own so a failure here never hides the totals. */
sealed interface HomeCommitmentsContent {
    data object Loading : HomeCommitmentsContent
    data object Error : HomeCommitmentsContent
    data class Ready(val commitments: HomeCommitments) : HomeCommitmentsContent
}

/** The weekly allowance snapshot. Also loaded on its own, so it never hides the totals. */
sealed interface HomeAllowanceContent {
    data object Loading : HomeAllowanceContent
    data object Error : HomeAllowanceContent
    data class Ready(val overview: AllowanceOverview) : HomeAllowanceContent
}

/** The selected main plan, loaded on its own so a failure here never hides the totals. [Empty] means no plan exists yet. */
sealed interface HomeGoalContent {
    data object Loading : HomeGoalContent
    data object Error : HomeGoalContent
    data object Empty : HomeGoalContent
    data class Ready(val goal: SavingsGoal, val progress: BackedGoalProgress?) : HomeGoalContent
}

data class HomeUiState(
    val month: YearMonth,
    /** False on the current month: Home never shows a future month. */
    val canGoForward: Boolean,
    val content: HomeContent,
    val commitments: HomeCommitmentsContent = HomeCommitmentsContent.Loading,
    val allowance: HomeAllowanceContent = HomeAllowanceContent.Loading,
    val goal: HomeGoalContent = HomeGoalContent.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOverview: ObserveHomeOverviewUseCase,
    observeCommitments: ObserveHomeCommitmentsUseCase,
    observeAllowance: ObserveAllowanceUseCase,
    observePlans: ObservePlansOverviewUseCase,
    private val planDetailRequests: PlanDetailRequests,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val overviewState: Flow<HomeUiState> = combine(month, attempt) { selected, _ -> selected }
        .flatMapLatest { selected ->
            observeOverview(selected)
                .map<HomeOverview, HomeContent> { HomeContent.Ready(it) }
                .onStart { emit(HomeContent.Loading) }
                .catch { emit(HomeContent.Error) }
                .map { HomeUiState(selected, selected < currentMonth(), it) }
        }

    // Owed money and the next bill depend on today, not on the month being viewed.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val commitmentsState: Flow<HomeCommitmentsContent> = attempt.flatMapLatest {
        observeCommitments(ZarTime.today())
            .map<HomeCommitments, HomeCommitmentsContent> { HomeCommitmentsContent.Ready(it) }
            .onStart { emit(HomeCommitmentsContent.Loading) }
            .catch { emit(HomeCommitmentsContent.Error) }
    }

    // This week's allowance depends on today, like the commitments.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allowanceState: Flow<HomeAllowanceContent> = attempt.flatMapLatest {
        observeAllowance()
            .map<AllowanceOverview, HomeAllowanceContent> { HomeAllowanceContent.Ready(it) }
            .onStart { emit(HomeAllowanceContent.Loading) }
            .catch { emit(HomeAllowanceContent.Error) }
    }

    // The same selected main plan the Plans tab shows, from the same use case.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val goalState: Flow<HomeGoalContent> = attempt.flatMapLatest {
        observePlans()
            .map<PlansOverview, HomeGoalContent> { plans ->
                plans.goal?.let { HomeGoalContent.Ready(it, plans.progress) } ?: HomeGoalContent.Empty
            }
            .onStart { emit(HomeGoalContent.Loading) }
            .catch { emit(HomeGoalContent.Error) }
    }

    val uiState: StateFlow<HomeUiState> = combine(overviewState, commitmentsState, allowanceState, goalState) { overview, commitments, allowance, goal ->
        overview.copy(commitments = commitments, allowance = allowance, goal = goal)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(month.value, canGoForward = false, content = HomeContent.Loading),
        )

    fun showPreviousMonth() = month.update { it.minusMonths(1) }

    fun showNextMonth() = month.update { if (it < currentMonth()) it.plusMonths(1) else it }

    /** Asks the Plans tab to open this plan's detail once Home switches to it. */
    fun openGoal(id: GoalId) = planDetailRequests.request(id)

    /** Tries again after a loading error. */
    fun retry() = attempt.update { it + 1 }

    private fun currentMonth(): YearMonth = YearMonth.from(ZarTime.today())
}
