package com.example.zar.features.transactions.presentation.income.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.transactions.domain.income.IncomeRules
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.income.model.IncomeFormInput
import com.example.zar.features.transactions.domain.income.model.IncomeFormResult
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import com.example.zar.features.transactions.domain.income.usecase.EditIncomeUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeAccountsUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeEntryUseCase
import com.example.zar.features.transactions.domain.income.usecase.RecordIncomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class IncomeFormMessage { NOT_SIGNED_IN, NOTHING_CHANGED, NOT_FOUND, NO_ACCOUNTS, FAILED }

data class IncomeFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val accountId: AccountId? = null,
    /** The edited record's currency. Account corrections keep this currency fixed. */
    val lockedCurrency: Currency? = null,
    val amount: String = "",
    val date: LocalDate = ZarTime.today(),
    val time: LocalTime? = null,
    val rate: String = "",
    val receivedFrom: String = "",
    val category: IncomeCategory? = null,
    val note: String = "",
    val problems: Set<IncomeProblem> = emptySet(),
    val message: IncomeFormMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }
    val currency: Currency? get() = lockedCurrency ?: selectedAccount?.currency
    val needsRate: Boolean get() = currency.let { it != null && it != Currencies.REPORTING }
}

@HiltViewModel
class IncomeFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeAccounts: ObserveIncomeAccountsUseCase,
    private val observeEntry: ObserveIncomeEntryUseCase,
    private val recordIncome: RecordIncomeUseCase,
    private val editIncome: EditIncomeUseCase,
    private val voidTransaction: VoidTransactionUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the same ViewModel serves both the edit route and the income tab of the
    // one Add-transaction session (which is a ManualEntryRoute), and so it can be created in plain unit tests.
    private val incomeId = savedStateHandle.get<String>("incomeId")?.let(::TransactionId)

    // Created once per screen and reused on every retry, so a repeated save can't duplicate.
    private val newIncomeId = TransactionId(RecordIds.newId())
    private val correctionId = CorrectionId(RecordIds.newId())
    private var entry: LedgerEntry? = null

    private val _uiState = MutableStateFlow(IncomeFormUiState(isEditing = incomeId != null))
    val uiState: StateFlow<IncomeFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val accounts = observeAccounts().first()
            if (incomeId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accounts = accounts,
                        accountId = accounts.firstOrNull()?.id,
                        message = if (accounts.isEmpty()) IncomeFormMessage.NO_ACCOUNTS else null,
                    )
                }
                return
            }
            val found = observeEntry(incomeId).first()
            if (found == null) {
                _uiState.update { it.copy(isLoading = false, message = IncomeFormMessage.NOT_FOUND) }
                return
            }
            entry = found
            val tx = found.current
            _uiState.update {
                it.copy(
                    isLoading = false,
                    accounts = accounts,
                    accountId = tx.accountId,
                    lockedCurrency = tx.amount.currency,
                    amount = tx.amount.toDecimal().toPlainString(),
                    date = tx.date,
                    time = tx.time,
                    rate = IncomeRules.storedRate(tx.reportingConversion)?.toPlainString().orEmpty(),
                    receivedFrom = tx.merchant.orEmpty(),
                    category = IncomeCategory.fromId(tx.categoryId),
                    note = tx.note.orEmpty(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.update { it.copy(isLoading = false, message = IncomeFormMessage.FAILED) }
        }
    }

    fun onAmountChange(value: String) = edit(IncomeProblem.AMOUNT_MISSING, IncomeProblem.AMOUNT_INVALID) { copy(amount = value) }

    fun onAccountSelect(id: AccountId) {
        val state = _uiState.value
        val account = state.accounts.firstOrNull { it.id == id && !it.isArchived } ?: return
        if (state.isEditing && account.currency != state.lockedCurrency) return
        edit { copy(accountId = id, rate = if (isEditing) rate else "") }
    }

    fun onDateSelect(value: LocalDate) = edit(IncomeProblem.DATE_IN_FUTURE) { copy(date = value) }

    fun onTimeSelect(value: LocalTime?) = edit { copy(time = value) }

    fun onRateChange(value: String) = edit(IncomeProblem.RATE_INVALID) { copy(rate = value) }

    fun onReceivedFromChange(value: String) = edit(IncomeProblem.RECEIVED_FROM_TOO_LONG) { copy(receivedFrom = value) }

    fun onCategorySelect(value: IncomeCategory) = edit { copy(category = if (category == value) null else value) }

    fun onNoteChange(value: String) = edit(IncomeProblem.NOTE_TOO_LONG) { copy(note = value) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val currency = state.currency ?: return _uiState.update { it.copy(message = IncomeFormMessage.NO_ACCOUNTS) }
        val input = IncomeFormInput(
            amount = state.amount,
            currency = currency,
            date = state.date,
            time = state.time,
            rate = state.rate,
            receivedFrom = state.receivedFrom,
            category = state.category,
            note = state.note,
        )
        when (val result = IncomeRules.validate(input, ZarTime.today())) {
            is IncomeFormResult.Invalid -> _uiState.update { it.copy(problems = result.problems) }
            is IncomeFormResult.Valid -> viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, problems = emptySet(), message = null) }
                val now = Instant.now()
                val existing = entry
                val outcome = if (existing != null) {
                    editIncome(existing, result.details, correctionId, now, accountId = state.accountId)
                } else {
                    val account = state.selectedAccount
                        ?: return@launch _uiState.update { it.copy(isSaving = false, message = IncomeFormMessage.NO_ACCOUNTS) }
                    recordIncome(newIncomeId, account, result.details, now)
                }
                finish(outcome)
            }
        }
    }

    fun undo() {
        val existing = entry ?: return
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            finish(voidTransaction(existing, Instant.now()))
        }
    }

    private fun finish(outcome: Result<Unit>) {
        outcome.fold(
            onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
            onFailure = { error -> _uiState.update { it.copy(isSaving = false, message = error.toMessage()) } },
        )
    }

    private fun edit(vararg cleared: IncomeProblem, change: IncomeFormUiState.() -> IncomeFormUiState) {
        _uiState.update { it.change().copy(problems = it.problems - cleared.toSet()) }
    }

    private fun Throwable.toMessage(): IncomeFormMessage = when (this) {
        is LedgerError.NotSignedIn -> IncomeFormMessage.NOT_SIGNED_IN
        is LedgerError.NothingToChange -> IncomeFormMessage.NOTHING_CHANGED
        is LedgerError.AlreadyVoided -> IncomeFormMessage.NOT_FOUND
        else -> IncomeFormMessage.FAILED
    }
}
