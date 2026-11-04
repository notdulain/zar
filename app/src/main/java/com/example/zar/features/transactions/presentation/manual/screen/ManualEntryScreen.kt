package com.example.zar.features.transactions.presentation.manual.screen

import com.example.zar.core.presentation.components.sheetBackScrim
import com.example.zar.core.presentation.components.sheetBackSlide
import com.example.zar.core.presentation.components.LocalSheetBack
import androidx.compose.ui.text.input.VisualTransformation
import com.example.zar.core.presentation.privacy.amountFieldTransformation
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.example.zar.core.presentation.components.SelectorIndicatorState
import com.example.zar.core.presentation.components.rememberSelectorIndicator
import com.example.zar.core.presentation.motion.ZarMotion
import kotlin.math.roundToInt
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.money.Money
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
import com.example.zar.core.presentation.motion.settleSize
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.presentation.screen.iconRes
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategoryChoice
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import com.example.zar.features.transactions.domain.category.CategoryNameProblem
import com.example.zar.features.transactions.presentation.activity.screen.InfoBanner
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryMessage
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import com.example.zar.features.transactions.presentation.manual.viewmodel.NewCategoryState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The expense and transfer bodies of the Add-transaction sheet (Figma Z06, Z12 and the Z31–Z35 states).
 * [focusNote] opens it with the note field focused, for the Add menu's "Quick note". The surrounding sheet, the type
 * switch and the one-session draft retention live in [com.example.zar.features.transactions.presentation.manual.screen.AddTransactionScreen].
 */
