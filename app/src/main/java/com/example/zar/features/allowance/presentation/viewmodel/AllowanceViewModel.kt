package com.example.zar.features.allowance.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.allowance.domain.model.AllowanceOverview
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.usecase.ObserveAllowanceUseCase
import com.example.zar.features.allowance.domain.usecase.SaveAllowanceSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AllowanceMessage { INVALID_AMOUNT, SAVE_FAILED }

/** What the user is typing in the allowance form. [amount] is text so a half-typed value is kept. */
data class AllowanceEditorState(
    val enabled: Boolean = true,
    val amount: String = "",
    val categoryIds: Set<String> = emptySet(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
)

data class AllowanceUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val overview: AllowanceOverview? = null,
    val editor: AllowanceEditorState? = null,
    val isSaving: Boolean = false,
    val message: AllowanceMessage? = null,
)

@HiltViewModel
class AllowanceViewModel @Inject constructor(
    private val observeAllowance: ObserveAllowanceUseCase,
    private val saveSettings: SaveAllowanceSettingsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllowanceUiState())
    val uiState: StateFlow<AllowanceUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init { observe() }

    fun retry() = observe()

    /** Opens the form with the saved setting, or with the allowance switched on for a first set-up. */
    fun openEditor() {
        val saved = _uiState.value.overview?.settings ?: AllowanceSettings.Disabled
        _uiState.update {
            it.copy(
                editor = AllowanceEditorState(
                    enabled = true,
                    amount = saved.amount?.toDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    categoryIds = saved.categoryIds,
                    weekStart = saved.weekStart,
                ),
                message = null,
            )
        }
    }

    fun closeEditor() = _uiState.update { it.copy(editor = null, message = null) }

    fun setEnabled(value: Boolean) = editEditor { it.copy(enabled = value) }

    fun updateAmount(value: String) = editEditor { it.copy(amount = value) }

    fun toggleCategory(id: String) = editEditor {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }

    fun setWeekStart(day: DayOfWeek) = editEditor { it.copy(weekStart = day) }

    fun submit() {
        val editor = _uiState.value.editor ?: return
        val typed = editor.amount.trim()
        val amount = if (typed.isEmpty()) null else FiatMoney.parse(typed, Currencies.REPORTING)
        // A value that was typed must always be valid; an empty one is only allowed while switched off.
        if ((typed.isNotEmpty() && (amount == null || !amount.isPositive)) || (editor.enabled && amount == null)) {
            _uiState.update { it.copy(message = AllowanceMessage.INVALID_AMOUNT) }
            return
        }
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val settings = AllowanceSettings(editor.enabled, amount, editor.categoryIds, editor.weekStart)
                saveSettings(settings)
                    .onSuccess { _uiState.update { it.copy(editor = null) } }
                    .onFailure { _uiState.update { it.copy(message = AllowanceMessage.SAVE_FAILED) } }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(message = AllowanceMessage.SAVE_FAILED) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun editEditor(change: (AllowanceEditorState) -> AllowanceEditorState) =
        _uiState.update { state -> state.copy(editor = state.editor?.let(change), message = null) }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            observeAllowance(ZarTime.today())
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { overview ->
                    _uiState.update { it.copy(isLoading = false, loadFailed = false, overview = overview) }
                }
        }
    }
}
