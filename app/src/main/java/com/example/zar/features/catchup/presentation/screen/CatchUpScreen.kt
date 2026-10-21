package com.example.zar.features.catchup.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.domain.reconcile.BalanceCheck
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpMessage
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpStep
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpUiState
import com.example.zar.features.catchup.presentation.viewmodel.CatchUpViewModel
import com.example.zar.features.transactions.presentation.activity.screen.InfoBanner
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.FieldError
import com.example.zar.features.transactions.presentation.manual.screen.TextInput

/**
 * The weekly cash catch-up (Figma Z74 to Z77, Z83, Z85): look over what is still open, count the cash, and save at
 * most one explicit adjustment. It works without a full history and never says the data is complete: the open
 * items stay open, and a gap that is not explained stays unknown spending.
 */
@Composable
fun CatchUpScreen(
    onClose: () -> Unit,
    onGoHome: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddCashAccount: () -> Unit,
    viewModel: CatchUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isLeaving) { if (state.isLeaving) onClose() }
    BackHandler(enabled = state.step == CatchUpStep.COUNT || state.step == CatchUpStep.SAVE) { viewModel.back() }
    CatchUpBody(
        state = state,
        actions = CatchUpActions(
            onBack = { if (!viewModel.back()) onClose() },
            onClose = onClose,
            onGoHome = onGoHome,
            onOpenEntry = onOpenEntry,
            onAddCashAccount = onAddCashAccount,
            onStartCounting = viewModel::startCounting,
            onSkipWeek = viewModel::skipThisWeek,
            onAccount = viewModel::onAccountSelect,
            onCounted = viewModel::onCountedChange,
            onReview = viewModel::reviewAdjustment,
            onSave = viewModel::save,
        ),
    )
}

internal class CatchUpActions(
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onGoHome: () -> Unit,
    val onOpenEntry: (String) -> Unit,
    val onAddCashAccount: () -> Unit,
    val onStartCounting: () -> Unit,
    val onSkipWeek: () -> Unit,
    val onAccount: (AccountId) -> Unit,
    val onCounted: (String) -> Unit,
    val onReview: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun CatchUpBody(state: CatchUpUiState, actions: CatchUpActions) {
    val colors = ZarTheme.colors
    val firstTime = state.overview?.hasNothingRecorded == true
    val (title, subtitle) = when (state.step) {
        CatchUpStep.REVIEW -> R.string.catch_up_title to (if (firstTime) R.string.catch_up_subtitle_first else R.string.catch_up_subtitle)
        CatchUpStep.COUNT -> R.string.catch_up_count_title to R.string.catch_up_count_subtitle
        CatchUpStep.SAVE ->
            if (state.check?.outcome == CheckOutcome.MATCHES) R.string.catch_up_save_matches_title to R.string.catch_up_save_matches_subtitle
            else R.string.catch_up_save_title to R.string.catch_up_save_subtitle
        CatchUpStep.DONE -> R.string.catch_up_saved_title to R.string.catch_up_saved_subtitle
    }
    val bottomActions: (@Composable ColumnScope.() -> Unit)? =
        if (state.isLoading || state.loadFailed) null else { { StepActions(state, firstTime, actions) } }
    CatchUpScaffold(
        title = stringResource(title),
        subtitle = if (state.isLoading || state.loadFailed) null else stringResource(subtitle),
        onBack = actions.onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = Modifier.testTag("catchup-screen"),
        actions = bottomActions,
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                CircularProgressIndicator(color = colors.accentSelected)
            }
            state.loadFailed -> Text(
                stringResource(R.string.review_load_failed),
                style = ZarType.bodyS,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).testTag("catchup-failed"),
            )
            else -> when (state.step) {
                CatchUpStep.REVIEW -> ReviewStep(state, firstTime, actions)
                CatchUpStep.COUNT -> CountStep(state, actions)
                CatchUpStep.SAVE -> SaveStep(state)
                CatchUpStep.DONE -> DoneStep(state)
            }
        }
    }
}

