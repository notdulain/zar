package com.example.zar.features.transactions.presentation.activity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivityList
import com.example.zar.features.transactions.domain.activity.model.ActivitySnapshot
import com.example.zar.features.transactions.domain.activity.usecase.ObserveActivityUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

sealed interface ActivityContent {
    data object Loading : ActivityContent
    data object Error : ActivityContent
    data class Ready(val list: ActivityList) : ActivityContent
}

data class ActivityUiState(
    val filter: ActivityFilter = ActivityFilter(),
    val content: ActivityContent = ActivityContent.Loading,
    /** Accounts offered in the filter sheet (archived ones are left out). */
    val accounts: List<Account> = emptyList(),
    /** Activity always shows what is stored on the phone; this only lets it say so while offline. */
    val isOnline: Boolean = true,
    val today: LocalDate = ZarTime.today(),
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    observeActivity: ObserveActivityUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter())
    private val attempt = MutableStateFlow(0)
    private var latest: ActivitySnapshot? = null

    private sealed interface Source {
        data object Loading : Source
        data object Failed : Source
        data class Ready(val snapshot: ActivitySnapshot) : Source
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source: Flow<Source> = attempt.flatMapLatest {
        observeActivity()
            .map<ActivitySnapshot, Source> { Source.Ready(it) }
            .onStart { emit(Source.Loading) }
            .catch { emit(Source.Failed) }
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        source,
        filter,
        observeOnline().catch { emit(true) },
    ) { src, selected, online ->
        val today = ZarTime.today()
        when (src) {
            Source.Loading -> ActivityUiState(selected, ActivityContent.Loading, isOnline = online, today = today)
            Source.Failed -> ActivityUiState(selected, ActivityContent.Error, isOnline = online, today = today)
            is Source.Ready -> {
                latest = src.snapshot
                ActivityUiState(
                    filter = selected,
                    content = ActivityContent.Ready(ActivityQuery.apply(src.snapshot, selected, today)),
                    accounts = src.snapshot.accounts.filterNot { it.isArchived },
                    isOnline = online,
                    today = today,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun applyFilter(value: ActivityFilter) = filter.update { value.copy(query = it.query) }

    /** Clears every filter and the search. */
    fun clearFilters() = filter.update { ActivityFilter() }

    fun setQuery(value: String) = filter.update { it.copy(query = value) }

    /** Tries again after a loading error. */
    fun retry() = attempt.update { it + 1 }

    /** How many entries a filter would show, so the sheet can say it before it is applied. */
    fun previewCount(candidate: ActivityFilter): Int =
        latest?.let { ActivityQuery.apply(it, candidate, ZarTime.today()).rows.size } ?: 0
}
