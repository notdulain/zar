package com.example.zar.features.capture.presentation.screen

import com.example.zar.core.presentation.motion.rememberRiseInOnFirstVisit
import com.example.zar.core.presentation.motion.riseIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonSize
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.capture.domain.BankCompatibility
import com.example.zar.features.capture.domain.CompatibilityStatus
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.review.DuplicateMatch
import com.example.zar.features.capture.domain.review.MatchStrength
import com.example.zar.features.capture.domain.review.TransferMatch
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.presentation.viewmodel.CaptureMessage
import com.example.zar.features.capture.presentation.viewmodel.CaptureReviewUiState
import com.example.zar.features.capture.presentation.viewmodel.CaptureSettlement
import com.example.zar.features.capture.presentation.viewmodel.SettleKind
import com.example.zar.features.capture.presentation.viewmodel.SettleStage
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
    CaptureContent(state, onBack, onReview, onAddManually, viewModel::onTextChange, { viewModel.read() }) { AutoCaptureSection(onAddManually) }
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
    // The status and paste cards rise in one after another on the first visit only.
    val play = rememberRiseInOnFirstVisit(loaded = true)
    CatchUpScaffold(stringResource(R.string.capture_title), null, onBack, stringResource(R.string.action_back), Modifier.imePadding()) {
        ZarCard(Modifier.fillMaxWidth().riseIn(0, play)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarEyebrow(stringResource(R.string.capture_health_label))
                Text(state.health?.lastCapturedAt?.let { stringResource(R.string.capture_health_last, CAPTURED_AT.format(it)) }
                    ?: stringResource(R.string.capture_health_never), style = ZarType.headingSheet)
                Detail(pluralStringResource(R.plurals.capture_waiting, state.health?.waitingForReview ?: 0, state.health?.waitingForReview ?: 0))
                Detail(stringResource(R.string.capture_health_caption))
            }
        }
        autoCapture()
        ZarCard(Modifier.fillMaxWidth().riseIn(1, play)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarEyebrow(stringResource(R.string.capture_paste_label))
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
            ZarEyebrow(stringResource(R.string.capture_waiting_label), Modifier.padding(start = 12.dp, top = 8.dp))
            state.pending.forEach { draft -> PendingDraftCard(draft) { onReview(draft.id.value) } }
        }
        ZarEyebrow(stringResource(R.string.capture_supported_label), Modifier.padding(start = 12.dp, top = 8.dp))
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

/** What the review screen can do once a message may already be counted. The defaults keep fixed-data tests short. */
internal data class CaptureSettleActions(
    val onTransfer: () -> Unit = {},
    val onTransferChoice: (TransactionId) -> Unit = {},
    val onLinkTransfer: () -> Unit = {},
    val onSamePayment: () -> Unit = {},
    val onKeepApart: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUndoKeepApart: () -> Unit = {},
)

