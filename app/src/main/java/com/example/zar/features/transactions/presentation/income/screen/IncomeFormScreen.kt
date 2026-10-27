package com.example.zar.features.transactions.presentation.income.screen

import com.example.zar.core.presentation.privacy.amountFieldTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.components.TransactionTimeField
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormMessage
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormViewModel
import com.example.zar.features.transactions.presentation.manual.screen.Segmented
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add or edit received income in the same bottom-sheet shell as the Add transaction sheet (Figma Z11): grabber,
 * "Add transaction" header and close, Expense/Income/Transfer switch with Income selected, amount and currency, the rate
 * when a conversion is needed, account and category chips, date and time, received-from and note, and the pinned actions.
 * The standalone Material form is replaced by Zar components; the domain behaviour (validation, conversion, edit/undo,
 * offline save) is unchanged.
 */
@Composable
fun IncomeFormScreen(
    onDone: () -> Unit,
    viewModel: IncomeFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
    IncomeFormContent(
        uiState = uiState,
        actions = IncomeFormActions(
            onBack = onDone,
            onAmountChange = viewModel::onAmountChange,
            onAccountSelect = viewModel::onAccountSelect,
            onDateSelect = viewModel::onDateSelect,
            onTimeSelect = viewModel::onTimeSelect,
            onRateChange = viewModel::onRateChange,
            onReceivedFromChange = viewModel::onReceivedFromChange,
            onCategorySelect = viewModel::onCategorySelect,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::save,
            onUndo = viewModel::undo,
        ),
    )
}

