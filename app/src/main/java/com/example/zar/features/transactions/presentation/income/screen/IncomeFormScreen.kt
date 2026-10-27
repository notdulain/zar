package com.example.zar.features.transactions.presentation.income.screen

import com.example.zar.core.presentation.components.sheetBackScrim
import com.example.zar.core.presentation.components.sheetBackSlide
import com.example.zar.core.presentation.privacy.amountFieldTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.presentation.screen.iconRes
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormMessage
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormViewModel
import com.example.zar.features.transactions.presentation.manual.screen.DateTimeRow
import com.example.zar.features.transactions.presentation.manual.screen.ManualDatePickerSheet
import com.example.zar.features.transactions.presentation.manual.screen.ManualTimePickerSheet
import com.example.zar.features.transactions.presentation.manual.screen.Section
import com.example.zar.features.transactions.presentation.manual.screen.Segmented
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import java.time.LocalTime
import java.time.LocalDate

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

@Composable
internal fun IncomeFormContent(uiState: IncomeFormUiState, actions: IncomeFormActions) {
    val colors = ZarTheme.colors
    var openPicker by rememberSaveable { mutableStateOf<String?>(null) }

    // Back closes an open date or time picker first, instead of dismissing the form and losing what was typed.
    BackHandler(enabled = openPicker != null) { openPicker = null }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().sheetBackScrim().background(colors.scrim.copy(alpha = 0.64f)).clickable(onClick = actions.onBack))
        if (openPicker == "date") {
            ManualDatePickerSheet(
                date = uiState.date,
                onCancel = { openPicker = null },
                onSetDate = { actions.onDateSelect(it); openPicker = null },
                modifier = Modifier.align(Alignment.BottomCenter).sheetBackSlide(),
            )
        } else if (openPicker == "time") {
            ManualTimePickerSheet(
                time = uiState.time ?: LocalTime.now(ZarTime.ZONE).withSecond(0).withNano(0),
                onCancel = { openPicker = null },
                onSetTime = { actions.onTimeSelect(it); openPicker = null },
                modifier = Modifier.align(Alignment.BottomCenter).sheetBackSlide(),
                onClear = if (uiState.time != null) ({ actions.onTimeSelect(null); openPicker = null }) else null,
            )
        } else {
            IncomeSheet(uiState, actions, onOpenDate = { openPicker = "date" }, onOpenTime = { openPicker = "time" }, modifier = Modifier.align(Alignment.BottomCenter).sheetBackSlide(), maxSheetHeight = maxHeight - 48.dp)
        }
    }
}

