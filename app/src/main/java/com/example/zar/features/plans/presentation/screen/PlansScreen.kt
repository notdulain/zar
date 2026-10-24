package com.example.zar.features.plans.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonSize
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarTrack
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.features.allowance.presentation.screen.AllowanceCard
import com.example.zar.features.allowance.presentation.screen.AllowanceEditorDialog
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceViewModel
import com.example.zar.features.plans.domain.model.BackedGoalProgress
import com.example.zar.features.plans.domain.model.GoalBackingCalculator
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.presentation.viewmodel.AllocationEditorState
import com.example.zar.features.plans.presentation.viewmodel.GoalEditorState
import com.example.zar.features.plans.presentation.viewmodel.PlansMessage
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import com.example.zar.features.plans.presentation.viewmodel.PlansViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
fun PlansScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    viewModel: PlansViewModel = hiltViewModel(),
    allowanceViewModel: AllowanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allowance by allowanceViewModel.uiState.collectAsStateWithLifecycle()
    PlansScreenContent(
        state = state,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        onRetry = viewModel::retry,
        onEditGoal = viewModel::openGoalEditor,
        onAllocate = viewModel::openAllocationEditor,
        allowanceSection = {
            AllowanceCard(allowance, onEdit = allowanceViewModel::openEditor, onRetry = allowanceViewModel::retry)
        },
    )
    allowance.editor?.let { editor ->
        AllowanceEditorDialog(
            editor = editor,
            isSaving = allowance.isSaving,
            message = allowance.message,
            onEnabledChange = allowanceViewModel::setEnabled,
            onAmountChange = allowanceViewModel::updateAmount,
            onToggleCategory = allowanceViewModel::toggleCategory,
            onWeekStartChange = allowanceViewModel::setWeekStart,
            onDismiss = allowanceViewModel::closeEditor,
            onSave = allowanceViewModel::submit,
        )
    }
    state.goalEditor?.let { editor ->
        GoalEditorDialog(
            editor = editor,
            isSaving = state.isSaving,
            message = state.message,
            onNameChange = viewModel::updateGoalName,
            onTargetChange = viewModel::updateGoalTarget,
            onDateChange = viewModel::updateGoalDate,
            onDismiss = viewModel::closeGoalEditor,
            onSave = viewModel::submitGoal,
        )
    }
    state.allocationEditor?.let { editor ->
        AllocationDialog(
            editor = editor,
            accounts = state.overview?.allocationAccounts.orEmpty(),
            isSaving = state.isSaving,
            message = state.message,
            onSelectAccount = viewModel::selectAllocationAccount,
            onAmountChange = viewModel::updateAllocationAmount,
            onDismiss = viewModel::closeAllocationEditor,
            onSave = viewModel::submitAllocation,
        )
    }
}

@Composable
internal fun PlansScreenContent(
    state: PlansUiState,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onRetry: () -> Unit,
    onEditGoal: () -> Unit,
    onAllocate: () -> Unit,
    allowanceSection: @Composable () -> Unit = {
        PlanShellCard(R.string.plans_allowance, R.string.plans_allowance_empty)
    },
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Plans,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
        ) {
            when {
                state.isLoading -> LoadingHero()
                state.loadFailed -> ErrorHero(onRetry)
                else -> GoalHero(
                    overview = state.overview ?: PlansOverview(null, emptyList()),
                    onEditGoal = onEditGoal,
                    onAllocate = onAllocate,
                )
            }
            Column(
                Modifier.padding(horizontal = ZarDimens.ScreenGutter),
                verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
            ) {
                state.overview?.takeIf { !state.isLoading && !state.loadFailed }?.let { overview ->
                    val goal = overview.goal
                    val progress = overview.progress
                    if (goal != null && progress != null) GoalProgressDetails(goal, progress)
                }
                allowanceSection()
                PlanShellCard(R.string.home_bills, R.string.plans_bills_empty)
            }
        }
    }
}

