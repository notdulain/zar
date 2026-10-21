package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.MatchState
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillOccurrence
import com.example.zar.features.bills.domain.model.BillSummary
import com.example.zar.features.bills.presentation.viewmodel.BillFormViewModel
import com.example.zar.features.bills.presentation.viewmodel.BillMessage
import com.example.zar.features.bills.presentation.viewmodel.BillOccurrenceViewModel
import com.example.zar.features.bills.presentation.viewmodel.BillViewModel
import com.example.zar.features.bills.presentation.viewmodel.BillsViewModel
import com.example.zar.features.freelance.presentation.screen.CenteredText
import com.example.zar.features.freelance.presentation.screen.DateField
import com.example.zar.features.freelance.presentation.screen.Loading
import com.example.zar.features.freelance.presentation.screen.SectionLabel
import com.example.zar.features.freelance.presentation.screen.display
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.SummaryHeaderCard
import com.example.zar.features.transactions.presentation.income.screen.ZarMono
import java.time.LocalDate

private fun BillFrequency.labelRes(): Int = when (this) {
    BillFrequency.WEEKLY -> R.string.bill_frequency_weekly
    BillFrequency.MONTHLY -> R.string.bill_frequency_monthly
    BillFrequency.YEARLY -> R.string.bill_frequency_yearly
}

private fun BillMessage.textRes(): Int = when (this) {
    BillMessage.NOT_SIGNED_IN -> R.string.bills_message_not_signed_in
    BillMessage.CURRENCY -> R.string.bills_message_currency
    BillMessage.FAILED -> R.string.bills_message_failed
}

/** A due date's state in honest words: no link means "not matched", never "unpaid". */
@Composable
private fun BillOccurrence.stateText(today: LocalDate): Pair<String, Boolean> = when {
    progress.state == MatchState.CANCELLED -> stringResource(R.string.bill_state_cancelled) to false
    progress.state == MatchState.MATCHED -> stringResource(R.string.bill_state_matched) to false
    progress.state == MatchState.PARTIALLY_MATCHED -> stringResource(R.string.bill_state_partly, IncomeFormat.amount(progress.remaining)) to true
    dueDate >= today -> stringResource(R.string.bill_state_upcoming) to false
    else -> stringResource(R.string.bill_state_not_matched) to true
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

@Composable
private fun AddButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) { Text("+", fontSize = 28.sp) }
}

// --- Bills list ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    onBack: () -> Unit,
    onAddBill: () -> Unit,
    onOpenBill: (String) -> Unit,
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overview = uiState.overview
    val today = ZarTime.today()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bills_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = { AddButton(onAddBill) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Loading(Modifier.padding(innerPadding))
            uiState.loadFailed || overview == null -> CenteredText(stringResource(R.string.bills_load_failed), Modifier.padding(innerPadding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SummaryHeaderCard(
                        label = stringResource(R.string.bills_due_soon_label),
                        amount = overview.dueSoon.takeIf { it.isNotEmpty() }?.display() ?: stringResource(R.string.bills_nothing_due),
                        caption = overview.annual.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.bills_annual, it.display()) }
                            ?: stringResource(R.string.bills_annual_none),
                    )
                }
                if (overview.bills.isEmpty()) item { CenteredText(stringResource(R.string.bills_empty), Modifier.padding(top = 32.dp)) }
                items(overview.bills, key = { it.bill.id.value }) { summary ->
                    BillCard(summary, today, onClick = { onOpenBill(summary.bill.id.value) })
                }
            }
        }
    }
}

