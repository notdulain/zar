package com.example.zar.features.spending.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.navigation.SpendingEntriesRoute
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivityList
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import com.example.zar.features.transactions.domain.activity.usecase.ObserveActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface SpendingEntriesContent {
    data object Loading : SpendingEntriesContent
    data object Error : SpendingEntriesContent
    data class Ready(val list: ActivityList) : SpendingEntriesContent
}

data class SpendingEntriesUiState(
    val month: YearMonth,
    val spendingClass: SpendingClass,
    /** The breakdown row key, or null for every category in the class. */
    val categoryKey: String?,
    val content: SpendingEntriesContent,
)

/** The entries behind one breakdown row: the Activity query, narrowed to the month, class and category. Read-only. */
@HiltViewModel
class SpendingEntriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeActivity: ObserveActivityUseCase,
) : ViewModel() {

    private val month = savedStateHandle.get<String>(SpendingEntriesRoute::month.name)
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(ZarTime.today())
    private val spendingClass = savedStateHandle.get<String>(SpendingEntriesRoute::spendingClass.name)
        ?.let { name -> SpendingClass.entries.firstOrNull { it.name == name } } ?: SpendingClass.UNKNOWN
    private val categoryKey = savedStateHandle.get<String>(SpendingEntriesRoute::categoryKey.name)

    private val filter = ActivityFilter(
        preset = DatePreset.CUSTOM,
        customFrom = month.atDay(1),
        customTo = month.atEndOfMonth(),
        spendingClass = spendingClass,
        categoryKey = categoryKey,
    )
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SpendingEntriesUiState> = attempt
        .flatMapLatest {
            observeActivity()
                .map<_, SpendingEntriesContent> { SpendingEntriesContent.Ready(ActivityQuery.apply(it, filter, ZarTime.today())) }
                .onStart { emit(SpendingEntriesContent.Loading) }
                .catch { emit(SpendingEntriesContent.Error) }
        }
        .map { SpendingEntriesUiState(month, spendingClass, categoryKey, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingEntriesUiState(month, spendingClass, categoryKey, SpendingEntriesContent.Loading))

    fun retry() = attempt.update { it + 1 }
}