/** Uses the same dusk surface, Geist amounts and rounded bottom edge as Home. */
@Composable
private fun PlansHero(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = ZarDimens.FullHeroRadius, bottomEnd = ZarDimens.FullHeroRadius))
            .background(ZarGradients.dusk)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun GoalHero(overview: PlansOverview, onEditGoal: () -> Unit, onAllocate: () -> Unit) {
    val goal = overview.goal
    val progress = overview.progress
    PlansHero {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
            TextButton(onClick = onEditGoal, modifier = Modifier.testTag("plans-edit-goal")) {
                Text(stringResource(if (goal == null) R.string.plans_create else R.string.action_edit), style = ZarType.labelAction, color = ZarWhite)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("plans-hero")) {
            Text(goal?.name ?: stringResource(R.string.plans_goal_name), style = ZarType.bodyL, color = ZarWhite)
            ZarMonoLabel(stringResource(R.string.plans_backed_heading), color = ZarSubtle)
            Text(
                progress?.backed?.formatWithCode() ?: stringResource(R.string.plans_zero_amount),
                style = ZarType.amountL, color = ZarWhite, modifier = Modifier.testTag("plans-backed"),
            )
            Text(
                goal?.let { stringResource(R.string.plans_of_target, it.target.formatWithCode()) }
                    ?: stringResource(R.string.plans_goal_not_created),
                style = ZarType.bodyS, color = ZarSubtle,
            )
        }
        if (goal != null && progress != null) GoalProgressBars(goal, progress)
        Text(
            when {
                goal == null -> stringResource(R.string.plans_create_note)
                goal.allocations.isEmpty() -> stringResource(R.string.plans_nothing_allocated)
                progress?.hasUnknownBalance == true -> stringResource(R.string.plans_unknown_backing)
                progress?.isApproximate == true -> stringResource(R.string.plans_estimated_backing)
                else -> stringResource(R.string.plans_recorded_backing)
            },
            style = ZarType.bodyCaption, color = ZarSubtle,
        )
        ZarButton(
            text = stringResource(if (goal == null) R.string.plans_create_action else R.string.plans_allocate_action),
            onClick = { if (goal == null) onEditGoal() else onAllocate() },
            style = ZarButtonStyle.Soft,
            modifier = Modifier.fillMaxWidth().testTag("plans-set-aside"),
            enabled = goal == null || overview.allocationAccounts.isNotEmpty(),
            size = ZarButtonSize.Medium,
        )
        if (goal != null && overview.allocationAccounts.isEmpty()) {
            Text(stringResource(R.string.plans_no_accounts), style = ZarType.bodyS, color = ZarSubtle)
        }
    }
}

@Composable
private fun GoalProgressBars(goal: SavingsGoal, progress: BackedGoalProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProgressRow(stringResource(R.string.plans_backed_label, progress.backed.formatWithCode()), progress.backedFraction(goal.target), "plans-backed-progress", ZarWhite)
        ProgressRow(stringResource(R.string.plans_allocated_label, progress.allocated.formatWithCode()), progress.allocatedFraction(goal.target), "plans-allocated-progress", ZarSubtle)
    }
}

