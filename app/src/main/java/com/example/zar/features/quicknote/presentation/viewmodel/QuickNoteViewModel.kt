package com.example.zar.features.quicknote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.usecase.ObserveLedgerUseCase
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import com.example.zar.features.quicknote.domain.review.ProposalBlocker
import com.example.zar.features.quicknote.domain.review.ProposalInput
import com.example.zar.features.quicknote.domain.review.QuickNoteReviewRules
import com.example.zar.features.quicknote.domain.usecase.ConfirmQuickNoteProposalUseCase
import com.example.zar.features.quicknote.domain.usecase.GenerateQuickNoteDraftUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuickNoteStage { WRITING, READING, REVIEWING }

/** Why a note could not be turned into proposals. The note is always kept so the person can try again or type it in. */
enum class QuickNoteFailure(val canRetry: Boolean) {
    TIMED_OUT(true),
    RATE_LIMITED(true),
    UNAVAILABLE(true),
    INVALID_RESPONSE(true),
    NOTHING_FOUND(true),
    NOT_SIGNED_IN(false),
    APP_NOT_VERIFIED(false),
    REJECTED(false),
}

enum class ProposalStatus { OPEN, SAVING, SAVED, DISCARDED }

data class ProposalState(
    val index: Int,
    val input: ProposalInput,
    val blockers: Set<ProposalBlocker> = emptySet(),
    val status: ProposalStatus = ProposalStatus.OPEN,
    val saveFailed: Boolean = false,
    /** The purchase a refund points at, once picked. */
    val purchase: LedgerEntry? = null,
) {
    val canSave: Boolean get() = status == ProposalStatus.OPEN && blockers.isEmpty()
}

data class QuickNoteUiState(
    val stage: QuickNoteStage = QuickNoteStage.WRITING,
    val note: String = "",
    val isOnline: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val failure: QuickNoteFailure? = null,
    val proposals: List<ProposalState> = emptyList(),
    /** The proposal being edited, or null. */
    val editing: Int? = null,
    /** Purchases a refund can point at, for the editor. */
    val purchases: List<LedgerEntry> = emptyList(),
    val isFinished: Boolean = false,
) {
    val canInterpret: Boolean get() = stage == QuickNoteStage.WRITING && note.isNotBlank() && isOnline
    val anySaved: Boolean get() = proposals.any { it.status == ProposalStatus.SAVED }
    val isSaving: Boolean get() = proposals.any { it.status == ProposalStatus.SAVING }
    val canEditNote: Boolean get() = !anySaved && !isSaving
}

/**
 * Write a rough note, ask for it to be read, review each proposal, and save the ones that are right. Interpreting
 * happens only on an explicit tap and never twice at once, and nothing is saved until a proposal is confirmed.
 * The note and the draft live only in this ViewModel: they are never stored, and they go when the screen does.
 */