@Composable
private fun StepActions(state: CatchUpUiState, firstTime: Boolean, actions: CatchUpActions) {
    when (state.step) {
        CatchUpStep.REVIEW -> {
            ZarButton(
                stringResource(if (firstTime) R.string.catch_up_count_cash else R.string.catch_up_continue),
                actions.onStartCounting,
                Modifier.fillMaxWidth().testTag("catchup-continue"),
            )
            ZarButton(
                stringResource(if (firstTime) R.string.catch_up_not_now else R.string.catch_up_skip_week),
                // "Not now" only closes; skipping the week also skips this week's reminder.
                if (firstTime) actions.onClose else actions.onSkipWeek,
                Modifier.fillMaxWidth().testTag("catchup-skip-week"),
                style = ZarButtonStyle.Soft,
            )
        }
        CatchUpStep.COUNT -> when {
            state.cashAccounts.isEmpty() -> {
                ZarButton(stringResource(R.string.catch_up_add_cash_account), actions.onAddCashAccount, Modifier.fillMaxWidth().testTag("catchup-add-account"))
                ZarButton(stringResource(R.string.catch_up_done), actions.onClose, Modifier.fillMaxWidth().testTag("catchup-done-empty"), style = ZarButtonStyle.Soft)
            }
            state.availability != CheckAvailability.AVAILABLE ->
                ZarButton(stringResource(R.string.catch_up_skip_counting), actions.onClose, Modifier.fillMaxWidth().testTag("catchup-skip-counting"), style = ZarButtonStyle.Soft)
            else -> {
                ZarButton(stringResource(R.string.catch_up_review_adjustment), actions.onReview, Modifier.fillMaxWidth().testTag("catchup-review"))
                ZarButton(stringResource(R.string.catch_up_skip_counting), actions.onClose, Modifier.fillMaxWidth().testTag("catchup-skip-counting"), style = ZarButtonStyle.Soft)
            }
        }
        CatchUpStep.SAVE -> {
            val matches = state.check?.outcome == CheckOutcome.MATCHES
            ZarButton(
                stringResource(if (matches) R.string.catch_up_finish else R.string.catch_up_save),
                actions.onSave,
                Modifier.fillMaxWidth().testTag("catchup-save"),
                enabled = !state.isSaving && state.check != null,
            )
            ZarButton(
                stringResource(R.string.catch_up_back),
                actions.onBack,
                Modifier.fillMaxWidth().testTag("catchup-back"),
                style = ZarButtonStyle.Soft,
                enabled = !state.isSaving,
            )
        }
        CatchUpStep.DONE ->
            ZarButton(stringResource(R.string.catch_up_back_home), actions.onGoHome, Modifier.fillMaxWidth().testTag("catchup-home"))
    }
}

