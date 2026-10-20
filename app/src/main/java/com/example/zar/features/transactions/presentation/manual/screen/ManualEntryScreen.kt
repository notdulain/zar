package com.example.zar.features.transactions.presentation.manual.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.format.formatForRow
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.presentation.screen.iconRes
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryMessage
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add a manual expense or transfer. Laid out like the Figma sheets (Z06, Z12 and the Z31–Z35 states).
 * [focusNote] opens it with the note field focused, for the Add menu's "Quick note".
 */
@Composable
fun ManualEntryScreen(
    focusNote: Boolean,
    onDone: () -> Unit,
    onOpenIncome: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
    ManualEntryContent(
        uiState = uiState,
        focusNote = focusNote,
        actions = ManualEntryActions(
            onClose = onDone,
            onOpenIncome = onOpenIncome,
            onAddAccount = onAddAccount,
            onKind = viewModel::onKindSelect,
            onAmount = viewModel::onAmountChange,
            onDate = viewModel::onDateSelect,
            onAccount = viewModel::onAccountSelect,
            onCategory = viewModel::onCategorySelect,
            onSpendingClass = viewModel::onSpendingClassSelect,
            onTitle = viewModel::onTitleChange,
            onDescription = viewModel::onDescriptionChange,
            onSuggestion = viewModel::onSuggestionSelect,
            onFee = viewModel::onFeeChange,
            onFrom = viewModel::onFromSelect,
            onTo = viewModel::onToSelect,
            onSwap = viewModel::onSwapAccounts,
            onTransferNote = viewModel::onTransferNoteChange,
            onSave = viewModel::save,
        ),
    )
}

internal class ManualEntryActions(
    val onClose: () -> Unit,
    val onOpenIncome: () -> Unit,
    val onAddAccount: () -> Unit,
    val onKind: (ManualEntryKind) -> Unit,
    val onAmount: (String) -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onAccount: (AccountId) -> Unit,
    val onCategory: (ExpenseCategory) -> Unit,
    val onSpendingClass: (SpendingClass) -> Unit,
    val onTitle: (String) -> Unit,
    val onDescription: (String) -> Unit,
    val onSuggestion: (EntrySuggestion) -> Unit,
    val onFee: (String) -> Unit,
    val onFrom: (AccountId) -> Unit,
    val onTo: (AccountId) -> Unit,
    val onSwap: () -> Unit,
    val onTransferNote: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun ManualEntryContent(uiState: ManualEntryUiState, focusNote: Boolean, actions: ManualEntryActions) {
    val colors = ZarTheme.colors
    val closeLabel = stringResource(R.string.manual_close)
    val noteFocus = remember { FocusRequester() }
    var quickNoteTaps by remember { mutableIntStateOf(0) }
    var openedWithFocus by rememberSaveable { mutableStateOf(false) }

    // "Quick note" focuses the note once; the field may not exist yet if the form was on Transfer.
    LaunchedEffect(uiState.isLoading, uiState.kind, quickNoteTaps) {
        val wanted = quickNoteTaps > 0 || (focusNote && !openedWithFocus)
        if (wanted && !uiState.isLoading && uiState.kind == ManualEntryKind.EXPENSE) {
            runCatching { noteFocus.requestFocus() }
            openedWithFocus = true
        }
    }

    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.manual_title), style = ZarType.headingSheet, color = colors.textPrimary)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.manual_close), onClick = actions.onClose)
                    .semantics { contentDescription = closeLabel }
                    .testTag("manual-close"),
                Alignment.Center,
            ) {
                ZarIcon(R.drawable.ic_ph_x, size = 20.dp)
            }
        }
        Segmented(
            labels = listOf(
                stringResource(R.string.manual_kind_expense),
                stringResource(R.string.manual_kind_income),
                stringResource(R.string.manual_kind_transfer),
            ),
            selectedIndex = if (uiState.kind == ManualEntryKind.EXPENSE) 0 else 2,
            onSelect = { index ->
                when (index) {
                    0 -> actions.onKind(ManualEntryKind.EXPENSE)
                    1 -> actions.onOpenIncome()
                    else -> actions.onKind(ManualEntryKind.TRANSFER)
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp),
            segmentHeight = 38.dp,
            tagPrefix = "manual-kind",
            tags = listOf("expense", "income", "transfer"),
        )

        if (uiState.isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (!uiState.isOnline) OfflineBanner()
                when (uiState.kind) {
                    ManualEntryKind.EXPENSE -> ExpenseFields(uiState, actions, noteFocus)
                    ManualEntryKind.TRANSFER -> TransferFields(uiState, actions)
                }
                uiState.message?.let { FieldError(it.textRes(), Modifier.testTag("manual-message")) }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(
                text = stringResource(R.string.manual_quick_note),
                onClick = {
                    actions.onKind(ManualEntryKind.EXPENSE)
                    quickNoteTaps++
                },
                modifier = Modifier.weight(1f).testTag("manual-quick-note"),
                style = ZarButtonStyle.Soft,
            )
            ZarButton(
                text = stringResource(R.string.manual_save),
                onClick = actions.onSave,
                modifier = Modifier.weight(1f).testTag("manual-save"),
                enabled = !uiState.isSaving && !uiState.isLoading && uiState.accounts.isNotEmpty(),
            )
        }
    }
}