@Composable
private fun ProgressRow(label: String, fraction: Float, tag: String, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = ZarType.bodyCaption, color = ZarSubtle)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).testTag(tag),
            color = color, trackColor = ZarTrack.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun GoalProgressDetails(goal: SavingsGoal, progress: BackedGoalProgress) {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ZarMonoLabel(stringResource(R.string.plans_progress_heading))
            if (progress.hasUnknownBalance) {
                Text(
                    stringResource(if (progress.hasMismatchedEntries) R.string.plans_mismatched_balance else R.string.plans_unknown_balance),
                    style = ZarType.bodyS, color = colors.textSecondary, modifier = Modifier.testTag("plans-balance-warning"),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("plans-shortfall")) {
                    ZarMonoLabel(stringResource(R.string.plans_remaining_heading))
                    Text(progress.targetShortfall!!.formatWithCode(), style = ZarType.amountM, color = colors.textPrimary)
                }
                Text(stringResource(R.string.plans_allocation_gap, progress.allocationGap!!.formatWithCode()), style = ZarType.bodyS, color = colors.textSecondary)
                progress.balanceAsOf?.let { date ->
                    Text(stringResource(R.string.plans_balance_as_of, date.displayDate()), style = ZarType.bodyCaption, color = colors.textTertiary)
                }
            }
            ZarDivider()
            ZarMonoLabel(stringResource(R.string.plans_pace_heading))
            val paceText = when {
                progress.targetShortfall?.isZero == true -> stringResource(R.string.plans_goal_backed)
                goal.targetDate == null -> stringResource(R.string.plans_set_date_hint)
                progress.hasUnknownBalance -> stringResource(R.string.plans_pace_unavailable)
                progress.daysRemaining != null && progress.daysRemaining <= 0 -> stringResource(R.string.plans_goal_overdue)
                progress.weeklyPace != null -> stringResource(R.string.plans_weekly_pace, progress.weeklyPace.formatWithCode(), goal.targetDate.displayDate())
                else -> stringResource(R.string.plans_pace_unavailable)
            }
            Text(paceText, style = ZarType.bodyL, color = colors.textPrimary, modifier = Modifier.testTag("plans-weekly-pace"))
        }
    }
}

private fun LocalDate.displayDate(): String = format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

@Composable
private fun LoadingHero() = PlansHero {
    ZarMonoLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
    CircularProgressIndicator(color = ZarWhite, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun ErrorHero(onRetry: () -> Unit) = PlansHero {
    ZarMonoLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
    Text(stringResource(R.string.plans_load_failed), style = ZarType.bodyL, color = ZarWhite)
    ZarButton(stringResource(R.string.action_retry), onRetry, style = ZarButtonStyle.Soft)
}

@Composable
private fun PlanShellCard(label: Int, value: Int) {
    ZarCard(Modifier.fillMaxWidth()) {
        ZarMonoLabel(stringResource(label))
        Text(stringResource(value), style = ZarType.bodyL, color = ZarTheme.colors.textPrimary, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Bounded, scrollable form so the actions remain reachable with large text or an IME. */
@Composable
internal fun PlanEditor(title: String, isSaving: Boolean, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(modifier = Modifier.testTag("plans-editor"), shape = ZarShapes.xl, color = ZarTheme.colors.surface) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, style = ZarType.headingSheet, color = ZarTheme.colors.textPrimary)
                content()
                ZarButton(stringResource(if (isSaving) R.string.plans_saving else R.string.action_save), onSave, Modifier.fillMaxWidth().testTag("plans-save"), enabled = !isSaving)
                ZarButton(stringResource(R.string.action_cancel), onDismiss, Modifier.fillMaxWidth(), style = ZarButtonStyle.Soft, enabled = !isSaving)
            }
        }
    }
}

@Composable
internal fun GoalEditorDialog(
    editor: GoalEditorState,
    isSaving: Boolean,
    message: PlansMessage?,
    onNameChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    PlanEditor(stringResource(R.string.plans_edit_goal), isSaving, onDismiss, onSave) {
        OutlinedTextField(editor.name, onNameChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.plans_goal_field)) }, singleLine = true, shape = ZarShapes.md, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZarTheme.colors.textSelected, focusedLabelColor = ZarTheme.colors.textSelected, cursorColor = ZarTheme.colors.textSelected), enabled = !isSaving)
        OutlinedTextField(
            editor.target, onTargetChange, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.plans_target_field)) }, prefix = { Text("LKR ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            shape = ZarShapes.md, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZarTheme.colors.textSelected, focusedLabelColor = ZarTheme.colors.textSelected, cursorColor = ZarTheme.colors.textSelected), enabled = !isSaving,
        )
        GoalDateField(editor.targetDate, onDateChange, enabled = !isSaving)
        if (message != null) DialogError(message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDateField(date: LocalDate?, onDateChange: (LocalDate?) -> Unit, enabled: Boolean) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    ZarButton(
        date?.let { stringResource(R.string.plans_target_date_value, it.displayDate()) } ?: stringResource(R.string.plans_target_date_empty),
        { showPicker = true }, Modifier.fillMaxWidth().testTag("plans-target-date"), style = ZarButtonStyle.Outline, enabled = enabled,
    )
    if (date != null) {
        TextButton(onClick = { onDateChange(null) }, enabled = enabled) {
            Text(stringResource(R.string.plans_clear_target_date), style = ZarType.labelAction, color = ZarTheme.colors.textSecondary)
        }
    }
    if (showPicker) {
        val initial = date ?: ZarTime.today().plusMonths(6)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val pickerColors = DatePickerDefaults.colors(containerColor = ZarTheme.colors.surface, selectedDayContainerColor = ZarTheme.colors.accentSelected)
        DatePickerDialog(
            colors = pickerColors,
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok), color = ZarTheme.colors.textSelected) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel), color = ZarTheme.colors.textSelected) } },
        ) { DatePicker(state = picker, colors = pickerColors) }
    }
}

