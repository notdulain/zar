package com.example.zar.features.freelance.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.navigation.MilestoneRoute
import com.example.zar.features.freelance.domain.Checked
import com.example.zar.features.freelance.domain.FreelanceProblem
import com.example.zar.features.freelance.domain.FreelanceRules
import com.example.zar.features.freelance.domain.model.LinkedReceipt
import com.example.zar.features.freelance.domain.model.MilestoneSummary
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ReceiptCandidate
import com.example.zar.features.freelance.domain.usecase.CancelMilestoneUseCase
import com.example.zar.features.freelance.domain.usecase.LinkReceiptUseCase
import com.example.zar.features.freelance.domain.usecase.ObserveFreelanceUseCase
import com.example.zar.features.freelance.domain.usecase.ObserveReceiptCandidatesUseCase
import com.example.zar.features.freelance.domain.usecase.SaveMilestoneUseCase
import com.example.zar.features.freelance.domain.usecase.UnlinkReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MilestoneUiState(
    val isLoading: Boolean = true,
    val project: Project? = null,
    /** Live progress of the milestone being edited; null for a new one. */
    val summary: MilestoneSummary? = null,
    val title: String = "",
    val amount: String = "",
    val dueDate: LocalDate = ZarTime.today(),
    val problems: Set<FreelanceProblem> = emptySet(),
    val candidates: List<ReceiptCandidate> = emptyList(),
    /** Typed link amounts and their problems, keyed by transaction ID. */
    val linkAmounts: Map<String, String> = emptyMap(),
    val linkProblems: Map<String, Set<FreelanceProblem>> = emptyMap(),
    val message: FreelanceMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val isEditing: Boolean get() = summary != null
    val isCancelled: Boolean get() = summary?.progress?.state == MatchState.CANCELLED
    val canEdit: Boolean get() = project?.isArchived == false && !isCancelled
    val receipts: List<LinkedReceipt> get() = summary?.receipts.orEmpty()

    /** Suggested link amount: what the receipt has left, up to what the milestone still expects. */
    fun suggestedAmount(candidate: ReceiptCandidate): Money {
        val remaining = summary?.progress?.remaining ?: return candidate.available
        return if (candidate.available > remaining) remaining else candidate.available
    }
}

@HiltViewModel
class MilestoneViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeFreelance: ObserveFreelanceUseCase,
    observeCandidates: ObserveReceiptCandidatesUseCase,
    private val saveMilestone: SaveMilestoneUseCase,
    private val cancelMilestone: CancelMilestoneUseCase,
    private val linkReceipt: LinkReceiptUseCase,
    private val unlinkReceipt: UnlinkReceiptUseCase,
) : ViewModel() {
    private val projectId = ProjectId(savedStateHandle.get<String>(MilestoneRoute::projectId.name).orEmpty())
    private val editingId = savedStateHandle.get<String>(MilestoneRoute::milestoneId.name)?.let(::ExpectedPaymentId)

    // Created once and reused on retry, so a repeated save can't create two milestones.
    private val newMilestoneId = ExpectedPaymentId(RecordIds.newId())

    private val _uiState = MutableStateFlow(MilestoneUiState())
    val uiState: StateFlow<MilestoneUiState> = _uiState.asStateFlow()

    private var formFilled = false

    init {
        viewModelScope.launch {
            combine(
                observeFreelance(ZarTime.today()),
                editingId?.let { observeCandidates(it) } ?: flowOf(emptyList()),
            ) { overview, candidates -> overview to candidates }
                .catch { _uiState.update { it.copy(isLoading = false, message = FreelanceMessage.FAILED) } }
                .collect { (overview, candidates) ->
                    val projectSummary = overview.projects.firstOrNull { it.project.id == projectId }
                    val summary = editingId?.let { id -> projectSummary?.milestones?.firstOrNull { it.milestone.id == id } }
                    val missing = projectSummary == null || (editingId != null && summary == null)
                    _uiState.update { state ->
                        var next = state.copy(
                            isLoading = false,
                            project = projectSummary?.project,
                            summary = summary,
                            candidates = candidates,
                            message = if (missing) FreelanceMessage.NOT_FOUND else state.message.takeUnless { it == FreelanceMessage.NOT_FOUND },
                        )
                        // Fill the form once; later updates (new links) must not overwrite typing.
                        if (!formFilled && summary != null) {
                            formFilled = true
                            val m = summary.milestone
                            next = next.copy(title = m.title, amount = m.amount.toDecimal().toPlainString(), dueDate = m.dueDate)
                        }
                        next
                    }
                }
        }
    }

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value, problems = it.problems - FreelanceProblem.NAME_MISSING - FreelanceProblem.NAME_TOO_LONG) }

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amount = value, problems = it.problems - FreelanceProblem.AMOUNT_MISSING - FreelanceProblem.AMOUNT_INVALID) }

    fun onDueDateSelect(value: LocalDate) = _uiState.update { it.copy(dueDate = value) }

    fun onLinkAmountChange(transactionId: String, value: String) = _uiState.update {
        it.copy(linkAmounts = it.linkAmounts + (transactionId to value), linkProblems = it.linkProblems - transactionId)
    }

    fun save() {
        val state = _uiState.value
        val project = state.project ?: return
        if (state.isSaving || state.isLoading) return
        when (val checked = FreelanceRules.checkMilestone(state.title, state.amount, project.currency, state.dueDate)) {
            is Checked.Invalid -> _uiState.update { it.copy(problems = checked.problems) }
            is Checked.Valid -> launchSaving(finish = true) {
                saveMilestone(project, editingId ?: newMilestoneId, checked.value, state.summary?.milestone, Instant.now())
            }
        }
    }

    fun link(candidate: ReceiptCandidate) {
        val state = _uiState.value
        val summary = state.summary ?: return
        val key = candidate.entry.id.value
        val typed = state.linkAmounts[key] ?: state.suggestedAmount(candidate).toDecimal().toPlainString()
        when (val checked = FreelanceRules.checkLink(typed, candidate.available, summary.progress.remaining)) {
            is Checked.Invalid -> _uiState.update { it.copy(linkProblems = it.linkProblems + (key to checked.problems)) }
            is Checked.Valid -> launchSaving(finish = false) {
                linkReceipt(summary, candidate, checked.value, Instant.now()).also { result ->
                    if (result.isSuccess) _uiState.update { it.copy(linkAmounts = it.linkAmounts - key) }
                }
            }
        }
    }

    fun unlink(receipt: LinkedReceipt) = launchSaving(finish = false) { unlinkReceipt(receipt.match) }

    fun cancel() {
        val milestone = _uiState.value.summary?.milestone ?: return
        launchSaving(finish = true) { cancelMilestone(milestone) }
    }

    private fun launchSaving(finish: Boolean, block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            block().fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = finish) } },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toFreelanceMessage()) } },
            )
        }
    }
}
