package com.example.zar.features.plans.presentation.screen

import com.example.zar.core.presentation.privacy.AmountMask
import com.example.zar.core.presentation.privacy.amountFieldTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import com.example.zar.core.presentation.components.ZarDatePickerDialog
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.window.DialogProperties
import com.example.zar.core.presentation.components.SheetBackHost
import com.example.zar.features.plans.presentation.viewmodel.PlansCounts
import com.example.zar.features.plans.presentation.viewmodel.PlansCountsViewModel
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
import com.example.zar.core.presentation.components.ZarDialog
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
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.components.CollapseStatusBand
import com.example.zar.core.presentation.components.rememberHomeCollapse
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
import com.example.zar.features.transactions.presentation.category.ProvideExpenseCategoryCatalog
import com.example.zar.features.plans.domain.model.BackedGoalProgress
import com.example.zar.features.plans.domain.model.GoalBackingCalculator
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.presentation.viewmodel.AllocationEditorState
import com.example.zar.features.plans.presentation.viewmodel.GoalEditorState
import com.example.zar.features.plans.presentation.viewmodel.PlansMessage
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import com.example.zar.features.plans.presentation.viewmodel.PlanDetailRequestViewModel
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
    onOpenBills: () -> Unit = {},
    onOpenFreelance: () -> Unit = {},
    onNewBill: () -> Unit = {},
    onNewProject: () -> Unit = {},
    viewModel: PlansViewModel = hiltViewModel(),
    allowanceViewModel: AllowanceViewModel = hiltViewModel(),
    countsViewModel: PlansCountsViewModel = hiltViewModel(),
    detailRequests: PlanDetailRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allowance by allowanceViewModel.uiState.collectAsStateWithLifecycle()
    val counts by countsViewModel.counts.collectAsStateWithLifecycle()
    var showNewPlan by rememberSaveable { mutableStateOf(false) }
    var showAllPlans by rememberSaveable { mutableStateOf(false) }
    // The plan whose detail is open, by ID, so it comes back after the process is recreated.
    var detailGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    // A request from Home to open one plan: moved into the saved detail ID so it survives recreation, then cleared.
    val requestedGoal by detailRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(requestedGoal) {
        requestedGoal?.let { detailGoalId = it.value; detailRequests.consume(it) }
    }
    // The sheet closes once the goal it created has been saved.
    LaunchedEffect(state.goalCreatedCount) { if (state.goalCreatedCount > 0) showNewPlan = false }
    PlansScreenContent(
        state = state,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        onRetry = viewModel::retry,
        onEditGoal = { viewModel.openGoalEditor() },
        onAllocate = { viewModel.openAllocationEditor() },
        onChangeMain = { showAllPlans = true },
        onViewGoal = { detailGoalId = state.overview?.goal?.id?.value },
        onSeeAll = { showAllPlans = true },
        onOpenPlan = { detailGoalId = it.value },
        onNewPlan = { viewModel.clearMessage(); showNewPlan = true },
        counts = counts,
        onOpenBills = onOpenBills,
        onOpenFreelance = onOpenFreelance,
        allowanceSection = if (allowance.overview?.settings?.isEnabled == true) {
            { AllowanceCard(allowance, onEdit = allowanceViewModel::openEditor, onRetry = allowanceViewModel::retry) }
        } else {
            null
        },
        weeklyLimitSummary = allowance.overview?.settings?.takeIf { it.isEnabled }?.amount?.let {
            stringResource(R.string.plans_weekly_limit_per_week, it.formatWithCode())
        },
        onEditWeeklyLimit = allowanceViewModel::openEditor,
    )
    if (showNewPlan) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SheetBackHost(onPop = { showNewPlan = false }, slideUpOnEnter = true) { dismiss ->
                NewPlanSheet(
                    hasWeeklyLimit = allowance.overview?.settings?.isEnabled == true,
                    // Disabled until the plans have loaded: nothing is written while the catalog is unknown.
                    isSaving = state.isSaving || state.overview == null,
                    message = state.message,
                    onDismiss = dismiss,
                    onCreateGoal = viewModel::createGoal,
                    onSetLimit = { showNewPlan = false; allowanceViewModel.openEditor() },
                    onAddBill = { showNewPlan = false; onNewBill() },
                    onAddProject = { showNewPlan = false; onNewProject() },
                )
            }
        }
    }
    val overview = state.overview
    if (showAllPlans && overview != null) {
        AllPlansSheet(
            overview = overview,
            isSaving = state.isSaving,
            onSelectMain = viewModel::selectMainGoal,
            onOpen = { showAllPlans = false; detailGoalId = it.value },
            onDismiss = { showAllPlans = false },
        )
    }
    detailGoalId?.let { id -> overview?.goalById(GoalId(id)) }?.let { goal ->
        PlanDetailSheet(
            goal = goal,
            progress = overview?.progressFor(goal.id),
            isMain = overview?.goal?.id == goal.id,
            canAllocate = overview?.allocationAccountsFor(goal.id)?.isNotEmpty() == true,
            isSaving = state.isSaving,
            onSetAside = { detailGoalId = null; viewModel.openAllocationEditor(goal.id) },
            onEdit = { detailGoalId = null; viewModel.openGoalEditor(goal.id) },
            onMakeMain = { viewModel.selectMainGoal(goal.id) },
            onDismiss = { detailGoalId = null },
        )
    }
    allowance.editor?.let { editor ->
        ProvideExpenseCategoryCatalog {
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
            accounts = editor.goalId?.let { state.overview?.allocationAccountsFor(it) } ?: state.overview?.allocationAccounts.orEmpty(),
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
    onChangeMain: () -> Unit = {},
    onViewGoal: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onOpenPlan: (GoalId) -> Unit = {},
    allowanceSection: (@Composable () -> Unit)? = null,
    weeklyLimitSummary: String? = null,
    onEditWeeklyLimit: () -> Unit = {},
    onNewPlan: () -> Unit = {},
    counts: PlansCounts = PlansCounts(),
    onOpenBills: () -> Unit = {},
    onOpenFreelance: () -> Unit = {},
) {
    val chromeActions = com.example.zar.core.presentation.components.LocalNavContextActions.current
    val currentNewPlan = androidx.compose.runtime.rememberUpdatedState(onNewPlan)
    androidx.compose.runtime.DisposableEffect(chromeActions) {
        chromeActions?.newPlan = { currentNewPlan.value() }
        onDispose { chromeActions?.newPlan = null }
    }
    ZarScreenScaffold(
        destination = TopLevelDestination.Plans,
        onNewPlan = onNewPlan,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        heroUnderStatusBar = true,
    ) { padding ->
        val scrollState = rememberScrollState()
        // The hero collapses into the status bar as the page scrolls, like Home's top card.
        val collapse = rememberHomeCollapse(scrollState, cardHeight = PlansHeroSpec.Height, fadeMargin = 8.dp)
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                    )
                    .verticalScroll(scrollState),
            ) {
                when {
                    state.isLoading -> LoadingHero(collapse, onNewPlan)
                    state.loadFailed -> ErrorHero(onRetry, collapse, onNewPlan)
                    else -> GoalHero(
                        overview = state.overview ?: PlansOverview(null, emptyList()),
                        onNewPlan = onNewPlan,
                        onEditGoal = onEditGoal,
                        onAllocate = onAllocate,
                        collapse = collapse,
                        onChangeMain = onChangeMain,
                        onViewGoal = onViewGoal,
                    )
                }
                Column(
                    Modifier.padding(horizontal = ZarDimens.ScreenGutter).padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
                ) {
                    // Other saved plans beside the main goal. A failed load shows the error hero above, never an empty list.
                    state.overview?.takeIf { !state.isLoading && !state.loadFailed }?.let { YourPlansSection(it, onSeeAll, onOpenPlan) }
                    Column(verticalArrangement = Arrangement.spacedBy(PlanSectionGap)) {
                        ZarEyebrow(stringResource(R.string.plans_other_plans).uppercase(), Modifier.padding(start = 12.dp))
                        // One grouped card, a row per plan kind (Figma Z04): each opens its real screen or editor.
                        ZarCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            PlanRow(
                                icon = R.drawable.ic_ph_wallet,
                                title = stringResource(R.string.plans_weekly_limit_title),
                                subtitle = weeklyLimitSummary ?: stringResource(R.string.plans_weekly_limit_unset),
                                onClick = onEditWeeklyLimit,
                                tag = "plans-weekly-limit-row",
                            )
                            ZarDivider(Modifier.padding(start = 70.dp))
                            PlanRow(
                                icon = R.drawable.ic_ph_receipt,
                                title = stringResource(R.string.plans_bills_title),
                                subtitle = countText(counts.bills, R.plurals.plans_bills_count, R.string.plans_bills_empty),
                                onClick = onOpenBills,
                                tag = "plans-bills-card",
                            )
                            ZarDivider(Modifier.padding(start = 70.dp))
                            PlanRow(
                                icon = R.drawable.ic_ph_briefcase,
                                title = stringResource(R.string.plans_freelance_title),
                                subtitle = countText(counts.projects, R.plurals.plans_projects_count, R.string.plans_freelance_empty),
                                onClick = onOpenFreelance,
                                tag = "plans-freelance-card",
                            )
                        }
                    }
                    // The weekly limit's status and coverage notes, once one is set (or when a caller supplies the section).
                    allowanceSection?.invoke()
                    state.overview?.takeIf { !state.isLoading && !state.loadFailed }?.let { overview ->
                        val goal = overview.goal
                        val progress = overview.progress
                        if (goal != null && progress != null) {
                            Text(
                                stringResource(R.string.plans_allocated_label, progress.allocated.formatWithCode()),
                                style = ZarType.bodyS, color = ZarTheme.colors.textSecondary, modifier = Modifier.padding(start = 12.dp),
                            )
                            GoalProgressDetails(goal, progress)
                        }
                    }
                }
                Spacer(Modifier.height(padding.calculateBottomPadding()))
            }
            CollapseStatusBand(collapse, "plans-status-band", Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun countText(count: Int?, plural: Int, empty: Int): String = when {
    count == null -> "…"
    count == 0 -> stringResource(empty)
    else -> pluralStringResource(plural, count, count)
}

@Composable
private fun PlanRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    val colors = ZarTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ZarIconChip(icon, size = 40.dp, iconSize = 19.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZarType.bodyL.copy(lineHeight = 20.sp), color = colors.textPrimary)
            Text(subtitle, style = ZarType.bodyS.copy(lineHeight = 18.sp), color = colors.textSecondary)
        }
        ZarIcon(R.drawable.ic_ph_caret_right, size = 16.dp, tint = colors.textTertiary)
    }
}