@Composable
private fun IncomeSheet(
    uiState: IncomeFormUiState,
    actions: IncomeFormActions,
    onOpenDate: () -> Unit,
    onOpenTime: () -> Unit,
    maxSheetHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    Column(
        modifier.fillMaxWidth().heightIn(max = maxSheetHeight)
            .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }
            .padding(top = 10.dp),
    ) {
        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.handle))
        }
        // The close target is 48dp; the padding is trimmed by the same amount so the header keeps Figma's 60dp height
        // and the X stays where Figma draws it.
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = if (uiState.isEditing) 4.dp else 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (uiState.isEditing) R.string.income_edit else R.string.manual_title),
                style = ZarType.headingSheet,
                color = colors.textPrimary,
            )
            Box(
                Modifier.minimumInteractiveComponentSize().size(48.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.manual_close), onClick = actions.onBack)
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
            // Figma Z162 rhythm: 48dp rows pitched by hairlines, 17dp above a block, 16dp between its label and chips.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = if (uiState.isEditing) 0.dp else 18.dp, bottom = 10.dp),
            ) {
                AmountRow(uiState, actions.onAmountChange, actions.onAccountSelect)
                FieldError(amountError(uiState.problems), "income-amount-error")
                if (uiState.needsRate) {
                    Spacer(Modifier.height(10.dp))
                    RateField(uiState, actions.onRateChange)
                }
                Spacer(Modifier.height(10.dp))
                ZarDivider()
                Spacer(Modifier.height(17.dp))

                AccountSection(uiState, actions.onAccountSelect)
                Spacer(Modifier.height(16.dp))
                CategorySection(uiState.category, actions.onCategorySelect)
                Spacer(Modifier.height(18.dp))
                ZarDivider()

                DateTimeRow(
                    date = uiState.date,
                    time = uiState.time,
                    onOpenDate = onOpenDate,
                    onOpenTime = onOpenTime,
                    dateText = IncomeFormat.dateLabel(uiState.date),
                    dateTag = "income-date",
                    timeTag = "income-time",
                    stackAtLargeText = true,
                    minTouchHeight = 48.dp,
                )
                FieldError(if (IncomeProblem.DATE_IN_FUTURE in uiState.problems) R.string.income_error_date_future else null, "income-date-error")
                ZarDivider()

                InlineField(
                    label = stringResource(R.string.manual_field_from),
                    value = uiState.receivedFrom,
                    onValueChange = actions.onReceivedFromChange,
                    error = IncomeProblem.RECEIVED_FROM_TOO_LONG in uiState.problems,
                    tag = "income-received-from",
                )
                ZarDivider()

                InlineField(
                    label = stringResource(R.string.manual_field_note),
                    value = uiState.note,
                    onValueChange = actions.onNoteChange,
                    error = IncomeProblem.NOTE_TOO_LONG in uiState.problems,
                    tag = "income-note",
                )

                uiState.message?.let { message ->
                    Text(stringResource(message.textRes()), style = ZarType.bodyCaption, color = colors.danger, modifier = Modifier.padding(top = 8.dp).testTag("income-message"))
                }
            }

            val save: @Composable (Modifier) -> Unit = { modifier ->
                ZarButton(
                    text = stringResource(R.string.income_save),
                    onClick = actions.onSave,
                    modifier = modifier.testTag("income-save"),
                    enabled = !uiState.isSaving,
                )
            }
            val undo: @Composable (Modifier) -> Unit = { modifier ->
                ZarButton(
                    text = stringResource(R.string.income_undo),
                    onClick = actions.onUndo,
                    modifier = modifier.testTag("income-undo"),
                    style = ZarButtonStyle.Soft,
                    enabled = !uiState.isSaving,
                )
            }
            val actionsModifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp)
            if (uiState.isEditing && LocalDensity.current.fontScale >= STACK_ACTIONS) {
                // Figma size rules: from 1.5 the two actions stack with Save first (Figma stacks at 2.0; "Undo this income" already clips at 1.5).
                Column(actionsModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    save(Modifier.fillMaxWidth())
                    undo(Modifier.fillMaxWidth())
                }
            } else {
                Row(actionsModifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.isEditing) undo(Modifier.weight(1f))
                    save(Modifier.weight(1f))
                }
            }
        }
    }
}

/** From the second step of 1.3x text the label moves above its value, as Figma's size rules say. */
@Composable
private fun FormRow(label: String, content: @Composable RowScope.() -> Unit) {
    if (LocalDensity.current.fontScale >= LARGE_TEXT) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZarMonoLabel(label)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    } else {
        ZarFormRow(label, Modifier.heightIn(min = 48.dp), content = content)
    }
}

private const val LARGE_TEXT = 1.3f
private const val STACK_ACTIONS = 1.5f

/** Figma AMOUNT row: the amount, then a currency dropdown that chooses between the currencies of the usable accounts. */
@Composable
private fun AmountRow(uiState: IncomeFormUiState, onAmountChange: (String) -> Unit, onAccountSelect: (AccountId) -> Unit) {
    FormRow(stringResource(R.string.income_field_amount)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = uiState.amount,
                onValueChange = onAmountChange,
                placeholder = "0",
                textStyle = ZarType.amountM,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).testTag("income-amount"),
            )
            uiState.currency?.let { CurrencySelect(uiState, it, onAccountSelect) }
        }
    }
}

/**
 * Currency comes from the account in the domain, so the dropdown never holds its own value: choosing a currency selects
 * the first usable account in it (keeping the current account when it already matches). An edit is locked to its
 * record's currency and shows the value without a menu.
 */