@Composable
private fun ReviewStep(state: CatchUpUiState, firstTime: Boolean, actions: CatchUpActions) {
    StepLabel(stringResource(R.string.catch_up_step_review), Modifier.testTag("catchup-step"))
    if (state.items.isEmpty()) {
        CatchUpCard(Modifier.testTag("catchup-none")) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ZarIconChip(R.drawable.ic_ph_info)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.catch_up_none_title), style = ZarType.bodyM, color = ZarTheme.colors.textPrimary)
                    Text(stringResource(R.string.catch_up_none_body), style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
                }
            }
        }
    } else {
        ZarCard(Modifier.fillMaxWidth().testTag("catchup-items"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            state.items.forEachIndexed { index, item ->
                UnresolvedRow(item, onOpen = { actions.onOpenEntry(item.entryId.value) })
                if (index < state.items.lastIndex) ZarDivider()
            }
        }
    }
    InfoBanner(
        title = stringResource(if (firstTime) R.string.catch_up_note_first_title else R.string.catch_up_note_title),
        body = stringResource(if (firstTime) R.string.catch_up_note_first_body else R.string.catch_up_note_body),
        modifier = Modifier.testTag("catchup-honest-note"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountStep(state: CatchUpUiState, actions: CatchUpActions) {
    val colors = ZarTheme.colors
    StepLabel(stringResource(R.string.catch_up_step_count), Modifier.testTag("catchup-step"))
    val balance = state.balance
    if (balance == null) {
        InfoBanner(
            title = stringResource(R.string.catch_up_no_cash_title),
            body = stringResource(R.string.catch_up_no_cash_body),
            modifier = Modifier.testTag("catchup-no-cash"),
        )
        return
    }
    if (state.cashAccounts.size > 1) {
        FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.cashAccounts.forEach { option ->
                ZarChip(
                    label = option.account.name,
                    selected = option.account.id == balance.account.id,
                    onClick = { actions.onAccount(option.account.id) },
                    modifier = Modifier.testTag("catchup-account-${option.account.id.value}"),
                )
            }
        }
    }
    if (state.availability != CheckAvailability.AVAILABLE) {
        InfoBanner(
            title = balance.account.name,
            body = stringResource(
                if (state.availability == CheckAvailability.ENTRIES_NEED_FIXING) R.string.catch_up_unavailable_fix else R.string.catch_up_unavailable_unknown,
            ),
            modifier = Modifier.testTag("catchup-unavailable"),
        )
        return
    }
    val check = state.check
    CatchUpCard(Modifier.testTag("catchup-count-card")) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIconChip(R.drawable.ic_ph_wallet)
            Text(balance.account.name, style = ZarType.bodyM, color = colors.textPrimary)
        }
        ZarFormRow(stringResource(R.string.catch_up_counted)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextInput(
                    value = state.counted,
                    onValueChange = actions.onCounted,
                    placeholder = "0",
                    textStyle = ZarType.amountM,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f).testTag("catchup-amount"),
                )
                Text(balance.account.currency.code, style = ZarType.labelChip, color = colors.textSecondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        FieldError(
            when {
                CheckProblem.AMOUNT_INVALID in state.problems -> R.string.catch_up_error_invalid
                CheckProblem.AMOUNT_MISSING in state.problems -> R.string.catch_up_error_amount
                else -> null
            },
            Modifier.testTag("catchup-error-amount"),
        )
        ZarDivider()
        KeyValueRow(stringResource(R.string.catch_up_recorded_cash), IncomeFormat.withCode(check?.recorded ?: balance.current!!), Modifier.testTag("catchup-recorded"))
        if (check != null) {
            KeyValueRow(stringResource(R.string.catch_up_you_counted), IncomeFormat.withCode(check.counted))
            KeyValueRow(
                stringResource(R.string.catch_up_difference),
                differenceText(check),
                Modifier.testTag("catchup-difference"),
                valueColor = if (check.outcome == CheckOutcome.MATCHES) colors.textPrimary else colors.textAttention,
            )
        }
    }
    InfoBanner(
        title = stringResource(R.string.catch_up_not_saved_title),
        body = stringResource(R.string.catch_up_not_saved_body),
        modifier = Modifier.testTag("catchup-not-saved"),
    )
}

@Composable
private fun differenceText(check: BalanceCheck): String = when (check.outcome) {
    CheckOutcome.MATCHES -> IncomeFormat.withCode(check.gap)
    CheckOutcome.SHORTFALL -> stringResource(R.string.catch_up_adjustment_out, IncomeFormat.withCode(check.gap))
    CheckOutcome.SURPLUS -> stringResource(R.string.catch_up_adjustment_in, IncomeFormat.withCode(check.gap))
}

@Composable
private fun SaveStep(state: CatchUpUiState) {
    val colors = ZarTheme.colors
    val check = state.check
    StepLabel(stringResource(R.string.catch_up_step_save), Modifier.testTag("catchup-step"))
    if (!state.isOnline) {
        InfoBanner(
            title = stringResource(R.string.manual_offline_title),
            body = stringResource(if (check?.outcome == CheckOutcome.MATCHES) R.string.catch_up_offline_body_matches else R.string.catch_up_offline_body),
            modifier = Modifier.testTag("catchup-offline"),
        )
    }
    if (check == null) return
    CatchUpCard(Modifier.testTag("catchup-summary")) {
        if (check.outcome != CheckOutcome.MATCHES) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ZarIconChip(R.drawable.ic_ph_receipt)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.catch_up_adjustment_title), style = ZarType.bodyM, color = colors.textPrimary)
                    Text(stringResource(R.string.catch_up_adjustment_meta), style = ZarType.bodyS, color = colors.textSecondary)
                }
            }
            Text(differenceText(check), style = ZarType.amountM, color = colors.textPrimary, modifier = Modifier.testTag("catchup-adjustment-amount"))
            ZarDivider()
        }
        KeyValueRow(stringResource(R.string.catch_up_recorded_cash), IncomeFormat.withCode(check.recorded))
        KeyValueRow(stringResource(R.string.catch_up_you_counted), IncomeFormat.withCode(check.counted))
        if (check.outcome != CheckOutcome.MATCHES) {
            KeyValueRow(stringResource(R.string.catch_up_balance_after), IncomeFormat.withCode(check.counted))
        }
    }
    when (check.outcome) {
        CheckOutcome.SHORTFALL -> InfoBanner(
            title = stringResource(R.string.catch_up_incomplete_title),
            body = stringResource(R.string.catch_up_incomplete_body, IncomeFormat.withCode(check.gap)),
            modifier = Modifier.testTag("catchup-incomplete-note"),
        )
        CheckOutcome.SURPLUS -> InfoBanner(
            title = stringResource(R.string.catch_up_extra_title),
            body = stringResource(R.string.catch_up_extra_body, IncomeFormat.withCode(check.gap)),
            modifier = Modifier.testTag("catchup-extra-note"),
        )
        CheckOutcome.MATCHES -> Unit
    }
    state.message?.let { message ->
        Text(
            stringResource(if (message == CatchUpMessage.NOT_SIGNED_IN) R.string.catch_up_message_sign_in else R.string.catch_up_message_failed),
            style = ZarType.bodyCaption,
            color = colors.danger,
            modifier = Modifier.padding(horizontal = 12.dp).testTag("catchup-message"),
        )
    }
}