@Composable
internal fun GoalProgressDetails(goal: SavingsGoal, progress: BackedGoalProgress) {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ZarEyebrow(stringResource(R.string.plans_progress_heading))
            if (progress.hasUnknownBalance) {
                Text(
                    stringResource(if (progress.hasMismatchedEntries) R.string.plans_mismatched_balance else R.string.plans_unknown_balance),
                    style = ZarType.bodyS, color = colors.textSecondary, modifier = Modifier.testTag("plans-balance-warning"),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("plans-shortfall")) {
                    ZarEyebrow(stringResource(R.string.plans_remaining_heading))
                    Text(progress.targetShortfall!!.formatWithCode(), style = ZarType.amountM, color = colors.textPrimary)
                }
                Text(stringResource(R.string.plans_allocation_gap, progress.allocationGap!!.formatWithCode()), style = ZarType.bodyS, color = colors.textSecondary)
                progress.balanceAsOf?.let { date ->
                    Text(stringResource(R.string.plans_balance_as_of, date.displayDate()), style = ZarType.bodyCaption, color = colors.textTertiary)
                }
            }
            ZarDivider()
            ZarEyebrow(stringResource(R.string.plans_pace_heading))
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
private fun PlanShellCard(label: Int, value: Int) {
    ZarCard(Modifier.fillMaxWidth()) {
        ZarEyebrow(stringResource(label))
        Text(stringResource(value), style = ZarType.bodyL, color = ZarTheme.colors.textPrimary, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Bounded, scrollable form so the actions remain reachable with large text or an IME. */
@Composable
internal fun PlanEditor(title: String, isSaving: Boolean, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ZarDialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, visualTransformation = amountFieldTransformation(),
            shape = ZarShapes.md, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZarTheme.colors.textSelected, focusedLabelColor = ZarTheme.colors.textSelected, cursorColor = ZarTheme.colors.textSelected), enabled = !isSaving,
        )
        GoalDateField(editor.targetDate, onDateChange, enabled = !isSaving)
        if (message != null) DialogError(message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalDateField(date: LocalDate?, onDateChange: (LocalDate?) -> Unit, enabled: Boolean) {
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
        ZarDatePickerDialog(
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, visualTransformation = amountFieldTransformation(),
            shape = ZarShapes.md, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZarTheme.colors.textSelected, focusedLabelColor = ZarTheme.colors.textSelected, cursorColor = ZarTheme.colors.textSelected), enabled = !isSaving,
        )
        if (message != null) DialogError(message)
    }
}

@Composable
internal fun DialogError(message: PlansMessage) {
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