@Composable
private fun BillCard(summary: BillSummary, today: LocalDate, onClick: () -> Unit) {
    val bill = summary.bill
    // The most recent due date that has passed, or the next one.
    val latest = summary.occurrences.firstOrNull { it.dueDate < today } ?: summary.next
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bill.name, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                Detail(
                    listOfNotNull(
                        stringResource(bill.frequency.labelRes()),
                        summary.account?.name,
                        summary.next?.let { stringResource(R.string.bills_next_on, IncomeFormat.shortDate(it.dueDate)) },
                        if (!bill.isActive) stringResource(R.string.bills_stopped) else null,
                    ).joinToString(" · "),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(bill.amount.display(), style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                latest?.let { occurrence ->
                    val (text, warning) = occurrence.stateText(today)
                    Text(text, style = MaterialTheme.typography.labelSmall, fontFamily = ZarMono,
                        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// --- Bill detail ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    onBack: () -> Unit,
    onEditBill: (String) -> Unit,
    onOpenOccurrence: (billId: String, dueDate: String) -> Unit,
    viewModel: BillViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    val today = ZarTime.today()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.bill?.name.orEmpty(), fontFamily = ZarMono) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
                actions = { if (summary != null) TextButton(onClick = { onEditBill(summary.bill.id.value) }) { Text(stringResource(R.string.action_edit)) } },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Loading(Modifier.padding(innerPadding))
            uiState.loadFailed -> CenteredText(stringResource(R.string.bills_load_failed), Modifier.padding(innerPadding))
            summary == null -> CenteredText(stringResource(R.string.bill_not_found), Modifier.padding(innerPadding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SummaryHeaderCard(
                        label = stringResource(R.string.bill_next_label),
                        amount = summary.next?.let { IncomeFormat.fullDate(it.dueDate) } ?: stringResource(R.string.bills_stopped),
                        caption = stringResource(
                            R.string.bill_caption,
                            summary.bill.amount.display(),
                            stringResource(summary.bill.frequency.labelRes()),
                            summary.annualCost.display(),
                        ),
                    )
                }
                item { Detail(stringResource(R.string.bill_never_posts)) }
                item { SectionLabel(stringResource(R.string.bill_due_dates), Modifier.padding(top = 8.dp)) }
                if (summary.occurrences.isEmpty()) item { CenteredText(stringResource(R.string.bill_no_due_dates)) }
                items(summary.occurrences, key = { it.progress.expected.id.value }) { occurrence ->
                    val (text, warning) = occurrence.stateText(today)
                    OutlinedCard(
                        onClick = { onOpenOccurrence(summary.bill.id.value, occurrence.dueDate.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(IncomeFormat.fullDate(occurrence.dueDate), style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                                Detail(text, warning)
                            }
                            Text(occurrence.progress.expected.amount.display(), style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                        }
                    }
                }
            }
        }
    }
}

// --- Bill form -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillFormScreen(
    onBack: () -> Unit,
    onSaved: (billId: String, isNew: Boolean) -> Unit,
    viewModel: BillFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.savedBillId) { uiState.savedBillId?.let { onSaved(it, !uiState.isEditing) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.isEditing) R.string.bill_edit else R.string.bill_add)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Loading(Modifier.padding(innerPadding))
            return@Scaffold
        }
        if (uiState.notFound) {
            CenteredText(stringResource(R.string.bill_not_found), Modifier.padding(innerPadding))
            return@Scaffold
        }
        val problems = uiState.problems
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.bill_name)) },
                isError = BillProblem.NAME_MISSING in problems || BillProblem.NAME_TOO_LONG in problems,
                supportingText = when {
                    BillProblem.NAME_MISSING in problems -> { { Text(stringResource(R.string.bills_error_name_missing)) } }
                    BillProblem.NAME_TOO_LONG in problems -> { { Text(stringResource(R.string.bills_error_too_long)) } }
                    else -> null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.bill_account))
                if (uiState.accounts.isEmpty()) {
                    Text(stringResource(R.string.bill_no_accounts), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.accounts.forEach { account ->
                        FilterChip(
                            selected = account.id == uiState.accountId,
                            onClick = { viewModel.onAccountSelect(account.id) },
                            label = { Text("${account.name} · ${account.currency.code}", fontFamily = ZarMono) },
                        )
                    }
                }
                if (BillProblem.ACCOUNT_MISSING in problems) Text(stringResource(R.string.bills_error_account), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::onAmountChange,
                label = { Text(stringResource(R.string.bill_amount)) },
                prefix = uiState.account?.let { { Text("${it.currency.code} ", fontFamily = ZarMono) } },
                isError = BillProblem.AMOUNT_MISSING in problems || BillProblem.AMOUNT_INVALID in problems,
                supportingText = when {
                    BillProblem.AMOUNT_MISSING in problems -> { { Text(stringResource(R.string.bills_error_amount_missing)) } }
                    BillProblem.AMOUNT_INVALID in problems -> { { Text(stringResource(R.string.bills_error_amount_invalid)) } }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.bill_frequency))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillFrequency.entries.forEach { frequency ->
                        FilterChip(
                            selected = frequency == uiState.frequency,
                            onClick = { viewModel.onFrequencySelect(frequency) },
                            label = { Text(stringResource(frequency.labelRes()), fontFamily = ZarMono) },
                        )
                    }
                }
            }
            DateField(stringResource(R.string.bill_first_due), uiState.firstDue, enabled = true, onSelect = viewModel::onFirstDueSelect)
            if (uiState.frequency != BillFrequency.WEEKLY && uiState.firstDue.dayOfMonth > 28) {
                Detail(stringResource(R.string.bill_month_end_hint))
            }
            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }
            Button(onClick = viewModel::save, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.income_save))
            }
            uiState.bill?.let { bill ->
                TextButton(onClick = { viewModel.setStopped(bill.isActive) }, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (bill.isActive) R.string.bill_stop else R.string.bill_resume),
                        color = if (bill.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// --- One due date --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillOccurrenceScreen(
    onBack: () -> Unit,
    viewModel: BillOccurrenceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val occurrence = uiState.occurrence
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(occurrence?.let { stringResource(R.string.occurrence_title, IncomeFormat.shortDate(it.dueDate)) }.orEmpty(), fontFamily = ZarMono) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Loading(Modifier.padding(innerPadding))
            return@Scaffold
        }
        if (occurrence == null || uiState.bill == null) {
            CenteredText(stringResource(R.string.bill_not_found), Modifier.padding(innerPadding))
            return@Scaffold
        }
        val progress = occurrence.progress
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryHeaderCard(
                label = stringResource(R.string.occurrence_label),
                amount = progress.remaining.display(),
                caption = stringResource(R.string.occurrence_caption, IncomeFormat.amount(progress.matched), progress.expected.amount.display()),
            )
            if (progress.state == MatchState.NOT_MATCHED) Detail(stringResource(R.string.occurrence_not_matched_note))
            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }

            if (uiState.payments.isNotEmpty()) {
                HorizontalDivider()
                SectionLabel(stringResource(R.string.occurrence_linked))
                uiState.payments.forEach { payment ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(payment.counted.display(), style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                                Detail(listOfNotNull(payment.entry.current.merchant, IncomeFormat.shortDate(payment.entry.current.date)).joinToString(" · "))
                            }
                            TextButton(onClick = { viewModel.unlink(payment) }, enabled = !uiState.isSaving) { Text(stringResource(R.string.occurrence_unlink)) }
                        }
                    }
                }
            }

            if (progress.remaining.isPositive && progress.state != MatchState.CANCELLED) {
                HorizontalDivider()
                SectionLabel(stringResource(R.string.occurrence_link_title))
                if (uiState.candidates.isEmpty()) {
                    Detail(stringResource(R.string.occurrence_no_candidates, progress.expected.amount.currency.code))
                }
                uiState.candidates.forEach { candidate ->
                    val key = candidate.entry.id.value
                    val typed = uiState.linkAmounts[key] ?: uiState.suggestedAmount(candidate).toDecimal().toPlainString()
                    val problems = uiState.linkProblems[key].orEmpty()
                    val error = when {
                        BillProblem.AMOUNT_MISSING in problems -> stringResource(R.string.bills_error_amount_missing)
                        BillProblem.AMOUNT_INVALID in problems -> stringResource(R.string.bills_error_amount_invalid)
                        BillProblem.MORE_THAN_PAYMENT in problems -> stringResource(R.string.bills_error_more_than_payment)
                        BillProblem.MORE_THAN_REMAINING in problems -> stringResource(R.string.bills_error_more_than_remaining)
                        else -> null
                    }
                    val tx = candidate.entry.current
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(listOfNotNull(tx.merchant, IncomeFormat.shortDate(tx.date), tx.amount.display()).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                            Detail(stringResource(R.string.occurrence_available, candidate.available.display()))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = typed,
                                    onValueChange = { viewModel.onLinkAmountChange(key, it) },
                                    prefix = { Text("${tx.amount.currency.code} ", fontFamily = ZarMono) },
                                    isError = error != null,
                                    supportingText = error?.let { { Text(it) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { viewModel.link(candidate) }, enabled = !uiState.isSaving) {
                                    Text(stringResource(R.string.occurrence_link))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
