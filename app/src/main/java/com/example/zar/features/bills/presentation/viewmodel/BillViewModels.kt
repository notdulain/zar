package com.example.zar.features.bills.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.navigation.BillFormRoute
import com.example.zar.core.navigation.BillOccurrenceRoute
import com.example.zar.core.navigation.BillRoute
import com.example.zar.features.bills.domain.BillCheck
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.BillRules
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillReminder
import com.example.zar.features.bills.domain.model.BillOccurrence
import com.example.zar.features.bills.domain.model.BillSummary
import com.example.zar.features.bills.domain.model.BillsOverview
import com.example.zar.features.bills.domain.model.LinkedPayment
import com.example.zar.features.bills.domain.model.PaymentCandidate
import com.example.zar.features.bills.domain.usecase.BillError
import com.example.zar.features.bills.domain.usecase.LinkBillPaymentUseCase
import com.example.zar.features.bills.domain.usecase.ObserveBillAccountsUseCase
import com.example.zar.features.bills.domain.usecase.ObserveBillsUseCase
import com.example.zar.features.bills.domain.usecase.ObservePaymentCandidatesUseCase
import com.example.zar.features.bills.domain.usecase.SaveBillUseCase
import com.example.zar.features.bills.domain.usecase.UnlinkBillPaymentUseCase
import com.example.zar.features.reminders.domain.RefreshRemindersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BillMessage { NOT_SIGNED_IN, CURRENCY, FAILED }

private fun Throwable.toBillMessage(): BillMessage = when (this) {
    is LedgerError.NotSignedIn -> BillMessage.NOT_SIGNED_IN
    is BillError.CurrencyMismatch -> BillMessage.CURRENCY
    else -> BillMessage.FAILED
}

// --- List and detail -----------------------------------------------------------------------

data class BillsUiState(val isLoading: Boolean = true, val overview: BillsOverview? = null, val loadFailed: Boolean = false)

@HiltViewModel
class BillsViewModel @Inject constructor(observeBills: ObserveBillsUseCase) : ViewModel() {
    val uiState: StateFlow<BillsUiState> = observeBills(ZarTime.today())
        .map { BillsUiState(isLoading = false, overview = it) }
        .catch { emit(BillsUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())
}

data class BillUiState(val isLoading: Boolean = true, val summary: BillSummary? = null, val loadFailed: Boolean = false)

@HiltViewModel
class BillViewModel @Inject constructor(savedStateHandle: SavedStateHandle, observeBills: ObserveBillsUseCase) : ViewModel() {
    private val billId = BillId(savedStateHandle.toRoute<BillRoute>().billId)

    val uiState: StateFlow<BillUiState> = observeBills(ZarTime.today())
        .map { overview -> BillUiState(isLoading = false, summary = overview.bills.firstOrNull { it.bill.id == billId }) }
        .catch { emit(BillUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillUiState())
}

// --- Form ----------------------------------------------------------------------------------

data class BillFormUiState(
    val isLoading: Boolean = true,
    val bill: Bill? = null,
    val accounts: List<Account> = emptyList(),
    val name: String = "",
    val accountId: AccountId? = null,
    val amount: String = "",
    val frequency: BillFrequency = BillFrequency.MONTHLY,
    val firstDue: LocalDate = ZarTime.today(),
    val reminder: BillReminder? = null,
    val problems: Set<BillProblem> = emptySet(),
    val message: BillMessage? = null,
    val notFound: Boolean = false,
    val isSaving: Boolean = false,
    val savedBillId: String? = null,
) {
    val isEditing: Boolean get() = bill != null
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
}

@HiltViewModel
class BillFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeAccounts: ObserveBillAccountsUseCase,
    private val observeBills: ObserveBillsUseCase,
    private val saveBill: SaveBillUseCase,
    private val refreshReminders: RefreshRemindersUseCase,
) : ViewModel() {
    private val editingId = savedStateHandle.toRoute<BillFormRoute>().billId?.let(::BillId)

    // Created once and reused on retry, so a repeated save can't create two bills.
    private val newBillId = BillId(RecordIds.newId())

    private val _uiState = MutableStateFlow(BillFormUiState())
    val uiState: StateFlow<BillFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accountList = runCatching { observeAccounts().first() }.getOrDefault(emptyList())
            val bill = editingId?.let { id -> runCatching { observeBills(ZarTime.today()).first() }.getOrNull()?.bills?.firstOrNull { it.bill.id == id }?.bill }
            _uiState.update { state ->
                when {
                    editingId != null && bill == null -> state.copy(isLoading = false, notFound = true)
                    // The currency can't change on edit, so only same-currency accounts are offered.
                    bill != null -> state.copy(
                        isLoading = false, bill = bill, accounts = accountList.filter { it.currency == bill.amount.currency },
                        name = bill.name, accountId = bill.accountId,
                        amount = bill.amount.toDecimal().toPlainString(), frequency = bill.frequency, firstDue = bill.firstDue,
                        reminder = bill.reminder,
                    )
                    else -> state.copy(isLoading = false, accounts = accountList, accountId = accountList.firstOrNull()?.id)
                }
            }
        }
    }

    fun onNameChange(value: String) = edit(BillProblem.NAME_MISSING, BillProblem.NAME_TOO_LONG) { copy(name = value) }
    fun onAccountSelect(id: AccountId) = edit(BillProblem.ACCOUNT_MISSING, BillProblem.AMOUNT_INVALID) { copy(accountId = id) }
    fun onAmountChange(value: String) = edit(BillProblem.AMOUNT_MISSING, BillProblem.AMOUNT_INVALID) { copy(amount = value) }
    fun onFrequencySelect(value: BillFrequency) = edit { copy(frequency = value) }
    fun onFirstDueSelect(value: LocalDate) = edit { copy(firstDue = value) }
    fun onReminderSelect(value: BillReminder?) = edit { copy(reminder = value) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        when (val checked = BillRules.checkBill(state.name, state.account, state.amount, state.frequency, state.firstDue, state.reminder)) {
            is BillCheck.Invalid -> _uiState.update { it.copy(problems = checked.problems) }
            is BillCheck.Valid -> run(state.bill?.id ?: newBillId) {
                state.bill?.let { saveBill.update(it, checked.value) } ?: saveBill.create(newBillId, checked.value, Instant.now())
            }
        }
    }

    fun setStopped(stopped: Boolean) {
        val bill = _uiState.value.bill ?: return
        run(bill.id) { saveBill.setStopped(bill, stopped) }
    }

    private fun run(id: BillId, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            block().fold(
                onSuccess = {
                    // A reminder due today shouldn't wait for tomorrow's check.
                    runCatching { refreshReminders() }
                    _uiState.update { it.copy(isSaving = false, savedBillId = id.value) }
                },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toBillMessage()) } },
            )
        }
    }

    private fun edit(vararg cleared: BillProblem, change: BillFormUiState.() -> BillFormUiState) {
        _uiState.update { it.change().copy(problems = it.problems - cleared.toSet()) }
    }
}

