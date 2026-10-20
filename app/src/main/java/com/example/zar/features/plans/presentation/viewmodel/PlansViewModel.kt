package com.example.zar.features.plans.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.MACBOOK_GOAL_ID
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.usecase.ObservePlansOverviewUseCase
import com.example.zar.features.plans.domain.usecase.SaveGoalUseCase
import com.example.zar.features.plans.domain.usecase.SetGoalAllocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlansMessage { INVALID_GOAL, INVALID_AMOUNT, ALLOCATION_TOO_LARGE, SAVE_FAILED }

data class GoalEditorState(val name: String = "MacBook fund", val target: String = "490000")

data class AllocationEditorState(val accountId: AccountId? = null, val amount: String = "")

data class PlansUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val overview: PlansOverview? = null,
    val goalEditor: GoalEditorState? = null,
    val allocationEditor: AllocationEditorState? = null,
    val isSaving: Boolean = false,
    val message: PlansMessage? = null,
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val observePlans: ObservePlansOverviewUseCase,
    private val saveGoal: SaveGoalUseCase,
    private val setAllocation: SetGoalAllocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlansUiState())
    val uiState: StateFlow<PlansUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init { observe() }

    fun retry() = observe()

    fun openGoalEditor() {
        val goal = _uiState.value.overview?.goal
        _uiState.update {
            it.copy(
                goalEditor = GoalEditorState(
                    name = goal?.name ?: "MacBook fund",
                    target = goal?.target?.toDecimal()?.toPlainString() ?: "490000",
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

    fun closeGoalEditor() = _uiState.update { it.copy(goalEditor = null, message = null) }

    fun submitGoal() {
        val editor = _uiState.value.goalEditor ?: return
        val target = Money.parse(editor.target, Currencies.LKR)
        if (editor.name.isBlank() || editor.name.length > 80 || target == null || !target.isPositive) {
            _uiState.update { it.copy(message = PlansMessage.INVALID_GOAL) }
            return
        }
        launchSave {
            saveGoal(GoalId(MACBOOK_GOAL_ID), editor.name, target)
                .onSuccess { _uiState.update { it.copy(goalEditor = null) } }
        }
    }

    fun openAllocationEditor() {
        val overview = _uiState.value.overview ?: return
        val selected = overview.allocationAccounts.firstOrNull()?.account?.id ?: return
        val existing = overview.goal?.allocations?.firstOrNull { it.accountId == selected }?.amount
        _uiState.update {
            it.copy(
                allocationEditor = AllocationEditorState(selected, existing?.toDecimal()?.toPlainString().orEmpty()),
                message = null,
            )
        }
    }

    fun selectAllocationAccount(accountId: AccountId) {
        val existing = _uiState.value.overview?.goal?.allocations
            ?.firstOrNull { it.accountId == accountId }
            ?.amount
        _uiState.update {
            it.copy(
                allocationEditor = AllocationEditorState(accountId, existing?.toDecimal()?.toPlainString().orEmpty()),
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
        val goal = state.overview?.goal ?: return
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
