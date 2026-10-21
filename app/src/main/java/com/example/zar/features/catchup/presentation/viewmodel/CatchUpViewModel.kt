package com.example.zar.features.catchup.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.features.accounts.domain.reconcile.BalanceCheck
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.accounts.domain.reconcile.CheckResult
import com.example.zar.features.accounts.domain.reconcile.ReconcileRules
import com.example.zar.features.catchup.domain.ReminderSchedule
import com.example.zar.features.catchup.domain.model.CatchUpOverview
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpPreferencesUseCase
import com.example.zar.features.catchup.domain.usecase.ObserveCatchUpUseCase
import com.example.zar.features.catchup.domain.usecase.RecordCatchUpUseCase
import com.example.zar.features.catchup.domain.usecase.UpdateReminderSettingsUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatchUpStep { REVIEW, COUNT, SAVE, DONE }

enum class CatchUpMessage { NOT_SIGNED_IN, FAILED, SKIP_FAILED }

data class CatchUpUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val step: CatchUpStep = CatchUpStep.REVIEW,
    val overview: CatchUpOverview? = null,
    /** The cash account being counted. Null when there is no cash account to count. */
    val balance: AccountBalance? = null,
    val availability: CheckAvailability? = null,
    val counted: String = "",
    /** The typed amount next to the recorded balance, once the amount can be read. */
    val check: BalanceCheck? = null,
    val problems: Set<CheckProblem> = emptySet(),
    val isOnline: Boolean = true,
    val isSaving: Boolean = false,
    val hasSaveAttempt: Boolean = false,
    val adjustmentId: TransactionId? = null,
    val message: CatchUpMessage? = null,
    /** What was saved. Set once the catch-up is finished. */
    val result: BalanceCheck? = null,
    /** When the weekly reminder fires next, or null when it is off. */
    val nextReminder: ZonedDateTime? = null,
    /** The person chose to skip this week, so the flow should close. */
    val isLeaving: Boolean = false,
) {
    val items get() = overview?.items.orEmpty()
    val cashAccounts get() = overview?.cashAccounts.orEmpty()
}

@HiltViewModel
class CatchUpViewModel @Inject constructor(
    observeCatchUp: ObserveCatchUpUseCase,
    private val observePreferences: ObserveCatchUpPreferencesUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
    private val recordCatchUp: RecordCatchUpUseCase,
    private val updateReminder: UpdateReminderSettingsUseCase,
) : ViewModel() {

    // Created once per catch-up and reused on every retry, so a repeated save can't adjust the balance twice.
    private val adjustmentId = TransactionId(RecordIds.newId())

    private data class Local(
        val step: CatchUpStep = CatchUpStep.REVIEW,
        val accountId: AccountId? = null,
        val counted: String = "",
        val problems: Set<CheckProblem> = emptySet(),
        val isSaving: Boolean = false,
        val confirmed: BalanceCheck? = null,
        val message: CatchUpMessage? = null,
        val result: BalanceCheck? = null,
        val isLeaving: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<CatchUpUiState> = combine(
        observeCatchUp(),
        observePreferences(),
        observeOnline().catch { emit(true) },
        local,
    ) { overview, prefs, online, l ->
        val balance = overview.cashAccounts.firstOrNull { it.account.id == l.accountId } ?: overview.cashAccounts.firstOrNull()
        val check = balance?.let { (ReconcileRules.evaluate(it, l.counted) as? CheckResult.Valid)?.check }
        CatchUpUiState(
            isLoading = false,
            step = l.step,
            overview = overview,
            balance = balance,
            availability = balance?.let(ReconcileRules::availability),
            counted = l.counted,
            check = l.confirmed ?: check,
            problems = l.problems,
            isOnline = online,
            isSaving = l.isSaving,
            hasSaveAttempt = l.confirmed != null,
            adjustmentId = if (l.result?.outcome != null && l.result.outcome != CheckOutcome.MATCHES) adjustmentId else null,
            message = l.message,
            result = l.result,
            nextReminder = ReminderSchedule.next(prefs.reminder, Instant.now()),
            isLeaving = l.isLeaving,
        )
    }
        .catch { emit(CatchUpUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatchUpUiState())

    fun onCountedChange(value: String) = local.update { if (it.step == CatchUpStep.COUNT && !it.isSaving) it.copy(counted = value, problems = emptySet(), message = null) else it }

    fun onAccountSelect(id: AccountId) = local.update { if (it.step == CatchUpStep.COUNT && !it.isSaving) it.copy(accountId = id, counted = "", problems = emptySet(), message = null) else it }

    /** Moves on from the list of open items to counting the cash. */
    fun startCounting() = local.update { if (it.step == CatchUpStep.REVIEW && !it.isSaving) it.copy(step = CatchUpStep.COUNT) else it }

    /** Shows what will be saved. A count that can't be read stays on the count step with its problem. */
    fun reviewAdjustment() {
        val state = uiState.value
        val balance = state.balance ?: return
        if (state.step != CatchUpStep.COUNT || state.availability != CheckAvailability.AVAILABLE) return
        when (val result = ReconcileRules.evaluate(balance, local.value.counted)) {
            is CheckResult.Invalid -> local.update { it.copy(problems = result.problems, message = null) }
            is CheckResult.Valid -> local.update { it.copy(step = CatchUpStep.SAVE, problems = emptySet(), message = null) }
        }
    }

    /** One step back. False when there is nothing earlier, so the screen should close instead. */
    fun back(): Boolean {
        if (local.value.isSaving) return true
        // After an uncertain save, Back closes this catch-up. Editing needs a fresh flow and ID.
        if (local.value.confirmed != null) return false
        val previous = when (local.value.step) {
            CatchUpStep.SAVE -> CatchUpStep.COUNT
            CatchUpStep.COUNT -> CatchUpStep.REVIEW
            CatchUpStep.REVIEW, CatchUpStep.DONE -> return false
        }
        local.update { it.copy(step = previous, message = null) }
        return true
    }

    /** Saves the gap as one explicit adjustment, or nothing when the cash already matches. */
    fun save() {
        val state = uiState.value
        val check = local.value.confirmed ?: state.check ?: return
        if (state.step != CatchUpStep.SAVE || local.value.isSaving || local.value.result != null) return
        local.update { it.copy(isSaving = true, confirmed = check, message = null) }
        viewModelScope.launch {
            recordCatchUp(adjustmentId, check.account, check.counted, ZarTime.today(), Instant.now()).fold(
                // Show what the person confirmed, even if a retry found the gap already closed by the first attempt.
                onSuccess = { local.update { it.copy(isSaving = false, step = CatchUpStep.DONE, result = check) } },
                onFailure = { error ->
                    val message = if (error is LedgerError.NotSignedIn) CatchUpMessage.NOT_SIGNED_IN else CatchUpMessage.FAILED
                    local.update { it.copy(isSaving = false, message = message) }
                },
            )
        }
    }

    /** Skips this week's reminder and closes the flow. Nothing in the ledger changes. */
    fun skipThisWeek() {
        if (local.value.isSaving || local.value.step != CatchUpStep.REVIEW) return
        local.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val settings = observePreferences().first().reminder
                updateReminder(ReminderSchedule.skipNext(settings, Instant.now()))
            }.fold(
                onSuccess = { local.update { it.copy(isSaving = false, isLeaving = true) } },
                onFailure = { local.update { it.copy(isSaving = false, message = CatchUpMessage.SKIP_FAILED) } },
            )
        }
    }
}
