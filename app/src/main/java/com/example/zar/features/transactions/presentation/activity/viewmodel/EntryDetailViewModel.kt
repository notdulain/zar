package com.example.zar.features.transactions.presentation.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.core.navigation.EntryDetailRoute
import com.example.zar.features.transactions.domain.activity.model.EntryDetail
import com.example.zar.features.transactions.domain.activity.usecase.ObserveEntryDetailUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EntryDetailContent {
    data object Loading : EntryDetailContent
    data object NotFound : EntryDetailContent
    data object Error : EntryDetailContent
    data class Ready(val detail: EntryDetail) : EntryDetailContent
}

enum class VoidProblem { NOT_SIGNED_IN, FAILED }

data class EntryDetailUiState(
    val content: EntryDetailContent = EntryDetailContent.Loading,
    val isOnline: Boolean = true,
    val showVoidSheet: Boolean = false,
    val isVoiding: Boolean = false,
    val voidProblem: VoidProblem? = null,
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObserveEntryDetailUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
    private val voidTransaction: VoidTransactionUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the ViewModel can be created in plain unit tests.
    private val entryId = TransactionId(savedStateHandle.get<String>(EntryDetailRoute::entryId.name).orEmpty())

    private data class Local(val showSheet: Boolean = false, val isVoiding: Boolean = false, val problem: VoidProblem? = null)

    private val local = MutableStateFlow(Local())

    private val content: Flow<EntryDetailContent> = observeDetail(entryId)
        .map<EntryDetail?, EntryDetailContent> { detail -> detail?.let { EntryDetailContent.Ready(it) } ?: EntryDetailContent.NotFound }
        .onStart { emit(EntryDetailContent.Loading) }
        .catch { emit(EntryDetailContent.Error) }

    val uiState: StateFlow<EntryDetailUiState> = combine(
        content,
        observeOnline().catch { emit(true) },
        local,
    ) { content, online, local ->
        EntryDetailUiState(content, online, local.showSheet, local.isVoiding, local.problem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailUiState())

    fun showVoidSheet() = local.update { it.copy(showSheet = true, problem = null) }

    fun dismissVoidSheet() = local.update { if (it.isVoiding) it else it.copy(showSheet = false, problem = null) }

    /** Undoes the entry with a durable void marker. Works offline: the marker syncs when the phone is back online. */
    fun confirmVoid() {
        val detail = (uiState.value.content as? EntryDetailContent.Ready)?.detail ?: return
        if (!detail.canVoid || local.value.isVoiding) return
        local.update { it.copy(isVoiding = true, problem = null) }
        viewModelScope.launch {
            voidTransaction(detail.entry, Instant.now()).fold(
                onSuccess = { local.update { Local() } },
                onFailure = { error ->
                    val problem = if (error is LedgerError.NotSignedIn) VoidProblem.NOT_SIGNED_IN else VoidProblem.FAILED
                    local.update { it.copy(isVoiding = false, problem = problem) }
                },
            )
        }
    }
}
