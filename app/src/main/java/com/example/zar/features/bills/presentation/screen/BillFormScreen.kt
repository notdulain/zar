package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import com.example.zar.core.presentation.components.ZarDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.presentation.screen.iconRes
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillReminder
import com.example.zar.features.bills.presentation.viewmodel.BillFormUiState
import com.example.zar.features.bills.presentation.viewmodel.BillFormViewModel
import com.example.zar.features.reminders.presentation.rememberNotificationAccess
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.FieldError
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Add or edit a bill, laid out like the Figma sheets (Z21): labels, chips and 58dp actions. */
@Composable
fun BillFormScreen(
    onBack: () -> Unit,
    onSaved: (billId: String, isNew: Boolean) -> Unit,
    viewModel: BillFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BillFormContent(uiState, onBack, onSaved, viewModel::save, viewModel::onNameChange,
        viewModel::onAccountSelect, viewModel::onAmountChange, viewModel::onFrequencySelect,
        viewModel::onFirstDueSelect, viewModel::onReminderSelect)
}

@Composable
internal fun BillFormContent(
    uiState: BillFormUiState,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onAccountSelect: (com.example.zar.core.domain.model.AccountId) -> Unit,
    onAmountChange: (String) -> Unit,
    onFrequencySelect: (BillFrequency) -> Unit,
    onFirstDueSelect: (LocalDate) -> Unit,
    onReminderSelect: (BillReminder?) -> Unit,
) {
    LaunchedEffect(uiState.savedBillId) { uiState.savedBillId?.let { onSaved(it, !uiState.isEditing) } }
    val colors = ZarTheme.colors
    StatusBarGlyphs(onDarkHero = false)
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (uiState.isEditing) R.string.bill_form_edit else R.string.bill_form_add),
                style = ZarType.headingSheet,
                color = colors.textPrimary,
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.bill_close), onClick = onBack),
                Alignment.Center,
            ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
        }
        when {
            uiState.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
            uiState.notFound -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.bill_not_found), style = ZarType.bodyS, color = colors.textSecondary)
            }
            else -> FormBody(uiState, onNameChange, onAccountSelect, onAmountChange, onFrequencySelect, onFirstDueSelect, onReminderSelect, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(stringResource(R.string.bill_cancel), onBack, Modifier.weight(1f), ZarButtonStyle.Soft)
            ZarButton(
                text = stringResource(R.string.bill_save),
                onClick = onSave,
                modifier = Modifier.weight(1f).testTag("bill-save"),
                enabled = !uiState.isSaving && !uiState.isLoading && !uiState.notFound,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormBody(
    state: BillFormUiState,
    onNameChange: (String) -> Unit,
    onAccountSelect: (com.example.zar.core.domain.model.AccountId) -> Unit,
    onAmountChange: (String) -> Unit,
    onFrequencySelect: (BillFrequency) -> Unit,
    onFirstDueSelect: (LocalDate) -> Unit,
    onReminderSelect: (BillReminder?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val problems = state.problems
    val notifications = rememberNotificationAccess()
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ZarFormRow(stringResource(R.string.bill_form_name)) {
            TextInput(state.name, onNameChange, stringResource(R.string.bill_form_name_hint), Modifier.weight(1f).testTag("bill-name"))
        }
        FieldError(
            when {
                BillProblem.NAME_MISSING in problems -> R.string.bill_error_name_missing
                BillProblem.NAME_TOO_LONG in problems -> R.string.bill_error_name_too_long
                else -> null
            },
        )
        ZarDivider()
        Section(stringResource(R.string.bill_form_pay_from)) {
            if (state.accounts.isEmpty()) {
                Note(stringResource(R.string.bill_form_no_accounts))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.forEach { account ->
                        ZarChip(account.name, selected = account.id == state.accountId, onClick = { onAccountSelect(account.id) }, icon = account.type.iconRes())
                    }
                }
            }
            state.bill?.takeIf { state.otherCurrencyHidden }?.let { Note(stringResource(R.string.bill_form_currency_locked, it.amount.currency.code)) }
            FieldError(if (BillProblem.ACCOUNT_MISSING in problems) R.string.bill_error_account else null)
        }
        ZarDivider()
        ZarFormRow(stringResource(R.string.bill_form_amount)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextInput(
                    value = state.amount,
                    onValueChange = onAmountChange,
                    placeholder = "0",
                    textStyle = ZarType.amountM,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f).testTag("bill-amount"),
                )
                state.account?.let { ZarEyebrow(it.currency.code, Modifier.padding(start = 8.dp)) }
            }
        }
        FieldError(
            when {
                BillProblem.AMOUNT_MISSING in problems -> R.string.bill_error_amount_missing
                BillProblem.AMOUNT_INVALID in problems -> R.string.bill_error_amount_invalid
                else -> null
            },
        )
        ZarDivider()
        Section(stringResource(R.string.bill_form_repeats)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BillFrequency.entries.forEach { frequency ->
                    ZarChip(stringResource(BillFormat.frequencyRes(frequency)), frequency == state.frequency, onClick = { onFrequencySelect(frequency) })
                }
            }
        }
        ZarDivider()
        DateRow(state.firstDue, onSelect = onFirstDueSelect)
        if (state.frequency != BillFrequency.WEEKLY && state.firstDue.dayOfMonth > 28) Note(stringResource(R.string.bill_form_month_end))
        ZarDivider()
        Section(stringResource(R.string.bill_form_remind)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<BillReminder?>(null) + BillReminder.entries).forEach { choice ->
                    ZarChip(
                        stringResource(BillFormat.reminderRes(choice)),
                        selected = choice == state.reminder,
                        onClick = {
                            onReminderSelect(choice)
                            // Permission is asked only when a reminder is switched on.
                            if (choice != null) notifications.request()
                        },
                    )
                }
            }
            Note(stringResource(R.string.bill_form_remind_hint))
            if (state.reminder != null && !notifications.enabled) {
                Text(stringResource(R.string.reminders_notifications_off), style = ZarType.bodyCaption.copy(lineHeight = 16.sp), color = colors.danger)
            }
        }
        state.message?.let { FieldError(it.textRes()) }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZarEyebrow(label)
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = ZarType.bodyS.copy(lineHeight = 19.sp), color = ZarTheme.colors.textSecondary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }.testTag("bill-first-due"), verticalAlignment = Alignment.CenterVertically) {
        ZarFormRow(stringResource(R.string.bill_form_first_due), Modifier.weight(1f)) {
            Row(Modifier.weight(1f).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(IncomeFormat.fullDate(date), style = ZarType.bodyM, color = colors.textPrimary)
                ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
    if (!showPicker) return
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    ZarDatePickerDialog(
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