@Composable
private fun AllocationDialog(
    editor: AllocationEditorState,
    accounts: List<AccountBalance>,
    isSaving: Boolean,
    message: PlansMessage?,
    onSelectAccount: (AccountId) -> Unit,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    PlanEditor(stringResource(R.string.plans_set_aside), isSaving, onDismiss, onSave) {
        Text(stringResource(R.string.plans_allocation_explanation), style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { balance ->
                Row(
                    Modifier.fillMaxWidth().clip(ZarShapes.md)
                        .background(if (editor.accountId == balance.account.id) ZarTheme.colors.selectedTint else ZarTheme.colors.subtle)
                        .selectable(selected = editor.accountId == balance.account.id, enabled = !isSaving, role = Role.RadioButton) { onSelectAccount(balance.account.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = editor.accountId == balance.account.id, onClick = null, enabled = !isSaving, colors = RadioButtonDefaults.colors(selectedColor = ZarTheme.colors.textSelected))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(balance.account.name, style = ZarType.bodyM, color = ZarTheme.colors.textPrimary)
                        Text(balance.current?.formatWithCode() ?: stringResource(R.string.plans_balance_unknown_short), style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
                    }
                }
            }
        }
        OutlinedTextField(
            editor.amount, onAmountChange, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.plans_allocation_field)) }, prefix = { Text("LKR ") },
            supportingText = { Text(stringResource(R.string.plans_zero_removes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            shape = ZarShapes.md, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZarTheme.colors.textSelected, focusedLabelColor = ZarTheme.colors.textSelected, cursorColor = ZarTheme.colors.textSelected), enabled = !isSaving,
        )
        if (message != null) DialogError(message)
    }
}

@Composable
private fun DialogError(message: PlansMessage) {
    val text = when (message) {
        PlansMessage.INVALID_GOAL -> R.string.plans_invalid_goal
        PlansMessage.INVALID_AMOUNT -> R.string.plans_invalid_amount
        PlansMessage.ALLOCATION_TOO_LARGE -> R.string.plans_allocation_too_large
        PlansMessage.SAVE_FAILED -> R.string.plans_save_failed
    }
    Text(stringResource(text), color = ZarTheme.colors.danger, style = ZarType.bodyS)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlansScreenPreview() {
    val account = Account(
        AccountId("savings"), "Savings", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Known(FiatMoney(1_120_000, Currencies.LKR)), LocalDate.of(2026, 10, 1),
    )
    val balance = AccountBalance(account, FiatMoney.zero(Currencies.LKR), FiatMoney(1_120_000, Currencies.LKR), false, 0)
    val goal = SavingsGoal(GoalId("macbook-fund"), "MacBook fund", FiatMoney(49_000_000, Currencies.LKR))
    ZarTheme {
        PlansScreenContent(
            state = PlansUiState(
                isLoading = false,
                overview = PlansOverview(
                    goal, listOf(balance), GoalBackingCalculator.calculate(goal, listOf(account), emptyList(), LocalDate.of(2026, 10, 20)),
                ),
            ),
            onNavigate = {}, onAddOption = {}, onRetry = {}, onEditGoal = {}, onAllocate = {},
        )
    }
}