// --- Expense --------------------------------------------------------------------------------

@Composable
private fun ExpenseFields(state: ManualEntryUiState, actions: ManualEntryActions, noteFocus: FocusRequester) {
    val problems = state.expenseProblems
    AmountRow(state.amount, state.currency.code, actions.onAmount)
    FieldError(if (ExpenseProblem.AMOUNT_MISSING in problems || ExpenseProblem.AMOUNT_INVALID in problems) R.string.manual_error_amount else null)
    ZarDivider()

    Section(stringResource(R.string.manual_field_account)) {
        if (state.accounts.isEmpty()) {
            NoAccountsCard(actions.onAddAccount)
        } else {
            AccountChips(state.accounts, selected = state.accountId, onSelect = actions.onAccount, tagPrefix = "manual-account")
        }
        FieldError(if (ExpenseProblem.ACCOUNT_MISSING in problems) R.string.manual_error_account else null)
    }

    Section(stringResource(R.string.manual_field_category)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { category ->
                ZarChip(
                    label = stringResource(category.labelRes()),
                    selected = category == state.category,
                    onClick = { actions.onCategory(category) },
                    modifier = Modifier.testTag("manual-category-${category.id}"),
                )
            }
        }
        FieldError(if (ExpenseProblem.CATEGORY_MISSING in problems) R.string.manual_error_category else null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(stringResource(R.string.manual_field_counts_as))
            state.category?.takeIf { it.usualClass != null }?.let {
                Text(
                    stringResource(R.string.manual_counts_as_hint, stringResource(it.labelRes())),
                    style = ZarType.bodyCaption,
                    color = ZarTheme.colors.textSecondary,
                )
            }
        }
        val classes = listOf(SpendingClass.COMMITTED, SpendingClass.DISCRETIONARY, SpendingClass.UNKNOWN)
        Segmented(
            labels = classes.map { stringResource(it.labelRes()) },
            selectedIndex = classes.indexOf(state.spendingClass),
            onSelect = { actions.onSpendingClass(classes[it]) },
            segmentHeight = 32.dp,
            tagPrefix = "manual-class",
            tags = classes.map { it.name.lowercase() },
        )
    }
    ZarDivider()

    DateRow(state.date, actions.onDate)
    FieldError(if (ExpenseProblem.DATE_IN_FUTURE in problems) R.string.manual_error_date_future else null)
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_note)) {
        TextInput(
            value = state.title,
            onValueChange = actions.onTitle,
            placeholder = stringResource(R.string.manual_note_hint),
            modifier = Modifier.weight(1f).focusRequester(noteFocus).testTag("manual-note"),
        )
    }
    if (state.suggestions.isNotEmpty()) Suggestions(state, actions.onSuggestion)
    FieldError(if (ExpenseProblem.TITLE_TOO_LONG in problems) R.string.manual_error_title_too_long else null)
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_description)) {
        TextInput(
            value = state.description,
            onValueChange = actions.onDescription,
            placeholder = stringResource(R.string.manual_description_hint),
            modifier = Modifier.weight(1f).testTag("manual-description"),
        )
    }
    FieldError(if (ExpenseProblem.DESCRIPTION_TOO_LONG in problems) R.string.manual_error_description_too_long else null)
}

