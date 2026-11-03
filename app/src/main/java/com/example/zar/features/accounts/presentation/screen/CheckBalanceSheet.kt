package com.example.zar.features.accounts.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckOutcome
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.accounts.presentation.viewmodel.BalanceCheckUiState
import com.example.zar.features.accounts.presentation.viewmodel.BalanceCheckViewModel
import com.example.zar.features.accounts.presentation.viewmodel.CheckMessage
import com.example.zar.features.accounts.presentation.viewmodel.GapChoice
import com.example.zar.features.transactions.presentation.activity.screen.InfoBanner
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.FieldError
import com.example.zar.features.transactions.presentation.manual.screen.TextInput

/**
 * Check cash (Figma Z29, Z70) and its bank-balance twin (Z66–Z68): compare the recorded balance with a count or a
 * statement. A gap is saved as an explicit adjustment; a shortfall can instead be explained by adding the missing
 * expense (Z69), which saves no adjustment.
 */
@Composable
fun CheckBalanceSheet(
    onDismiss: () -> Unit,
    onAddMissingEntry: (accountId: String, amount: String) -> Unit,
    viewModel: BalanceCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The ViewModel outlives the sheet, so every way out starts the next check from scratch.
    val close = {
        if (!viewModel.uiState.value.isSaving) {
            viewModel.reset()
            onDismiss()
        }
    }
    LaunchedEffect(state.isFinished) { if (state.isFinished) close() }
    LaunchedEffect(state.missingEntry) {
        state.missingEntry?.let { request ->
            onAddMissingEntry(request.accountId.value, request.amount)
            viewModel.onMissingEntryHandled()
            close()
        }
    }
    CheckBalanceBody(
        state = state,
        actions = CheckActions(
            onDismiss = close,
            onCounted = viewModel::onCountedChange,
            onChoice = viewModel::onChoiceSelect,
            onConfirm = viewModel::confirm,
        ),
    )
}