// --- One due date --------------------------------------------------------------------------

data class BillOccurrenceUiState(
    val isLoading: Boolean = true,
    val bill: Bill? = null,
    val occurrence: BillOccurrence? = null,
    val candidates: List<PaymentCandidate> = emptyList(),
    val linkAmounts: Map<String, String> = emptyMap(),
    val linkProblems: Map<String, Set<BillProblem>> = emptyMap(),
    val message: BillMessage? = null,
    val isSaving: Boolean = false,
) {
    val payments: List<LinkedPayment> get() = occurrence?.payments.orEmpty()

    fun suggestedAmount(candidate: PaymentCandidate) = occurrence?.progress?.remaining
        ?.let { if (candidate.available > it) it else candidate.available } ?: candidate.available
}

@HiltViewModel
class BillOccurrenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeBills: ObserveBillsUseCase,
    observeCandidates: ObservePaymentCandidatesUseCase,
    private val linkPayment: LinkBillPaymentUseCase,
    private val unlinkPayment: UnlinkBillPaymentUseCase,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<BillOccurrenceRoute>()
    private val billId = BillId(route.billId)
    private val dueDate = LocalDate.parse(route.dueDate)

    private val _uiState = MutableStateFlow(BillOccurrenceUiState())
    val uiState: StateFlow<BillOccurrenceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeBills(ZarTime.today()), observeCandidates(billId, dueDate)) { overview, candidates -> overview to candidates }
                .catch { _uiState.update { it.copy(isLoading = false, message = BillMessage.FAILED) } }
                .collect { (overview, candidates) ->
                    val summary = overview.bills.firstOrNull { it.bill.id == billId }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bill = summary?.bill,
                            occurrence = summary?.occurrences?.firstOrNull { o -> o.dueDate == dueDate },
                            candidates = candidates,
                        )
                    }
                }
        }
    }

    fun onLinkAmountChange(key: String, value: String) =
        _uiState.update { it.copy(linkAmounts = it.linkAmounts + (key to value), linkProblems = it.linkProblems - key) }

    fun link(candidate: PaymentCandidate) {
        val state = _uiState.value
        val bill = state.bill ?: return
        val occurrence = state.occurrence ?: return
        val key = candidate.entry.id.value
        val typed = state.linkAmounts[key] ?: state.suggestedAmount(candidate).toDecimal().toPlainString()
        when (val checked = BillRules.checkLink(typed, candidate.available, occurrence.progress.remaining)) {
            is BillCheck.Invalid -> _uiState.update { it.copy(linkProblems = it.linkProblems + (key to checked.problems)) }
            is BillCheck.Valid -> save { linkPayment(bill, occurrence, candidate, checked.value, Instant.now()) }
        }
    }

    fun unlink(payment: LinkedPayment) = save { unlinkPayment(payment.match) }

    private fun save(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val result = block()
            _uiState.update { it.copy(isSaving = false, message = result.exceptionOrNull()?.toBillMessage()) }
        }
    }
}