@Composable
private fun Suggestions(state: ManualEntryUiState, onSelect: (EntrySuggestion) -> Unit) {
    val colors = ZarTheme.colors
    // The note sits near the bottom with the keyboard open, so bring the list up where it can be seen.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(state.suggestions) { bringIntoView.bringIntoView() }
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(ZarShapes.sm)
            .background(colors.subtle)
            .padding(vertical = 4.dp)
            .testTag("manual-suggestions"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ZarMonoLabel(stringResource(R.string.manual_suggestions_header))
            ZarMonoLabel(stringResource(R.string.manual_suggestions_fills), color = colors.textPlaceholder)
        }
        state.suggestions.forEach { suggestion ->
            val account = state.accounts.firstOrNull { it.id == suggestion.accountId }
            val details = listOfNotNull(suggestion.category?.let { stringResource(it.labelRes()) }, account?.name).joinToString(" · ")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelect(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("manual-suggestion"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(suggestion.title, style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1)
                    if (details.isNotEmpty()) Text(details, style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 1)
                }
                Text(suggestion.amount.formatWithCode(), style = ZarType.bodyS, color = colors.textPrimary, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

// --- Transfer -------------------------------------------------------------------------------

@Composable
private fun TransferFields(state: ManualEntryUiState, actions: ManualEntryActions) {
    val problems = state.transferProblems
    AmountRow(state.amount, state.currency.code, actions.onAmount)
    FieldError(if (TransferProblem.AMOUNT_MISSING in problems || TransferProblem.AMOUNT_INVALID in problems) R.string.manual_error_amount else null)

    ZarFormRow(stringResource(R.string.manual_field_fees)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(state.currency.code)
            TextInput(
                value = state.fee,
                onValueChange = actions.onFee,
                placeholder = stringResource(R.string.manual_fee_hint),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).testTag("manual-fee"),
            )
        }
    }
    FieldError(if (TransferProblem.FEE_INVALID in problems) R.string.manual_error_fee else null)
    ZarDivider()

    Section(stringResource(R.string.manual_field_from)) {
        if (state.accounts.isEmpty()) {
            NoAccountsCard(actions.onAddAccount)
        } else {
            AccountChips(state.accounts, selected = state.fromId, onSelect = actions.onFrom, tagPrefix = "manual-from")
        }
        FieldError(if (TransferProblem.FROM_MISSING in problems) R.string.manual_error_from else null)
    }

    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        val swapLabel = stringResource(R.string.manual_swap_accounts)
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ZarTheme.colors.subtle)
                .clickable(enabled = state.toAccount != null, role = Role.Button, onClickLabel = swapLabel, onClick = actions.onSwap)
                .semantics { contentDescription = swapLabel }
                .testTag("manual-swap"),
            Alignment.Center,
        ) {
            ZarIcon(R.drawable.ic_ph_arrows_left_right, size = 18.dp)
        }
    }

    Section(stringResource(R.string.manual_field_to)) {
        if (state.accounts.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { account ->
                    ZarChip(
                        label = account.name,
                        selected = account.id == state.toId,
                        onClick = { actions.onTo(account.id) },
                        icon = account.type.iconRes(),
                        enabled = state.transferTargets.any { it.id == account.id },
                        modifier = Modifier.testTag("manual-to-${account.id.value}"),
                    )
                }
            }
            if (state.hidesOtherCurrencies) {
                Text(stringResource(R.string.manual_other_currencies), style = ZarType.bodyCaption, color = ZarTheme.colors.textSecondary)
            }
        }
        FieldError(
            when {
                TransferProblem.SAME_ACCOUNT in problems -> R.string.manual_error_same_account
                TransferProblem.DIFFERENT_CURRENCIES in problems -> R.string.manual_error_currencies
                TransferProblem.TO_MISSING in problems -> R.string.manual_error_to
                else -> null
            },
        )
    }

    val out = state.transferOutTotal
    val into = state.transferInTotal
    val from = state.fromAccount
    val to = state.toAccount
    if (out != null && into != null && from != null && to != null) {
        TransferPreview(from.name, "− ${out.formatForRow()}", to.name, "+ ${into.formatForRow()}")
    }
    ZarDivider()

    DateRow(state.date, actions.onDate)
    FieldError(if (TransferProblem.DATE_IN_FUTURE in problems) R.string.manual_error_date_future else null)
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_note)) {
        TextInput(
            value = state.transferNote,
            onValueChange = actions.onTransferNote,
            placeholder = stringResource(R.string.manual_note_hint),
            modifier = Modifier.weight(1f).testTag("manual-transfer-note"),
        )
    }
    FieldError(if (TransferProblem.NOTE_TOO_LONG in problems) R.string.manual_error_description_too_long else null)
}

@Composable
private fun TransferPreview(fromName: String, fromAmount: String, toName: String, toAmount: String) {
    val colors = ZarTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.sm).background(colors.subtle).padding(14.dp).testTag("manual-transfer-preview"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewLine(fromName, fromAmount)
        PreviewLine(toName, toAmount)
    }
}

@Composable
private fun PreviewLine(name: String, amount: String) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = ZarType.bodyS, color = colors.textSecondary)
        Text(amount, style = ZarType.bodyS, color = colors.textPrimary)
    }
}

// --- Shared pieces --------------------------------------------------------------------------

@Composable
private fun AmountRow(amount: String, currencyCode: String, onChange: (String) -> Unit) {
    val colors = ZarTheme.colors
    ZarFormRow(stringResource(R.string.manual_field_amount)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = amount,
                onValueChange = onChange,
                placeholder = "0",
                textStyle = ZarType.amountM,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).testTag("manual-amount"),
            )
            Box(
                Modifier.padding(start = 8.dp).clip(ZarShapes.sm).background(colors.subtle).padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(currencyCode, style = ZarType.labelChip, color = colors.textPrimary, modifier = Modifier.testTag("manual-currency"))
            }
        }
    }
}