internal class IncomeFormActions(
    val onBack: () -> Unit,
    val onAmountChange: (String) -> Unit,
    val onAccountSelect: (AccountId) -> Unit,
    val onDateSelect: (LocalDate) -> Unit,
    val onRateChange: (String) -> Unit,
    val onReceivedFromChange: (String) -> Unit,
    val onCategorySelect: (IncomeCategory) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onUndo: () -> Unit,
    val onTimeSelect: (LocalTime?) -> Unit = {},
    /**
     * Switch to another tab of the one Add-transaction session (0 Expense, 1 Income, 2 Transfer). Null in edit mode,
     * where the type switch is not shown at all: an edit is not a creation and must not offer the other types.
     */
    val onSelectKind: ((Int) -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IncomeFormContent(uiState: IncomeFormUiState, actions: IncomeFormActions) {
    val colors = ZarTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.64f)).clickable(onClick = actions.onBack))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight - 48.dp)
                .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }
                .padding(top = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.handle))
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (uiState.isEditing) R.string.income_edit else R.string.manual_title),
                    style = ZarType.headingSheet,
                    color = colors.textPrimary,
                )
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = actions.onBack)
                        .testTag("income-close"),
                    Alignment.Center,
                ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
            }
            // The type switch belongs to the one Add-transaction session. Editing an existing income hides it: an
            // edit keeps its own type and must not silently become a new creation in another tab.
            if (!uiState.isEditing) {
                Segmented(
                    labels = listOf(
                        stringResource(R.string.manual_kind_expense),
                        stringResource(R.string.manual_kind_income),
                        stringResource(R.string.manual_kind_transfer),
                    ),
                    selectedIndex = 1,
                    // Income is the current tab; Expense and Transfer switch the shared session instead of closing it.
                    onSelect = { actions.onSelectKind?.invoke(it) },
                    modifier = Modifier.padding(horizontal = 24.dp),
                    segmentHeight = 38.dp,
                    tagPrefix = "income-kind",
                    tags = listOf("expense", "income", "transfer"),
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
            } else if (uiState.message == IncomeFormMessage.NOT_FOUND) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.income_message_not_found), style = ZarType.bodyM, color = colors.textSecondary)
                }
            } else {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Figma Z162 order: amount and currency, the LKR rate when a conversion is needed, account,
                    // category, date and time, then the optional received-from and note.
                    AmountField(uiState, actions.onAmountChange)

                    if (uiState.needsRate) RateField(uiState, actions.onRateChange)

                    AccountChips(uiState, actions.onAccountSelect)

                    CategoryChips(uiState.category, actions.onCategorySelect)

                    DateAndTime(uiState, actions.onDateSelect, actions.onTimeSelect)

                    ZarDivider()
                    LabeledField(
                        label = stringResource(R.string.income_field_received_from),
                        value = uiState.receivedFrom,
                        onValueChange = actions.onReceivedFromChange,
                        error = IncomeProblem.RECEIVED_FROM_TOO_LONG in uiState.problems,
                        tag = "income-received-from",
                    )

                    LabeledField(
                        label = stringResource(R.string.income_field_note),
                        value = uiState.note,
                        onValueChange = actions.onNoteChange,
                        error = IncomeProblem.NOTE_TOO_LONG in uiState.problems,
                        tag = "income-note",
                    )

                    uiState.message?.let { message ->
                        Text(stringResource(message.textRes()), style = ZarType.bodyCaption, color = colors.danger, modifier = Modifier.testTag("income-message"))
                    }
                }

                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (uiState.isEditing) {
                        ZarButton(
                            text = stringResource(R.string.income_undo),
                            onClick = actions.onUndo,
                            modifier = Modifier.weight(1f).testTag("income-undo"),
                            style = ZarButtonStyle.Soft,
                            enabled = !uiState.isSaving,
                        )
                    }
                    ZarButton(
                        text = stringResource(R.string.income_save),
                        onClick = actions.onSave,
                        modifier = Modifier.weight(1f).testTag("income-save"),
                        enabled = !uiState.isSaving,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountField(uiState: IncomeFormUiState, onAmountChange: (String) -> Unit) {
    val colors = ZarTheme.colors
    val amountProblems = uiState.problems.any { it == IncomeProblem.AMOUNT_MISSING || it == IncomeProblem.AMOUNT_INVALID }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(stringResource(R.string.income_field_amount))
            Spacer(Modifier.weight(1f))
            uiState.currency?.let {
                Text(it.code, style = ZarType.monoLabel, color = colors.textTertiary, modifier = Modifier.testTag("income-currency"))
            }
        }
        TextInput(
            value = uiState.amount,
            onValueChange = onAmountChange,
            placeholder = "0",
            textStyle = ZarType.amountL,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth().testTag("income-amount"),
        )
        FieldError(amountError(uiState.problems), "income-amount-error")
        // Keep the top border of the sheet's amount block aligned with Figma's divider.
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderHairline))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(uiState: IncomeFormUiState, onSelect: (AccountId) -> Unit) {
    val colors = ZarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ZarMonoLabel(stringResource(R.string.income_field_account))
        if (uiState.accounts.isEmpty()) {
            Text(stringResource(R.string.income_no_accounts), style = ZarType.bodyCaption, color = colors.textSecondary, modifier = Modifier.testTag("income-no-accounts"))
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.accounts.filter { !uiState.isEditing || it.currency == uiState.lockedCurrency }.forEach { account ->
                ZarChip(
                    label = "${account.name} · ${account.currency.code}",
                    selected = account.id == uiState.accountId,
                    onClick = { if (!account.isArchived) onSelect(account.id) },
                    enabled = !account.isArchived,
                    modifier = Modifier.testTag("income-account-${account.id.value}"),
                )
            }
        }
        if (uiState.isEditing) {
            Text(stringResource(R.string.entry_account_same_currency), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: IncomeCategory?, onSelect: (IncomeCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ZarMonoLabel(stringResource(R.string.income_field_category))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IncomeCategory.entries.forEach { category ->
                ZarChip(
                    label = stringResource(category.labelRes()),
                    selected = category == selected,
                    onClick = { onSelect(category) },
                    modifier = Modifier.testTag("income-category-${category.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun DateAndTime(uiState: IncomeFormUiState, onDateSelect: (LocalDate) -> Unit, onTimeSelect: (LocalTime?) -> Unit) {
    val colors = ZarTheme.colors
    var showDate by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ZarMonoLabel(stringResource(R.string.income_field_date))
        Row(
            Modifier.fillMaxWidth().clip(ZarShapes.sm).background(colors.subtle)
                .clickable(role = Role.Button) { showDate = true }.padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("income-date"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(IncomeFormat.fullDate(uiState.date), style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.weight(1f))
            ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
        }
        FieldError(if (IncomeProblem.DATE_IN_FUTURE in uiState.problems) R.string.income_error_date_future else null, "income-date-error")
    }
    TransactionTimeField(uiState.time, tag = "income-time", onSelect = onTimeSelect)

    if (showDate) {
        val todayMillis = remember { ZarTime.today().toUtcMillis() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun RateField(uiState: IncomeFormUiState, onRateChange: (String) -> Unit) {
    val colors = ZarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ZarMonoLabel(stringResource(R.string.income_field_rate, uiState.currency?.code.orEmpty()))
        TextInput(
            value = uiState.rate,
            onValueChange = onRateChange,
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth().testTag("income-rate"),
        )
        if (IncomeProblem.RATE_INVALID in uiState.problems) {
            Text(stringResource(R.string.income_error_rate_invalid), style = ZarType.bodyCaption, color = colors.danger)
        } else {
            Text(stringResource(R.string.income_field_rate_hint), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit, error: Boolean, tag: String) {
    val colors = ZarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ZarMonoLabel(label)
        TextInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        if (error) Text(stringResource(R.string.income_error_too_long), style = ZarType.bodyCaption, color = colors.danger)
    }
}

@Composable
private fun FieldError(res: Int?, tag: String) {
    if (res != null) Text(stringResource(res), style = ZarType.bodyCaption, color = ZarTheme.colors.danger, modifier = Modifier.testTag(tag))
}

private fun amountError(problems: Set<IncomeProblem>): Int? = when {
    IncomeProblem.AMOUNT_MISSING in problems -> R.string.income_error_amount_missing
    IncomeProblem.AMOUNT_INVALID in problems -> R.string.income_error_amount_invalid
    else -> null
}

private fun IncomeFormMessage.textRes(): Int = when (this) {
    IncomeFormMessage.NOT_SIGNED_IN -> R.string.income_message_not_signed_in
    IncomeFormMessage.NOTHING_CHANGED -> R.string.income_message_nothing_changed
    IncomeFormMessage.NOT_FOUND -> R.string.income_message_not_found
    IncomeFormMessage.NO_ACCOUNTS -> R.string.income_no_accounts
    IncomeFormMessage.FAILED -> R.string.income_message_failed
}

/** Material's date picker works in UTC midnight milliseconds. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Preview(showBackground = true)
@Composable
private fun IncomeFormPreview() {
    val payoneer = Account(AccountId("p"), "Payoneer", AccountType.FREELANCE, Currencies.USD, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))
    val bank = Account(AccountId("b"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))
    ZarTheme {
        IncomeFormContent(
            uiState = IncomeFormUiState(
                isLoading = false,
                accounts = listOf(bank, payoneer),
                accountId = payoneer.id,
                amount = "400",
                date = LocalDate.of(2026, 10, 14),
                receivedFrom = "Upwork client",
                category = IncomeCategory.FREELANCE,
                problems = setOf(IncomeProblem.RATE_INVALID),
            ),
            actions = IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
