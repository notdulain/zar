package com.example.zar.features.transactions.presentation.manual.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.transactions.domain.manual.EntrySuggestions
import com.example.zar.features.transactions.domain.manual.ManualEntryRules
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormInput
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormResult
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferFormInput
import com.example.zar.features.transactions.domain.manual.model.TransferFormResult
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import com.example.zar.features.transactions.domain.manual.usecase.ObserveEntrySuggestionsUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordExpenseUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ManualEntryKind { EXPENSE, TRANSFER }

enum class ManualEntryMessage { NOT_SIGNED_IN, ACCOUNT_ARCHIVED, FAILED }

data class ManualEntryUiState(
    val isLoading: Boolean = true,
    val kind: ManualEntryKind = ManualEntryKind.EXPENSE,
    val accounts: List<Account> = emptyList(),
    /** Saving works offline; this only lets the form say where the entry is kept. */
    val isOnline: Boolean = true,
    // Shared by both kinds, so switching tabs keeps what was typed.
    val amount: String = "",
    val date: LocalDate = ZarTime.today(),
    val time: LocalTime = LocalTime.now(ZarTime.ZONE).truncatedTo(ChronoUnit.MINUTES),
    // Expense
    val accountId: AccountId? = null,
    val category: ExpenseCategory? = null,
    val spendingClass: SpendingClass = SpendingClass.DISCRETIONARY,
    /** True once the user picked "Counts as" themselves, so a category no longer overrides it. */
    val spendingClassChosen: Boolean = false,
    val title: String = "",
    val description: String = "",
    val suggestions: List<EntrySuggestion> = emptyList(),
    val expenseProblems: Set<ExpenseProblem> = emptySet(),
    // Transfer
    val fee: String = "",
    val fromId: AccountId? = null,
    val toId: AccountId? = null,
    val transferNote: String = "",
    val transferProblems: Set<TransferProblem> = emptySet(),
    val message: ManualEntryMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }
    val fromAccount: Account? get() = accounts.firstOrNull { it.id == fromId }
    val toAccount: Account? get() = accounts.firstOrNull { it.id == toId }

    /** The currency the amount is typed in: always the account's, never chosen separately. */
    val currency: Currency
        get() = when (kind) {
            ManualEntryKind.EXPENSE -> selectedAccount?.currency
            ManualEntryKind.TRANSFER -> fromAccount?.currency
        } ?: Currencies.REPORTING

    /** Accounts money can be sent to: any other account in the sender's currency. */
    val transferTargets: List<Account>
        get() = accounts.filter { it.id != fromId && it.currency == fromAccount?.currency }

    /** True when some accounts are left out of the "to" choices only because of their currency. */
    val hidesOtherCurrencies: Boolean
        get() = accounts.any { it.id != fromId && it.currency != fromAccount?.currency }

    private val amountMoney: Money? get() = Money.parse(amount, currency)?.takeIf { it.isPositive }
    private val feeMoney: Money? get() = if (fee.isBlank()) null else Money.parse(fee, currency)?.takeIf { it.isPositive }

    /** What leaves the sender: the amount plus any fee. Null until the amount is valid. */
    val transferOutTotal: Money?
        get() = amountMoney?.let { amount -> runCatching { feeMoney?.let { amount + it } ?: amount }.getOrNull() }

    /** What arrives. Null until the amount is valid. */
    val transferInTotal: Money? get() = amountMoney
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val observeAccounts: ObserveActiveAccountsUseCase,
    private val observeSuggestions: ObserveEntrySuggestionsUseCase,
    private val observeOnline: ObserveOnlineStatusUseCase,
    private val recordExpense: RecordExpenseUseCase,
    private val recordTransfer: RecordTransferUseCase,
) : ViewModel() {

    // Created once per screen and reused on every retry, so a repeated save can't duplicate.
    private val expenseId = TransactionId(RecordIds.newId())
    private val transferId = TransferId(RecordIds.newId())
    private var allSuggestions: List<EntrySuggestion> = emptyList()

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAccounts()
                .catch { _uiState.update { it.copy(isLoading = false, message = ManualEntryMessage.FAILED) } }
                .collect { accounts -> _uiState.update { it.withAccounts(accounts) } }
        }
        viewModelScope.launch {
            observeSuggestions().catch { }.collect { suggestions ->
                allSuggestions = suggestions
                _uiState.update { it.copy(suggestions = suggestionsFor(it.title)) }
            }
        }
        viewModelScope.launch {
            observeOnline().catch { }.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun onKindSelect(kind: ManualEntryKind) = _uiState.update { it.copy(kind = kind, message = null) }

    fun onAmountChange(value: String) = _uiState.update {
        it.copy(
            amount = value,
            expenseProblems = it.expenseProblems - setOf(ExpenseProblem.AMOUNT_MISSING, ExpenseProblem.AMOUNT_INVALID),
            transferProblems = it.transferProblems - setOf(TransferProblem.AMOUNT_MISSING, TransferProblem.AMOUNT_INVALID),
        )
    }

    fun onDateSelect(value: LocalDate) = _uiState.update {
        it.copy(
            date = value,
            expenseProblems = it.expenseProblems - ExpenseProblem.DATE_IN_FUTURE,
            transferProblems = it.transferProblems - TransferProblem.DATE_IN_FUTURE,
        )
    }

    fun onTimeSelect(value: LocalTime) = _uiState.update {
        it.copy(time = value.truncatedTo(ChronoUnit.MINUTES))
    }

    fun onAccountSelect(id: AccountId) = _uiState.update {
        it.copy(accountId = id, expenseProblems = it.expenseProblems - ExpenseProblem.ACCOUNT_MISSING)
    }

    fun onCategorySelect(value: ExpenseCategory) = _uiState.update {
        val usual = value.usualClass
        it.copy(
            category = value,
            spendingClass = if (!it.spendingClassChosen && usual != null) usual else it.spendingClass,
            expenseProblems = it.expenseProblems - ExpenseProblem.CATEGORY_MISSING,
        )
    }

    fun onSpendingClassSelect(value: SpendingClass) = _uiState.update {
        it.copy(spendingClass = value, spendingClassChosen = true)
    }

    fun onTitleChange(value: String) = _uiState.update {
        it.copy(
            title = value,
            suggestions = suggestionsFor(value),
            expenseProblems = it.expenseProblems - ExpenseProblem.TITLE_TOO_LONG,
        )
    }

    fun onDescriptionChange(value: String) = _uiState.update {
        it.copy(description = value, expenseProblems = it.expenseProblems - ExpenseProblem.DESCRIPTION_TOO_LONG)
    }

    /** Fills the form from a recent entry. The amount is only filled if none was typed yet. */
    fun onSuggestionSelect(suggestion: EntrySuggestion) = _uiState.update {
        val account = it.accounts.firstOrNull { account -> account.id == suggestion.accountId && account.currency == suggestion.amount.currency } ?: return@update it
        it.copy(
            kind = ManualEntryKind.EXPENSE,
            title = suggestion.title,
            suggestions = emptyList(),
            category = suggestion.category ?: it.category,
            accountId = account.id,
            amount = it.amount.ifBlank { suggestion.amount.toDecimal().stripTrailingZeros().toPlainString() },
            spendingClass = suggestion.spendingClass,
            spendingClassChosen = true,
            expenseProblems = emptySet(),
        )
    }

    fun onFeeChange(value: String) = _uiState.update {
        it.copy(fee = value, transferProblems = it.transferProblems - TransferProblem.FEE_INVALID)
    }

    fun onFromSelect(id: AccountId) = _uiState.update {
        val from = it.accounts.firstOrNull { account -> account.id == id } ?: return@update it
        val keepTo = it.toAccount?.takeIf { to -> to.id != from.id && to.currency == from.currency }
        it.copy(
            fromId = id,
            toId = (keepTo ?: it.accounts.firstOrNull { to -> to.id != from.id && to.currency == from.currency })?.id,
            transferProblems = it.transferProblems - setOf(
                TransferProblem.FROM_MISSING,
                TransferProblem.SAME_ACCOUNT,
                TransferProblem.DIFFERENT_CURRENCIES,
            ),
        )
    }

    /** Ignored for the sending account and for accounts in another currency. */
    fun onToSelect(id: AccountId) = _uiState.update {
        if (it.transferTargets.none { account -> account.id == id }) return@update it
        it.copy(
            toId = id,
            transferProblems = it.transferProblems - setOf(
                TransferProblem.TO_MISSING,
                TransferProblem.SAME_ACCOUNT,
                TransferProblem.DIFFERENT_CURRENCIES,
            ),
        )
    }

    fun onSwapAccounts() = _uiState.update {
        val to = it.toAccount ?: return@update it
        it.copy(fromId = to.id, toId = it.fromId, transferProblems = emptySet())
    }

    fun onTransferNoteChange(value: String) = _uiState.update {
        it.copy(transferNote = value, transferProblems = it.transferProblems - TransferProblem.NOTE_TOO_LONG)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.isFinished) return
        when (state.kind) {
            ManualEntryKind.EXPENSE -> saveExpense(state)
            ManualEntryKind.TRANSFER -> saveTransfer(state)
        }
    }

    private fun saveExpense(state: ManualEntryUiState) {
        val input = ExpenseFormInput(
            amount = state.amount,
            account = state.selectedAccount,
            date = state.date,
            time = state.time,
            category = state.category,
            spendingClass = state.spendingClass,
            title = state.title,
            description = state.description,
        )
        when (val result = ManualEntryRules.validateExpense(input, ZarTime.today())) {
            is ExpenseFormResult.Invalid -> _uiState.update { it.copy(expenseProblems = result.problems, message = null) }
            is ExpenseFormResult.Valid -> {
                _uiState.update { it.copy(isSaving = true, expenseProblems = emptySet(), message = null) }
                viewModelScope.launch { finish(recordExpense(expenseId, result.details, Instant.now())) }
            }
        }
    }

    private fun saveTransfer(state: ManualEntryUiState) {
        val input = TransferFormInput(
            amount = state.amount,
            fee = state.fee,
            from = state.fromAccount,
            to = state.toAccount,
            date = state.date,
            time = state.time,
            note = state.transferNote,
        )
        when (val result = ManualEntryRules.validateTransfer(input, ZarTime.today())) {
            is TransferFormResult.Invalid -> _uiState.update { it.copy(transferProblems = result.problems, message = null) }
            is TransferFormResult.Valid -> {
                _uiState.update { it.copy(isSaving = true, transferProblems = emptySet(), message = null) }
                viewModelScope.launch { finish(recordTransfer(transferId, result.details, Instant.now())) }
            }
        }
    }

    private fun finish(outcome: Result<Unit>) {
        outcome.fold(
            onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
            onFailure = { error -> _uiState.update { it.copy(isSaving = false, message = error.toMessage()) } },
        )
    }

    private fun suggestionsFor(typed: String, accounts: List<Account> = _uiState.value.accounts): List<EntrySuggestion> =
        EntrySuggestions.matching(allSuggestions.filter { suggestion ->
            accounts.any { it.id == suggestion.accountId && it.currency == suggestion.amount.currency }
        }, typed).filterNot { it.title.equals(typed.trim(), ignoreCase = true) }

    /** Keeps each choice that is still valid and fills the rest with the first sensible account. */
    private fun ManualEntryUiState.withAccounts(list: List<Account>): ManualEntryUiState {
        val first = list.firstOrNull()
        val expenseAccount = list.firstOrNull { it.id == accountId } ?: first
        val from = list.firstOrNull { it.id == fromId } ?: first
        val to = list.firstOrNull { it.id == toId && it.id != from?.id && it.currency == from?.currency }
            ?: list.firstOrNull { it.id != from?.id && it.currency == from?.currency }
        return copy(
            isLoading = false,
            accounts = list,
            accountId = expenseAccount?.id,
            fromId = from?.id,
            toId = to?.id,
            suggestions = suggestionsFor(title, list),
        )
    }

    private fun Throwable.toMessage(): ManualEntryMessage = when (this) {
        is LedgerError.NotSignedIn -> ManualEntryMessage.NOT_SIGNED_IN
        is LedgerError.AccountArchived -> ManualEntryMessage.ACCOUNT_ARCHIVED
        else -> ManualEntryMessage.FAILED
    }
}
