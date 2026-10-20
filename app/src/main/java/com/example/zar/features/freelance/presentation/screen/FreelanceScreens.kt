package com.example.zar.features.freelance.presentation.screen

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.MatchState
import com.example.zar.features.freelance.domain.FreelanceProblem
import com.example.zar.features.freelance.domain.model.MilestoneSummary
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.freelance.domain.model.ProjectSummary
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceMessage
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceViewModel
import com.example.zar.features.freelance.presentation.viewmodel.ProjectFormViewModel
import com.example.zar.features.freelance.presentation.viewmodel.ProjectViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.SummaryHeaderCard
import com.example.zar.features.transactions.presentation.income.screen.ZarMono

// --- Projects list -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreelanceScreen(
    onBack: () -> Unit,
    onAddProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    viewModel: FreelanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.freelance_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = { AddButton(onAddProject) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Loading(Modifier.padding(innerPadding))
            uiState.loadFailed -> CenteredText(stringResource(R.string.freelance_load_failed), Modifier.padding(innerPadding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { OwedHeader(uiState.outstanding) }
                if (uiState.projects.isEmpty()) item { CenteredText(stringResource(R.string.freelance_empty), Modifier.padding(top = 48.dp)) }
                items(uiState.projects, key = { it.project.id.value }) { summary ->
                    ProjectCard(summary, onClick = { onOpenProject(summary.project.id.value) })
                }
            }
        }
    }
}

@Composable
private fun OwedHeader(outstanding: Outstanding?) {
    val owed = outstanding?.takeUnless { it.isEmpty }
    SummaryHeaderCard(
        label = stringResource(R.string.freelance_owed_label),
        amount = owed?.owed?.display() ?: stringResource(R.string.freelance_owed_none),
        caption = when {
            owed == null -> stringResource(R.string.income_header_caption)
            owed.overdue.isNotEmpty() -> stringResource(R.string.freelance_overdue, owed.overdue.display())
            else -> pluralStringResource(R.plurals.freelance_owed_caption, owed.projectsWithBalance, owed.projectsWithBalance)
        },
    )
}

@Composable
private fun ProjectCard(summary: ProjectSummary, onClick: () -> Unit) {
    val project = summary.project
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                val details = listOfNotNull(
                    project.client,
                    pluralStringResource(R.plurals.freelance_milestone_count, summary.milestones.size, summary.milestones.size),
                    if (project.isArchived) stringResource(R.string.freelance_archived) else null,
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.freelance_received_of, IncomeFormat.amount(summary.received), summary.expected.display()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = ZarMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.freelance_left, summary.remaining.display()), style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                if (summary.overdue.isPositive) {
                    Text(
                        stringResource(R.string.freelance_overdue, IncomeFormat.amount(summary.overdue)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ZarMono,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
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

// --- Project detail ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    onBack: () -> Unit,
    onEditProject: (String) -> Unit,
    onAddMilestone: (String) -> Unit,
    onOpenMilestone: (projectId: String, milestoneId: String) -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.project?.name.orEmpty(), fontFamily = ZarMono) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
                actions = {
                    if (summary != null) TextButton(onClick = { onEditProject(summary.project.id.value) }) { Text(stringResource(R.string.action_edit)) }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Loading(Modifier.padding(innerPadding))
            uiState.loadFailed -> CenteredText(stringResource(R.string.freelance_load_failed), Modifier.padding(innerPadding))
            summary == null -> CenteredText(stringResource(R.string.project_not_found), Modifier.padding(innerPadding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SummaryHeaderCard(
                        label = stringResource(R.string.project_still_owed),
                        amount = summary.remaining.display(),
                        caption = buildString {
                            append(stringResource(R.string.freelance_received_of, IncomeFormat.amount(summary.received), summary.expected.display()))
                            if (summary.overdue.isPositive) append(" · ").append(stringResource(R.string.freelance_overdue, IncomeFormat.amount(summary.overdue)))
                        },
                    )
                }
                if (summary.project.isArchived) item { Text(stringResource(R.string.project_archived_note), fontFamily = ZarMono, color = MaterialTheme.colorScheme.error) }
                if (summary.milestones.isEmpty()) item { CenteredText(stringResource(R.string.project_no_milestones)) }
                items(summary.milestones, key = { it.milestone.id.value }) { milestone ->
                    MilestoneCard(milestone, onClick = { onOpenMilestone(summary.project.id.value, milestone.milestone.id.value) })
                }
                if (!summary.project.isArchived) {
                    item {
                        OutlinedButton(onClick = { onAddMilestone(summary.project.id.value) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.project_add_milestone), fontFamily = ZarMono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(summary: MilestoneSummary, onClick: () -> Unit) {
    val progress = summary.progress
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.milestone.title, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                val details = listOfNotNull(
                    stringResource(R.string.milestone_due_on, IncomeFormat.shortDate(summary.milestone.dueDate)),
                    if (summary.receipts.isNotEmpty()) pluralStringResource(R.plurals.milestone_payment_count, summary.receipts.size, summary.receipts.size) else null,
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, fontFamily = ZarMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(summary.milestone.amount.display(), style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                val (state, isWarning) = when {
                    progress.state == MatchState.CANCELLED -> stringResource(R.string.milestone_state_cancelled) to false
                    progress.state == MatchState.MATCHED -> stringResource(R.string.milestone_state_paid) to false
                    progress.overdue.isPositive -> stringResource(R.string.milestone_state_overdue, IncomeFormat.amount(progress.overdue)) to true
                    else -> stringResource(R.string.freelance_left, IncomeFormat.amount(progress.remaining)) to false
                }
                Text(
                    state,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ZarMono,
                    color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- Project form --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectFormScreen(
    onBack: () -> Unit,
    onSaved: (projectId: String, isNew: Boolean) -> Unit,
    viewModel: ProjectFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.savedProjectId) { uiState.savedProjectId?.let { onSaved(it, !uiState.isEditing) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.isEditing) R.string.project_edit else R.string.project_add)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Loading(Modifier.padding(innerPadding))
            return@Scaffold
        }
        if (uiState.message == FreelanceMessage.NOT_FOUND) {
            CenteredText(stringResource(R.string.project_not_found), Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.project_name)) },
                isError = uiState.problems.nameErrorRes() != null,
                supportingText = uiState.problems.nameErrorRes()?.let { { Text(stringResource(it)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.client,
                onValueChange = viewModel::onClientChange,
                label = { Text(stringResource(R.string.project_client)) },
                isError = FreelanceProblem.CLIENT_TOO_LONG in uiState.problems,
                supportingText = if (FreelanceProblem.CLIENT_TOO_LONG in uiState.problems) {
                    { Text(stringResource(R.string.income_error_too_long)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.project_currency))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.currencyChoices.forEach { currency ->
                        FilterChip(
                            selected = currency == uiState.currency,
                            onClick = { viewModel.onCurrencySelect(currency) },
                            enabled = !uiState.isEditing,
                            label = { Text(currency.code, fontFamily = ZarMono) },
                        )
                    }
                }
                if (uiState.isEditing) {
                    Text(stringResource(R.string.project_currency_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            uiState.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, fontFamily = ZarMono) }
            Button(onClick = viewModel::save, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.income_save))
            }
            uiState.project?.let { project ->
                TextButton(onClick = { viewModel.setArchived(!project.isArchived) }, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (project.isArchived) R.string.project_unarchive else R.string.project_archive))
                }
            }
        }
    }
}
