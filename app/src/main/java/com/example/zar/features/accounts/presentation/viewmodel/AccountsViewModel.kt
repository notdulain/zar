package com.example.zar.features.accounts.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.accounts.domain.model.AccountsOverview
import com.example.zar.features.accounts.domain.usecase.ObserveAccountsOverviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AccountsUiState(
    val isLoading: Boolean = true,
    val overview: AccountsOverview? = null,
    val loadFailed: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    observeOverview: ObserveAccountsOverviewUseCase,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = observeOverview()
        .map { AccountsUiState(isLoading = false, overview = it) }
        .catch { emit(AccountsUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}
