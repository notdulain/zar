package com.example.zar.features.transactions.presentation.activity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.navigation.EditEntryRoute
import com.example.zar.features.transactions.domain.activity.EditProblem
import com.example.zar.features.transactions.domain.activity.EditResult
import com.example.zar.features.transactions.domain.activity.EntryEditRules
import com.example.zar.features.transactions.domain.activity.ExpenseEditInput
import com.example.zar.features.transactions.domain.activity.model.EditKind
import com.example.zar.features.transactions.domain.activity.usecase.CorrectExpenseUseCase
import com.example.zar.features.transactions.domain.activity.usecase.ObserveEntryDetailUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditMessage { REFUNDED_AMOUNT, NOT_SIGNED_IN, ALREADY_VOIDED, FAILED }

data class EditEntryUiState(
    val isLoading: Boolean = true,
    /** The entry cannot be corrected here: it is gone, voided, or not an expense. */
    val notEditable: Boolean = false,
    val entry: LedgerEntry? = null,
    val account: Account? = null,
    val input: ExpenseEditInput? = null,
    val problems: Set<EditProblem> = emptySet(),
    val message: EditMessage? = null,
    val isOnline: Boolean = true,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    /** The currency is the entry's own and cannot be corrected. */
    val currencyCode: String get() = entry?.current?.amount?.currency?.code.orEmpty()
}

@HiltViewModel
class EditEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDetail: ObserveEntryDetailUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
    private val correctExpense: CorrectExpenseUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the ViewModel can be created in plain unit tests.
    private val entryId = TransactionId(savedStateHandle.get<String>(EditEntryRoute::entryId.name).orEmpty())

    // Created once per screen and reused on every retry, so a repeated save can't add a second correction.
    private val correctionId = CorrectionId(RecordIds.newId())

    private val _uiState = MutableStateFlow(EditEntryUiState())
    val uiState: StateFlow<EditEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Read once: later changes to the ledger must not overwrite what is being typed.
            val detail = runCatching { observeDetail(entryId).first() }.getOrNull()
            _uiState.update {
                if (detail == null || detail.editKind != EditKind.EXPENSE) {
                    it.copy(isLoading = false, notEditable = true)
                } else {
                    it.copy(
                        isLoading = false,
                        entry = detail.entry,
                        account = detail.account,
                        input = ExpenseEditInput.from(detail.entry.current),
                    )
                }
            }
        }
        viewModelScope.launch {
            observeOnline().catch { }.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    private fun edit(clear: Set<EditProblem> = emptySet(), change: (ExpenseEditInput) -> ExpenseEditInput) =
        _uiState.update { state ->
            val input = state.input ?: return@update state
            state.copy(input = change(input), problems = state.problems - clear - EditProblem.NOTHING_CHANGED, message = null)
        }

    fun onAmountChange(value: String) =
        edit(setOf(EditProblem.AMOUNT_MISSING, EditProblem.AMOUNT_INVALID)) { it.copy(amount = value) }

    fun onDateSelect(value: LocalDate) = edit(setOf(EditProblem.DATE_IN_FUTURE)) { it.copy(date = value) }

    fun onCategorySelect(id: String) = edit { it.copy(categoryId = id) }

    fun onSpendingClassSelect(value: SpendingClass) = edit { it.copy(spendingClass = value) }

    fun onTitleChange(value: String) = edit(setOf(EditProblem.TITLE_TOO_LONG)) { it.copy(title = value) }

    fun onDescriptionChange(value: String) = edit(setOf(EditProblem.DESCRIPTION_TOO_LONG)) { it.copy(description = value) }

    fun save() {
        val state = _uiState.value
        val entry = state.entry ?: return
        val input = state.input ?: return
        if (state.isSaving || state.isFinished) return
        when (val result = EntryEditRules.validate(entry, input, ZarTime.today())) {
            is EditResult.Invalid -> _uiState.update { it.copy(problems = result.problems, message = null) }
            is EditResult.Valid -> {
                _uiState.update { it.copy(isSaving = true, problems = emptySet(), message = null) }
                viewModelScope.launch {
                    // An entry voided since the form opened can no longer change; say so instead of saving.
                    val latest = runCatching { observeDetail(entryId).first() }.getOrNull()
                    if (latest == null || latest.entry.isVoided) {
                        _uiState.update { it.copy(isSaving = false, message = EditMessage.ALREADY_VOIDED) }
                        return@launch
                    }
                    // The entry as first loaded is used on purpose: a retry of an already-stored correction
                    // then repeats the same write instead of failing with "nothing to change".
                    correctExpense(entry, result.changes, correctionId, Instant.now()).fold(
                        onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
                        onFailure = { error ->
                            _uiState.update {
                                when (error) {
                                    is LedgerError.RefundTooHigh -> it.copy(isSaving = false, message = EditMessage.REFUNDED_AMOUNT)
                                    is LedgerError.NothingToChange ->
                                        it.copy(isSaving = false, problems = setOf(EditProblem.NOTHING_CHANGED))
                                    is LedgerError.AlreadyVoided -> it.copy(isSaving = false, message = EditMessage.ALREADY_VOIDED)
                                    is LedgerError.NotSignedIn -> it.copy(isSaving = false, message = EditMessage.NOT_SIGNED_IN)
                                    else -> it.copy(isSaving = false, message = EditMessage.FAILED)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
