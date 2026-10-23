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

data class HomeUiState(
    val month: YearMonth,
    /** False on the current month: Home never shows a future month. */
    val canGoForward: Boolean,
    val content: HomeContent,
    val commitments: HomeCommitmentsContent = HomeCommitmentsContent.Loading,
    val allowance: HomeAllowanceContent = HomeAllowanceContent.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOverview: ObserveHomeOverviewUseCase,
    observeCommitments: ObserveHomeCommitmentsUseCase,
    observeAllowance: ObserveAllowanceUseCase,
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
        observeAllowance(ZarTime.today())
            .map<AllowanceOverview, HomeAllowanceContent> { HomeAllowanceContent.Ready(it) }
            .onStart { emit(HomeAllowanceContent.Loading) }
            .catch { emit(HomeAllowanceContent.Error) }
    }

    val uiState: StateFlow<HomeUiState> = combine(overviewState, commitmentsState, allowanceState) { overview, commitments, allowance ->
        overview.copy(commitments = commitments, allowance = allowance)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(month.value, canGoForward = false, content = HomeContent.Loading),
        )

    fun showPreviousMonth() = month.update { it.minusMonths(1) }

    fun showNextMonth() = month.update { if (it < currentMonth()) it.plusMonths(1) else it }

    /** Tries again after a loading error. */
    fun retry() = attempt.update { it + 1 }

    private fun currentMonth(): YearMonth = YearMonth.from(ZarTime.today())
}
