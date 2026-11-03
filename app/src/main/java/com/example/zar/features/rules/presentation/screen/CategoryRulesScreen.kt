package com.example.zar.features.rules.presentation.screen

import com.example.zar.features.transactions.presentation.category.LocalExpenseCategoryCatalog
import com.example.zar.features.transactions.presentation.manual.screen.labelText
import com.example.zar.features.transactions.presentation.category.ProvideExpenseCategoryCatalog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.catchup.presentation.screen.CatchUpNote
import com.example.zar.features.catchup.presentation.screen.CatchUpScaffold
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.presentation.viewmodel.CategoryRulesUiState
import com.example.zar.features.rules.presentation.viewmodel.CategoryRulesViewModel
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim
import com.example.zar.features.transactions.presentation.manual.screen.labelRes

/** Every remembered name (Figma Z117, Z121), with an on/off switch each and an editor (Z118). Rules are never deleted. */
@Composable
fun CategoryRulesScreen(onBack: () -> Unit, viewModel: CategoryRulesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProvideExpenseCategoryCatalog {
        CategoryRulesBody(
            state = state,
            onBack = onBack,
            onOpen = viewModel::open,
            onToggle = viewModel::setEnabled,
            onCloseEditor = viewModel::closeEditor,
            onCategory = viewModel::onCategorySelect,
            onAccount = viewModel::onAccountSelect,
            onEnabled = viewModel::onEnabledChange,
            onSave = viewModel::saveEdit,
        )
    }
}

@Composable
internal fun CategoryRulesBody(
    state: CategoryRulesUiState,
    onBack: () -> Unit,
    onOpen: (CategoryRule) -> Unit,
    onToggle: (CategoryRule, Boolean) -> Unit,
    onCloseEditor: () -> Unit,
    onCategory: (String) -> Unit,
    onAccount: (AccountId?) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ZarTheme.colors
    CatchUpScaffold(
        title = stringResource(R.string.rules_title),
        subtitle = stringResource(R.string.rules_subtitle),
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = Modifier.testTag("rules-screen"),
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                CircularProgressIndicator(color = colors.accentSelected)
            }
            state.hasError -> Text(
                stringResource(R.string.rules_failed),
                style = ZarType.bodyS,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("rules-error"),
            )
            state.rules.isEmpty() -> CatchUpNote(
                title = stringResource(R.string.rules_empty_title),
                body = stringResource(R.string.rules_empty_body),
                modifier = Modifier.testTag("rules-empty"),
            )
            else -> {
                ZarCard(Modifier.fillMaxWidth().testTag("rules-list"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                    state.rules.forEachIndexed { index, rule ->
                        if (index > 0) ZarDivider()
                        RuleRow(rule, state.accountName(rule.accountId), onOpen = { onOpen(rule) }, onToggle = { onToggle(rule, it) })
                    }
                }
                CatchUpNote(
                    title = stringResource(R.string.rules_note_title),
                    body = stringResource(R.string.rules_note_body),
                    modifier = Modifier.testTag("rules-note"),
                )
            }
        }
    }
    val editing = state.editing
    val draft = state.draft
    if (editing != null && draft != null) {
        EditRuleSheet(state, editing, onCloseEditor, onCategory, onAccount, onEnabled, onSave)
    }
}

@Composable
private fun RuleRow(rule: CategoryRule, accountName: String?, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = ZarTheme.colors
    val scope = accountName?.let { stringResource(R.string.rules_row_account, it) } ?: stringResource(R.string.rules_row_any)
    val detail = stringResource(if (rule.enabled) R.string.rules_row_detail else R.string.rules_row_detail_off, scope, rule.version)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) {}
                .testTag("rule-row-${rule.id.value}"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("${rule.displayName} → ${categoryName(rule.categoryId)}", style = ZarType.bodyM, color = if (rule.enabled) colors.textPrimary else colors.textTertiary)
            Text(detail, style = ZarType.bodyS, color = colors.textSecondary)
        }
        ZarSwitch(checked = rule.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("rule-switch-${rule.id.value}"))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditRuleSheet(
    state: CategoryRulesUiState,
    rule: CategoryRule,
    onDismiss: () -> Unit,
    onCategory: (String) -> Unit,
    onAccount: (AccountId?) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ZarTheme.colors
    val draft = state.draft ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 14.dp)
                .testTag("rule-edit-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(R.string.rules_edit_title, rule.displayName), onClose = onDismiss)
            ZarEyebrow(stringResource(R.string.rules_edit_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalExpenseCategoryCatalog.current.choices.forEach { category ->
                    ZarChip(
                        label = category.labelText(),
                        selected = draft.categoryId == category.id,
                        onClick = { onCategory(category.id) },
                        modifier = Modifier.testTag("rule-category-${category.id}"),
                    )
                }
            }
            ZarEyebrow(stringResource(R.string.rules_edit_account))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZarChip(
                    label = stringResource(R.string.rules_scope_any),
                    selected = draft.accountId == null,
                    onClick = { onAccount(null) },
                    modifier = Modifier.testTag("rule-account-any"),
                )
                state.accounts.forEach { account ->
                    ZarChip(
                        label = account.name,
                        selected = draft.accountId == account.id,
                        onClick = { onAccount(account.id) },
                        modifier = Modifier.testTag("rule-account-${account.id.value}"),
                    )
                }
            }
            Text(
                stringResource(if (state.failed) R.string.rules_failed else R.string.rules_edit_note, rule.displayName, rule.version + 1),
                style = ZarType.bodyS,
                color = if (state.failed) colors.danger else colors.textSecondary,
                modifier = Modifier.testTag("rule-edit-note"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(
                    stringResource(if (draft.enabled) R.string.rules_edit_switch_off else R.string.rules_edit_switch_on),
                    { onEnabled(!draft.enabled) },
                    Modifier.weight(1f).testTag("rule-edit-toggle"),
                    style = ZarButtonStyle.Soft,
                )
                ZarButton(
                    stringResource(R.string.rules_edit_save),
                    onSave,
                    Modifier.weight(1f).testTag("rule-edit-save"),
                    enabled = !state.isSaving,
                )
            }
        }
    }
}