internal class ManualEntryActions(
    val onClose: () -> Unit,
    val onOpenIncome: () -> Unit,
    val onAddAccount: () -> Unit,
    val onKind: (ManualEntryKind) -> Unit,
    val onAmount: (String) -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onTime: (LocalTime) -> Unit,
    val onAccount: (AccountId) -> Unit,
    val onCategory: (ExpenseCategoryChoice) -> Unit,
    val onAddCategoryStart: () -> Unit,
    val onNewCategoryName: (String) -> Unit,
    val onAddCategoryCancel: () -> Unit,
    val onAddCategorySave: () -> Unit,
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
internal fun ManualEntryContent(
    uiState: ManualEntryUiState,
    focusNote: Boolean,
    actions: ManualEntryActions,
    typeIndicator: SelectorIndicatorState? = null,
    sheetHeight: Dp? = null,
) {
    val colors = ZarTheme.colors
    val typeIndex = if (uiState.kind == ManualEntryKind.EXPENSE) 0 else 2
    // The host hoists this above the Expense / Income body switch; a caller that does not owns one for this body.
    val typeSlider = typeIndicator ?: rememberSelectorIndicator(typeIndex)
    val closeLabel = stringResource(R.string.manual_close)
    val noteFocus = remember { FocusRequester() }
    var quickNoteTaps by remember { mutableIntStateOf(0) }
    var openedWithFocus by rememberSaveable { mutableStateOf(false) }
    var openPicker by rememberSaveable { mutableStateOf<String?>(null) }

    // Back closes an open date or time picker first, instead of dismissing the whole form and losing what was typed.
    BackHandler(enabled = openPicker != null) { openPicker = null }

    // "Quick note" focuses the note once; the field may not exist yet if the form was on Transfer.
    val sheet = LocalSheetBack.current
    LaunchedEffect(uiState.isLoading, uiState.kind, quickNoteTaps) {
        val wanted = quickNoteTaps > 0 || (focusNote && !openedWithFocus)
        if (wanted && !uiState.isLoading && uiState.kind == ManualEntryKind.EXPENSE) {
            sheet?.awaitEntrance()
            runCatching { noteFocus.requestFocus() }
            openedWithFocus = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().sheetBackScrim().background(colors.scrim.copy(alpha = 0.64f)).clickable(onClick = actions.onClose))
        if (openPicker == "date") {
            ManualDatePickerSheet(
                date = uiState.date,
                onCancel = { openPicker = null },
                onSetDate = { actions.onDate(it); openPicker = null },
                modifier = Modifier.align(Alignment.BottomCenter).sheetBackSlide(swipeEnabled = false),
            )
        } else if (openPicker == "time") {
            ManualTimePickerSheet(
                time = uiState.time,
                onCancel = { openPicker = null },
                onSetTime = { actions.onTime(it); openPicker = null },
                modifier = Modifier.align(Alignment.BottomCenter).sheetBackSlide(swipeEnabled = false),
            )
        } else {
        Column(
            Modifier.align(Alignment.BottomCenter).sheetBackSlide(swipeEnabled = !uiState.isSaving).fillMaxWidth()
                .then(if (sheetHeight != null) Modifier.height(minOf(sheetHeight, maxHeight - 48.dp)) else Modifier.heightIn(max = maxHeight - 48.dp))
                .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }.padding(top = 10.dp),
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
                    stringResource(if (uiState.gapAmount != null) R.string.manual_gap_sheet_title else R.string.manual_title),
                    style = ZarType.headingSheet,
                    color = colors.textPrimary,
                )
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.manual_close), onClick = actions.onClose)
                        .semantics { contentDescription = closeLabel }.testTag("manual-close"),
                    Alignment.Center,
                ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
            }
        if (uiState.gapAmount == null) Segmented(
            labels = listOf(
                stringResource(R.string.manual_kind_expense),
                stringResource(R.string.manual_kind_income),
                stringResource(R.string.manual_kind_transfer),
            ),
            selectedIndex = typeIndex,
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
            indicator = typeSlider,
        )

        if (uiState.isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
        } else {
            Column(
                // A fixed-height sheet fills the room so Save stays pinned; the fields scroll when they are taller.
                Modifier.weight(1f, fill = sheetHeight != null).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (!uiState.isOnline) OfflineBanner()
                when (uiState.kind) {
                    ManualEntryKind.EXPENSE -> ExpenseFields(uiState, actions, noteFocus, { openPicker = "date" }, { openPicker = "time" })
                    ManualEntryKind.TRANSFER -> TransferFields(uiState, actions, { openPicker = "date" }, { openPicker = "time" })
                }
                uiState.message?.let { FieldError(it.textRes(), Modifier.testTag("manual-message")) }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp),
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
                icon = R.drawable.ic_ph_note_pencil,
            )
            ZarButton(
                text = stringResource(R.string.manual_save),
                onClick = actions.onSave,
                modifier = Modifier.weight(1f).testTag("manual-save"),
                enabled = !uiState.isSaving && !uiState.isLoading && uiState.accounts.isNotEmpty(),
            )
        }
        }
        if (uiState.newCategory != null) {
            Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.64f)).clickable(onClick = actions.onAddCategoryCancel))
            AddCategorySheet(
                state = uiState.newCategory,
                onName = actions.onNewCategoryName,
                onCancel = actions.onAddCategoryCancel,
                onSave = actions.onAddCategorySave,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
}

// --- Expense --------------------------------------------------------------------------------

