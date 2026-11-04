package com.example.zar.features.accounts.presentation.screen

import androidx.compose.ui.text.input.VisualTransformation
import com.example.zar.core.presentation.privacy.amountFieldTransformation
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import com.example.zar.core.presentation.components.ZarDatePickerDialog
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.domain.model.OpeningKind
import com.example.zar.features.accounts.presentation.viewmodel.AccountFormMessage
import com.example.zar.features.accounts.presentation.viewmodel.AccountFormUiState
import com.example.zar.features.accounts.presentation.viewmodel.AccountFormViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Add or edit an account. Laid out like the Figma sheets (Z21, Z29): labels, chips, 58dp actions. */
@Composable
fun AccountFormScreen(
    onDone: () -> Unit,
    viewModel: AccountFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
    val colors = ZarTheme.colors
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (uiState.isEditing) R.string.account_form_edit else R.string.account_form_add),
                style = ZarType.headingSheet,
                color = colors.textPrimary,
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.account_form_close), onClick = onDone),
                Alignment.Center,
            ) {
                ZarIcon(R.drawable.ic_ph_x, size = 20.dp)
            }
        }
        when {
            uiState.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
            uiState.message == AccountFormMessage.NOT_FOUND -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.account_form_not_found), style = ZarType.bodyS, color = colors.textSecondary)
            }
            else -> FormBody(uiState, viewModel, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(stringResource(R.string.account_form_cancel), onDone, Modifier.weight(1f), ZarButtonStyle.Soft)
            ZarButton(
                text = stringResource(R.string.account_form_save),
                onClick = viewModel::save,
                modifier = Modifier.weight(1f),
                enabled = !uiState.isSaving && !uiState.isLoading && uiState.message != AccountFormMessage.NOT_FOUND,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormBody(state: AccountFormUiState, vm: AccountFormViewModel, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(stringResource(R.string.account_form_type), error = state.problems.typeErrorRes()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.typeChoices.forEach { type ->
                    ZarChip(stringResource(type.labelRes()), selected = type == state.type, onClick = { vm.onTypeSelect(type) }, icon = type.iconRes())
                }
            }
        }
        ZarDivider()
        ZarFormRow(stringResource(R.string.account_form_name)) {
            TextInput(
                value = state.name,
                onValueChange = vm::onNameChange,
                placeholder = stringResource(R.string.account_form_name_hint),
                modifier = Modifier.weight(1f),
            )
        }
        FieldError(state.problems.nameErrorRes())
        ZarDivider()
        Section(stringResource(R.string.account_form_currency)) {
            CurrencyChips(state.currencyChoices, state.currency, enabled = !state.currencyLocked, onSelect = vm::onCurrencySelect)
            if (state.currencyLocked) Note(stringResource(R.string.account_form_currency_locked))
        }
        ZarDivider()
        if (state.openingKindChoices.size > 1) {
            Section(stringResource(R.string.account_form_opening_kind)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.openingKindChoices.forEach { kind ->
                        ZarChip(stringResource(kind.labelRes()), selected = kind == state.openingKind, onClick = { vm.onOpeningKindSelect(kind) })
                    }
                }
            }
        }
        if (state.showsAmount) {
            ZarFormRow(stringResource(R.string.account_form_opening)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextInput(
                        value = state.openingAmount,
                        onValueChange = vm::onOpeningAmountChange,
                        placeholder = "0",
                        textStyle = ZarType.amountM,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    ZarEyebrow(state.currency.code, Modifier.padding(start = 8.dp))
                }
            }
            FieldError(state.problems.openingErrorRes())
        } else {
            FieldError(state.problems.openingErrorRes())
        }
        Note(
            stringResource(
                if (state.type == AccountType.CRYPTO) R.string.account_form_opening_note_crypto else R.string.account_form_opening_note,
            ),
        )
        ZarDivider()
        DateRow(state.openingDate, onSelect = vm::onOpeningDateSelect)
        FieldError(if (com.example.zar.features.accounts.domain.model.AccountProblem.OPENING_DATE_IN_FUTURE in state.problems) R.string.account_error_date_future else null)
        state.message?.let { message ->
            FieldError(
                when (message) {
                    AccountFormMessage.NOT_SIGNED_IN -> R.string.account_form_not_signed_in
                    else -> R.string.account_form_failed
                },
            )
        }
    }
}

@Composable
private fun Section(label: String, error: Int? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZarEyebrow(label)
        content()
        FieldError(error)
    }
}

@Composable
private fun CurrencyChips(choices: List<Currency>, selected: Currency, enabled: Boolean, onSelect: (Currency) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { currency ->
            ZarChip(currency.code, selected = currency == selected, onClick = { onSelect(currency) }, enabled = enabled || currency == selected)
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
        visualTransformation = if (keyboardType == KeyboardType.Decimal) amountFieldTransformation() else VisualTransformation.None,
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
private fun Note(text: String) {
    Text(text, style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
}

@Composable
private fun FieldError(@androidx.annotation.StringRes res: Int?) {
    if (res != null) Text(stringResource(res), style = ZarType.bodyCaption, color = ZarTheme.colors.danger)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }, verticalAlignment = Alignment.CenterVertically) {
        ZarFormRow(stringResource(R.string.account_form_as_of), Modifier.weight(1f)) {
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

private fun OpeningKind.labelRes(): Int = when (this) {
    OpeningKind.KNOWN -> R.string.account_form_kind_known
    OpeningKind.APPROXIMATE -> R.string.account_form_kind_approximate
    OpeningKind.UNKNOWN -> R.string.account_form_kind_unknown
}
