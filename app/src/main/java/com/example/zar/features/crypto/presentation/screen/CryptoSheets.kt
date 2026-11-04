package com.example.zar.features.crypto.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarDatePickerDialog
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarModalSheet
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.presentation.screen.BillFormat
import com.example.zar.features.crypto.domain.CryptoProblem
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.CryptoMovementRow
import com.example.zar.features.crypto.presentation.model.CryptoOverview
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMovementUiState
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMovementViewModel
import com.example.zar.features.crypto.presentation.viewmodel.CryptoUiState
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class Side { FROM, TO }

/** Z95–Z100 — Record movement: from and to accounts, both amounts, an LKR value when it isn't implied, date and note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordMovementSheet(session: Int, onDismiss: () -> Unit) {
    val viewModel: CryptoMovementViewModel = hiltViewModel(key = "crypto-movement-$session")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var choosing by rememberSaveable(session) { mutableStateOf<Side?>(null) }
    ZarModalSheet(onDismissRequest = onDismiss, canDismiss = !uiState.isSaving) { dismiss ->
        LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) dismiss() }
        val side = choosing
        if (side != null) {
            ChooseAccount(
                side = side,
                accounts = uiState.accounts,
                selected = if (side == Side.FROM) uiState.fromId else uiState.toId,
                onSelect = { id -> if (side == Side.FROM) viewModel.onFromSelect(id) else viewModel.onToSelect(id) },
                onDone = { choosing = null },
            )
        } else {
            MovementForm(
                uiState = uiState,
                onChoose = { choosing = it },
                onSent = viewModel::onSentChange,
                onReceived = viewModel::onReceivedChange,
                onValue = viewModel::onValueChange,
                onDate = viewModel::onDateSelect,
                onNote = viewModel::onNoteChange,
                onSave = viewModel::save,
                onClose = dismiss,
            )
        }
    }
}

@Composable
private fun MovementForm(
    uiState: CryptoMovementUiState,
    onChoose: (Side) -> Unit,
    onSent: (String) -> Unit,
    onReceived: (String) -> Unit,
    onValue: (String) -> Unit,
    onDate: (LocalDate) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = ZarTheme.colors
    val problems = uiState.problems
    Column(
        Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 14.dp).testTag("crypto-record-sheet"),
    ) {
        SheetHeader(stringResource(R.string.crypto_record_movement), onClose = onClose)
        Column(Modifier.padding(top = 12.dp)) {
            AccountField(
                label = stringResource(R.string.crypto_from),
                account = uiState.from,
                error = if (CryptoProblem.FROM_MISSING in problems) stringResource(R.string.crypto_error_account) else null,
                onClick = { onChoose(Side.FROM) },
                tag = "crypto-from",
            )
            ZarDivider()
            AccountField(
                label = stringResource(R.string.crypto_to),
                account = uiState.to,
                error = when {
                    CryptoProblem.TO_MISSING in problems -> stringResource(R.string.crypto_error_account)
                    CryptoProblem.SAME_ACCOUNT in problems -> stringResource(R.string.crypto_error_same)
                    CryptoProblem.NO_CRYPTO_ACCOUNT in problems -> stringResource(R.string.crypto_error_no_crypto)
                    else -> null
                },
                onClick = { onChoose(Side.TO) },
                tag = "crypto-to",
            )
            ZarDivider()
            uiState.kind?.let { kind ->
                Text(
                    stringResource(
                        when (kind) {
                            CryptoMovementKind.BUY -> R.string.crypto_explain_buy
                            CryptoMovementKind.SELL -> R.string.crypto_explain_sell
                            CryptoMovementKind.TRADE -> R.string.crypto_explain_trade
                            CryptoMovementKind.MOVE -> R.string.crypto_explain_move
                        },
                    ),
                    style = ZarType.bodyS.copy(lineHeight = 19.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            AmountRow(
                label = stringResource(R.string.crypto_sent),
                value = uiState.sent,
                code = uiState.from?.currency?.code,
                error = amountError(problems, CryptoProblem.SENT_MISSING, CryptoProblem.SENT_INVALID),
                onChange = onSent,
                tag = "crypto-sent",
            )
            AmountRow(
                label = stringResource(R.string.crypto_received),
                value = uiState.received,
                code = uiState.to?.currency?.code,
                error = amountError(problems, CryptoProblem.RECEIVED_MISSING, CryptoProblem.RECEIVED_INVALID),
                onChange = onReceived,
                tag = "crypto-received",
            )
            if (uiState.kind == CryptoMovementKind.MOVE) Hint(stringResource(R.string.crypto_received_same))
            // Asked only once both accounts are chosen and the value is not already one of the amounts (a trade).
            if (uiState.kind != null && uiState.showValue) {
                AmountRow(
                    label = stringResource(R.string.crypto_value),
                    value = uiState.lkrValue,
                    code = "LKR",
                    error = if (CryptoProblem.VALUE_INVALID in problems) stringResource(R.string.crypto_error_value_invalid) else null,
                    onChange = onValue,
                    tag = "crypto-value",
                )
                Hint(stringResource(R.string.crypto_value_hint))
            }
            ZarDivider(Modifier.padding(top = 14.dp))
            DateRow(uiState.date, onDate)
            FieldError(if (CryptoProblem.DATE_IN_FUTURE in problems) stringResource(R.string.crypto_error_date_future) else null)
            ZarDivider()
            NoteRow(uiState.note, onNote, error = if (CryptoProblem.NOTE_TOO_LONG in problems) stringResource(R.string.crypto_error_too_long) else null)
            ZarDivider()
            uiState.message?.let { FieldError(stringResource(it.textRes()), Modifier.padding(top = 12.dp)) }
            ZarButton(
                text = stringResource(if (uiState.isSaving) R.string.crypto_saving else R.string.income_save),
                onClick = onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("crypto-save"),
            )
        }
    }
}

@Composable
private fun AccountField(label: String, account: Account?, error: String?, onClick: () -> Unit, tag: String) {
    val colors = ZarTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp).testTag(tag)) {
        ZarFormRow(label) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    account?.let { "${it.name} · ${it.currency.code}" } ?: stringResource(R.string.crypto_choose_account),
                    style = ZarType.bodyM,
                    color = if (account == null) colors.textSecondary else colors.textPrimary,
                )
                ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
            }
        }
        FieldError(error, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AmountRow(label: String, value: String, code: String?, error: String?, onChange: (String) -> Unit, tag: String) {
    Column(Modifier.padding(top = 10.dp)) {
        // Not ZarFormRow: its one-line label would cut "Amount received" and "Value in LKR, optional".
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(100.dp).padding(end = 8.dp)) { ZarEyebrow(label) }
            AmountInput(value, onChange, Modifier.weight(1f).testTag(tag), isError = error != null)
            ZarEyebrow(code ?: "—", Modifier.padding(start = 8.dp))
        }
        FieldError(error, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = ZarType.bodyCaption.copy(lineHeight = 15.sp), color = ZarTheme.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun amountError(problems: Set<CryptoProblem>, missing: CryptoProblem, invalid: CryptoProblem): String? = when {
    missing in problems -> stringResource(R.string.crypto_error_amount_missing)
    invalid in problems -> stringResource(R.string.crypto_error_amount_invalid)
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }.padding(vertical = 14.dp)) {
        ZarFormRow(stringResource(R.string.crypto_date)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(IncomeFormat.dateLabel(date), style = ZarType.bodyM, color = colors.textPrimary)
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

@Composable
private fun NoteRow(note: String, onChange: (String) -> Unit, error: String?) {
    val colors = ZarTheme.colors
    Column(Modifier.padding(vertical = 14.dp)) {
        ZarFormRow(stringResource(R.string.crypto_note_label)) {
            BasicTextField(
                value = note,
                onValueChange = onChange,
                textStyle = ZarType.bodyM.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.textSelected),
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("crypto-note"),
                decorationBox = { inner ->
                    Box {
                        if (note.isEmpty()) Text(stringResource(R.string.crypto_optional), style = ZarType.bodyM, color = colors.textPlaceholder)
                        inner()
                    }
                },
            )
        }
        FieldError(error, Modifier.padding(top = 8.dp))
    }
}

/** Z100 — Choose account: crypto wallets first, then money accounts, each with its currency. */
@Composable
private fun ChooseAccount(side: Side, accounts: List<Account>, selected: AccountId?, onSelect: (AccountId) -> Unit, onDone: () -> Unit) {
    val colors = ZarTheme.colors
    val (wallets, money) = accounts.partition { it.type == AccountType.CRYPTO }
    Column(
        Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 14.dp).testTag("crypto-choose-account"),
    ) {
        SheetHeader(stringResource(R.string.crypto_choose_account), onClose = onDone)
        ZarEyebrow(stringResource(if (side == Side.FROM) R.string.crypto_from_account else R.string.crypto_to_account), Modifier.padding(top = 18.dp))
        listOf(R.string.crypto_group_wallets to wallets, R.string.crypto_group_money to money).forEach { (group, list) ->
            if (list.isEmpty()) return@forEach
            ZarEyebrow(stringResource(group), Modifier.padding(top = 14.dp, bottom = 4.dp))
            list.forEachIndexed { index, account ->
                if (index > 0) ZarDivider()
                val isSelected = account.id == selected
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(ZarShapes.md)
                        .background(if (isSelected) colors.accentSelected else colors.surface)
                        .clickable(role = Role.Button) { onSelect(account.id) }
                        .padding(horizontal = if (isSelected) 12.dp else 0.dp, vertical = 16.dp)
                        .testTag("crypto-account-${account.id.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(74.dp)) {
                        ZarEyebrow(
                            if (isSelected) stringResource(R.string.crypto_selected) else account.currency.code,
                            color = if (isSelected) colors.textOnInverse.copy(alpha = 0.7f) else colors.textTertiary,
                        )
                    }
                    Text(
                        "${account.name} · ${account.currency.code}",
                        style = ZarType.bodyM,
                        color = if (isSelected) colors.textOnInverse else colors.textPrimary,
                    )
                }
            }
        }
        ZarButton(stringResource(R.string.crypto_done), onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
    }
}