@Composable
private fun ExpenseFields(state: ManualEntryUiState, actions: ManualEntryActions, noteFocus: FocusRequester, onOpenDate: () -> Unit, onOpenTime: () -> Unit) {
    val problems = state.expenseProblems
    var allAccounts by rememberSaveable { mutableStateOf(false) }
    var allCategories by rememberSaveable { mutableStateOf(false) }
    state.gapAmount?.let { gap ->
        InfoBanner(
            title = stringResource(R.string.manual_gap_title, Money.parse(gap, state.currency)?.let { IncomeFormat.withCode(it) } ?: gap),
            body = stringResource(R.string.manual_gap_body),
            icon = R.drawable.ic_ph_info,
            modifier = Modifier.testTag("manual-gap-banner"),
        )
    }
    AmountRow(state.amount, state.currency.code, actions.onAmount)
    FieldError(if (ExpenseProblem.AMOUNT_MISSING in problems || ExpenseProblem.AMOUNT_INVALID in problems) R.string.manual_error_amount else null)
    ZarDivider()

    if (state.accountLocked) LockedAccountRow(state) else Section(stringResource(R.string.manual_field_account), stringResource(R.string.manual_all_accounts), { allAccounts = !allAccounts }) {
        if (state.accounts.isEmpty()) {
            NoAccountsCard(actions.onAddAccount)
        } else if (allAccounts) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { account ->
                    ZarChip(
                        label = account.name,
                        selected = account.id == state.accountId,
                        onClick = { actions.onAccount(account.id); allAccounts = false },
                        icon = account.type.iconRes(),
                        modifier = Modifier.testTag("manual-account-${account.id.value}"),
                    )
                }
            }
        } else {
            AccountChips(state.accounts, selected = state.accountId, onSelect = actions.onAccount, tagPrefix = "manual-account")
        }
        FieldError(if (ExpenseProblem.ACCOUNT_MISSING in problems) R.string.manual_error_account else null)
    }

    Section(stringResource(R.string.manual_field_category), stringResource(R.string.manual_all_categories), { allCategories = !allCategories }) {
        if (allCategories) {
            CategoryGrid(
                choices = state.categoryCatalog.choices,
                selectedId = state.category?.id,
                onSelect = { actions.onCategory(it); allCategories = false },
                onAddCategory = actions.onAddCategoryStart,
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categoryCatalog.choices.forEach { category ->
                    ZarChip(
                        label = category.labelText(), selected = category.id == state.category?.id,
                        onClick = { actions.onCategory(category) }, icon = category.iconRes(),
                        modifier = Modifier.testTag("manual-category-${category.id}"),
                    )
                }
            }
        }
        state.appliedRule?.let { rule ->
            Text(
                stringResource(R.string.rules_filled_by_rule, rule.displayName),
                style = ZarType.bodyCaption,
                color = ZarTheme.colors.textSecondary,
                modifier = Modifier.testTag("manual-rule-hint"),
            )
        }
        FieldError(if (ExpenseProblem.CATEGORY_MISSING in problems) R.string.manual_error_category else null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(stringResource(R.string.manual_field_counts_as))
            state.category?.takeIf { it.usualClass != null }?.let {
                Text(
                    stringResource(R.string.manual_counts_as_hint, it.labelText()),
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
            showClassDots = true,
            // Created where the Expense body appears, so returning to it shows the stored class with no replay.
            indicator = rememberSelectorIndicator(classes.indexOf(state.spendingClass)),
        )
    }
    ZarDivider()

    DateTimeRow(state.date, state.time, onOpenDate, onOpenTime)
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

/** The account a balance check was for: shown, but not chosen again. */
@Composable
private fun LockedAccountRow(state: ManualEntryUiState) {
    val colors = ZarTheme.colors
    ZarFormRow(stringResource(R.string.manual_field_account)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.selectedAccount?.name.orEmpty(), style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.testTag("manual-account-locked"))
            ZarIcon(R.drawable.ic_ph_lock, size = 14.dp, tint = colors.iconMuted)
            Text(stringResource(R.string.activity_edit_locked), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
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
            val details = listOfNotNull(suggestion.category?.let { it.labelText() }, account?.name).joinToString(" · ")
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

/**
 * Figma Z12's swap row: a 36dp circle with a 16dp up/down arrow glyph, a 12dp gap and a 1dp divider on each side that
 * share the rest of the width equally. The circle sits in a 48dp touch target.
 */
@Composable
private fun SwapAccountsRow(canSwap: Boolean, onSwap: () -> Unit) {
    val colors = ZarTheme.colors
    val swapLabel = stringResource(R.string.manual_swap_accounts)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ZarDivider(Modifier.weight(1f))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = canSwap, role = Role.Button, onClickLabel = swapLabel, onClick = onSwap)
                .semantics { contentDescription = swapLabel }
                .testTag("manual-swap"),
            Alignment.Center,
        ) {
            // Unavailable reads as an outlined, muted circle rather than only a fainter arrow.
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .then(if (canSwap) Modifier.background(colors.subtle) else Modifier.border(1.dp, colors.borderDefault, CircleShape)),
                Alignment.Center,
            ) {
                ZarIcon(R.drawable.ic_ph_arrows_down_up, size = 16.dp, tint = if (canSwap) colors.iconStrong else colors.iconMuted)
            }
        }
        ZarDivider(Modifier.weight(1f))
    }
}

