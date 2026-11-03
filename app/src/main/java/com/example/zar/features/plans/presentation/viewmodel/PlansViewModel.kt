package com.example.zar.features.plans.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.newGoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.usecase.ObservePlansOverviewUseCase
import com.example.zar.features.plans.domain.usecase.SaveGoalUseCase
import com.example.zar.features.plans.domain.usecase.SetGoalAllocationUseCase
import com.example.zar.features.plans.domain.usecase.SetMainGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlansMessage { INVALID_GOAL, INVALID_AMOUNT, ALLOCATION_TOO_LARGE, SAVE_FAILED }

data class GoalEditorState(
    /** The goal being edited; null while composing a new one. */
    val goalId: GoalId? = null,
    val name: String = "MacBook fund",
    val target: String = "490000",
    val targetDate: LocalDate? = null,
)

data class AllocationEditorState(val goalId: GoalId? = null, val accountId: AccountId? = null, val amount: String = "")

data class PlansUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val overview: PlansOverview? = null,
    val goalEditor: GoalEditorState? = null,
    val allocationEditor: AllocationEditorState? = null,
    val isSaving: Boolean = false,
    val message: PlansMessage? = null,
    /** Counts goals created from the New plan sheet, so the sheet can close once, after the save. */
    val goalCreatedCount: Int = 0,
    /** The ID of the plan being created. Made on the first attempt and reused by every retry, so a retry never adds a second plan. */
    val pendingGoalId: GoalId? = null,
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val observePlans: ObservePlansOverviewUseCase,
    private val saveGoal: SaveGoalUseCase,
    private val setAllocation: SetGoalAllocationUseCase,
    private val setMainGoal: SetMainGoalUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlansUiState())
    val uiState: StateFlow<PlansUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init { observe() }

    fun retry() = observe()

    fun openGoalEditor(goalId: GoalId? = null) {
        val overview = _uiState.value.overview
        val goal = goalId?.let { id -> overview?.goalById(id) } ?: overview?.goal
        _uiState.update {
            it.copy(
                goalEditor = GoalEditorState(
                    goalId = goal?.id,
                    name = goal?.name ?: "MacBook fund",
                    target = goal?.target?.toDecimal()?.toPlainString() ?: "490000",
                    targetDate = goal?.targetDate,
                ),
                message = null,
            )
        }
    }

    fun updateGoalName(value: String) = _uiState.update {
        it.copy(goalEditor = it.goalEditor?.copy(name = value), message = null)
    }

    fun updateGoalTarget(value: String) = _uiState.update {
        it.copy(goalEditor = it.goalEditor?.copy(target = value), message = null)
    }

    fun updateGoalDate(value: LocalDate?) = _uiState.update {
        it.copy(goalEditor = it.goalEditor?.copy(targetDate = value), message = null)
    }

    fun closeGoalEditor() = _uiState.update { it.copy(goalEditor = null, message = null) }

    fun submitGoal() {
        val editor = _uiState.value.goalEditor ?: return
        val target = Money.parse(editor.target, Currencies.LKR)
        if (editor.name.isBlank() || editor.name.length > 80 || target == null || !target.isPositive) {
            _uiState.update { it.copy(message = PlansMessage.INVALID_GOAL) }
            return
        }
        // Editing addresses the exact plan. An editor opened with no plan is a new one and gets its own stable ID.
        val goalId = editor.goalId
            ?: _uiState.value.pendingGoalId
            ?: newGoalId().also { fresh -> _uiState.update { it.copy(pendingGoalId = fresh) } }
        launchSave {
            saveGoal(goalId, editor.name, target, editor.targetDate)
                .onSuccess { _uiState.update { it.copy(goalEditor = null, pendingGoalId = null) } }
        }
    }

    /**
     * Creates a new savings plan from the New plan sheet under its own stable ID, alongside any existing plans. The ID is made
     * once per create attempt and kept until the save succeeds, so a retry rewrites the same plan instead of adding another.
     * Until the plans have loaded nothing is written, because the catalog is not yet known.
     */
    fun createGoal(name: String, target: String, date: LocalDate?) {
        if (_uiState.value.overview == null) return
        val amount = Money.parse(target, Currencies.LKR)
        if (name.isBlank() || name.length > 80 || amount == null || !amount.isPositive) {
            _uiState.update { it.copy(message = PlansMessage.INVALID_GOAL) }
            return
        }
        val id = _uiState.value.pendingGoalId ?: newGoalId().also { fresh -> _uiState.update { it.copy(pendingGoalId = fresh) } }
        // With no plan yet the new one becomes main explicitly, so adding more later can never move it by ordering.
        val firstPlan = _uiState.value.overview?.goals.isNullOrEmpty()
        launchSave {
            saveGoal(id, name.trim(), amount, date)
                .onSuccess { if (firstPlan) setMainGoal(id) }
                .onSuccess {
                    _uiState.update { it.copy(goalCreatedCount = it.goalCreatedCount + 1, pendingGoalId = null, message = null) }
                }
        }
    }

    /** Makes [goalId] the main plan shown on Plans and Home. Nothing else changes. */
    fun selectMainGoal(goalId: GoalId) {
        if (_uiState.value.overview?.mainGoalId == goalId) return
        launchSave { setMainGoal(goalId) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun openAllocationEditor(goalId: GoalId? = null) {
        val overview = _uiState.value.overview ?: return
        val goal = goalId?.let(overview::goalById) ?: overview.goal ?: return
        val selected = overview.allocationAccountsFor(goal.id).firstOrNull()?.account?.id ?: return
        val existing = goal.allocations.firstOrNull { it.accountId == selected }?.amount
        _uiState.update {
            it.copy(
                allocationEditor = AllocationEditorState(goal.id, selected, existing?.toDecimal()?.toPlainString().orEmpty()),
                message = null,
            )
        }
    }

    fun selectAllocationAccount(accountId: AccountId) {
        val state = _uiState.value
        val goalId = state.allocationEditor?.goalId ?: state.overview?.goal?.id
        val existing = goalId?.let { id -> state.overview?.goalById(id) }?.allocations
            ?.firstOrNull { it.accountId == accountId }
            ?.amount
        _uiState.update {
            it.copy(
                allocationEditor = AllocationEditorState(goalId, accountId, existing?.toDecimal()?.toPlainString().orEmpty()),
                message = null,
            )
        }
    }

    fun updateAllocationAmount(value: String) = _uiState.update {
        it.copy(allocationEditor = it.allocationEditor?.copy(amount = value), message = null)
    }

    fun closeAllocationEditor() = _uiState.update { it.copy(allocationEditor = null, message = null) }

    fun submitAllocation() {
        val state = _uiState.value
        val editor = state.allocationEditor ?: return
        val goal = (editor.goalId?.let { id -> state.overview?.goalById(id) } ?: state.overview?.goal) ?: return
        val accountId = editor.accountId ?: return
        val amount = Money.parse(editor.amount, goal.target.currency)
        if (amount == null || amount.isNegative) {
            _uiState.update { it.copy(message = PlansMessage.INVALID_AMOUNT) }
            return
        }
        launchSave {
            setAllocation(goal.id, accountId, amount)
                .onSuccess { _uiState.update { it.copy(allocationEditor = null) } }
                .onFailure { _uiState.update { it.copy(message = PlansMessage.ALLOCATION_TOO_LARGE) } }
        }
    }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            observePlans()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { overview ->
                    _uiState.update { it.copy(isLoading = false, loadFailed = false, overview = overview) }
                }
        }
    }

    private fun launchSave(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val result = block()
                if (result.isFailure && _uiState.value.message == null) {
                    _uiState.update { it.copy(message = PlansMessage.SAVE_FAILED) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(message = PlansMessage.SAVE_FAILED) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
