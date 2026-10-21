package com.example.zar.features.accounts.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.navigation.AccountRoute
import com.example.zar.features.accounts.domain.reconcile.BalanceCheck
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.accounts.domain.reconcile.CheckResult
import com.example.zar.features.accounts.domain.reconcile.ReconcileRules
import com.example.zar.features.accounts.domain.usecase.ObserveAccountDetailUseCase
import com.example.zar.features.accounts.domain.usecase.RecordBalanceCheckUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What to do with a shortfall: save it as an adjustment for now, or add the expense that explains it. */
enum class GapChoice { LEAVE, FIND }

enum class CheckMessage { NOT_SIGNED_IN, FAILED }

/** The expense form to open, with the gap already filled in, when the person chose to find the missing entry. */
data class MissingEntryRequest(val accountId: AccountId, val amount: String)

data class BalanceCheckUiState(
    val isLoading: Boolean = true,
    val balance: AccountBalance? = null,
    /** Null only while loading or when the account no longer exists. */
    val availability: CheckAvailability? = null,
    val counted: String = "",
    /** The typed amount next to the recorded balance, once the amount can be read. */
    val check: BalanceCheck? = null,
    val problems: Set<CheckProblem> = emptySet(),
    val choice: GapChoice = GapChoice.LEAVE,
    val isOnline: Boolean = true,
    val isSaving: Boolean = false,
    val message: CheckMessage? = null,
    val isFinished: Boolean = false,
    val missingEntry: MissingEntryRequest? = null,
) {
    /** Only a shortfall can be explained by a missing expense. An extra is saved as an adjustment. */
    val canFindMissing: Boolean get() = check?.outcome == CheckOutcome.SHORTFALL
}

@HiltViewModel
class BalanceCheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObserveAccountDetailUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
    private val recordCheck: RecordBalanceCheckUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the ViewModel can be created in plain unit tests.
    private val accountId = AccountId(savedStateHandle.get<String>(AccountRoute::accountId.name).orEmpty())

    // Created per check and reused on every retry, so a repeated confirm can't adjust the balance twice.
    private var adjustmentId = TransactionId(RecordIds.newId())

    private data class Local(
        val counted: String = "",
        val problems: Set<CheckProblem> = emptySet(),
        val choice: GapChoice = GapChoice.LEAVE,
        val isSaving: Boolean = false,
        val message: CheckMessage? = null,
        val isFinished: Boolean = false,
        val missingEntry: MissingEntryRequest? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<BalanceCheckUiState> = combine(
        observeDetail(accountId).map { it?.balance },
        observeOnline().catch { emit(true) },
        local,
    ) { balance, online, l ->
        val check = balance?.let { (ReconcileRules.evaluate(it, l.counted) as? CheckResult.Valid)?.check }
        BalanceCheckUiState(
            isLoading = false,
            balance = balance,
            availability = balance?.let(ReconcileRules::availability),
            counted = l.counted,
            check = check,
            problems = l.problems,
            choice = l.choice,
            isOnline = online,
            isSaving = l.isSaving,
            message = l.message,
            isFinished = l.isFinished,
            missingEntry = l.missingEntry,
        )
    }
        .catch { emit(BalanceCheckUiState(isLoading = false, message = CheckMessage.FAILED)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BalanceCheckUiState())

    fun onCountedChange(value: String) =
        local.update { it.copy(counted = value, problems = emptySet(), message = null) }

    fun onChoiceSelect(choice: GapChoice) = local.update { it.copy(choice = choice, message = null) }

    /**
     * Saves the gap as one explicit adjustment, saves nothing when the balance already matches, or, when the
     * person chose to find the missing entry, hands the gap to the expense form instead of saving an adjustment.
     */
    fun confirm() {
        val state = uiState.value
        val balance = state.balance ?: return
        if (state.availability != CheckAvailability.AVAILABLE || local.value.isSaving || local.value.isFinished) return
        when (val result = ReconcileRules.evaluate(balance, local.value.counted)) {
            is CheckResult.Invalid -> local.update { it.copy(problems = result.problems, message = null) }
            is CheckResult.Valid -> {
                val check = result.check
                if (check.outcome == CheckOutcome.SHORTFALL && local.value.choice == GapChoice.FIND) {
                    val amount = check.gap.toDecimal().stripTrailingZeros().toPlainString()
                    local.update { it.copy(missingEntry = MissingEntryRequest(balance.account.id, amount)) }
                    return
                }
                local.update { it.copy(isSaving = true, problems = emptySet(), message = null) }
                viewModelScope.launch {
                    recordCheck(adjustmentId, balance.account, check.counted, ZarTime.today(), Instant.now()).fold(
                        onSuccess = { local.update { it.copy(isSaving = false, isFinished = true) } },
                        onFailure = { error ->
                            val message = if (error is LedgerError.NotSignedIn) CheckMessage.NOT_SIGNED_IN else CheckMessage.FAILED
                            local.update { it.copy(isSaving = false, message = message) }
                        },
                    )
                }
            }
        }
    }

    /** The expense form has been opened for [MissingEntryRequest]; do not open it again. */
    fun onMissingEntryHandled() = local.update { it.copy(missingEntry = null) }

    /** Starts a fresh check the next time the sheet opens. */
    fun reset() {
        adjustmentId = TransactionId(RecordIds.newId())
        local.value = Local()
    }
}
