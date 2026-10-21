package com.example.zar.features.bills.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.bills.domain.RecurringSuggestion
import com.example.zar.features.bills.domain.usecase.ConfirmSuggestionUseCase
import com.example.zar.features.bills.domain.usecase.DismissSuggestionUseCase
import com.example.zar.features.bills.domain.usecase.ObserveRecurringSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SubscriptionSuggestionsViewModel @Inject constructor(
    observeSuggestions: ObserveRecurringSuggestionsUseCase,
    private val confirmSuggestion: ConfirmSuggestionUseCase,
    private val dismissSuggestion: DismissSuggestionUseCase,
) : ViewModel() {
    val suggestions: StateFlow<List<RecurringSuggestion>> = observeSuggestions()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun confirm(suggestion: RecurringSuggestion) = viewModelScope.launch {
        _failed.value = confirmSuggestion(suggestion, Instant.now()).isFailure
    }

    fun dismiss(suggestion: RecurringSuggestion) = viewModelScope.launch {
        _failed.value = dismissSuggestion(suggestion).isFailure
    }
}

