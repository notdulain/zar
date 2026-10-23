package com.example.zar.features.crypto.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.crypto.domain.CryptoCheck
import com.example.zar.features.crypto.domain.CryptoMovementInput
import com.example.zar.features.crypto.domain.CryptoMovementRules
import com.example.zar.features.crypto.domain.CryptoProblem
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.domain.model.CryptoPortfolio
import com.example.zar.features.crypto.domain.usecase.ObserveCryptoUseCase
import com.example.zar.features.crypto.domain.usecase.ObserveMovementAccountsUseCase
import com.example.zar.features.crypto.domain.usecase.RecordCryptoMovementUseCase
import com.example.zar.features.crypto.domain.usecase.SetOpeningCostUseCase
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

enum class CryptoMessage { NOT_SIGNED_IN, FAILED }

private fun Throwable.toCryptoMessage(): CryptoMessage =
    if (this is LedgerError.NotSignedIn) CryptoMessage.NOT_SIGNED_IN else CryptoMessage.FAILED

// --- Overview ------------------------------------------------------------------------------

data class CryptoUiState(
    val isLoading: Boolean = true,
    val portfolio: CryptoPortfolio? = null,
    val loadFailed: Boolean = false,
    /** Wallet whose opening cost is being edited, with the typed value. */
    val editingCostFor: Account? = null,
    val costText: String = "",
    val costApproximate: Boolean = false,
    val costInvalid: Boolean = false,
    val message: CryptoMessage? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class CryptoViewModel @Inject constructor(
    observeCrypto: ObserveCryptoUseCase,
    private val setOpeningCost: SetOpeningCostUseCase,
    private val voidTransaction: VoidTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CryptoUiState())
    val uiState: StateFlow<CryptoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeCrypto()
                .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { portfolio -> _uiState.update { it.copy(isLoading = false, portfolio = portfolio, loadFailed = false) } }
        }
    }

    fun editCost(wallet: Account) {
        val existing = _uiState.value.portfolio?.openingCosts?.get(wallet.id)
        _uiState.update {
            it.copy(
                editingCostFor = wallet,
                costText = existing?.cost?.toDecimal()?.toPlainString().orEmpty(),
                costApproximate = existing?.isApproximate ?: false,
                costInvalid = false,
            )
        }
    }

    fun onCostChange(value: String) = _uiState.update { it.copy(costText = value, costInvalid = false) }

    fun onCostApproximateChange(value: Boolean) = _uiState.update { it.copy(costApproximate = value) }

    fun dismissCost() = _uiState.update { it.copy(editingCostFor = null) }

    fun saveCost() {
        val state = _uiState.value
        val wallet = state.editingCostFor ?: return
        when (val checked = CryptoMovementRules.parseOpeningCost(state.costText)) {
            is CryptoCheck.Invalid -> _uiState.update { it.copy(costInvalid = true) }
            is CryptoCheck.Valid -> save {
                setOpeningCost(wallet, checked.value, state.costApproximate, Instant.now()).also { result ->
                    if (result.isSuccess) _uiState.update { it.copy(editingCostFor = null) }
                }
            }
        }
    }

    fun undo(row: CryptoMovementRow) = save { voidTransaction(row.outEntry, Instant.now()) }

    private fun save(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val result = block()
            _uiState.update { it.copy(isSaving = false, message = result.exceptionOrNull()?.toCryptoMessage()) }
        }
    }
}

// --- Movement form -------------------------------------------------------------------------

data class CryptoMovementUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val fromId: AccountId? = null,
    val toId: AccountId? = null,
    val sent: String = "",
    val received: String = "",
    val lkrValue: String = "",
    val date: LocalDate = ZarTime.today(),
    val note: String = "",
    val problems: Set<CryptoProblem> = emptySet(),
    val message: CryptoMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val from: Account? get() = accounts.firstOrNull { it.id == fromId }
    val to: Account? get() = accounts.firstOrNull { it.id == toId }
    val kind: CryptoMovementKind? get() {
        val f = from ?: return null
        val t = to ?: return null
        return if (f.id == t.id) null else CryptoMovementRules.kindOf(f, t)
    }
    val showValue: Boolean get() = !CryptoMovementRules.valueIsImplied(from, to)
}

@HiltViewModel
class CryptoMovementViewModel @Inject constructor(
    private val observeAccounts: ObserveMovementAccountsUseCase,
    private val recordMovement: RecordCryptoMovementUseCase,
) : ViewModel() {

    // Created once and reused on retry, so a repeated save can't record the movement twice.
    private val transferId = TransferId(RecordIds.newId())

    private val _uiState = MutableStateFlow(CryptoMovementUiState())
    val uiState: StateFlow<CryptoMovementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = runCatching { observeAccounts().first() }.getOrDefault(emptyList())
            _uiState.update { it.copy(isLoading = false, accounts = accounts) }
        }
    }

    fun onFromSelect(id: AccountId) = edit(CryptoProblem.FROM_MISSING, CryptoProblem.SAME_ACCOUNT, CryptoProblem.NO_CRYPTO_ACCOUNT) { copy(fromId = id) }

    fun onToSelect(id: AccountId) = edit(CryptoProblem.TO_MISSING, CryptoProblem.SAME_ACCOUNT, CryptoProblem.NO_CRYPTO_ACCOUNT) { copy(toId = id) }

    fun onSentChange(value: String) = edit(CryptoProblem.SENT_MISSING, CryptoProblem.SENT_INVALID) { copy(sent = value) }

    fun onReceivedChange(value: String) = edit(CryptoProblem.RECEIVED_MISSING, CryptoProblem.RECEIVED_INVALID) { copy(received = value) }

    fun onValueChange(value: String) = edit(CryptoProblem.VALUE_INVALID) { copy(lkrValue = value) }

    fun onDateSelect(value: LocalDate) = edit(CryptoProblem.DATE_IN_FUTURE) { copy(date = value) }

    fun onNoteChange(value: String) = edit(CryptoProblem.NOTE_TOO_LONG) { copy(note = value) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val input = CryptoMovementInput(state.from, state.to, state.sent, state.received, state.lkrValue, state.date, state.note)
        when (val checked = CryptoMovementRules.validate(input, ZarTime.today())) {
            is CryptoCheck.Invalid -> _uiState.update { it.copy(problems = checked.problems) }
            is CryptoCheck.Valid -> viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, message = null) }
                recordMovement(transferId, checked.value, Instant.now()).fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
                    onFailure = { e -> _uiState.update { it.copy(isSaving = false, message = e.toCryptoMessage()) } },
                )
            }
        }
    }

    private fun edit(vararg cleared: CryptoProblem, change: CryptoMovementUiState.() -> CryptoMovementUiState) {
        _uiState.update { it.change().copy(problems = it.problems - cleared.toSet()) }
    }
}