@Composable
fun CaptureReviewScreen(onDone: () -> Unit, viewModel: CaptureReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    val settle = CaptureSettleActions(
        viewModel::onTransferSelect, viewModel::onTransferChoice, viewModel::onLinkTransfer, viewModel::onSamePayment,
        viewModel::onKeepApart, viewModel::onUndo, viewModel::onFinish, viewModel::onRetry, viewModel::onUndoKeepApart,
    )
    CaptureReviewContent(state, viewModel::onFinish, viewModel::onAccountSelect, viewModel::onTypeSelect,
        viewModel::onSpendingClassSelect, viewModel::confirm, viewModel::discard, settle)
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
    settle: CaptureSettleActions = CaptureSettleActions(),
) {
    BackHandler(state.isSaving || state.settlement?.stage == SettleStage.SAVING) { }
    // Leaving while an answer is still waiting writes it first, so going back never loses a choice.
    BackHandler(state.settlement != null && state.settlement.stage != SettleStage.SAVING) { settle.onFinish() }
    val proposal = state.proposal
    val settlement = state.settlement
    if (settlement != null && proposal != null && state.draft != null) {
        SettledReview(state, settlement, settle)
        return
    }
    val editable = !state.isSaving && !state.confirmationStarted
    val duplicate = state.duplicates.firstOrNull()
    CatchUpScaffold(stringResource(R.string.capture_review_title), null, { if (!state.isSaving) onBack() }, stringResource(R.string.action_back),
        actions = if (proposal != null && state.draft != null) {{
            state.message?.let { Message(it, review = true) }
            when {
                state.asTransfer -> {
                    ZarButton(stringResource(R.string.capture_transfer_link), settle.onLinkTransfer,
                        Modifier.fillMaxWidth().testTag("capture-link-transfer"), enabled = editable && state.transferMatches.any { it.leg.id == state.transferChoice })
                    ZarButton(stringResource(R.string.capture_review_discard), onDiscard, Modifier.fillMaxWidth().testTag("capture-discard"),
                        style = ZarButtonStyle.Soft, enabled = editable)
                }
                duplicate != null -> {
                    ZarButton(stringResource(R.string.capture_dup_same), settle.onSamePayment, Modifier.fillMaxWidth().testTag("capture-same-payment"), enabled = editable)
                    ZarButton(stringResource(R.string.capture_dup_apart), settle.onKeepApart, Modifier.fillMaxWidth().testTag("capture-keep-apart"),
                        style = ZarButtonStyle.Soft, enabled = editable)
                }
                else -> {
                    ZarButton(stringResource(if (state.isSaving) R.string.capture_saving else R.string.capture_review_confirm), onConfirm,
                        Modifier.fillMaxWidth().testTag("capture-confirm"), enabled = !state.isSaving)
                    ZarButton(stringResource(R.string.capture_review_discard), onDiscard, Modifier.fillMaxWidth().testTag("capture-discard"),
                        style = ZarButtonStyle.Soft, enabled = editable)
                }
            }
        }} else null,
    ) {
        if (state.isLoading) { Detail(stringResource(R.string.home_loading)); return@CatchUpScaffold }
        state.message?.takeIf { proposal == null }?.let { Message(it, review = true) }
        if (state.draft == null || proposal == null) { Detail(stringResource(R.string.capture_review_gone)); return@CatchUpScaffold }
        ZarCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarEyebrow(proposal.date?.let(IncomeFormat::fullDate).orEmpty())
                Text(proposal.amount?.display().orEmpty(), style = ZarType.amountL)
                Text(proposal.merchant.orEmpty(), style = ZarType.bodyM)
                Detail(stringResource(R.string.capture_review_intro))
            }
        }
        if (state.twins.isNotEmpty()) Detail(stringResource(R.string.capture_twins))
        if (state.matchesUnavailable) Detail(stringResource(R.string.capture_dup_unavailable), true)
        duplicate?.let { DuplicateCard(it, state) }
        if (state.keptApart.isNotEmpty() && !state.asTransfer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.capture_kept_apart), Modifier.weight(1f), style = ZarType.bodyS)
                ZarButton(stringResource(R.string.capture_undo), settle.onUndoKeepApart,
                    Modifier.testTag("capture-undo-apart"), enabled = editable,
                    style = ZarButtonStyle.Soft, size = ZarButtonSize.Medium)
            }
        }
        ZarCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarEyebrow(stringResource(R.string.capture_review_account))
                if (state.matchingAccounts.isEmpty()) Detail(stringResource(R.string.capture_review_no_account, proposal.amount?.currency?.code.orEmpty()), true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.matchingAccounts.forEach { account ->
                        CaptureChip("${account.name} · ${account.currency.code}", account.id == state.accountId, editable) { onAccount(account.id) }
                    }
                }
                if (state.showErrors && state.accountId == null) Detail(stringResource(R.string.capture_review_pick_account), true)
                if (DraftField.ACCOUNT in proposal.uncertainFields && state.accountId != null && state.accountId == proposal.accountId) {
                    Detail(stringResource(R.string.capture_review_account_matched))
                }
                ZarEyebrow(stringResource(R.string.capture_review_type))
                if (proposal.type == null || DraftField.TYPE in proposal.uncertainFields) Detail(stringResource(R.string.capture_review_type_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureChip(stringResource(R.string.capture_type_expense), !state.asTransfer && state.type == TransactionType.EXPENSE, editable, Modifier.testTag("capture-type-expense")) { onType(TransactionType.EXPENSE) }
                    CaptureChip(stringResource(R.string.capture_type_income), !state.asTransfer && state.type == TransactionType.INCOME, editable, Modifier.testTag("capture-type-income")) { onType(TransactionType.INCOME) }
                    CaptureChip(stringResource(R.string.capture_type_transfer), state.asTransfer, editable, Modifier.testTag("capture-type-transfer")) { settle.onTransfer() }
                }
                Detail(stringResource(R.string.capture_review_other_type))
                if (state.showErrors && state.type == null && !state.asTransfer) Detail(stringResource(R.string.capture_review_pick_type), true)
                if (!state.asTransfer && state.type == TransactionType.EXPENSE) {
                    ZarEyebrow(stringResource(R.string.capture_review_counts_as))
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
        if (state.asTransfer) TransferPicker(state, editable, settle.onTransferChoice)
        // Linking adds no entry, so the line about saving one would mislead.
        if (!state.asTransfer) Detail(stringResource(R.string.capture_connection_hint))
    }
}

