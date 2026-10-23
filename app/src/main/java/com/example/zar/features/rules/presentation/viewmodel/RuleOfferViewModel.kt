package com.example.zar.features.rules.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.model.RuleScope
import com.example.zar.features.rules.domain.repository.RuleOfferInbox
import com.example.zar.features.rules.domain.usecase.RememberCategoryUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleOfferUiState(
    val offer: RuleOffer? = null,
    val sheetOpen: Boolean = false,
    /** The name of the account the entry is in, for "Only in Cash". */
    val accountName: String? = null,
    /** Saving works offline; this only lets the sheet say where the rule is kept. */
    val isOnline: Boolean = true,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Answers the "Remember this choice" offer that follows a saved expense or a correction (Figma Z116, Z115, Z123).
 * A rule is created or changed only when the person taps Remember; asking again never adds a second rule.
 */
@HiltViewModel
class RuleOfferViewModel @Inject constructor(
    private val inbox: RuleOfferInbox,
    private val remember: RememberCategoryUseCase,
    observeAccounts: ObserveActiveAccountsUseCase,
    observeOnline: ObserveOnlineStatusUseCase,
) : ViewModel() {

    private data class Local(val isSaving: Boolean = false, val failed: Boolean = false)

    private val local = MutableStateFlow(Local())

    // Created once per offer and reused on every retry, so a repeated tap can't add a second rule.
    private var ruleId: Pair<RuleOffer, CategoryRuleId>? = null

    val uiState: StateFlow<RuleOfferUiState> = combine(
        inbox.state,
        observeAccounts().catch { emit(emptyList()) },
        observeOnline().catch { emit(true) },
        local,
    ) { offerState, accounts, online, local ->
        val offer = offerState.offer
        RuleOfferUiState(
            offer = offer,
            sheetOpen = offerState.sheetOpen,
            accountName = accounts.firstOrNull { it.id == offer?.accountId }?.name,
            isOnline = online,
            isSaving = local.isSaving,
            failed = local.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleOfferUiState())

    fun openSheet() {
        val offer = inbox.state.value.offer ?: return
        local.value = Local()
        inbox.open(offer)
    }

    fun dismiss() {
        if (local.value.isSaving) return
        local.value = Local()
        inbox.dismiss()
    }

    fun remember(scope: RuleScope) {
        val offer = inbox.state.value.offer ?: return
        if (local.value.isSaving) return
        local.value = Local(isSaving = true)
        val id = ruleId?.takeIf { it.first == offer }?.second ?: CategoryRuleId(RecordIds.newId()).also { ruleId = offer to it }
        viewModelScope.launch {
            remember(offer, scope, id, Instant.now()).fold(
                onSuccess = {
                    local.value = Local()
                    ruleId = null
                    inbox.dismiss()
                },
                onFailure = { local.update { Local(failed = true) } },
            )
        }
    }
}
