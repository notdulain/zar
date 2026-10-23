package com.example.zar.features.capture.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.capture.domain.BankCompatibility
import com.example.zar.features.capture.domain.CompatibilityStatus
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.presentation.viewmodel.CaptureMessage
import com.example.zar.features.capture.presentation.viewmodel.CaptureReviewUiState
import com.example.zar.features.capture.presentation.viewmodel.CaptureReviewViewModel
import com.example.zar.features.capture.presentation.viewmodel.CaptureUiState
import com.example.zar.features.capture.presentation.viewmodel.CaptureViewModel
import com.example.zar.features.catchup.presentation.screen.CatchUpScaffold
import com.example.zar.features.freelance.presentation.screen.display
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CAPTURED_AT = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(ZarTime.ZONE)

@Composable
private fun Detail(text: String, warning: Boolean = false) {
    Text(text, style = ZarType.bodyS, color = if (warning) MaterialTheme.colorScheme.error else ZarTheme.colors.textSecondary)
}

@Composable
private fun Message(message: CaptureMessage, review: Boolean = false) = Detail(stringResource(when (message) {
    CaptureMessage.NOT_SIGNED_IN -> R.string.capture_message_not_signed_in
    CaptureMessage.CURRENCY -> R.string.capture_message_currency
    CaptureMessage.INVALID -> R.string.capture_message_invalid
    CaptureMessage.FAILED -> if (review) R.string.capture_review_failed else R.string.capture_message_failed
}), warning = true)

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onAddManually: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }
    CaptureContent(state, onBack, onReview, onAddManually, viewModel::onTextChange, { viewModel.read() }) { AutoCaptureSection() }
}