@Composable
private fun TransferFields(state: ManualEntryUiState, actions: ManualEntryActions, onOpenDate: () -> Unit, onOpenTime: () -> Unit) {
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

    SwapAccountsRow(canSwap = state.toAccount != null, onSwap = actions.onSwap)

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

    DateTimeRow(state.date, state.time, onOpenDate, onOpenTime)
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
internal fun Section(label: String, action: String? = null, onAction: (() -> Unit)? = null, content: @Composable () -> Unit) {
    // Expanding "All categories" / "All accounts" settles to its new height instead of jumping.
    Column(Modifier.settleSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(label)
            if (action != null && onAction != null) {
                Text(action, style = ZarType.labelAction, color = ZarTheme.colors.textSelected,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onAction))
            }
        }
        content()
    }
}

/**
 * "All categories" opens this complete picker: a visible Add category action above a three equal-width column grid,
 * built-ins first then the user's own, each tile at least 48dp tall with the label wrapping rather than shrinking.
 */
@Composable
private fun CategoryGrid(
    choices: List<ExpenseCategoryChoice>,
    selectedId: String?,
    onSelect: (ExpenseCategoryChoice) -> Unit,
    onAddCategory: () -> Unit,
) {
    val colors = ZarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .clip(ZarShapes.sm)
                .background(colors.subtle)
                .clickable(role = Role.Button, onClick = onAddCategory)
                .padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp)
                .testTag("manual-add-category"),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZarIcon(R.drawable.ic_ph_plus, size = 16.dp, tint = colors.textSelected)
            Text(stringResource(R.string.manual_add_category), style = ZarType.labelChip, color = colors.textSelected)
        }
        // Three equal columns, row-major. The final partial row keeps the same width by filling the gap with spacers.
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        CategoryTile(
                            category = category,
                            selected = category.id == selectedId,
                            onClick = { onSelect(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: ExpenseCategoryChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(ZarShapes.sm)
            .background(if (selected) colors.accentSelected else colors.subtle)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("manual-category-${category.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ZarIcon(
            category.iconRes(),
            size = 20.dp,
            tint = if (selected) colors.textOnInverse else colors.iconStrong,
        )
        Text(
            category.labelText(),
            style = ZarType.labelChip,
            color = if (selected) colors.textOnInverse else colors.textPrimary,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The Add category form. It never saves the transaction; a name problem or a failed write keeps the typed name. */
@Composable
private fun AddCategorySheet(
    state: NewCategoryState,
    onName: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
    Column(
        modifier.fillMaxWidth().heightIn(max = 420.dp)
            .shadow(14.dp, ZarShapes.sheet, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
            .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }
            .navigationBarsPadding().imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 22.dp)
            .testTag("manual-new-category-sheet"),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).background(colors.handle, RoundedCornerShape(2.dp)))
            }
            Text(stringResource(R.string.manual_new_category_title), style = ZarType.headingSheet, color = colors.textPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().clip(ZarShapes.md).background(colors.subtle).padding(horizontal = 14.dp)) {
                TextInput(
                    value = state.name,
                    onValueChange = onName,
                    placeholder = stringResource(R.string.manual_new_category_hint),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("manual-new-category-name"),
                )
            }
            val problemRes = state.problem?.let { it.textRes() } ?: if (state.failed) R.string.manual_category_error_failed else null
            FieldError(problemRes, Modifier.testTag("manual-new-category-error"))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ZarButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f).testTag("manual-new-category-cancel"),
                style = ZarButtonStyle.Soft,
            )
            ZarButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                modifier = Modifier.weight(1f).testTag("manual-new-category-save"),
                enabled = !state.isSaving && state.name.isNotBlank(),
            )
        }
    }
}