@Composable
private fun DoneStep(state: CatchUpUiState) {
    val colors = ZarTheme.colors
    val result = state.result ?: return
    val savedGap = result.outcome != CheckOutcome.MATCHES
    CatchUpCard(Modifier.testTag("catchup-result")) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIcon(R.drawable.ic_ph_info, size = 20.dp, tint = colors.iconStrong)
            Text(stringResource(R.string.catch_up_saved_cash_is, IncomeFormat.withCode(result.counted)), style = ZarType.bodyM, color = colors.textPrimary)
        }
        ZarDivider()
        KeyValueRow(
            stringResource(R.string.catch_up_saved_adjustment_row),
            stringResource(if (savedGap) R.string.catch_up_saved_adjustment_one else R.string.catch_up_saved_adjustment_none),
            Modifier.testTag("catchup-result-adjustment"),
        )
        KeyValueRow(stringResource(R.string.catch_up_saved_left_row), state.items.size.toString(), Modifier.testTag("catchup-result-left"))
        KeyValueRow(
            stringResource(R.string.catch_up_saved_next_row),
            state.nextReminder?.let { CatchUpFormat.next(it) } ?: stringResource(R.string.catch_up_saved_next_off),
            Modifier.testTag("catchup-result-next"),
        )
    }
    // Only a shortfall leaves the totals incomplete. Either way the note says what catch-up did not cover.
    val incomplete = result.outcome == CheckOutcome.SHORTFALL
    InfoBanner(
        title = stringResource(if (incomplete) R.string.catch_up_saved_incomplete_title else R.string.catch_up_saved_complete_title),
        body = stringResource(if (incomplete) R.string.catch_up_saved_incomplete_body else R.string.catch_up_saved_complete_body),
        modifier = Modifier.testTag("catchup-result-note"),
    )
}
