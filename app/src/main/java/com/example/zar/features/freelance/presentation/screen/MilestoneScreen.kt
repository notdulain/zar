package com.example.zar.features.freelance.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.features.freelance.domain.model.LinkedReceipt
import com.example.zar.features.freelance.domain.model.ReceiptCandidate
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceMessage
import com.example.zar.features.freelance.presentation.viewmodel.MilestoneUiState
import com.example.zar.features.freelance.presentation.viewmodel.MilestoneViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.SummaryHeaderCard
import com.example.zar.features.transactions.presentation.income.screen.ZarMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneScreen(
    onDone: () -> Unit,
    viewModel: MilestoneViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.isEditing) R.string.milestone_edit else R.string.milestone_add)) },
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Loading(Modifier.padding(innerPadding))
            return@Scaffold
        }
        if (uiState.message == FreelanceMessage.NOT_FOUND || uiState.project == null) {
            CenteredText(stringResource(R.string.milestone_not_found), Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.summary?.let { summary ->
                val progress = summary.progress
                SummaryHeaderCard(
                    label = stringResource(R.string.project_still_owed),
                    amount = progress.remaining.display(),
                    caption = buildString {
                        append(stringResource(R.string.freelance_received_of, IncomeFormat.amount(progress.matched), summary.milestone.amount.display()))
                        if (progress.overdue.isPositive) append(" · ").append(stringResource(R.string.freelance_overdue, IncomeFormat.amount(progress.overdue)))
                    },
                )
            }
            if (uiState.isCancelled) Text(stringResource(R.string.milestone_cancelled_note), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono)
            if (uiState.project?.isArchived == true) Text(stringResource(R.string.project_archived_note), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono)

            MilestoneFields(uiState, viewModel)

            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }

            if (uiState.canEdit) {
                Button(onClick = viewModel::save, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.income_save))
                }
            }

            if (uiState.isEditing) {
                LinkedReceipts(uiState.receipts, canEdit = uiState.canEdit && !uiState.isSaving, onUnlink = viewModel::unlink)
                if (uiState.canEdit && uiState.summary?.progress?.remaining?.isPositive == true) {
                    LinkCandidates(uiState, viewModel)
                }
                if (uiState.canEdit) CancelMilestoneButton(enabled = !uiState.isSaving, onConfirm = viewModel::cancel)
            }
        }
    }
}

@Composable
private fun MilestoneFields(uiState: MilestoneUiState, viewModel: MilestoneViewModel) {
    val currency = uiState.project?.currency ?: return
    OutlinedTextField(
        value = uiState.title,
        onValueChange = viewModel::onTitleChange,
        label = { Text(stringResource(R.string.milestone_title)) },
        enabled = uiState.canEdit,
        isError = uiState.problems.nameErrorRes() != null,
        supportingText = uiState.problems.nameErrorRes()?.let { { Text(stringResource(it)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.amount,
        onValueChange = viewModel::onAmountChange,
        label = { Text(stringResource(R.string.milestone_amount)) },
        prefix = { Text("${currency.code} ", fontFamily = ZarMono) },
        enabled = uiState.canEdit,
        isError = uiState.problems.amountErrorRes() != null,
        supportingText = uiState.problems.amountErrorRes()?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    DateField(stringResource(R.string.milestone_due), uiState.dueDate, enabled = uiState.canEdit, onSelect = viewModel::onDueDateSelect)
}

@Composable
private fun LinkedReceipts(receipts: List<LinkedReceipt>, canEdit: Boolean, onUnlink: (LinkedReceipt) -> Unit) {
    if (receipts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        SectionLabel(stringResource(R.string.milestone_linked), Modifier.padding(top = 8.dp))
        receipts.forEach { receipt ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(receipt.counted.display(), style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                        Text(
                            listOfNotNull(receipt.entry.current.merchant, IncomeFormat.shortDate(receipt.entry.current.date)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = ZarMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (canEdit) TextButton(onClick = { onUnlink(receipt) }) { Text(stringResource(R.string.milestone_unlink)) }
                }
            }
        }
    }
}

@Composable
private fun LinkCandidates(uiState: MilestoneUiState, viewModel: MilestoneViewModel) {
    val currencyCode = uiState.project?.currency?.code.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        SectionLabel(stringResource(R.string.milestone_link_title), Modifier.padding(top = 8.dp))
        if (uiState.candidates.isEmpty()) {
            Text(stringResource(R.string.milestone_no_candidates, currencyCode), style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono)
            return@Column
        }
        Text(stringResource(R.string.milestone_link_hint, currencyCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        uiState.candidates.forEach { candidate -> CandidateCard(uiState, candidate, viewModel) }
    }
}

@Composable
private fun CandidateCard(uiState: MilestoneUiState, candidate: ReceiptCandidate, viewModel: MilestoneViewModel) {
    val key = candidate.entry.id.value
    val typed = uiState.linkAmounts[key] ?: uiState.suggestedAmount(candidate).toDecimal().toPlainString()
    val problems = uiState.linkProblems[key].orEmpty()
    val tx = candidate.entry.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOfNotNull(tx.merchant, IncomeFormat.shortDate(tx.date), IncomeFormat.received(tx.amount)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ZarMono,
            )
            Text(
                stringResource(R.string.milestone_available, candidate.available.display()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ZarMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { viewModel.onLinkAmountChange(key, it) },
                    prefix = { Text("${tx.amount.currency.code} ", fontFamily = ZarMono) },
                    isError = problems.amountErrorRes() != null,
                    supportingText = problems.amountErrorRes()?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { viewModel.link(candidate) }, enabled = !uiState.isSaving) {
                    Text(stringResource(R.string.milestone_link))
                }
            }
        }
    }
}

@Composable
private fun CancelMilestoneButton(enabled: Boolean, onConfirm: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { confirming = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.milestone_cancel), color = MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.milestone_cancel_title)) },
            text = { Text(stringResource(R.string.milestone_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onConfirm()
                }) { Text(stringResource(R.string.milestone_cancel_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
