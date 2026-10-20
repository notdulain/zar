package com.example.zar.features.plans.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarHeroCard
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarTrack
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.presentation.viewmodel.AllocationEditorState
import com.example.zar.features.plans.presentation.viewmodel.GoalEditorState
import com.example.zar.features.plans.presentation.viewmodel.PlansMessage
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import com.example.zar.features.plans.presentation.viewmodel.PlansViewModel
import java.time.LocalDate

@Composable
fun PlansScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlansScreenContent(
        state = state,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        onRetry = viewModel::retry,
        onEditGoal = viewModel::openGoalEditor,
        onAllocate = viewModel::openAllocationEditor,
    )
    state.goalEditor?.let { editor ->
        GoalEditorDialog(
            editor = editor,
            isSaving = state.isSaving,
            message = state.message,
            onNameChange = viewModel::updateGoalName,
            onTargetChange = viewModel::updateGoalTarget,
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
private fun PlansScreenContent(
    state: PlansUiState,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onRetry: () -> Unit,
    onEditGoal: () -> Unit,
    onAllocate: () -> Unit,
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Plans,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = ZarDimens.ScreenGutter, vertical = ZarDimens.SpaceSm),
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
            PlanShellCard(R.string.plans_allowance, R.string.plans_allowance_empty)
            PlanShellCard(R.string.home_bills, R.string.plans_bills_empty)
        }
    }
}

@Composable
private fun GoalHero(overview: PlansOverview, onEditGoal: () -> Unit, onAllocate: () -> Unit) {
    val goal = overview.goal
    ZarHeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 316.dp)
            .testTag("plans-hero"),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ZarLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
            Text(
                stringResource(if (goal == null) R.string.plans_create else R.string.action_edit),
                style = MaterialTheme.typography.labelMedium,
                color = ZarWhite,
                modifier = Modifier.clickable(onClick = onEditGoal),
            )
        }
        Spacer(Modifier.height(ZarDimens.SpaceLg))
        ZarLabel(goal?.name ?: stringResource(R.string.plans_goal_name), color = ZarSubtle)
        Text(
            goal?.allocated?.formatWithCode() ?: stringResource(R.string.plans_zero_amount),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.testTag("plans-allocated"),
        )
        Text(
            goal?.let { stringResource(R.string.plans_of_target, it.target.formatWithCode()) }
                ?: stringResource(R.string.plans_goal_not_created),
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = { goal?.progress ?: 0f },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = ZarWhite,
            trackColor = ZarTrack.copy(alpha = 0.35f),
        )
        Text(
            when {
                goal == null -> stringResource(R.string.plans_create_note)
                goal.allocations.isEmpty() -> stringResource(R.string.plans_nothing_allocated)
                else -> stringResource(R.string.plans_remaining, goal.remaining.formatWithCode())
            },
            style = MaterialTheme.typography.labelSmall,
            color = ZarSubtle,
        )
        Spacer(Modifier.height(ZarDimens.SpaceLg))
        Text(
            stringResource(if (goal == null) R.string.plans_create_action else R.string.plans_allocate_action),
            style = MaterialTheme.typography.labelMedium,
            color = ZarWhite,
            modifier = Modifier
                .testTag("plans-set-aside")
                .clickable(enabled = goal == null || overview.allocationAccounts.isNotEmpty()) {
                    if (goal == null) onEditGoal() else onAllocate()
                }
                .padding(vertical = ZarDimens.SpaceSm),
        )
        if (goal != null && overview.allocationAccounts.isEmpty()) {
            Text(stringResource(R.string.plans_no_accounts), style = MaterialTheme.typography.bodySmall, color = ZarSubtle)
        }
    }
}

@Composable
private fun LoadingHero() = ZarHeroCard(Modifier.fillMaxWidth().height(316.dp)) {
    ZarLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
    CircularProgressIndicator(color = ZarWhite, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun ErrorHero(onRetry: () -> Unit) = ZarHeroCard(Modifier.fillMaxWidth().height(316.dp)) {
    ZarLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
    Text(stringResource(R.string.plans_load_failed), style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry), color = ZarWhite) }
}

@Composable
private fun PlanShellCard(label: Int, value: Int) {
    ZarCard(
        modifier = Modifier.fillMaxWidth().height(94.dp),
        contentPadding = PaddingValues(ZarDimens.CardPadding),
    ) {
        ZarLabel(stringResource(label))
        Text(stringResource(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = ZarDimens.SpaceSm))
    }
}

@Composable
private fun GoalEditorDialog(
    editor: GoalEditorState,
    isSaving: Boolean,
    message: PlansMessage?,
    onNameChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_edit_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd)) {
                OutlinedTextField(editor.name, onNameChange, label = { Text(stringResource(R.string.plans_goal_field)) }, singleLine = true)
                OutlinedTextField(
                    editor.target,
                    onTargetChange,
                    label = { Text(stringResource(R.string.plans_target_field)) },
                    prefix = { Text("LKR ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (message != null) DialogError(message)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !isSaving) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_set_aside)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd)) {
                Text(stringResource(R.string.plans_allocation_explanation), style = MaterialTheme.typography.bodyMedium)
                accounts.forEach { balance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAccount(balance.account.id) }
                            .padding(vertical = ZarDimens.SpaceSm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (editor.accountId == balance.account.id) "● ${balance.account.name}" else "○ ${balance.account.name}")
                        Text(balance.current?.formatWithCode().orEmpty())
                    }
                }
                OutlinedTextField(
                    editor.amount,
                    onAmountChange,
                    label = { Text(stringResource(R.string.plans_allocation_field)) },
                    prefix = { Text("LKR ") },
                    supportingText = { Text(stringResource(R.string.plans_zero_removes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (message != null) DialogError(message)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !isSaving) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DialogError(message: PlansMessage) {
    val text = when (message) {
        PlansMessage.INVALID_GOAL -> R.string.plans_invalid_goal
        PlansMessage.INVALID_AMOUNT -> R.string.plans_invalid_amount
        PlansMessage.ALLOCATION_TOO_LARGE -> R.string.plans_allocation_too_large
        PlansMessage.SAVE_FAILED -> R.string.plans_save_failed
    }
    Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            state = PlansUiState(isLoading = false, overview = PlansOverview(goal, listOf(balance))),
            onNavigate = {}, onAddOption = {}, onRetry = {}, onEditGoal = {}, onAllocate = {},
        )
    }
}