@Composable
private fun AccountChips(accounts: List<Account>, selected: AccountId?, onSelect: (AccountId) -> Unit, tagPrefix: String) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { account ->
            ZarChip(
                label = account.name,
                selected = account.id == selected,
                onClick = { onSelect(account.id) },
                icon = account.type.iconRes(),
                modifier = Modifier.testTag("$tagPrefix-${account.id.value}"),
            )
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZarMonoLabel(label)
        content()
    }
}

/** Z32's "No accounts yet" state: recording needs an account, so point at adding one. */
@Composable
private fun NoAccountsCard(onAddAccount: () -> Unit) {
    val colors = ZarTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.subtle).padding(16.dp).testTag("manual-no-accounts"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.manual_no_accounts_title), style = ZarType.labelButton, color = colors.textPrimary)
        Text(stringResource(R.string.manual_no_accounts_body), style = ZarType.bodyCaption, color = colors.textSecondary)
        Box(
            Modifier
                .clip(ZarShapes.sm)
                .background(colors.accentPrimary)
                .clickable(role = Role.Button, onClick = onAddAccount)
                .padding(horizontal = 18.dp, vertical = 11.dp)
                .testTag("manual-add-account"),
        ) {
            Text(stringResource(R.string.manual_no_accounts_action), style = ZarType.labelChip, color = colors.textOnInverse)
        }
    }
}

/** Z34's offline banner. Saving still works; the entry is kept on the phone and synced later. */
@Composable
private fun OfflineBanner() {
    val colors = ZarTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.subtle).padding(horizontal = 16.dp, vertical = 14.dp).testTag("manual-offline"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.manual_offline_title), style = ZarType.labelChip, color = colors.textPrimary)
        Text(stringResource(R.string.manual_offline_body), style = ZarType.bodyCaption, color = colors.textSecondary)
    }
}

/** Equal-width segments on a subtle track; the selected one is a white tile. */
@Composable
private fun Segmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    segmentHeight: androidx.compose.ui.unit.Dp,
    tagPrefix: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    Row(
        modifier.fillMaxWidth().selectableGroup().clip(ZarShapes.lg).background(colors.subtle).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = segmentHeight)
                    .clip(ZarShapes.sm)
                    .background(if (selected) colors.surface else colors.subtle)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(vertical = 8.dp)
                    .testTag("$tagPrefix-${tags[index]}"),
                Alignment.Center,
            ) {
                Text(label, style = ZarType.labelChip, color = if (selected) colors.textPrimary else colors.textTertiary)
            }
        }
    }
}

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = ZarType.bodyM,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = ZarTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.textSelected),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.textPlaceholder)
                inner()
            }
        },
    )
}

@Composable
private fun FieldError(@androidx.annotation.StringRes res: Int?, modifier: Modifier = Modifier) {
    if (res != null) Text(stringResource(res), style = ZarType.bodyCaption, color = ZarTheme.colors.danger, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }.testTag("manual-date"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarFormRow(stringResource(R.string.manual_field_date), Modifier.weight(1f)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(IncomeFormat.fullDate(date), style = ZarType.bodyM, color = colors.textPrimary)
                ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
    if (!showPicker) return
    val todayMillis = ZarTime.today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = { showPicker = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showPicker = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

private fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.COFFEE_DINING -> R.string.manual_category_coffee_dining
    ExpenseCategory.GROCERIES -> R.string.manual_category_groceries
    ExpenseCategory.TRANSPORT -> R.string.manual_category_transport
    ExpenseCategory.BILLS -> R.string.manual_category_bills
    ExpenseCategory.RENT -> R.string.manual_category_rent
    ExpenseCategory.SEASON_TICKET -> R.string.manual_category_season_ticket
    ExpenseCategory.SUBSCRIPTIONS -> R.string.manual_category_subscriptions
    ExpenseCategory.OTHER -> R.string.manual_category_other
}

private fun SpendingClass.labelRes(): Int = when (this) {
    SpendingClass.COMMITTED -> R.string.manual_class_committed
    SpendingClass.DISCRETIONARY -> R.string.manual_class_discretionary
    SpendingClass.UNKNOWN -> R.string.manual_class_unknown
}

private fun ManualEntryMessage.textRes(): Int = when (this) {
    ManualEntryMessage.NOT_SIGNED_IN -> R.string.manual_message_not_signed_in
    ManualEntryMessage.ACCOUNT_ARCHIVED -> R.string.manual_message_archived
    ManualEntryMessage.FAILED -> R.string.manual_message_failed
}