@Composable
private fun accountName(state: CaptureReviewUiState, id: AccountId): String = state.accounts.firstOrNull { it.id == id }?.name.orEmpty()

@Composable
private fun entryName(saved: Transaction): String = saved.merchant ?: saved.note
    ?: stringResource(if (saved.type == TransactionType.INCOME) R.string.capture_type_income else R.string.capture_type_expense)

/** The entry this message may be a second report of. It only asks: the two buttons below decide. */
@Composable
private fun DuplicateCard(match: DuplicateMatch, state: CaptureReviewUiState) {
    val saved = match.entry.current
    ZarCard(Modifier.fillMaxWidth().testTag("capture-duplicate")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZarEyebrow(
                stringResource(if (match.strength == MatchStrength.LIKELY) R.string.capture_dup_label_likely else R.string.capture_dup_label_possible),
                color = ZarTheme.colors.textAttention,
            )
            ZarFormRow(stringResource(R.string.capture_dup_have), labelWidth = 112.dp) { Text(entryName(saved), style = ZarType.bodyM) }
            ZarFormRow(stringResource(R.string.capture_dup_amount), labelWidth = 112.dp) { Text(saved.amount.display(), style = ZarType.bodyM) }
            ZarFormRow(stringResource(R.string.capture_dup_date), labelWidth = 112.dp) { Text(IncomeFormat.shortDate(saved.date), style = ZarType.bodyM) }
            accountName(state, saved.accountId).takeIf { it.isNotEmpty() }?.let { name ->
                ZarFormRow(stringResource(R.string.capture_dup_account), labelWidth = 112.dp) { Text(name, style = ZarType.bodyM) }
            }
            Detail(stringResource(R.string.capture_dup_explain))
        }
    }
}

@Composable
private fun transferTitle(match: TransferMatch, state: CaptureReviewUiState): String {
    val leg = accountName(state, match.leg.current.accountId)
    val other = match.other?.let { accountName(state, it.current.accountId) }.orEmpty()
    return when {
        other.isEmpty() -> leg
        match.leg.current.type == TransactionType.TRANSFER_OUT -> stringResource(R.string.capture_transfer_option, leg, other)
        else -> stringResource(R.string.capture_transfer_option, other, leg)
    }
}

/** The transfers already in the ledger that this message could be the bank's record of. Linking posts no money. */
@Composable
private fun TransferPicker(state: CaptureReviewUiState, editable: Boolean, onChoice: (TransactionId) -> Unit) {
    val matches = state.transferMatches
    ZarEyebrow(stringResource(R.string.capture_transfer_which), Modifier.padding(start = 12.dp, top = 4.dp))
    if (matches.isEmpty()) {
        Detail(stringResource(R.string.capture_transfer_none), true)
        return
    }
    ZarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            matches.forEach { match ->
                val id = match.leg.id
                Row(
                    Modifier.fillMaxWidth().selectable(selected = id == state.transferChoice, enabled = editable, role = Role.RadioButton) { onChoice(id) }.testTag("capture-transfer-${id.value}"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = id == state.transferChoice, onClick = null, enabled = editable)
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(transferTitle(match, state), style = ZarType.bodyM)
                        Detail(stringResource(R.string.capture_transfer_option_detail, match.leg.current.amount.display(), IncomeFormat.shortDate(match.leg.current.date)))
                    }
                }
            }
        }
    }
    val amount = state.proposal?.amount?.display().orEmpty()
    Detail(stringResource(if (state.transferChoice == null) R.string.capture_transfer_pick else R.string.capture_transfer_explain, amount))
}

