package com.example.zar.features.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.settings.domain.usecase.ObserveAppPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val isLoading: Boolean = true,
    val currencyCode: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAppPreferences: ObserveAppPreferencesUseCase,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = observeAppPreferences()
        .map { SettingsUiState(isLoading = false, currencyCode = it.currencyCode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
}
