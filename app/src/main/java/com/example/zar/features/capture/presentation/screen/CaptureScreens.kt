package com.example.zar.features.capture.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.capture.domain.BankCompatibility
import com.example.zar.features.capture.domain.CompatibilityStatus
import com.example.zar.features.capture.domain.model.SmsIgnoreReason
import com.example.zar.features.capture.domain.usecase.CaptureOutcome
import com.example.zar.features.capture.presentation.viewmodel.CaptureMessage
import com.example.zar.features.capture.presentation.viewmodel.CaptureReviewViewModel
import com.example.zar.features.capture.presentation.viewmodel.CaptureViewModel
import com.example.zar.features.freelance.presentation.screen.CenteredText
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.freelance.presentation.screen.Loading
import com.example.zar.features.freelance.presentation.screen.SectionLabel
import com.example.zar.features.freelance.presentation.screen.display
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.SummaryHeaderCard
import com.example.zar.features.transactions.presentation.income.screen.ZarMono
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CAPTURED_AT = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(ZarTime.ZONE)

private fun CaptureMessage.textRes(): Int = when (this) {
    CaptureMessage.NOT_SIGNED_IN -> R.string.capture_message_not_signed_in
    CaptureMessage.CURRENCY -> R.string.capture_message_currency
    CaptureMessage.FAILED -> R.string.capture_message_failed
}

