package com.example.zar.features.accounts.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.navigation.AccountFormRoute
import com.example.zar.features.accounts.domain.AccountRules
import com.example.zar.features.accounts.domain.model.AccountFormInput
import com.example.zar.features.accounts.domain.model.AccountFormResult
import com.example.zar.features.accounts.domain.model.AccountProblem
import com.example.zar.features.accounts.domain.model.OpeningKind
import com.example.zar.features.accounts.domain.usecase.ObserveAccountUseCase
import com.example.zar.features.accounts.domain.usecase.SaveAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountFormMessage { NOT_SIGNED_IN, NOT_FOUND, FAILED }

data class AccountFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val currency: Currency = Currencies.LKR,
    val openingKind: OpeningKind = OpeningKind.KNOWN,
    val openingAmount: String = "",
    val openingDate: LocalDate = ZarTime.today(),
    val problems: Set<AccountProblem> = emptySet(),
    val message: AccountFormMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    /** Currencies can't change once an account has been saved (firestore.rules). */
    val currencyLocked: Boolean get() = isEditing

    val typeChoices: List<AccountType>
        get() = if (isEditing) AccountRules.typesFor(currency) else AccountRules.TYPE_ORDER

    val currencyChoices: List<Currency> get() = AccountRules.currencyChoices(type)

    val openingKindChoices: List<OpeningKind> get() = AccountRules.openingKindsFor(type)

    val showsAmount: Boolean get() = openingKind != OpeningKind.UNKNOWN
}

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeAccount: ObserveAccountUseCase,
    private val saveAccount: SaveAccountUseCase,
) : ViewModel() {

    private val editedId = savedStateHandle.toRoute<AccountFormRoute>().accountId?.let(::AccountId)

    // Created once per screen and reused on every retry, so a repeated save can't add a second account.
    private val newId = AccountId(RecordIds.newId())
    private var existing: Account? = null

    private val _uiState = MutableStateFlow(AccountFormUiState(isLoading = editedId != null, isEditing = editedId != null))
    val uiState: StateFlow<AccountFormUiState> = _uiState.asStateFlow()

    init {
        if (editedId != null) viewModelScope.launch { load(editedId) }
    }

    private suspend fun load(id: AccountId) {
        try {
            val account = observeAccount(id).first()
            if (account == null) {
                _uiState.update { it.copy(isLoading = false, message = AccountFormMessage.NOT_FOUND) }
                return
            }
            existing = account
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = account.name,
                    type = account.type,
                    currency = account.currency,
                    openingKind = AccountRules.openingKind(account.openingBalance),
                    openingAmount = AccountRules.openingAmount(account.openingBalance)?.toDecimal()?.toPlainString().orEmpty(),
                    openingDate = account.openingDate,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.update { it.copy(isLoading = false, message = AccountFormMessage.FAILED) }
        }
    }

    fun onNameChange(value: String) = edit(AccountProblem.NAME_MISSING, AccountProblem.NAME_TOO_LONG) { copy(name = value) }

    fun onTypeSelect(value: AccountType) = edit(
        AccountProblem.TYPE_CURRENCY_MISMATCH,
        AccountProblem.OPENING_NOT_ALLOWED,
        AccountProblem.OPENING_INVALID,
        AccountProblem.OPENING_MISSING,
    ) {
        if (value == type) return@edit this
        // Fiat and crypto hold different assets: crossing over picks that side's first currency.
        val crossesAssetKind = AccountRules.currencyKindFor(value) != currency.kind
        copy(
            type = value,
            currency = if (crossesAssetKind) AccountRules.currencyChoices(value).first() else currency,
            openingKind = if (openingKind in AccountRules.openingKindsFor(value)) openingKind else OpeningKind.KNOWN,
            openingAmount = if (crossesAssetKind) "" else openingAmount,
        )
    }

    fun onCurrencySelect(value: Currency) = edit(AccountProblem.TYPE_CURRENCY_MISMATCH, AccountProblem.OPENING_INVALID) {
        if (currencyLocked) this else copy(currency = value)
    }

    fun onOpeningKindSelect(value: OpeningKind) = edit(AccountProblem.OPENING_NOT_ALLOWED, AccountProblem.OPENING_INVALID, AccountProblem.OPENING_MISSING) {
        copy(openingKind = value)
    }

    fun onOpeningAmountChange(value: String) = edit(AccountProblem.OPENING_MISSING, AccountProblem.OPENING_INVALID) { copy(openingAmount = value) }

    fun onOpeningDateSelect(value: LocalDate) = edit(AccountProblem.OPENING_DATE_IN_FUTURE) { copy(openingDate = value) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.message == AccountFormMessage.NOT_FOUND) return
        val input = AccountFormInput(state.name, state.type, state.currency, state.openingKind, state.openingAmount, state.openingDate)
        when (val result = AccountRules.validate(input, ZarTime.today())) {
            is AccountFormResult.Invalid -> _uiState.update { it.copy(problems = result.problems) }
            is AccountFormResult.Valid -> viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, problems = emptySet(), message = null) }
                val outcome = saveAccount(editedId ?: newId, result.details, existing)
                outcome.fold(
                    onSuccess = { _uiState.update { it.copy(isSaving = false, isFinished = true) } },
                    onFailure = { error ->
                        val message = if (error is LedgerError.NotSignedIn) AccountFormMessage.NOT_SIGNED_IN else AccountFormMessage.FAILED
                        _uiState.update { it.copy(isSaving = false, message = message) }
                    },
                )
            }
        }
    }

    private fun edit(vararg cleared: AccountProblem, change: AccountFormUiState.() -> AccountFormUiState) {
        _uiState.update { it.change().copy(problems = it.problems - cleared.toSet()) }
    }
}
