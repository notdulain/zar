package com.example.zar.features.catchup.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.features.catchup.domain.model.UnresolvedItem
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NeedsReviewUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val items: List<UnresolvedItem> = emptyList(),
    /** Whole days since the last finished catch-up, or null when there has been none. */
    val daysSinceCatchUp: Int? = null,
)

@HiltViewModel
class NeedsReviewViewModel @Inject constructor(
    observeCatchUp: ObserveCatchUpUseCase,
) : ViewModel() {

    val uiState: StateFlow<NeedsReviewUiState> = observeCatchUp()
        .map { overview ->
            NeedsReviewUiState(
                isLoading = false,
                items = overview.items,
                daysSinceCatchUp = overview.lastCatchUp?.let { ChronoUnit.DAYS.between(it, ZarTime.today()).toInt().coerceAtLeast(0) },
            )
        }
        .catch { emit(NeedsReviewUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NeedsReviewUiState())
}
