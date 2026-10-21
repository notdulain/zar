package com.example.zar.features.transactions.presentation.refund.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.navigation.RefundRoute
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.refund.RefundContext
import com.example.zar.features.transactions.domain.refund.RefundFormInput
import com.example.zar.features.transactions.domain.refund.RefundFormResult
import com.example.zar.features.transactions.domain.refund.RefundProblem
import com.example.zar.features.transactions.domain.refund.RefundRules
import com.example.zar.features.transactions.domain.refund.RefundSummary
import com.example.zar.features.transactions.domain.refund.usecase.ObserveRefundContextUseCase
import com.example.zar.features.transactions.domain.refund.usecase.RecordRefundUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RefundMessage { NOT_SIGNED_IN, ALREADY_VOIDED, SAVED_FIRST, FAILED }

data class RefundFormUiState(
    val isLoading: Boolean = true,
    /** The purchase is gone, voided, fully refunded or on an archived account. */
    val notRefundable: Boolean = false,
    val context: RefundContext? = null,
    val amount: String = "",
    val date: LocalDate = ZarTime.today(),
    val note: String = "",
    val problems: Set<RefundProblem> = emptySet(),
    val message: RefundMessage? = null,
    val isOnline: Boolean = true,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val summary: RefundSummary? get() = context?.summary

    /** The currency is the purchase's own and cannot be changed. */
    val currencyCode: String get() = context?.summary?.purchase?.current?.amount?.currency?.code.orEmpty()
}

@HiltViewModel
class RefundFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeContext: ObserveRefundContextUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
    private val recordRefund: RecordRefundUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the ViewModel can be created in plain unit tests.
    private val purchaseId = TransactionId(savedStateHandle.get<String>(RefundRoute::purchaseId.name).orEmpty())

    // Created once per screen and reused on every retry, so a repeated save can't add a second refund.
    private val refundId = TransactionId(RecordIds.newId())

    private val _uiState = MutableStateFlow(RefundFormUiState())
    val uiState: StateFlow<RefundFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Live, so "what is left" follows any refund saved meanwhile. What has been typed is never touched.
            observeContext(purchaseId)
                .catch { _uiState.update { it.copy(isLoading = false, notRefundable = true) } }
                .collect { context ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            context = context,
                            // Once saving starts the form is on its way out; the last refund making the purchase
                            // "fully refunded" must not flash the not-refundable message.
                            notRefundable = !it.isSaving && !it.isFinished && (context == null ||
                                (!context.canRefund && context.summary.refunds.none { refund -> refund.id == refundId })),
                        )
                    }
                }
        }
        viewModelScope.launch {
            observeOnline().catch { }.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    private fun edit(clear: Set<RefundProblem> = emptySet(), change: (RefundFormUiState) -> RefundFormUiState) =
        _uiState.update { change(it).copy(problems = it.problems - clear, message = null) }

    fun onAmountChange(value: String) = edit(
        setOf(RefundProblem.AMOUNT_MISSING, RefundProblem.AMOUNT_INVALID, RefundProblem.AMOUNT_TOO_HIGH),
    ) { it.copy(amount = value) }

    fun onDateSelect(value: LocalDate) = edit(setOf(RefundProblem.DATE_IN_FUTURE)) { it.copy(date = value) }

    fun onNoteChange(value: String) = edit(setOf(RefundProblem.NOTE_TOO_LONG)) { it.copy(note = value) }

    fun save() {
        val state = _uiState.value
        val context = state.context ?: return
        val account = context.account ?: return
        if (state.isSaving || state.isFinished || state.isLoading) return
        if (context.summary.refunds.any { it.id == refundId }) {
            // The local write succeeded but its acknowledgement was lost. A full refund now has
            // nothing left to validate; retrying this form must still finish without another write.
            _uiState.update { it.copy(isFinished = true, message = null) }
            return
        }
        val input = RefundFormInput(state.amount, state.date, state.note)
        when (val result = RefundRules.validate(context.summary, account, input, ZarTime.today())) {
            is RefundFormResult.Invalid -> _uiState.update { it.copy(problems = result.problems, message = null) }
            is RefundFormResult.Valid -> {
                _uiState.update { it.copy(isSaving = true, problems = emptySet(), message = null) }
                viewModelScope.launch {
                    recordRefund(refundId, result.details, Instant.now()).fold(
                        onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
                        onFailure = { error ->
                            _uiState.update {
                                when (error) {
                                    // Another refund took part of what was left: the live summary already shows the new limit.
                                    is LedgerError.RefundTooHigh -> it.copy(
                                        isSaving = false,
                                        problems = setOf(RefundProblem.AMOUNT_TOO_HIGH),
                                        message = RefundMessage.SAVED_FIRST,
                                    )
                                    is LedgerError.AlreadyVoided -> it.copy(isSaving = false, message = RefundMessage.ALREADY_VOIDED)
                                    is LedgerError.NotSignedIn -> it.copy(isSaving = false, message = RefundMessage.NOT_SIGNED_IN)
                                    else -> it.copy(isSaving = false, message = RefundMessage.FAILED)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
