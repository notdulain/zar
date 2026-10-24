package com.example.zar.features.transactions.presentation.income.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormMessage
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormViewModel
import java.time.Instant
import com.example.zar.core.presentation.components.TransactionTimeField
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun IncomeFormScreen(
    onDone: () -> Unit,
    viewModel: IncomeFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IncomeFormContent(uiState: IncomeFormUiState, actions: IncomeFormActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.isEditing) R.string.income_edit else R.string.income_add)) },
                navigationIcon = { TextButton(onClick = actions.onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (uiState.message == IncomeFormMessage.NOT_FOUND) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.income_message_not_found), fontFamily = ZarMono)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = actions.onAmountChange,
                label = { Text(stringResource(R.string.income_field_amount)) },
                prefix = uiState.currency?.let { { Text("${it.code} ", fontFamily = ZarMono) } },
                isError = IncomeProblem.AMOUNT_MISSING in uiState.problems || IncomeProblem.AMOUNT_INVALID in uiState.problems,
                supportingText = amountError(uiState.problems)?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AccountPicker(uiState, actions.onAccountSelect)

            DateField(uiState.date, IncomeProblem.DATE_IN_FUTURE in uiState.problems, actions.onDateSelect)
            TransactionTimeField(uiState.time, tag = "income-time", onSelect = actions.onTimeSelect)

            if (uiState.needsRate) {
                OutlinedTextField(
                    value = uiState.rate,
                    onValueChange = actions.onRateChange,
                    label = { Text(stringResource(R.string.income_field_rate, uiState.currency?.code.orEmpty())) },
                    isError = IncomeProblem.RATE_INVALID in uiState.problems,
                    supportingText = {
                        Text(
                            stringResource(
                                if (IncomeProblem.RATE_INVALID in uiState.problems) R.string.income_error_rate_invalid
                                else R.string.income_field_rate_hint,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = uiState.receivedFrom,
                onValueChange = actions.onReceivedFromChange,
                label = { Text(stringResource(R.string.income_field_received_from)) },
                isError = IncomeProblem.RECEIVED_FROM_TOO_LONG in uiState.problems,
                supportingText = if (IncomeProblem.RECEIVED_FROM_TOO_LONG in uiState.problems) {
                    { Text(stringResource(R.string.income_error_too_long)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryPicker(uiState.category, actions.onCategorySelect)

            OutlinedTextField(
                value = uiState.note,
                onValueChange = actions.onNoteChange,
                label = { Text(stringResource(R.string.income_field_note)) },
                isError = IncomeProblem.NOTE_TOO_LONG in uiState.problems,
                supportingText = if (IncomeProblem.NOTE_TOO_LONG in uiState.problems) {
                    { Text(stringResource(R.string.income_error_too_long)) }
                } else null,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.message?.let { message ->
                Text(stringResource(message.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono)
            }

            Button(
                onClick = actions.onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.income_save))
            }

            if (uiState.isEditing) UndoButton(enabled = !uiState.isSaving, onUndo = actions.onUndo)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountPicker(uiState: IncomeFormUiState, onSelect: (AccountId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(stringResource(R.string.income_field_account))
        if (uiState.accounts.isEmpty() && !uiState.isEditing) {
            Text(stringResource(R.string.income_no_accounts), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono)
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.accounts.filter { !uiState.isEditing || it.currency == uiState.lockedCurrency }.forEach { account ->
                FilterChip(
                    selected = account.id == uiState.accountId,
                    onClick = { onSelect(account.id) },
                    enabled = !account.isArchived,
                    label = { Text("${account.name} · ${account.currency.code}", fontFamily = ZarMono) },
                )
            }
        }
        if (uiState.isEditing) {
            Text(
                stringResource(R.string.entry_account_same_currency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(selected: IncomeCategory?, onSelect: (IncomeCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(stringResource(R.string.income_field_category))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IncomeCategory.entries.forEach { category ->
                FilterChip(
                    selected = category == selected,
                    onClick = { onSelect(category) },
                    label = { Text(stringResource(category.labelRes()), fontFamily = ZarMono) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, isError: Boolean, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(stringResource(R.string.income_field_date))
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(IncomeFormat.fullDate(date), fontFamily = ZarMono)
        }
        if (isError) {
            Text(stringResource(R.string.income_error_date_future), color = MaterialTheme.colorScheme.error)
        }
    }
    if (!showPicker) return

    val todayMillis = remember { ZarTime.today().toUtcMillis() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
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
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun UndoButton(enabled: Boolean, onUndo: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { confirming = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.income_undo), color = MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.income_undo_confirm_title)) },
            text = { Text(stringResource(R.string.income_undo_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onUndo()
                }) { Text(stringResource(R.string.income_undo_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontFamily = ZarMono)
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
