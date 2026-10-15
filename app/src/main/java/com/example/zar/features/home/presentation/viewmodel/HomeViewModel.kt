package com.example.zar.features.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.domain.usecase.ObserveHomeOverviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

data class HomeUiState(
    val month: YearMonth,
    /** False on the current month: Home never shows a future month. */
    val canGoForward: Boolean,
    val content: HomeContent,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOverview: ObserveHomeOverviewUseCase,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(month, attempt) { selected, _ -> selected }
        .flatMapLatest { selected ->
            observeOverview(selected)
                .map<HomeOverview, HomeContent> { HomeContent.Ready(it) }
                .onStart { emit(HomeContent.Loading) }
                .catch { emit(HomeContent.Error) }
                .map { HomeUiState(selected, selected < currentMonth(), it) }
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
