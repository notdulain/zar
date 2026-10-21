package com.example.zar.features.capture.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.navigation.CaptureReviewRoute
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.usecase.CaptureHealth
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import com.example.zar.features.capture.domain.usecase.ConfirmCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.DiscardCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureAccountsUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureHealthUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCapturedDraftsUseCase
import com.example.zar.features.capture.presentation.SharedTextInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureMessage { NOT_SIGNED_IN, CURRENCY, FAILED }

private fun Throwable.toCaptureMessage(): CaptureMessage = when (this) {
    is LedgerError.NotSignedIn -> CaptureMessage.NOT_SIGNED_IN
    is LedgerError.CurrencyMismatch -> CaptureMessage.CURRENCY
    else -> CaptureMessage.FAILED
}

/** Lets the navigation host open the capture screen when text is shared to Zar. */
@HiltViewModel
class ShareLaunchViewModel @Inject constructor(inbox: SharedTextInbox) : ViewModel() {
    val hasSharedText: StateFlow<Boolean> = inbox.hasText
}

// --- Capture screen ------------------------------------------------------------------------

data class CaptureUiState(
    val text: String = "",
    val cameFromShare: Boolean = false,
    val outcome: CaptureOutcome? = null,
    val pending: List<Draft> = emptyList(),
    val health: CaptureHealth? = null,
    val message: CaptureMessage? = null,
    val isReading: Boolean = false,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val inbox: SharedTextInbox,
    private val captureText: CaptureTextUseCase,
    observeDrafts: ObserveCapturedDraftsUseCase,
    observeHealth: ObserveCaptureHealthUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeDrafts(), observeHealth()) { drafts, health -> drafts to health }
                .catch { _uiState.update { it.copy(message = CaptureMessage.FAILED) } }
                .collect { (drafts, health) -> _uiState.update { it.copy(pending = drafts, health = health) } }
        }
        takeShared()
    }

    /** Reads text shared from another app straight away; it never needs to be typed or kept. */
    fun takeShared() {
        val shared = inbox.take() ?: return
        _uiState.update { it.copy(text = shared, cameFromShare = true) }
        read(DraftSource.SHARED_TEXT)
    }

    fun onTextChange(value: String) = _uiState.update { it.copy(text = value, outcome = null, message = null) }

    fun read(source: DraftSource = DraftSource.SHARED_TEXT) {
        val state = _uiState.value
        if (state.isReading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReading = true, message = null) }
            captureText(state.text, source, Instant.now()).fold(
                // The text is cleared once read, so it isn't kept on screen or in memory longer than needed.
                onSuccess = { outcome ->
                    _uiState.update { it.copy(isReading = false, outcome = outcome, text = if (outcome is CaptureOutcome.Drafted) "" else it.text) }
                },
                onFailure = { e -> _uiState.update { it.copy(isReading = false, message = e.toCaptureMessage()) } },
            )
        }
    }
}

// --- Review screen -------------------------------------------------------------------------

data class CaptureReviewUiState(
    val isLoading: Boolean = true,
    val draft: Draft? = null,
    val accounts: List<Account> = emptyList(),
    val accountId: AccountId? = null,
    val type: TransactionType? = null,
    val spendingClass: SpendingClass = SpendingClass.UNKNOWN,
    val showErrors: Boolean = false,
    val message: CaptureMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val proposal: DraftProposal.ActualTransaction? get() = draft?.proposals?.singleOrNull() as? DraftProposal.ActualTransaction

    /** Only accounts in the message's currency can hold it. */
    val matchingAccounts: List<Account> get() = proposal?.amount?.currency?.let { c -> accounts.filter { it.currency == c } } ?: accounts
}

@HiltViewModel
class CaptureReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDrafts: ObserveCapturedDraftsUseCase,
    observeAccounts: ObserveCaptureAccountsUseCase,
    private val confirmDraft: ConfirmCapturedDraftUseCase,
    private val discardDraft: DiscardCapturedDraftUseCase,
) : ViewModel() {
    private val draftId = DraftId(savedStateHandle.toRoute<CaptureReviewRoute>().draftId)

    private val _uiState = MutableStateFlow(CaptureReviewUiState())
    val uiState: StateFlow<CaptureReviewUiState> = _uiState.asStateFlow()
    private var filled = false

    init {
        viewModelScope.launch {
            combine(observeDrafts(), observeAccounts()) { drafts, accounts -> drafts.firstOrNull { it.id == draftId } to accounts }
                .catch { _uiState.update { it.copy(isLoading = false, message = CaptureMessage.FAILED) } }
                .collect { (draft, accounts) ->
                    _uiState.update { state ->
                        var next = state.copy(isLoading = false, draft = draft ?: state.draft.takeIf { state.isFinished }, accounts = accounts)
                        if (!filled && draft != null) {
                            filled = true
                            val proposal = draft.proposals.singleOrNull() as? DraftProposal.ActualTransaction
                            next = next.copy(
                                type = proposal?.type?.takeIf { it in CapturedDraftRules.CONFIRMABLE_TYPES },
                                accountId = next.matchingAccounts.singleOrNull()?.id,
                            )
                        }
                        next
                    }
                }
        }
    }

    fun onAccountSelect(id: AccountId) = _uiState.update { it.copy(accountId = id, message = null) }
    fun onTypeSelect(type: TransactionType) = _uiState.update { it.copy(type = type, message = null) }
    fun onSpendingClassSelect(value: SpendingClass) = _uiState.update { it.copy(spendingClass = value) }

    fun confirm() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val account = state.accounts.firstOrNull { it.id == state.accountId }
        val type = state.type
        if (account == null || type == null) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        save { confirmDraft(draft, account, type, state.spendingClass.takeIf { type == TransactionType.EXPENSE }, Instant.now()) }
    }

    fun discard() {
        val draft = _uiState.value.draft ?: return
        save { discardDraft(draft) }
    }

    private fun save(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            block().fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toCaptureMessage()) } },
            )
        }
    }
}