private fun CategoryNameProblem.textRes(): Int = when (this) {
    CategoryNameProblem.EMPTY -> R.string.manual_category_error_empty
    CategoryNameProblem.TOO_LONG -> R.string.manual_category_error_too_long
    CategoryNameProblem.INVALID_CHARACTERS -> R.string.manual_category_error_invalid
    CategoryNameProblem.DUPLICATE -> R.string.manual_category_error_duplicate
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

/**
 * The Add transaction sheet's one height, shared by Expense, Income and Transfer so switching tabs never resizes it.
 * It is Income's natural height (the shortest tab, about 637dp at Figma's 412 x 915 frame); taller tabs scroll, and the
 * window or keyboard can still make it shorter.
 */
internal val AddTransactionSheetHeight: Dp = 640.dp

/**
 * Equal-width segments on a subtle track; the selected one is a white tile.
 *
 * With an [indicator] the tile is one surface that slides behind the fixed slots and settles with a small spring bounce
 * (Add transaction's two selectors). Without one, each selected slot paints its own tile, as the other callers expect.
 */
@Composable
internal fun Segmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    segmentHeight: Dp,
    tagPrefix: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
    showClassDots: Boolean = false,
    indicator: SelectorIndicatorState? = null,
) {
    val colors = ZarTheme.colors
    val gap = 4.dp
    Box(modifier.fillMaxWidth().selectableGroup().clip(ZarShapes.md).background(colors.subtle).padding(4.dp)) {
        if (indicator != null) {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val count = labels.size
                        val gapPx = gap.roundToPx()
                        val slot = (constraints.maxWidth - gapPx * (count - 1)) / count
                        val tile = measurable.measure(Constraints.fixed(slot, constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val pitch = (slot + gapPx).toFloat()
                            val cap = ZarMotion.AddSelectorMaxOvershoot.toPx() / pitch
                            val x = (indicator.presentation(count - 1, cap) * pitch).roundToInt()
                            val left = if (rtl) constraints.maxWidth - slot - x else x
                            indicator.placedAt(Rect(left.toFloat(), 0f, (left + slot).toFloat(), constraints.maxHeight.toFloat()))
                            tile.place(left, 0)
                        }
                    }
                    .shadow(2.dp, ZarShapes.sm, ambientColor = Color(0x140F1A2B), spotColor = Color(0x140F1A2B))
                    .clip(ZarShapes.sm)
                    .background(colors.surface),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = segmentHeight)
                        .then(
                            if (indicator != null) Modifier
                            else Modifier
                                .then(if (selected) Modifier.shadow(2.dp, ZarShapes.sm, ambientColor = Color(0x140F1A2B), spotColor = Color(0x140F1A2B)) else Modifier)
                                .clip(ZarShapes.sm)
                                .background(if (selected) colors.surface else colors.subtle),
                        )
                        // A moving indicator means a repeat tap changes nothing: no second selection, no replayed bounce.
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { if (indicator == null || !selected) onSelect(index) })
                        .padding(vertical = 8.dp)
                        .testTag("$tagPrefix-${tags[index]}"),
                    Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (showClassDots) {
                            val dot = when (index) { 0 -> colors.accentSelected; 1 -> colors.accentDiscretionary; else -> Color.Transparent }
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dot)
                                .then(if (index == 2) Modifier.border(1.dp, colors.textTertiary, CircleShape) else Modifier))
                        }
                        Text(label, style = ZarType.labelChip, color = if (selected) colors.textPrimary else colors.textTertiary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TextInput(
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
internal fun FieldError(@androidx.annotation.StringRes res: Int?, modifier: Modifier = Modifier) {
    if (res != null) Text(stringResource(res), style = ZarType.bodyCaption, color = ZarTheme.colors.danger, modifier = modifier)
}

/**
 * Figma date and time row. Expense passes a real time; Income may have none ("Not set"), shows "Today, 15 Oct" through
 * [dateText], and stacks the two values under the label once text is enlarged ([stackAtLargeText]).
 */
@Composable
internal fun DateTimeRow(
    date: LocalDate,
    time: LocalTime?,
    onOpenDate: () -> Unit,
    onOpenTime: () -> Unit,
    dateText: String = IncomeFormat.fullDate(date),
    dateTag: String = "manual-date",
    timeTag: String = "manual-time",
    stackAtLargeText: Boolean = false,
    minTouchHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = ZarTheme.colors
    val stacked = stackAtLargeText && LocalDensity.current.fontScale >= 1.3f
    val values: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.heightIn(min = minTouchHeight).clip(ZarShapes.xs).clickable(role = Role.Button, onClick = onOpenDate)
                    .padding(vertical = 8.dp).testTag(dateTag),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dateText, style = ZarType.bodyM, color = colors.textPrimary)
                ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
            }
            Row(
                Modifier.heightIn(min = minTouchHeight).clip(ZarShapes.xs).clickable(role = Role.Button, onClick = onOpenTime)
                    .padding(vertical = 8.dp).testTag(timeTag),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    time?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) ?: stringResource(R.string.entry_time_not_set),
                    style = ZarType.bodyM,
                    color = if (time == null) colors.textTertiary else colors.textPrimary,
                )
                ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZarMonoLabel(stringResource(R.string.manual_field_date))
            values(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(stringResource(R.string.manual_field_date), modifier = Modifier.size(width = 96.dp, height = 16.dp))
            values(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRow(date: LocalDate, onSelect: (LocalDate) -> Unit) {
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
    val pickerColors = DatePickerDefaults.colors(containerColor = ZarTheme.colors.surface, selectedDayContainerColor = ZarTheme.colors.accentSelected)
    DatePickerDialog(
        colors = pickerColors,
        tonalElevation = 0.dp,
        onDismissRequest = { showPicker = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showPicker = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state, colors = pickerColors) }
}

/** The text for any category choice: a built-in's string resource or a custom category's own name. */
@Composable
internal fun ExpenseCategoryChoice.labelText(): String = when (this) {
    is ExpenseCategory -> stringResource(labelRes())
    is CustomExpenseCategory -> name
    else -> id
}

internal fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.COFFEE_DINING -> R.string.manual_category_coffee_dining
    ExpenseCategory.GROCERIES -> R.string.manual_category_groceries
    ExpenseCategory.TRANSPORT -> R.string.manual_category_transport
    ExpenseCategory.BILLS -> R.string.manual_category_bills
    ExpenseCategory.RENT -> R.string.manual_category_rent
    ExpenseCategory.SEASON_TICKET -> R.string.manual_category_season_ticket
    ExpenseCategory.SUBSCRIPTIONS -> R.string.manual_category_subscriptions
    ExpenseCategory.OTHER -> R.string.manual_category_other
}

/** A custom category has no icon of its own yet; the picker design supplies one generic icon for all of them. */
private fun ExpenseCategoryChoice.iconRes(): Int = (this as? ExpenseCategory)?.iconRes() ?: R.drawable.ic_ph_receipt

private fun ExpenseCategory.iconRes(): Int = when (this) {
    ExpenseCategory.COFFEE_DINING -> R.drawable.ic_figma_coffee
    ExpenseCategory.GROCERIES -> R.drawable.ic_figma_groceries
    ExpenseCategory.TRANSPORT -> R.drawable.ic_figma_transport
    ExpenseCategory.BILLS -> R.drawable.ic_ph_receipt
    ExpenseCategory.RENT -> R.drawable.ic_ph_house_inactive
    ExpenseCategory.SEASON_TICKET -> R.drawable.ic_figma_transport
    ExpenseCategory.SUBSCRIPTIONS -> R.drawable.ic_ph_receipt
    ExpenseCategory.OTHER -> R.drawable.ic_ph_wallet
}

internal fun SpendingClass.labelRes(): Int = when (this) {
    SpendingClass.COMMITTED -> R.string.manual_class_committed
    SpendingClass.DISCRETIONARY -> R.string.manual_class_discretionary
    SpendingClass.UNKNOWN -> R.string.manual_class_unknown
}

private fun ManualEntryMessage.textRes(): Int = when (this) {
    ManualEntryMessage.NOT_SIGNED_IN -> R.string.manual_message_not_signed_in
    ManualEntryMessage.ACCOUNT_ARCHIVED -> R.string.manual_message_archived
    ManualEntryMessage.FAILED -> R.string.manual_message_failed
}