/** After "same payment" or "link": what was decided, how to take it back, and whether it has been written. */
@Composable
private fun SettledReview(state: CaptureReviewUiState, settlement: CaptureSettlement, settle: CaptureSettleActions) {
    val stage = settlement.stage
    val transfer = settlement.kind == SettleKind.TRANSFER
    val saved = settlement.target.current
    val busy = stage == SettleStage.SAVING
    CatchUpScaffold(stringResource(R.string.capture_review_title), null, { if (!busy) settle.onFinish() }, stringResource(R.string.action_back),
        actions = {
            when (stage) {
                SettleStage.FAILED -> {
                    Detail(stringResource(R.string.capture_settled_failed), true)
                    ZarButton(stringResource(R.string.capture_retry), settle.onRetry, Modifier.fillMaxWidth().testTag("capture-retry"))
                    ZarButton(stringResource(R.string.capture_undo_choice), settle.onUndo, Modifier.fillMaxWidth().testTag("capture-undo-choice"), style = ZarButtonStyle.Soft)
                }
                else -> {
                    if (stage == SettleStage.UNDO_WINDOW) {
                        ZarCard(Modifier.fillMaxWidth().testTag("capture-undo-toast")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (transfer) R.string.capture_toast_linked else R.string.capture_toast_same), style = ZarType.bodyM, modifier = Modifier.weight(1f))
                                ZarButton(stringResource(R.string.capture_undo), settle.onUndo, Modifier.testTag("capture-undo"), style = ZarButtonStyle.Soft, size = ZarButtonSize.Medium)
                            }
                        }
                    }
                    ZarButton(stringResource(if (busy) R.string.capture_saving else R.string.capture_done), settle.onFinish, Modifier.fillMaxWidth().testTag("capture-done"), enabled = !busy)
                }
            }
        },
    ) {
        ZarCard(Modifier.fillMaxWidth().testTag("capture-settled")) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarEyebrow(stringResource(if (transfer) R.string.capture_settled_linked_title else R.string.capture_settled_same_title))
                if (transfer) {
                    val match = TransferMatch(settlement.target, settlement.other)
                    ZarFormRow(stringResource(R.string.capture_settled_transfer), labelWidth = 112.dp) { Text(transferTitle(match, state), style = ZarType.bodyM) }
                } else {
                    ZarFormRow(stringResource(R.string.capture_settled_entry), labelWidth = 112.dp) { Text(entryName(saved), style = ZarType.bodyM) }
                }
                ZarFormRow(stringResource(R.string.capture_settled_amount), labelWidth = 112.dp) { Text(saved.amount.display(), style = ZarType.bodyM) }
                if (!transfer) {
                    accountName(state, saved.accountId).takeIf { it.isNotEmpty() }?.let { name ->
                        ZarFormRow(stringResource(R.string.capture_settled_account), labelWidth = 112.dp) { Text(name, style = ZarType.bodyM) }
                    }
                }
                ZarFormRow(stringResource(R.string.capture_settled_evidence), labelWidth = 112.dp) { Text(stringResource(R.string.capture_settled_evidence_value), style = ZarType.bodyM) }
            }
        }
        Detail(stringResource(if (transfer) R.string.capture_settled_linked_explain else R.string.capture_settled_same_explain))
        if (stage == SettleStage.QUEUED) Detail(stringResource(R.string.capture_settled_queued), true)
    }
}

@Composable
private fun CaptureChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    FilterChip(selected, onClick, label = { Text(label, style = ZarType.labelChip) }, modifier = modifier, enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface, labelColor = colors.textPrimary,
            selectedContainerColor = colors.selectedTint, selectedLabelColor = colors.textSelected))
}