@Composable
private fun Detail(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = ZarMono,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Capture -------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onAddManually: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DarkStatusBarIcons()
    // Text shared while this screen is already open.
    LaunchedEffect(Unit) { viewModel.takeShared() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val health = uiState.health
            SummaryHeaderCard(
                label = stringResource(R.string.capture_health_label),
                amount = health?.lastCapturedAt?.let { stringResource(R.string.capture_health_last, CAPTURED_AT.format(it)) }
                    ?: stringResource(R.string.capture_health_never),
                caption = stringResource(R.string.capture_health_caption),
            )
            Detail(stringResource(R.string.capture_sms_auto))

            SectionLabel(stringResource(R.string.capture_paste_label), Modifier.padding(top = 8.dp))
            if (uiState.cameFromShare) Detail(stringResource(R.string.capture_from_share))
            OutlinedTextField(
                value = uiState.text,
                onValueChange = viewModel::onTextChange,
                label = { Text(stringResource(R.string.capture_paste_field)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Detail(stringResource(R.string.capture_paste_hint))
            Button(onClick = { viewModel.read() }, enabled = !uiState.isReading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.capture_read))
            }
            uiState.outcome?.let { OutcomeText(it) }
            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }
            // Manual entry always works, whatever capture can or can't read.
            OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.capture_add_manually)) }

            if (uiState.pending.isNotEmpty()) {
                HorizontalDivider()
                SectionLabel(
                    pluralStringResource(R.plurals.capture_waiting, uiState.pending.size, uiState.pending.size),
                    Modifier.padding(top = 4.dp),
                )
                uiState.pending.forEach { draft -> PendingDraftCard(draft, onClick = { onReview(draft.id.value) }) }
            }

            HorizontalDivider()
            SectionLabel(stringResource(R.string.capture_supported_label), Modifier.padding(top = 4.dp))
            Detail(stringResource(R.string.capture_supported_intro))
            BankCompatibility.formats.forEach { format ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(format.bank, style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                            Detail(listOfNotNull(format.messages, format.note).joinToString(" · "))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                when (format.status) {
                                    CompatibilityStatus.SUPPORTED -> R.string.capture_status_supported
                                    CompatibilityStatus.NEEDS_REVIEW -> R.string.capture_status_review
                                    CompatibilityStatus.UNKNOWN -> R.string.capture_status_unknown
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ZarMono,
                            color = if (format.status == CompatibilityStatus.UNKNOWN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeText(outcome: CaptureOutcome) {
    val (text, warning) = when (outcome) {
        is CaptureOutcome.Drafted -> stringResource(
            R.string.capture_drafted,
            outcome.parsed.amount.display(),
            IncomeFormat.shortDate(outcome.parsed.date),
        ) to false
        is CaptureOutcome.Ignored -> stringResource(
            when (outcome.reason) {
                SmsIgnoreReason.OTP -> R.string.capture_ignored_otp
                SmsIgnoreReason.DECLINED -> R.string.capture_ignored_declined
                SmsIgnoreReason.NOT_A_TRANSACTION -> R.string.capture_ignored_notice
            },
        ) to false
        CaptureOutcome.Unrecognised -> stringResource(R.string.capture_unrecognised) to true
        CaptureOutcome.Empty -> stringResource(R.string.capture_empty) to true
    }
    Text(text, fontFamily = ZarMono, style = MaterialTheme.typography.bodyMedium,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun PendingDraftCard(draft: Draft, onClick: () -> Unit) {
    val proposal = draft.proposals.singleOrNull() as? DraftProposal.ActualTransaction
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(proposal?.merchant ?: stringResource(R.string.capture_title), style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                Detail(
                    listOfNotNull(
                        proposal?.date?.let(IncomeFormat::shortDate),
                        when (proposal?.type) {
                            TransactionType.EXPENSE -> stringResource(R.string.capture_type_expense)
                            TransactionType.INCOME -> stringResource(R.string.capture_type_income)
                            else -> stringResource(R.string.capture_type_unknown)
                        },
                    ).joinToString(" · "),
                )
            }
            proposal?.amount?.let { Text(it.display(), style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono) }
        }
    }
}

// --- Review --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureReviewScreen(
    onDone: () -> Unit,
    viewModel: CaptureReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DarkStatusBarIcons()
    LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_review_title)) },
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        val proposal = uiState.proposal
        if (uiState.isLoading) {
            Loading(Modifier.padding(innerPadding))
            return@Scaffold
        }
        if (uiState.draft == null || proposal == null) {
            CenteredText(stringResource(R.string.capture_review_gone), Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryHeaderCard(
                label = proposal.date?.let(IncomeFormat::fullDate).orEmpty(),
                amount = proposal.amount?.display().orEmpty(),
                caption = proposal.merchant.orEmpty(),
            )
            Detail(stringResource(R.string.capture_review_intro))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.capture_review_account))
                if (uiState.matchingAccounts.isEmpty()) {
                    Detail(stringResource(R.string.capture_review_no_account, proposal.amount?.currency?.code.orEmpty()), warning = true)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.matchingAccounts.forEach { account ->
                        FilterChip(
                            selected = account.id == uiState.accountId,
                            onClick = { viewModel.onAccountSelect(account.id) },
                            label = { Text("${account.name} · ${account.currency.code}", fontFamily = ZarMono) },
                        )
                    }
                }
                if (uiState.showErrors && uiState.accountId == null) Detail(stringResource(R.string.capture_review_pick_account), warning = true)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.capture_review_type))
                if (proposal.type == null) Detail(stringResource(R.string.capture_review_type_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = uiState.type == TransactionType.EXPENSE, onClick = { viewModel.onTypeSelect(TransactionType.EXPENSE) },
                        label = { Text(stringResource(R.string.capture_type_expense), fontFamily = ZarMono) })
                    FilterChip(selected = uiState.type == TransactionType.INCOME, onClick = { viewModel.onTypeSelect(TransactionType.INCOME) },
                        label = { Text(stringResource(R.string.capture_type_income), fontFamily = ZarMono) })
                }
                Detail(stringResource(R.string.capture_review_other_type))
                if (uiState.showErrors && uiState.type == null) Detail(stringResource(R.string.capture_review_pick_type), warning = true)
            }

            if (uiState.type == TransactionType.EXPENSE) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(stringResource(R.string.capture_review_counts_as))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            SpendingClass.COMMITTED to R.string.capture_class_committed,
                            SpendingClass.DISCRETIONARY to R.string.capture_class_discretionary,
                            SpendingClass.UNKNOWN to R.string.capture_class_unknown,
                        ).forEach { (value, label) ->
                            FilterChip(selected = uiState.spendingClass == value, onClick = { viewModel.onSpendingClassSelect(value) },
                                label = { Text(stringResource(label), fontFamily = ZarMono) })
                        }
                    }
                }
            }

            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }
            Button(onClick = viewModel::confirm, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.capture_review_confirm))
            }
            TextButton(onClick = viewModel::discard, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.capture_review_discard), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
