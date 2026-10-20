package com.example.zar.features.accounts.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.navigation.AccountRoute
import com.example.zar.features.accounts.domain.model.AccountDetail
import com.example.zar.features.accounts.domain.usecase.ObserveAccountDetailUseCase
import com.example.zar.features.accounts.domain.usecase.SetAccountArchivedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val detail: AccountDetail? = null,
    val loadFailed: Boolean = false,
    val isWorking: Boolean = false,
    val actionFailed: Boolean = false,
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObserveAccountDetailUseCase,
    private val setArchived: SetAccountArchivedUseCase,
) : ViewModel() {

    private val accountId = AccountId(savedStateHandle.toRoute<AccountRoute>().accountId)

    private val action = MutableStateFlow(ActionState())

    val uiState: StateFlow<AccountDetailUiState> = combine(observeDetail(accountId), action) { detail, action ->
        AccountDetailUiState(
            isLoading = false,
            detail = detail,
            isWorking = action.isWorking,
            actionFailed = action.failed,
        )
    }
        .catch { emit(AccountDetailUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())

    /** Archives the account, or restores it when it is already archived. */
    fun toggleArchived() {
        val account = uiState.value.detail?.account ?: return
        if (action.value.isWorking) return
        viewModelScope.launch {
            action.update { ActionState(isWorking = true) }
            val result = setArchived(account, archived = !account.isArchived)
            action.update { ActionState(failed = result.isFailure) }
        }
    }

    private data class ActionState(val isWorking: Boolean = false, val failed: Boolean = false)
}