@Composable
private fun CurrencySelect(uiState: IncomeFormUiState, current: Currency, onAccountSelect: (AccountId) -> Unit) {
    val colors = ZarTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val options = if (uiState.isEditing) listOf(current) else uiState.accounts.filter { !it.isArchived }.map { it.currency }.distinct()
    val interactive = options.size > 1
    Box(Modifier.padding(start = 8.dp)) {
        Row(
            Modifier.minimumInteractiveComponentSize()
                .then(if (interactive) Modifier.clickable(role = Role.DropdownList, onClick = { open = true }) else Modifier)
                .clip(ZarShapes.sm).background(colors.subtle)
                .padding(start = 14.dp, end = if (interactive) 12.dp else 14.dp, top = 11.dp, bottom = 11.dp)
                .testTag("income-currency"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.code, style = ZarType.monoLabel, color = colors.textPrimary)
            if (interactive) ZarIcon(R.drawable.ic_ph_caret_down, size = 12.dp, tint = colors.iconMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            options.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency.code, style = ZarType.bodyM, color = colors.textPrimary) },
                    onClick = {
                        open = false
                        if (currency != current) uiState.accounts.firstOrNull { it.currency == currency && !it.isArchived }?.let { onAccountSelect(it.id) }
                    },
                    modifier = Modifier.testTag("income-currency-${currency.code}"),
                )
            }
        }
    }
}

/** A horizontal chip strip with Figma's 40dp fade at the edge that still has more to scroll to. */
@Composable
private fun ChipStrip(content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    val surface = ZarTheme.colors.surface
    Box {
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (scroll.canScrollForward) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    val width = 40.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(surface.copy(alpha = 0f), surface), startX = size.width - width, endX = size.width),
                        topLeft = Offset(size.width - width, 0f),
                        size = Size(width, size.height),
                    )
                },
            )
        }
    }
}

@Composable
private fun AccountSection(uiState: IncomeFormUiState, onSelect: (AccountId) -> Unit) {
    val colors = ZarTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }
    val accounts = uiState.accounts.filter { !uiState.isEditing || it.currency == uiState.lockedCurrency }
    Section(
        label = stringResource(R.string.income_field_account),
        action = stringResource(R.string.manual_all_accounts).takeIf { accounts.isNotEmpty() },
        onAction = { all = !all },
    ) {
        if (uiState.accounts.isEmpty()) {
            Text(stringResource(R.string.income_no_accounts), style = ZarType.bodyCaption, color = colors.textSecondary, modifier = Modifier.testTag("income-no-accounts"))
            return@Section
        }
        val chip: @Composable (Account) -> Unit = { account ->
            ZarChip(
                label = account.chipLabel(),
                selected = account.id == uiState.accountId,
                onClick = { if (!account.isArchived) { onSelect(account.id); all = false } },
                enabled = !account.isArchived,
                icon = account.type.iconRes(),
                modifier = Modifier.testTag("income-account-${account.id.value}"),
            )
        }
        if (all) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { chip(it) } }
        } else {
            ChipStrip { accounts.forEach { chip(it) } }
        }
        if (uiState.isEditing) {
            Text(stringResource(R.string.entry_account_same_currency), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
}

/** Accounts are named plainly; only a foreign currency is spelled out, since the dropdown already shows the rest. */
private fun Account.chipLabel(): String = if (currency == Currencies.REPORTING) name else "$name · ${currency.code}"

/** Figma lists Freelance first; only the form's order changes, not the domain enum. */
private val FORM_CATEGORY_ORDER = listOf(
    IncomeCategory.FREELANCE, IncomeCategory.SALARY, IncomeCategory.ALLOWANCE, IncomeCategory.GIFT, IncomeCategory.OTHER,
)

@Composable
private fun CategorySection(selected: IncomeCategory?, onSelect: (IncomeCategory) -> Unit) {
    Section(stringResource(R.string.manual_field_category)) {
        ChipStrip {
            FORM_CATEGORY_ORDER.forEach { category ->
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
private fun InlineField(label: String, value: String, onValueChange: (String) -> Unit, error: Boolean, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FormRow(label) {
            TextInput(
                value = value,
                onValueChange = onValueChange,
                placeholder = stringResource(R.string.income_field_optional),
                modifier = Modifier.weight(1f).testTag(tag),
            )
        }
        if (error) Text(stringResource(R.string.income_error_too_long), style = ZarType.bodyCaption, color = ZarTheme.colors.danger)
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
