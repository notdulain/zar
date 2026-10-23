package com.example.zar.features.rules.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.CategoryRuleId
import com.example.zar.features.rules.domain.usecase.EditCategoryRuleUseCase
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The rule's category, account and on/off state as the editor shows them before saving. */
data class RuleDraft(
    val categoryId: String,
    val accountId: AccountId?,
    val enabled: Boolean,
)

data class CategoryRulesUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val rules: List<CategoryRule> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** The rule open in the editor, with what is being changed. */
    val editing: CategoryRule? = null,
    val draft: RuleDraft? = null,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
) {
    fun accountName(id: AccountId?): String? = accounts.firstOrNull { it.id == id }?.name
}

/** Lists the person's category rules (Figma Z117, Z121) and edits one (Z118). Rules are switched off, never deleted. */
@HiltViewModel
class CategoryRulesViewModel @Inject constructor(
    observeRules: ObserveCategoryRulesUseCase,
    observeAccounts: ObserveActiveAccountsUseCase,
    private val editRule: EditCategoryRuleUseCase,
) : ViewModel() {

    private data class Local(
        val editingId: CategoryRuleId? = null,
        val draft: RuleDraft? = null,
        val isSaving: Boolean = false,
        val failed: Boolean = false,
    )

    private data class Loaded(val rules: List<CategoryRule>?, val failed: Boolean)

    private val local = MutableStateFlow(Local())

    private val rules = observeRules()
        .map { Loaded(it, failed = false) }
        .catch { emit(Loaded(emptyList(), failed = true)) }
        .onStart { emit(Loaded(null, failed = false)) }

    val uiState: StateFlow<CategoryRulesUiState> = combine(
        rules,
        observeAccounts().catch { emit(emptyList()) },
        local,
    ) { loaded, accounts, local ->
        val list = loaded.rules.orEmpty()
        val editing = list.firstOrNull { it.id == local.editingId }
        CategoryRulesUiState(
            isLoading = loaded.rules == null,
            hasError = loaded.failed,
            rules = list,
            accounts = accounts,
            editing = editing,
            draft = if (editing != null) local.draft else null,
            isSaving = local.isSaving,
            failed = local.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryRulesUiState())

    fun open(rule: CategoryRule) {
        local.value = Local(editingId = rule.id, draft = RuleDraft(rule.categoryId, rule.accountId, rule.enabled))
    }

    fun closeEditor() {
        if (!local.value.isSaving) local.value = Local()
    }

    fun onCategorySelect(id: String) = local.update { state -> state.draft?.let { state.copy(draft = it.copy(categoryId = id), failed = false) } ?: state }

    fun onAccountSelect(id: AccountId?) = local.update { state -> state.draft?.let { state.copy(draft = it.copy(accountId = id), failed = false) } ?: state }

    fun onEnabledChange(value: Boolean) = local.update { state -> state.draft?.let { state.copy(draft = it.copy(enabled = value), failed = false) } ?: state }

    /** Saves the editor. An unchanged rule keeps its version. */
    fun saveEdit() {
        val state = local.value
        val id = state.editingId ?: return
        val draft = state.draft ?: return
        if (state.isSaving) return
        local.value = state.copy(isSaving = true, failed = false)
        viewModelScope.launch {
            editRule(id, draft.categoryId, draft.accountId, draft.enabled, Instant.now()).fold(
                onSuccess = { local.value = Local() },
                onFailure = { local.update { it.copy(isSaving = false, failed = true) } },
            )
        }
    }

    /** The on/off switch on a list row. */
    fun setEnabled(rule: CategoryRule, enabled: Boolean) {
        if (rule.enabled == enabled) return
        viewModelScope.launch {
            editRule(rule.id, rule.categoryId, rule.accountId, enabled, Instant.now()).onFailure {
                local.update { state -> state.copy(failed = true) }
            }
        }
    }
}
