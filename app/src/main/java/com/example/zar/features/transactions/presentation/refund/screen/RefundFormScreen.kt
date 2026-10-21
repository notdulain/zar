package com.example.zar.features.transactions.presentation.refund.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.transactions.domain.refund.RefundProblem
import com.example.zar.features.transactions.presentation.activity.EntryDisplay
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.transactions.presentation.activity.screen.InfoBanner
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.DateRow
import com.example.zar.features.transactions.presentation.manual.screen.FieldError
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundFormUiState
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundFormViewModel
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundMessage
import java.time.LocalDate

/**
 * Link a refund to a purchase (Figma Z60, with Z61 for an amount that is too high). The refund goes back to
 * the purchase's own account and is never counted as income; it can't add up to more than the purchase.
 */
@Composable
fun RefundFormScreen(
    onDone: () -> Unit,
    viewModel: RefundFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    RefundFormBody(
        state = state,
        actions = RefundActions(
            onClose = onDone,
            onAmount = viewModel::onAmountChange,
            onDate = viewModel::onDateSelect,
            onNote = viewModel::onNoteChange,
            onSave = viewModel::save,
        ),
    )
}

internal class RefundActions(
    val onClose: () -> Unit,
    val onAmount: (String) -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun RefundFormBody(state: RefundFormUiState, actions: RefundActions) {
    DarkStatusBarIcons()
    val colors = ZarTheme.colors
    val closeLabel = stringResource(R.string.manual_close)
    val summary = state.summary
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.refund_title), style = ZarType.headingSheet, color = colors.textPrimary)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = actions.onClose)
                    .semantics { contentDescription = closeLabel }
                    .testTag("refund-close"),
                Alignment.Center,
            ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
        }
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = colors.accentSelected)
            }
            state.notRefundable || summary == null -> Text(
                stringResource(R.string.refund_not_refundable),
                style = ZarType.bodyS,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp).padding(top = 48.dp).testTag("refund-not-refundable"),
            )
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val purchase = summary.purchase.current
                InfoBanner(
                    title = purchase.merchant?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.refund_banner_title, it) }
                        ?: stringResource(R.string.refund_banner_title_untitled),
                    body = stringResource(R.string.refund_banner_body, IncomeFormat.withCode(purchase.amount), EntryDisplay.dayMonth(purchase.date)),
                    icon = R.drawable.ic_ph_info,
                    modifier = Modifier.testTag("refund-banner"),
                )
                if (!state.isOnline) {
                    InfoBanner(
                        title = stringResource(R.string.manual_offline_title),
                        body = stringResource(R.string.manual_offline_body),
                        modifier = Modifier.testTag("refund-offline"),
                    )
                }
                RefundFields(state, actions)
                state.message?.let { FieldError(it.textRes(), Modifier.testTag("refund-message")) }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(
                text = stringResource(R.string.action_cancel),
                onClick = actions.onClose,
                modifier = Modifier.weight(1f).testTag("refund-cancel"),
                style = ZarButtonStyle.Soft,
            )
            ZarButton(
                text = stringResource(R.string.refund_save),
                onClick = actions.onSave,
                modifier = Modifier.weight(1f).testTag("refund-save"),
                enabled = !state.isSaving && !state.isLoading && !state.notRefundable && summary != null,
            )
        }
    }
}

@Composable
private fun RefundFields(state: RefundFormUiState, actions: RefundActions) {
    val colors = ZarTheme.colors
    val summary = state.summary ?: return
    val problems = state.problems

    ZarFormRow(stringResource(R.string.manual_field_amount)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = state.amount,
                onValueChange = actions.onAmount,
                placeholder = "0",
                textStyle = ZarType.amountM,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).testTag("refund-amount"),
            )
            Row(
                Modifier.padding(start = 8.dp).clip(ZarShapes.sm).background(colors.subtle).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.currencyCode, style = ZarType.labelChip, color = colors.textSecondary, modifier = Modifier.testTag("refund-currency"))
                ZarIcon(R.drawable.ic_ph_lock, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
    // The helper line says how much can be refunded; a too-high amount swaps it for the error in red.
    val remaining = IncomeFormat.withCode(summary.remaining)
    val refunded = IncomeFormat.withCode(summary.refunded)
    val tooHigh = RefundProblem.AMOUNT_TOO_HIGH in problems
    val invalidAmount = RefundProblem.AMOUNT_MISSING in problems || RefundProblem.AMOUNT_INVALID in problems
    Text(
        when {
            tooHigh && summary.hasRefunds -> stringResource(R.string.refund_error_too_high_some, remaining, refunded)
            tooHigh -> stringResource(R.string.refund_error_too_high_none, remaining)
            invalidAmount -> stringResource(R.string.manual_error_amount)
            summary.hasRefunds -> stringResource(R.string.refund_helper_some, remaining, refunded)
            else -> stringResource(R.string.refund_helper_none, remaining)
        },
        style = ZarType.bodyCaption,
        color = if (tooHigh || invalidAmount) colors.danger else colors.textSecondary,
        modifier = Modifier.testTag(if (tooHigh || invalidAmount) "refund-error-amount" else "refund-helper"),
    )
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_account)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.context?.account?.name ?: stringResource(R.string.activity_none),
                style = ZarType.bodyM,
                color = colors.textPrimary,
                modifier = Modifier.testTag("refund-account"),
            )
            Text(stringResource(R.string.refund_account_same), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
    ZarDivider()

    DateRow(state.date, actions.onDate)
    FieldError(if (RefundProblem.DATE_IN_FUTURE in problems) R.string.manual_error_date_future else null, Modifier.testTag("refund-error-date"))
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_note)) {
        TextInput(
            value = state.note,
            onValueChange = actions.onNote,
            placeholder = stringResource(R.string.refund_note_hint),
            modifier = Modifier.weight(1f).testTag("refund-note"),
        )
    }
    FieldError(if (RefundProblem.NOTE_TOO_LONG in problems) R.string.refund_error_note_too_long else null, Modifier.testTag("refund-error-note"))
}

private fun RefundMessage.textRes(): Int = when (this) {
    RefundMessage.NOT_SIGNED_IN -> R.string.manual_message_not_signed_in
    RefundMessage.ALREADY_VOIDED -> R.string.refund_message_voided
    RefundMessage.SAVED_FIRST -> R.string.refund_message_race
    RefundMessage.FAILED -> R.string.refund_message_failed
}
