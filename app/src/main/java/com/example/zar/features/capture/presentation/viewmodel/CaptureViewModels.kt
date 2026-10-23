package com.example.zar.features.capture.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.review.CaptureMatchRules
import com.example.zar.features.capture.domain.review.DuplicateMatch
import com.example.zar.features.capture.domain.review.TransferMatch
import com.example.zar.features.capture.domain.usecase.CaptureHealth
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import com.example.zar.features.capture.domain.usecase.ConfirmCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.DiscardCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.LinkCapturedDraftUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureLedgerUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureAccountsUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCaptureHealthUseCase
import com.example.zar.features.capture.domain.usecase.ObserveCapturedDraftsUseCase
import com.example.zar.features.capture.presentation.SharedTextInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureMessage { NOT_SIGNED_IN, CURRENCY, INVALID, FAILED }

private fun Throwable.toCaptureMessage(): CaptureMessage = when (this) {
    is LedgerError.NotSignedIn -> CaptureMessage.NOT_SIGNED_IN
    is LedgerError.CurrencyMismatch -> CaptureMessage.CURRENCY
    is IllegalArgumentException, is LedgerError.AccountArchived -> CaptureMessage.INVALID
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

    private var active = false
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeDrafts(), observeHealth()) { drafts, health -> drafts to health }
                .catch { _uiState.update { it.copy(message = CaptureMessage.FAILED) } }
                .collect { (drafts, health) -> _uiState.update { it.copy(pending = drafts, health = health) } }
        }
        viewModelScope.launch { inbox.revision.collect { takeShared() } }
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) takeShared()
    }

    /** Reads text shared from another app straight away; it never needs to be typed or kept. */
    fun takeShared() {
        if (!active || _uiState.value.isReading) return
        val shared = inbox.take() ?: return
        _uiState.update { it.copy(text = shared, cameFromShare = true) }
        read(DraftSource.SHARED_TEXT)
    }

    fun onTextChange(value: String) {
        if (!_uiState.value.isReading) _uiState.update { it.copy(text = value.take(16_384), outcome = null, message = null) }
    }

    fun read(source: DraftSource = DraftSource.SHARED_TEXT) {
        val state = _uiState.value
        if (state.isReading) return
        _uiState.update { it.copy(isReading = true, message = null) }
        viewModelScope.launch {
            captureText(state.text, source, Instant.now()).fold(
                // The text is cleared once read, so it isn't kept on screen or in memory longer than needed.
                onSuccess = { outcome ->
                    _uiState.update { it.copy(isReading = false, outcome = outcome, text = "") }
                },
                onFailure = { e -> _uiState.update { it.copy(isReading = false, text = "", message = e.toCaptureMessage()) } },
            )
            takeShared()
        }
    }
}

// --- Review screen -------------------------------------------------------------------------

/** What the person said about a message that is already counted. */
enum class SettleKind { SAME_PAYMENT, TRANSFER }

/** Where an "already counted" answer is. It is held back for a few seconds so it can be taken back before anything is written. */
enum class SettleStage { UNDO_WINDOW, SAVING, SAVED, QUEUED, FAILED }

/**
 * The message is attached to [target], an entry that already exists, and posts no money. [other] is the entry on the
 * other side when the message is one half of a transfer.
 */
data class CaptureSettlement(
    val kind: SettleKind,
    val target: LedgerEntry,
    val other: LedgerEntry? = null,
    val stage: SettleStage = SettleStage.UNDO_WINDOW,
)

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
    val confirmationStarted: Boolean = false,
    /** What the message is compared with. Empty when the entries could not be read (see [matchesUnavailable]). */
    val ledger: List<LedgerEntry> = emptyList(),
    val matchesUnavailable: Boolean = false,
    /** Other messages still waiting for review. */
    val waiting: List<Draft> = emptyList(),
    /** Entries the person said this message is not a second report of. */
    val keptApart: Set<TransactionId> = emptySet(),
    /** The person said this message is one half of a transfer between their own accounts. */
    val asTransfer: Boolean = false,
    val transferChoice: TransactionId? = null,
    val settlement: CaptureSettlement? = null,
) {
    val proposal: DraftProposal.ActualTransaction? get() = draft?.proposals?.singleOrNull() as? DraftProposal.ActualTransaction

    /** Only accounts in the message's currency can hold it. */
    val matchingAccounts: List<Account> get() = proposal?.amount?.currency?.let { c -> accounts.filter { it.currency == c } } ?: accounts

    /** Existing entries this may be a second report of, until the person has said it is not. */
    val duplicates: List<DuplicateMatch>
        get() {
            val current = draft ?: return emptyList()
            if (asTransfer || settlement != null) return emptyList()
            return CaptureMatchRules.possibleDuplicates(current, accountId, ledger, type).filter { it.entry.id !in keptApart }
        }

    /** Transfer legs this message would be the bank's record of. Only asked about once the person says it is a transfer. */
    val transferMatches: List<TransferMatch>
        get() {
            val current = draft ?: return emptyList()
            if (!asTransfer) return emptyList()
            return CaptureMatchRules.transferCounterparts(current, accountId, ledger, type)
        }

    /** Other waiting messages that look like the same payment. */
    val twins: List<Draft> get() = draft?.let { CaptureMatchRules.pendingTwins(it, waiting) }.orEmpty()
}