internal class CheckActions(
    val onDismiss: () -> Unit,
    val onCounted: (String) -> Unit,
    val onChoice: (GapChoice) -> Unit,
    val onConfirm: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckBalanceBody(state: BalanceCheckUiState, actions: CheckActions) {
    val colors = ZarTheme.colors
    val isCash = state.balance?.account?.type == AccountType.CASH
    ModalBottomSheet(
        onDismissRequest = { if (!state.isSaving) actions.onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 14.dp)
                .testTag("check-sheet"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetHeader(
                stringResource(if (isCash) R.string.balance_check_title_cash else R.string.balance_check_title),
                onClose = { if (!state.isSaving) actions.onDismiss() },
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                state.availability == null || state.balance == null -> Text(
                    stringResource(R.string.account_not_found),
                    style = ZarType.bodyS,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("check-unavailable"),
                )
                state.availability != CheckAvailability.AVAILABLE -> Unavailable(state.availability, actions.onDismiss)
                else -> CheckForm(state, isCash, actions)
            }
        }
    }
}

@Composable
private fun Unavailable(availability: CheckAvailability, onClose: () -> Unit) {
    val colors = ZarTheme.colors
    Text(
        stringResource(
            when (availability) {
                CheckAvailability.BALANCE_UNKNOWN -> R.string.balance_check_unavailable_unknown
                CheckAvailability.ENTRIES_NEED_FIXING -> R.string.balance_check_unavailable_fix
                CheckAvailability.ARCHIVED -> R.string.balance_check_unavailable_archived
                CheckAvailability.AVAILABLE -> R.string.balance_check_failed
            },
        ),
        style = ZarType.bodyS.copy(lineHeight = 18.sp),
        color = colors.textSecondary,
        modifier = Modifier.testTag("check-unavailable"),
    )
    ZarButton(
        text = stringResource(R.string.action_close),
        onClick = onClose,
        style = ZarButtonStyle.Soft,
        modifier = Modifier.fillMaxWidth().testTag("check-close"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckForm(state: BalanceCheckUiState, isCash: Boolean, actions: CheckActions) {
    val colors = ZarTheme.colors
    val balance = state.balance ?: return
    val check = state.check
    val currency = balance.account.currency.code

    ZarFormRow(stringResource(if (isCash) R.string.balance_check_counted else R.string.balance_check_statement)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = state.counted,
                onValueChange = actions.onCounted,
                placeholder = "0",
                textStyle = ZarType.amountM,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).testTag("check-amount"),
            )
            Text(currency, style = ZarType.labelChip, color = colors.textSecondary, modifier = Modifier.padding(start = 8.dp))
        }
    }
    val invalid = CheckProblem.AMOUNT_MISSING in state.problems || CheckProblem.AMOUNT_INVALID in state.problems
    FieldError(
        when {
            CheckProblem.AMOUNT_INVALID in state.problems -> R.string.balance_check_error_invalid
            invalid -> R.string.balance_check_error_amount
            else -> null
        },
        Modifier.testTag("check-error-amount"),
    )
    ZarDivider()

    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.md).background(colors.subtle).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryRow(stringResource(R.string.balance_check_recorded), balance.current?.let { IncomeFormat.withCode(it) }.orEmpty(), "check-recorded")
        SummaryRow(
            stringResource(if (isCash) R.string.balance_check_you_counted else R.string.balance_check_statement_says),
            check?.let { IncomeFormat.withCode(it.counted) } ?: "—",
            "check-counted",
        )
        SummaryRow(
            stringResource(R.string.balance_check_difference),
            when (check?.outcome) {
                null -> "—"
                CheckOutcome.MATCHES -> IncomeFormat.withCode(check.gap)
                CheckOutcome.SHORTFALL -> "− ${IncomeFormat.withCode(check.gap)}"
                CheckOutcome.SURPLUS -> "+ ${IncomeFormat.withCode(check.gap)}"
            },
            "check-difference",
            highlight = check?.outcome == CheckOutcome.SHORTFALL,
        )
    }

    if (check != null && check.outcome != CheckOutcome.MATCHES) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZarEyebrow(stringResource(R.string.balance_check_the_gap, IncomeFormat.withCode(check.gap)))
            if (state.canFindMissing) {
                // Wraps onto a second line on a narrow phone instead of cutting the second choice off.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZarChip(
                        label = stringResource(R.string.balance_check_leave),
                        selected = state.choice == GapChoice.LEAVE,
                        onClick = { actions.onChoice(GapChoice.LEAVE) },
                        modifier = Modifier.testTag("check-choice-leave"),
                    )
                    ZarChip(
                        label = stringResource(R.string.balance_check_find),
                        selected = state.choice == GapChoice.FIND,
                        onClick = { actions.onChoice(GapChoice.FIND) },
                        modifier = Modifier.testTag("check-choice-find"),
                    )
                }
            }
        }
    }
    check?.let {
        Text(
            stringResource(
                when {
                    it.outcome == CheckOutcome.MATCHES -> R.string.balance_check_note_matches
                    it.outcome == CheckOutcome.SURPLUS -> R.string.balance_check_note_over
                    state.choice == GapChoice.FIND -> R.string.balance_check_note_find
                    isCash -> R.string.balance_check_note_short_cash
                    else -> R.string.balance_check_note_short_other
                },
            ),
            style = ZarType.bodyS.copy(lineHeight = 18.sp),
            color = colors.textSecondary,
            modifier = Modifier.testTag("check-note"),
        )
    }
    if (!state.isOnline) {
        InfoBanner(
            title = stringResource(R.string.manual_offline_title),
            body = stringResource(R.string.balance_check_offline_body),
            modifier = Modifier.testTag("check-offline"),
        )
    }
    state.message?.let { message ->
        FieldError(
            if (message == CheckMessage.NOT_SIGNED_IN) R.string.balance_check_sign_in else R.string.balance_check_failed,
            Modifier.testTag("check-message"),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ZarButton(
            text = stringResource(R.string.action_cancel),
            onClick = actions.onDismiss,
            style = ZarButtonStyle.Soft,
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f).testTag("check-cancel"),
        )
        ZarButton(
            text = stringResource(
                when {
                    check?.outcome == CheckOutcome.MATCHES -> R.string.balance_check_confirm_matches
                    state.canFindMissing && state.choice == GapChoice.FIND -> R.string.balance_check_confirm_find
                    isCash -> R.string.balance_check_confirm_cash
                    else -> R.string.balance_check_confirm
                },
            ),
            onClick = actions.onConfirm,
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f).testTag("check-confirm"),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, tag: String, highlight: Boolean = false) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ZarType.bodyS, color = colors.textSecondary)
        Text(
            value,
            style = ZarType.labelChip,
            color = if (highlight) colors.textAttention else colors.textPrimary,
            modifier = Modifier.testTag(tag),
        )
    }
}