@HiltViewModel
class QuickNoteViewModel internal constructor(
    private val generate: GenerateQuickNoteDraftUseCase,
    private val confirmProposal: ConfirmQuickNoteProposalUseCase,
    observeAccounts: ObserveActiveAccountsUseCase,
    observeLedger: ObserveLedgerUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
    private val today: () -> LocalDate,
    private val now: () -> Instant,
) : ViewModel() {

    @Inject
    constructor(
        generate: GenerateQuickNoteDraftUseCase,
        confirmProposal: ConfirmQuickNoteProposalUseCase,
        observeAccounts: ObserveActiveAccountsUseCase,
        observeLedger: ObserveLedgerUseCase,
        observeOnline: ObserveOnlineStatusUseCase,
    ) : this(generate, confirmProposal, observeAccounts, observeLedger, observeOnline, ZarTime::today, Instant::now)

    private val _uiState = MutableStateFlow(QuickNoteUiState())
    val uiState: StateFlow<QuickNoteUiState> = _uiState.asStateFlow()

    private var draft: Draft? = null
    private var entries: List<LedgerEntry> = emptyList()
    private var reading: Job? = null

    // The time a proposal was first confirmed, reused by every retry so a replayed save carries the same facts.
    private val firstConfirmedAt = mutableMapOf<Int, Instant>()

    init {
        viewModelScope.launch {
            observeAccounts().catch { }.collect { accounts -> _uiState.update { recheck(it.copy(accounts = accounts)) } }
        }
        viewModelScope.launch {
            observeLedger().catch { }.collect { list ->
                entries = list
                _uiState.update { recheck(it) }
            }
        }
        viewModelScope.launch {
            observeOnline().catch { }.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun onNoteChange(value: String) = _uiState.update {
        if (it.stage != QuickNoteStage.WRITING) it else it.copy(note = value.take(QuickNoteLimits.MAX_NOTE_LENGTH), failure = null)
    }

    /** Reads the note. Ignored while a read is under way, offline, or with nothing written. */
    fun interpret() {
        val state = _uiState.value
        if (!state.canInterpret || reading?.isActive == true) return
        _uiState.update { it.copy(stage = QuickNoteStage.READING, failure = null) }
        reading = viewModelScope.launch {
            generate(state.note).fold(onSuccess = ::onDraft, onFailure = ::onFailure)
        }
    }

    fun cancelReading() {
        reading?.cancel()
        reading = null
        _uiState.update { if (it.stage == QuickNoteStage.READING) it.copy(stage = QuickNoteStage.WRITING) else it }
    }

    private fun onDraft(result: Draft) {
        if (result.proposals.isEmpty()) return onFailure(null)
        draft = result
        firstConfirmedAt.clear()
        _uiState.update {
            recheck(
                it.copy(
                    stage = QuickNoteStage.REVIEWING,
                    failure = null,
                    proposals = result.proposals.mapIndexed { index, proposal ->
                        ProposalState(index, QuickNoteReviewRules.inputFrom(proposal))
                    },
                ),
            )
        }
    }

    private fun onFailure(error: Throwable?) {
        val failure = when (error) {
            null -> QuickNoteFailure.NOTHING_FOUND
            is QuickNoteDraftError.TimedOut -> QuickNoteFailure.TIMED_OUT
            is QuickNoteDraftError.RateLimited -> QuickNoteFailure.RATE_LIMITED
            is QuickNoteDraftError.InvalidResponse -> QuickNoteFailure.INVALID_RESPONSE
            is QuickNoteDraftError.NotSignedIn -> QuickNoteFailure.NOT_SIGNED_IN
            is QuickNoteDraftError.AppNotVerified -> QuickNoteFailure.APP_NOT_VERIFIED
            is QuickNoteDraftError.InvalidInput -> QuickNoteFailure.REJECTED
            else -> QuickNoteFailure.UNAVAILABLE
        }
        _uiState.update { it.copy(stage = QuickNoteStage.WRITING, failure = failure) }
    }

    /** Back to the note to reword it. Only before anything was saved, so a second read can't repeat a record. */
    fun editNote() = _uiState.update {
        if (it.stage != QuickNoteStage.REVIEWING || !it.canEditNote) it else {
            draft = null
            it.copy(stage = QuickNoteStage.WRITING, proposals = emptyList(), editing = null)
        }
    }

    fun openEditor(index: Int) = _uiState.update { state ->
        val proposal = state.proposals.getOrNull(index)?.takeIf { it.status == ProposalStatus.OPEN } ?: return@update state
        state.copy(editing = index, purchases = purchasesFor(proposal.input))
    }

    fun closeEditor() = _uiState.update { it.copy(editing = null) }

    /** Applies an edit. A field the person touches is no longer shown as guessed. */
    fun update(index: Int, transform: (ProposalInput) -> ProposalInput) = _uiState.update { state ->
        val current = state.proposals.getOrNull(index)?.takeIf { it.status == ProposalStatus.OPEN } ?: return@update state
        val edited = transform(current.input)
        val changed = recheck(
            state.copy(
                proposals = state.proposals.map {
                    if (it.index == index) it.copy(input = edited.withUncertain(edited.uncertain - touched(current.input, edited)), saveFailed = false) else it
                },
            ),
        )
        changed.copy(purchases = if (state.editing == index) purchasesFor(edited) else state.purchases)
    }

    fun confirm(index: Int) {
        val proposal = _uiState.value.proposals.getOrNull(index) ?: return
        val saving = draft ?: return
        if (!proposal.canSave) return
        val savedAt = firstConfirmedAt.getOrPut(index) { now() }
        setStatus(index, ProposalStatus.SAVING)
        viewModelScope.launch {
            confirmProposal(saving, index, proposal.input, today(), savedAt).fold(
                onSuccess = { setStatus(index, ProposalStatus.SAVED) },
                onFailure = { _uiState.update { state -> state.copy(proposals = state.proposals.map { if (it.index == index) it.copy(status = ProposalStatus.OPEN, saveFailed = true) else it }) } },
            )
        }
    }

    fun discard(index: Int) {
        if (_uiState.value.proposals.getOrNull(index)?.status == ProposalStatus.OPEN) setStatus(index, ProposalStatus.DISCARDED)
    }

    /** Drops every proposal that is still open. What was already saved stays saved. */
    fun discardAll() = _uiState.update { state ->
        finish(state.copy(proposals = state.proposals.map { if (it.status == ProposalStatus.OPEN) it.copy(status = ProposalStatus.DISCARDED) else it }))
    }

    private fun setStatus(index: Int, status: ProposalStatus) = _uiState.update { state ->
        finish(state.copy(proposals = state.proposals.map { if (it.index == index) it.copy(status = status, saveFailed = false) else it }, editing = if (state.editing == index) null else state.editing))
    }

    /** Once nothing is left to decide the note is dropped and the screen can close. */
    private fun finish(state: QuickNoteUiState): QuickNoteUiState {
        val done = state.proposals.isNotEmpty() && state.proposals.none { it.status == ProposalStatus.OPEN || it.status == ProposalStatus.SAVING }
        if (!done) return state
        draft = null
        return state.copy(isFinished = true, note = "", editing = null)
    }

    private fun recheck(state: QuickNoteUiState): QuickNoteUiState {
        val current = draft ?: return state
        val at = now()
        val today = today()
        return state.copy(
            proposals = state.proposals.map {
                if (it.status != ProposalStatus.OPEN) it
                else it.copy(
                    blockers = QuickNoteReviewRules.check(current, it.index, it.input, state.accounts, entries, today, at).blockers,
                    purchase = (it.input as? ProposalInput.Actual)?.refundOf?.let { id -> entries.firstOrNull { entry -> entry.id == id } },
                )
            },
        )
    }

    private fun purchasesFor(input: ProposalInput): List<LedgerEntry> =
        if (input is ProposalInput.Actual && input.type == TransactionType.REFUND) {
            QuickNoteReviewRules.refundCandidates(entries, accountId = null)
        } else {
            emptyList()
        }

    private fun ProposalInput.withUncertain(set: Set<DraftField>): ProposalInput = when (this) {
        is ProposalInput.Actual -> copy(uncertain = set)
        is ProposalInput.Expected -> copy(uncertain = set)
        is ProposalInput.Transfer -> copy(uncertain = set)
    }

    private fun touched(old: ProposalInput, new: ProposalInput): Set<DraftField> = buildSet {
        when {
            old is ProposalInput.Actual && new is ProposalInput.Actual -> {
                if (old.type != new.type) add(DraftField.TYPE)
                if (old.amount != new.amount) add(DraftField.AMOUNT)
                if (old.accountId != new.accountId) add(DraftField.ACCOUNT)
                if (old.date != new.date) add(DraftField.DATE)
                if (old.categoryId != new.categoryId) add(DraftField.CATEGORY)
                if (old.title != new.title) add(DraftField.MERCHANT)
            }
            old is ProposalInput.Expected && new is ProposalInput.Expected -> {
                if (old.direction != new.direction) add(DraftField.TYPE)
                if (old.amount != new.amount) add(DraftField.AMOUNT)
                if (old.accountId != new.accountId) add(DraftField.ACCOUNT)
                if (old.dueDate != new.dueDate) add(DraftField.DATE)
                if (old.title != new.title) add(DraftField.TITLE)
            }
            old is ProposalInput.Transfer && new is ProposalInput.Transfer -> {
                if (old.amount != new.amount || old.received != new.received) add(DraftField.AMOUNT)
                if (old.fromId != new.fromId) add(DraftField.ACCOUNT)
                if (old.toId != new.toId) add(DraftField.COUNTERPART_ACCOUNT)
                if (old.date != new.date) add(DraftField.DATE)
            }
        }
    }
}
