package com.example.zar.features.spending.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.navigation.SpendingRoute
import com.example.zar.features.spending.domain.model.SpendingBreakdownReport
import com.example.zar.features.spending.domain.usecase.ObserveSpendingBreakdownUseCase
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

sealed interface SpendingContent {
    data object Loading : SpendingContent
    data object Error : SpendingContent
    data class Ready(val report: SpendingBreakdownReport) : SpendingContent
}

data class SpendingUiState(
    val month: YearMonth,
    /** False on the current month: the breakdown never shows a future month. */
    val canGoForward: Boolean,
    val isCurrentMonth: Boolean,
    val content: SpendingContent,
)

@HiltViewModel
class SpendingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeBreakdown: ObserveSpendingBreakdownUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the ViewModel can be created in plain unit tests. The month lives in the saved
    // state, so the month the user stepped to survives process recreation and Back from a drill-down.
    private val handle = savedStateHandle
    private val month = MutableStateFlow(
        savedStateHandle.get<String>(SpendingRoute::month.name)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?.coerceAtMost(currentMonth()) ?: currentMonth(),
    )
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SpendingUiState> = combine(month, attempt) { selected, _ -> selected }
        .flatMapLatest { selected ->
            val content: Flow<SpendingContent> = observeBreakdown(selected)
                .map<SpendingBreakdownReport, SpendingContent> { SpendingContent.Ready(it) }
                .onStart { emit(SpendingContent.Loading) }
                .catch { emit(SpendingContent.Error) }
            content.map { SpendingUiState(selected, selected < currentMonth(), selected == currentMonth(), it) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SpendingUiState(month.value, month.value < currentMonth(), month.value == currentMonth(), SpendingContent.Loading),
        )

    fun showPreviousMonth() = step { it.minusMonths(1) }

    fun showNextMonth() = step { if (it < currentMonth()) it.plusMonths(1) else it }

    fun retry() = attempt.update { it + 1 }

    private fun step(change: (YearMonth) -> YearMonth) {
        month.update(change)
        handle[SpendingRoute::month.name] = month.value.toString()
    }

    private fun currentMonth(): YearMonth = YearMonth.from(ZarTime.today())
}