/** The production screen, also rendered with fixed data by the native UI regressions. */
@Composable
internal fun CaptureContent(
    state: CaptureUiState,
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onAddManually: () -> Unit,
    onTextChange: (String) -> Unit,
    onRead: () -> Unit,
    autoCapture: @Composable () -> Unit = {},
) {
    CatchUpScaffold(stringResource(R.string.capture_title), null, onBack, stringResource(R.string.action_back), Modifier.imePadding()) {
        ZarCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarMonoLabel(stringResource(R.string.capture_health_label))
                Text(state.health?.lastCapturedAt?.let { stringResource(R.string.capture_health_last, CAPTURED_AT.format(it)) }
                    ?: stringResource(R.string.capture_health_never), style = ZarType.headingSheet)
                Detail(pluralStringResource(R.plurals.capture_waiting, state.health?.waitingForReview ?: 0, state.health?.waitingForReview ?: 0))
                Detail(stringResource(R.string.capture_health_caption))
            }
        }
        autoCapture()
        ZarCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarMonoLabel(stringResource(R.string.capture_paste_label))
                if (state.cameFromShare) Detail(stringResource(R.string.capture_from_share))
                OutlinedTextField(
                    value = state.text, onValueChange = onTextChange, enabled = !state.isReading,
                    label = { Text(stringResource(R.string.capture_paste_field)) }, minLines = 3,
                    textStyle = ZarType.bodyS, shape = com.example.zar.core.presentation.theme.ZarShapes.sm,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZarTheme.colors.borderSelected,
                        unfocusedBorderColor = ZarTheme.colors.borderDefault, cursorColor = ZarTheme.colors.textPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("capture-text"),
                )
                Detail(stringResource(R.string.capture_paste_hint))
                ZarButton(stringResource(if (state.isReading) R.string.capture_reading else R.string.capture_read), onRead,
                    Modifier.fillMaxWidth().testTag("capture-read"), enabled = !state.isReading && state.text.isNotBlank())
                state.outcome?.let { OutcomeText(it) }
                state.message?.let { Message(it) }
            }
        }
        ZarButton(stringResource(R.string.capture_add_manually), onAddManually, Modifier.fillMaxWidth(), style = ZarButtonStyle.Outline)
        if (state.pending.isNotEmpty()) {
            ZarMonoLabel(stringResource(R.string.capture_waiting_label), Modifier.padding(start = 12.dp, top = 8.dp))
            state.pending.forEach { draft -> PendingDraftCard(draft) { onReview(draft.id.value) } }
        }
        ZarMonoLabel(stringResource(R.string.capture_supported_label), Modifier.padding(start = 12.dp, top = 8.dp))
        Detail(stringResource(R.string.capture_supported_intro))
        BankCompatibility.formats.forEach { format ->
            ZarCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(format.bank, style = ZarType.bodyM)
                    Detail(listOfNotNull(format.messages, format.note).joinToString(" · "))
                    Text(stringResource(when (format.status) {
                        CompatibilityStatus.SUPPORTED -> R.string.capture_status_supported
                        CompatibilityStatus.NEEDS_REVIEW -> R.string.capture_status_review
                        CompatibilityStatus.UNKNOWN -> R.string.capture_status_unknown
                    }), style = ZarType.labelChip, color = if (format.status == CompatibilityStatus.SUPPORTED) ZarTheme.colors.textIncome else ZarTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun OutcomeText(outcome: CaptureOutcome) {
    val text = when (outcome) {
        is CaptureOutcome.Drafted -> stringResource(R.string.capture_drafted, outcome.parsed.amount.display(), IncomeFormat.shortDate(outcome.parsed.date))
        is CaptureOutcome.Ignored -> stringResource(when (outcome.reason) {
            SmsIgnoreReason.OTP -> R.string.capture_ignored_otp
            SmsIgnoreReason.DECLINED -> R.string.capture_ignored_declined
            SmsIgnoreReason.NOT_A_TRANSACTION -> R.string.capture_ignored_notice
        })
        CaptureOutcome.Unrecognised -> stringResource(R.string.capture_unrecognised)
        CaptureOutcome.Empty -> stringResource(R.string.capture_empty)
        CaptureOutcome.AlreadyReviewed -> stringResource(R.string.capture_already_reviewed)
    }
    Detail(text, outcome == CaptureOutcome.Unrecognised || outcome == CaptureOutcome.Empty)
}

@Composable
private fun PendingDraftCard(draft: Draft, onClick: () -> Unit) {
    val proposal = draft.proposals.singleOrNull() as? DraftProposal.ActualTransaction
    ZarCard(Modifier.fillMaxWidth().testTag("capture-draft-${draft.id.value}"), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(proposal?.merchant ?: stringResource(R.string.capture_review_title), style = ZarType.bodyM)
            Detail(listOfNotNull(proposal?.date?.let(IncomeFormat::shortDate),
                stringResource(if (proposal?.type == TransactionType.EXPENSE && com.example.zar.core.domain.model.DraftField.TYPE !in proposal.uncertainFields)
                    R.string.capture_type_expense else R.string.capture_type_unknown)).joinToString(" · "))
            proposal?.amount?.let { Text(it.display(), style = ZarType.amountS) }
        }
    }
}

@Composable
fun CaptureReviewScreen(onDone: () -> Unit, viewModel: CaptureReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    CaptureReviewContent(state, onDone, viewModel::onAccountSelect, viewModel::onTypeSelect,
        viewModel::onSpendingClassSelect, viewModel::confirm, viewModel::discard)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaptureReviewContent(
    state: CaptureReviewUiState,
    onBack: () -> Unit,
    onAccount: (AccountId) -> Unit,
    onType: (TransactionType) -> Unit,
    onClass: (SpendingClass) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    BackHandler(state.isSaving) { }
    val proposal = state.proposal
    val editable = !state.isSaving && !state.confirmationStarted
    CatchUpScaffold(stringResource(R.string.capture_review_title), null, { if (!state.isSaving) onBack() }, stringResource(R.string.action_back),
        actions = if (proposal != null && state.draft != null) {{
            state.message?.let { Message(it, review = true) }
            ZarButton(stringResource(if (state.isSaving) R.string.capture_saving else R.string.capture_review_confirm), onConfirm,
                Modifier.fillMaxWidth().testTag("capture-confirm"), enabled = !state.isSaving)
            ZarButton(stringResource(R.string.capture_review_discard), onDiscard, Modifier.fillMaxWidth().testTag("capture-discard"),
                style = ZarButtonStyle.Soft, enabled = editable)
        }} else null,
    ) {
        if (state.isLoading) { Detail(stringResource(R.string.home_loading)); return@CatchUpScaffold }
        state.message?.takeIf { proposal == null }?.let { Message(it, review = true) }
        if (state.draft == null || proposal == null) { Detail(stringResource(R.string.capture_review_gone)); return@CatchUpScaffold }
        ZarCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarMonoLabel(proposal.date?.let(IncomeFormat::fullDate).orEmpty())
                Text(proposal.amount?.display().orEmpty(), style = ZarType.amountL)
                Text(proposal.merchant.orEmpty(), style = ZarType.bodyM)
                Detail(stringResource(R.string.capture_review_intro))
            }
        }
        ZarCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarMonoLabel(stringResource(R.string.capture_review_account))
                if (state.matchingAccounts.isEmpty()) Detail(stringResource(R.string.capture_review_no_account, proposal.amount?.currency?.code.orEmpty()), true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.matchingAccounts.forEach { account ->
                        CaptureChip("${account.name} · ${account.currency.code}", account.id == state.accountId, editable) { onAccount(account.id) }
                    }
                }
                if (state.showErrors && state.accountId == null) Detail(stringResource(R.string.capture_review_pick_account), true)
                ZarMonoLabel(stringResource(R.string.capture_review_type))
                if (proposal.type == null || com.example.zar.core.domain.model.DraftField.TYPE in proposal.uncertainFields) Detail(stringResource(R.string.capture_review_type_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureChip(stringResource(R.string.capture_type_expense), state.type == TransactionType.EXPENSE, editable) { onType(TransactionType.EXPENSE) }
                    CaptureChip(stringResource(R.string.capture_type_income), state.type == TransactionType.INCOME, editable) { onType(TransactionType.INCOME) }
                }
                Detail(stringResource(R.string.capture_review_other_type))
                if (state.showErrors && state.type == null) Detail(stringResource(R.string.capture_review_pick_type), true)
                if (state.type == TransactionType.EXPENSE) {
                    ZarMonoLabel(stringResource(R.string.capture_review_counts_as))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(SpendingClass.COMMITTED to R.string.capture_class_committed,
                            SpendingClass.DISCRETIONARY to R.string.capture_class_discretionary,
                            SpendingClass.UNKNOWN to R.string.capture_class_unknown).forEach { (value, label) ->
                            CaptureChip(stringResource(label), state.spendingClass == value, editable) { onClass(value) }
                        }
                    }
                }
            }
        }
        Detail(stringResource(R.string.capture_connection_hint))
    }
}

@Composable
private fun CaptureChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    FilterChip(selected, onClick, enabled = enabled, label = { Text(label, style = ZarType.labelChip) },
        colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface, labelColor = colors.textPrimary,
            selectedContainerColor = colors.selectedTint, selectedLabelColor = colors.textSelected))
}