/** Z102/Z103/Z106 — Opening cost: what the coins a wallet started with cost in LKR, and whether that is an estimate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpeningCostSheet(
    wallet: Account,
    uiState: CryptoUiState,
    onCostChange: (String) -> Unit,
    onApproximateChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ZarTheme.colors
    ZarModalSheet(onDismissRequest = onDismiss, canDismiss = !uiState.isSaving) { dismiss ->
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 14.dp)
                .testTag("crypto-cost-sheet"),
        ) {
            SheetHeader(stringResource(R.string.crypto_cost_title, wallet.name), onClose = dismiss)
            Text(stringResource(R.string.crypto_cost_body), style = ZarType.bodyS.copy(lineHeight = 19.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 14.dp))
            Text(stringResource(R.string.crypto_cost_field), style = ZarType.labelButton, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ZarEyebrow("LKR", Modifier.padding(end = 12.dp))
                AmountInput(
                    uiState.costText,
                    onCostChange,
                    Modifier.weight(1f).testTag("crypto-cost"),
                    placeholder = stringResource(R.string.crypto_cost_placeholder),
                    isError = uiState.costInvalid,
                )
            }
            if (uiState.costInvalid) {
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(1.dp).background(colors.danger))
            } else {
                ZarDivider(Modifier.padding(top = 4.dp))
            }
            FieldError(if (uiState.costInvalid) stringResource(R.string.crypto_cost_invalid) else null, Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.crypto_cost_approximate), style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.weight(1f))
                ZarSwitch(uiState.costApproximate, onApproximateChange, large = true)
            }
            Text(stringResource(R.string.crypto_cost_estimate_note), style = ZarType.bodyCaption.copy(lineHeight = 15.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 14.dp))
            uiState.message?.let { FieldError(stringResource(it.textRes()), Modifier.padding(top = 10.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(stringResource(R.string.action_cancel), onClick = dismiss, modifier = Modifier.weight(1f), style = ZarButtonStyle.Soft)
                ZarButton(
                    stringResource(R.string.income_save),
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.weight(1f).testTag("crypto-cost-save"),
                )
            }
        }
    }
}

/** Z104 — Undo this movement? Removing both sides is final. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UndoMovementSheet(row: CryptoMovementRow, isSaving: Boolean, onUndo: () -> Unit, onDismiss: () -> Unit) {
    val colors = ZarTheme.colors
    ZarModalSheet(onDismissRequest = onDismiss, canDismiss = !isSaving) { dismiss ->
        Column(Modifier.navigationBarsPadding().padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 14.dp).testTag("crypto-undo-sheet")) {
            SheetHeader(stringResource(R.string.crypto_undo_title), onClose = dismiss)
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(ZarShapes.md).background(colors.subtle).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZarIconChip(currencyIcon(row.outEntry.current.amount.currency), size = 36.dp, iconSize = 18.dp, background = colors.surface)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "${movementTitle(row)} ${IncomeFormat.amount(CryptoOverview.titleAmount(row))}",
                        style = ZarType.labelButton.copy(lineHeight = 19.sp),
                        color = colors.textPrimary,
                    )
                    Text("${row.fromName} → ${row.toName} · ${BillFormat.dayMonth(row.date)}", style = ZarType.bodyCaption, color = colors.textSecondary)
                }
            }
            Text(stringResource(R.string.crypto_undo_body), style = ZarType.bodyS.copy(lineHeight = 19.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(stringResource(R.string.action_cancel), onClick = dismiss, modifier = Modifier.weight(1f), style = ZarButtonStyle.Soft)
                Box(
                    Modifier.weight(1f).height(58.dp).clip(ZarShapes.lg).background(colors.accentDiscretionary)
                        .clickable(enabled = !isSaving, role = Role.Button, onClick = onUndo).testTag("crypto-undo-confirm"),
                    Alignment.Center,
                ) {
                    Text(stringResource(R.string.crypto_undo_movement), style = ZarType.labelButton, color = Color.White)
                }
            }
        }
    }
}