@HiltViewModel
class CaptureReviewViewModel internal constructor(
    savedStateHandle: SavedStateHandle,
    observeDrafts: ObserveCapturedDraftsUseCase,
    observeAccounts: ObserveCaptureAccountsUseCase,
    observeLedger: ObserveCaptureLedgerUseCase,
    private val confirmDraft: ConfirmCapturedDraftUseCase,
    private val discardDraft: DiscardCapturedDraftUseCase,
    private val linkDraft: LinkCapturedDraftUseCase,
    private val now: () -> Instant,
    private val undoWindowMs: Long,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeDrafts: ObserveCapturedDraftsUseCase,
        observeAccounts: ObserveCaptureAccountsUseCase,
        observeLedger: ObserveCaptureLedgerUseCase,
        confirmDraft: ConfirmCapturedDraftUseCase,
        discardDraft: DiscardCapturedDraftUseCase,
        linkDraft: LinkCapturedDraftUseCase,
    ) : this(savedStateHandle, observeDrafts, observeAccounts, observeLedger, confirmDraft, discardDraft, linkDraft, Instant::now, UNDO_WINDOW_MS)

    // Read by name (the route's argument) instead of toRoute so the ViewModel can be created in plain unit tests.
    private val draftId = DraftId(requireNotNull(savedStateHandle.get<String>("draftId")))

    private val _uiState = MutableStateFlow(CaptureReviewUiState())
    val uiState: StateFlow<CaptureReviewUiState> = _uiState.asStateFlow()
    private var filled = false
    private var undoTimer: Job? = null

    init {
        // Matching is a help, never a gate: if the entries cannot be read the review still works, and says so.
        val ledgerOrNull = observeLedger().map<List<LedgerEntry>, List<LedgerEntry>?> { it }.catch { emit(null) }
        viewModelScope.launch {
            combine(observeDrafts(), observeAccounts(), ledgerOrNull) { drafts, accounts, ledger -> Triple(drafts, accounts, ledger) }
                .catch { _uiState.update { it.copy(isLoading = false, message = CaptureMessage.FAILED) } }
                .collect { (drafts, accounts, ledger) ->
                    val draft = drafts.firstOrNull { it.id == draftId }
                    _uiState.update { state ->
                        var next = state.copy(
                            isLoading = false,
                            // A message that was just closed leaves the waiting list: keep showing what was decided.
                            draft = draft ?: state.draft.takeIf { state.isFinished || state.isSaving || state.settlement != null },
                            accounts = accounts,
                            ledger = ledger.orEmpty(),
                            matchesUnavailable = ledger == null,
                            waiting = drafts.filter { it.id != draftId },
                        )
                        if (!filled && draft != null) {
                            filled = true
                            val proposal = draft.proposals.singleOrNull() as? DraftProposal.ActualTransaction
                            next = next.copy(
                                type = proposal?.type?.takeIf { it in CapturedDraftRules.CONFIRMABLE_TYPES && DraftField.TYPE !in proposal.uncertainFields },
                                accountId = next.matchingAccounts.singleOrNull()?.id,
                            )
                        }
                        next
                    }
                }
        }
    }

    private fun canEdit(): Boolean = _uiState.value.run { !confirmationStarted && !isSaving && settlement == null }

    fun onAccountSelect(id: AccountId) { if (canEdit()) _uiState.update { it.copy(accountId = id, message = null) } }
    fun onTypeSelect(type: TransactionType) { if (canEdit()) _uiState.update { it.copy(type = type, asTransfer = false, transferChoice = null, message = null) } }
    fun onSpendingClassSelect(value: SpendingClass) { if (canEdit()) _uiState.update { it.copy(spendingClass = value) } }

    /** The message is one half of a transfer between the person's own accounts. Nothing is picked for them unless only one entry fits. */
    fun onTransferSelect() {
        if (!canEdit()) return
        _uiState.update { state ->
            val only = state.copy(asTransfer = true, type = null, message = null).transferMatches.singleOrNull()
            state.copy(asTransfer = true, type = null, transferChoice = only?.leg?.id, message = null)
        }
    }

    fun onTransferChoice(id: TransactionId) { if (canEdit()) _uiState.update { it.copy(transferChoice = id) } }

    /** "It's a different purchase": nothing is attached, and these entries are not offered again for this message. */
    fun onKeepApart() {
        if (!canEdit()) return
        _uiState.update { state -> state.copy(keptApart = state.keptApart + state.duplicates.map { it.entry.id }) }
    }

    /** "It's the same payment": the message becomes evidence on the existing entry, once the short undo window has passed. */
    fun onSamePayment() {
        val state = _uiState.value
        if (!canEdit()) return
        val match = state.duplicates.firstOrNull() ?: return
        settle(CaptureSettlement(SettleKind.SAME_PAYMENT, match.entry))
    }

    /** "Link, add nothing": the same, for a transfer that is already in the ledger. */
    fun onLinkTransfer() {
        val state = _uiState.value
        if (!canEdit()) return
        val match = state.transferMatches.firstOrNull { it.leg.id == state.transferChoice } ?: return
        settle(CaptureSettlement(SettleKind.TRANSFER, match.leg, match.other))
    }

    private fun settle(settlement: CaptureSettlement) {
        _uiState.update { it.copy(settlement = settlement, message = null) }
        undoTimer?.cancel()
        undoTimer = viewModelScope.launch {
            delay(undoWindowMs)
            commitSettlement(thenFinish = false)
        }
    }

    /** Takes the answer back. It is only possible before it is written, or after a failed write. */
    fun onUndo() {
        val stage = _uiState.value.settlement?.stage
        if (stage != SettleStage.UNDO_WINDOW && stage != SettleStage.FAILED) return
        undoTimer?.cancel()
        _uiState.update { it.copy(settlement = null, message = null) }
    }

    /** "Done", or Back: writes an answer that is still waiting, then leaves. Without an answer it simply leaves. */
    fun onFinish() {
        val settlement = _uiState.value.settlement
        when (settlement?.stage) {
            null, SettleStage.SAVED, SettleStage.QUEUED -> _uiState.update { it.copy(isFinished = true) }
            SettleStage.UNDO_WINDOW, SettleStage.FAILED -> {
                undoTimer?.cancel()
                viewModelScope.launch { commitSettlement(thenFinish = true) }
            }
            SettleStage.SAVING -> Unit
        }
    }

    /** Writes the answer again after a failure. Repeating a write that already went through changes nothing. */
    fun onRetry() {
        if (_uiState.value.settlement?.stage != SettleStage.FAILED) return
        viewModelScope.launch { commitSettlement(thenFinish = false) }
    }

    private suspend fun commitSettlement(thenFinish: Boolean) {
        val state = _uiState.value
        val settlement = state.settlement ?: return
        val draft = state.draft ?: return
        if (settlement.stage == SettleStage.SAVING || settlement.stage == SettleStage.SAVED || settlement.stage == SettleStage.QUEUED) return
        _uiState.update { it.copy(settlement = settlement.copy(stage = SettleStage.SAVING), message = null) }
        linkDraft(draft, settlement.target.id, now()).fold(
            onSuccess = { write ->
                val stage = if (write == LinkWrite.QUEUED) SettleStage.QUEUED else SettleStage.SAVED
                _uiState.update { it.copy(settlement = settlement.copy(stage = stage), isFinished = thenFinish) }
            },
            onFailure = { e -> _uiState.update { it.copy(settlement = settlement.copy(stage = SettleStage.FAILED), message = e.toCaptureMessage()) } },
        )
    }

    fun confirm() {
        val state = _uiState.value
        if (state.settlement != null) return
        val draft = state.draft ?: return
        val account = state.accounts.firstOrNull { it.id == state.accountId }
        val type = state.type
        if (account == null || type == null) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        _uiState.update { it.copy(confirmationStarted = true) }
        save { confirmDraft(draft, account, type, state.spendingClass.takeIf { type == TransactionType.EXPENSE }, now()) }
    }

    fun discard() {
        if (_uiState.value.confirmationStarted || _uiState.value.settlement != null) return
        val draft = _uiState.value.draft ?: return
        save { discardDraft(draft) }
    }

    private fun save(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            block().fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toCaptureMessage()) } },
            )
        }
    }

    companion object {
        /** How long an "already counted" answer can be taken back before it is written. */
        const val UNDO_WINDOW_MS = 8_000L
    }
}
