package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.model.PaymentCandidate
import com.example.zar.features.bills.presentation.viewmodel.BillOccurrenceUiState
import com.example.zar.features.bills.presentation.viewmodel.BillOccurrenceViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.FieldError
import com.example.zar.features.transactions.presentation.manual.screen.TextInput

/**
 * Link the real expense to one due date, laid out like Figma's Z28 payment sheet: a summary, the
 * unlinked expenses that might match, and Cancel / Link payment.
 */
@Composable
fun BillPaymentScreen(
    onBack: () -> Unit,
    viewModel: BillOccurrenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BillPaymentContent(state, onBack, viewModel::link, viewModel::unlink, viewModel::onSelect, viewModel::onAmountChange)
}

@Composable
internal fun BillPaymentContent(
    state: BillOccurrenceUiState,
    onBack: () -> Unit,
    onLink: () -> Unit,
    onUnlink: (com.example.zar.features.bills.domain.model.LinkedPayment) -> Unit,
    onSelect: (PaymentCandidate) -> Unit,
    onAmountChange: (String) -> Unit,
) {
    LaunchedEffect(state.isLinked) { if (state.isLinked) onBack() }
    val colors = ZarTheme.colors
    val occurrence = state.occurrence
    StatusBarGlyphs(onDarkHero = false)
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                occurrence?.let { stringResource(R.string.bill_payment_title, BillFormat.dayMonth(it.dueDate)) }.orEmpty(),
                style = ZarType.headingSheet,
                color = colors.textPrimary,
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.bill_close), onClick = onBack),
                Alignment.Center,
            ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
        }
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
            occurrence == null || state.bill == null -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.bill_not_found), style = ZarType.bodyS, color = colors.textSecondary)
            }
            else -> Body(state, onUnlink, onSelect, onAmountChange, Modifier.weight(1f))
        }
        val canLink = occurrence != null && occurrence.progress.remaining.isPositive &&
            occurrence.progress.state != MatchState.CANCELLED && state.selected != null
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(stringResource(if (canLink) R.string.bill_cancel else R.string.bill_close), onBack, Modifier.weight(1f), ZarButtonStyle.Soft)
            if (canLink) {
                ZarButton(
                    stringResource(R.string.bill_payment_link),
                    onClick = onLink,
                    modifier = Modifier.weight(1f).testTag("bill-payment-link"),
                    enabled = !state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun Body(state: BillOccurrenceUiState, onUnlink: (com.example.zar.features.bills.domain.model.LinkedPayment) -> Unit, onSelect: (PaymentCandidate) -> Unit, onAmountChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    val occurrence = state.occurrence ?: return
    val progress = occurrence.progress
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Summary box: Expected, Matched so far, Due.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.subtle).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryLine(stringResource(R.string.bill_payment_expected), IncomeFormat.withCode(progress.expected.amount))
            SummaryLine(
                stringResource(R.string.bill_payment_matched_so_far),
                if (progress.matched.isPositive) IncomeFormat.withCode(progress.matched) else stringResource(R.string.bill_payment_nothing_yet),
            )
            SummaryLine(stringResource(R.string.bill_payment_due), BillFormat.weekdayDayMonth(occurrence.dueDate))
        }
        if (progress.state == MatchState.NOT_MATCHED) Note(stringResource(R.string.bill_payment_honest))

        if (state.payments.isNotEmpty()) {
            ZarMonoLabel(stringResource(R.string.bill_payment_linked))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, colors.borderDefault, RoundedCornerShape(14.dp))) {
                state.payments.forEachIndexed { index, payment ->
                    if (index > 0) ZarDivider()
                    val tx = payment.entry.current
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(IncomeFormat.withCode(payment.counted), style = ZarType.bodyM.copy(lineHeight = 19.sp), color = colors.textPrimary)
                            Text(candidateMeta(state, tx.accountId, tx.date, tx.merchant), style = ZarType.bodyS.copy(lineHeight = 18.sp), color = colors.textSecondary)
                        }
                        Text(
                            stringResource(R.string.bill_payment_remove),
                            style = ZarType.labelAction,
                            color = colors.textAttention,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !state.isSaving, role = Role.Button) { onUnlink(payment) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        when {
            progress.state == MatchState.CANCELLED -> Unit
            !progress.remaining.isPositive -> Note(stringResource(R.string.bill_payment_fully_matched))
            state.candidates.isEmpty() -> Note(stringResource(R.string.bill_payment_none_found, progress.expected.amount.currency.code))
            else -> {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZarMonoLabel(stringResource(R.string.bill_payment_candidates))
                    state.candidates.forEach { candidate ->
                        CandidateCard(state, candidate, selected = candidate.entry.id.value == state.selectedId, onClick = { onSelect(candidate) })
                    }
                }
                ZarFormRow(stringResource(R.string.bill_payment_amount)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextInput(
                            value = state.amount,
                            onValueChange = onAmountChange,
                            placeholder = "0",
                            textStyle = ZarType.amountS,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f).testTag("bill-payment-amount"),
                        )
                        ZarMonoLabel(progress.expected.amount.currency.code, Modifier.padding(start = 8.dp))
                    }
                }
                FieldError(
                    when {
                        BillProblem.AMOUNT_MISSING in state.problems -> R.string.bill_error_amount_missing
                        BillProblem.AMOUNT_INVALID in state.problems -> R.string.bill_error_amount_invalid
                        BillProblem.MORE_THAN_PAYMENT in state.problems -> R.string.bill_error_more_than_payment
                        BillProblem.MORE_THAN_REMAINING in state.problems -> R.string.bill_error_more_than_remaining
                        else -> null
                    },
                )
                Note(stringResource(R.string.bill_payment_only_unlinked))
            }
        }
        state.message?.let { FieldError(it.textRes()) }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = ZarType.bodyS.copy(lineHeight = 17.sp), color = colors.textSecondary)
        Text(value, style = ZarType.labelChip.copy(lineHeight = 17.sp), color = colors.textPrimary)
    }
}

@Composable
private fun candidateMeta(state: BillOccurrenceUiState, accountId: com.example.zar.core.domain.model.AccountId, date: java.time.LocalDate, merchant: String?): String =
    listOfNotNull(state.accountNames[accountId], BillFormat.dayMonth(date), merchant?.let { "“$it”" }).joinToString(" · ")

/** Z28 selectable row: a 1.5dp selected border with a filled check, otherwise a hairline and an empty circle. */
@Composable
private fun CandidateCard(state: BillOccurrenceUiState, candidate: PaymentCandidate, selected: Boolean, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val tx = candidate.entry.current
    val partial = candidate.available < tx.amount
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.borderSelected else colors.borderDefault, shape)
            .selectable(selected = selected, enabled = !state.isSaving, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("bill-candidate"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(IncomeFormat.withCode(tx.amount), style = ZarType.bodyM.copy(lineHeight = 19.sp), color = colors.textPrimary)
            val meta = candidateMeta(state, tx.accountId, tx.date, tx.merchant)
            Text(
                if (partial) stringResource(R.string.bill_payment_candidate_left, meta, IncomeFormat.withCode(candidate.available)) else meta,
                style = ZarType.bodyS.copy(lineHeight = 18.sp),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZarIcon(
            if (selected) R.drawable.ic_ph_check_circle else R.drawable.ic_ph_circle,
            size = 20.dp,
            tint = if (selected) colors.iconStrong else colors.iconMuted,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = ZarType.bodyS.copy(lineHeight = 19.sp), color = ZarTheme.colors.textSecondary)
}
