package com.example.zar.features.freelance.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.example.zar.core.presentation.components.ZarAddPill
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarWhite
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Switch
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
import com.example.zar.core.presentation.components.HeroScrollOffset
import com.example.zar.core.presentation.components.ZarHeroItemKey
import com.example.zar.core.presentation.components.heroDisplacement
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.features.bills.presentation.screen.StatusBarGlyphs
import com.example.zar.features.freelance.domain.FreelanceProblem
import com.example.zar.features.freelance.domain.model.MilestoneSummary
import com.example.zar.features.freelance.domain.model.Outstanding
import com.example.zar.features.freelance.domain.model.ProjectSummary
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceMessage
import com.example.zar.features.freelance.presentation.viewmodel.FreelanceViewModel
import com.example.zar.features.freelance.presentation.viewmodel.ProjectFormViewModel
import com.example.zar.features.freelance.presentation.viewmodel.ProjectViewModel
import com.example.zar.features.reminders.presentation.NotificationsOffNote
import com.example.zar.features.reminders.presentation.rememberNotificationAccess
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.SummaryHeaderCard
import com.example.zar.features.transactions.presentation.income.screen.ZarMono

// --- Projects list -------------------------------------------------------------------------

/** Figma Z197: a full-width gradient hero with Back, the owed summary, grouped project cards and a "New project" pill. */
@Composable
fun FreelanceScreen(
    onBack: () -> Unit,
    onAddProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    viewModel: FreelanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overdueAlerts by viewModel.overdueAlerts.collectAsStateWithLifecycle()
    val colors = ZarTheme.colors
    StatusBarGlyphs(onDarkHero = true)
    val listState = rememberLazyListState()
    val heroOffset = listState.heroDisplacement()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("freelance-list"),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = ZarHeroItemKey) {
                OwedHero(uiState.outstanding, uiState.projects.isEmpty(), topInset, onBack, heroOffset)
            }
            when {
                uiState.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
                }
                uiState.loadFailed -> item { FreelanceNote(stringResource(R.string.freelance_load_failed)) }
                else -> {
                    item { OverdueAlertsRow(enabled = overdueAlerts, onChange = viewModel::setOverdueAlerts) }
                    if (uiState.projects.isEmpty()) {
                        item { FreelanceNote(stringResource(R.string.freelance_empty), Modifier.testTag("freelance-empty")) }
                    }
                    items(uiState.projects, key = { it.project.id.value }) { summary ->
                        ProjectCard(summary, onClick = { onOpenProject(summary.project.id.value) })
                    }
                }
            }
        }
        ZarAddPill(
            text = stringResource(R.string.project_add),
            onClick = onAddProject,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp).testTag("freelance-add"),
        )
    }
}

@Composable
private fun FreelanceNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ZarType.bodyS, color = ZarTheme.colors.textSecondary, modifier = modifier.padding(horizontal = 22.dp, vertical = 4.dp))
}

@Composable
private fun OverdueAlertsRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ZarTheme.colors
    val notifications = rememberNotificationAccess()
    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.freelance_overdue_alerts), style = ZarType.bodyS, color = colors.textPrimary, modifier = Modifier.weight(1f).padding(end = 12.dp))
            ZarSwitch(checked = enabled, onCheckedChange = { checked ->
                onChange(checked)
                if (checked) notifications.request()
            })
        }
        if (enabled && !notifications.enabled) {
            Text(stringResource(R.string.reminders_notifications_off), style = ZarType.bodyCaption, color = colors.textAttentionStrong)
        }
    }
}

@Composable
private fun OwedHero(outstanding: Outstanding?, noProjects: Boolean, topInset: Dp, onBack: () -> Unit, heroOffset: HeroScrollOffset) {
    val owed = outstanding?.takeUnless { it.isEmpty }
    val textLayer = Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap)
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.hero).background(ZarGradients.received).padding(top = topInset),
    ) {
        ZarBackButton(onBack, stringResource(R.string.action_back), Modifier.padding(start = 10.dp, top = 4.dp), tint = ZarWhite)
        if (noProjects && owed == null) {
            // Z197: nothing to summarise, so the hero says so in two plain lines.
            Text(
                stringResource(R.string.freelance_hero_empty),
                style = ZarType.displayTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = ZarWhite,
                textAlign = TextAlign.Center,
                modifier = textLayer.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp).testTag("freelance-hero"),
            )
        } else {
            Column(textLayer.padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 32.dp).testTag("freelance-hero"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZarMonoLabel(stringResource(R.string.freelance_owed_label), color = ZarSubtle)
                Text(
                    owed?.owed?.display() ?: stringResource(R.string.freelance_owed_none),
                    style = ZarType.amountL,
                    color = ZarWhite,
                )
                Text(
                    when {
                        owed == null -> stringResource(R.string.income_header_caption)
                        owed.overdue.isNotEmpty() -> stringResource(R.string.freelance_overdue, owed.overdue.display())
                        else -> pluralStringResource(R.plurals.freelance_owed_caption, owed.projectsWithBalance, owed.projectsWithBalance)
                    },
                    style = ZarType.bodyS,
                    color = ZarWhite.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(summary: ProjectSummary, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val project = summary.project
    ZarCard(Modifier.padding(horizontal = 10.dp).fillMaxWidth(), onClick = onClick, radius = ZarDimens.RowCardRadius, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(project.name, style = ZarType.labelButton, color = colors.textPrimary)
                val details = listOfNotNull(
                    project.client,
                    pluralStringResource(R.plurals.freelance_milestone_count, summary.milestones.size, summary.milestones.size),
                    if (project.isArchived) stringResource(R.string.freelance_archived) else null,
                )
                Text(details.joinToString(" · "), style = ZarType.bodyCaption, color = colors.textSecondary)
                Text(
                    stringResource(R.string.freelance_received_of, IncomeFormat.amount(summary.received), summary.expected.display()),
                    style = ZarType.bodyCaption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.freelance_left, summary.remaining.display()), style = ZarType.bodyM, color = colors.textPrimary)
                if (summary.overdue.isPositive) {
                    Text(
                        stringResource(R.string.freelance_overdue, IncomeFormat.amount(summary.overdue)),
                        style = ZarType.bodyCaption,
                        color = colors.textAttentionStrong,
                    )
                }
            }
        }
    }
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
            else -> {
                val listState = rememberLazyListState()
                val heroOffset = listState.heroDisplacement()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = ZarHeroItemKey) {
                        SummaryHeaderCard(
                            label = stringResource(R.string.project_still_owed),
                            amount = summary.remaining.display(),
                            caption = buildString {
                                append(stringResource(R.string.freelance_received_of, IncomeFormat.amount(summary.received), summary.expected.display()))
                                if (summary.overdue.isPositive) append(" · ").append(stringResource(R.string.freelance_overdue, IncomeFormat.amount(summary.overdue)))
                            },
                            modifier = Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap),
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

/** Figma Z195: Zar header with close, mono-labelled rows, currency chips and the Cancel / Save pair, like the account form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectFormScreen(
    onBack: () -> Unit,
    onSaved: (projectId: String, isNew: Boolean) -> Unit,
    viewModel: ProjectFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.savedProjectId) { uiState.savedProjectId?.let { onSaved(it, !uiState.isEditing) } }
    DarkStatusBarIcons()
    val colors = ZarTheme.colors
    val notFound = uiState.message == FreelanceMessage.NOT_FOUND
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (uiState.isEditing) R.string.project_edit else R.string.project_add),
                style = ZarType.headingSheet,
                color = colors.textPrimary,
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.account_form_close), onClick = onBack)
                    .testTag("project-form-close"),
                Alignment.Center,
            ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
        }
        when {
            uiState.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
            notFound -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.project_not_found), style = ZarType.bodyS, color = colors.textSecondary)
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ZarFormRow(stringResource(R.string.project_form_name)) {
                    TextInput(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        placeholder = stringResource(R.string.project_form_name_hint),
                        modifier = Modifier.weight(1f).testTag("project-name"),
                    )
                }
                uiState.problems.nameErrorRes()?.let { ProjectFieldError(it) }
                ZarDivider()
                ZarFormRow(stringResource(R.string.project_form_client)) {
                    TextInput(
                        value = uiState.client,
                        onValueChange = viewModel::onClientChange,
                        placeholder = stringResource(R.string.project_form_client_hint),
                        modifier = Modifier.weight(1f).testTag("project-client"),
                    )
                }
                if (FreelanceProblem.CLIENT_TOO_LONG in uiState.problems) ProjectFieldError(R.string.income_error_too_long)
                ZarDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZarMonoLabel(stringResource(R.string.project_currency))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.currencyChoices.forEach { currency ->
                            ZarChip(
                                label = currency.code,
                                selected = currency == uiState.currency,
                                onClick = { viewModel.onCurrencySelect(currency) },
                                enabled = !uiState.isEditing || currency == uiState.currency,
                                modifier = Modifier.testTag("project-currency-${currency.code}"),
                            )
                        }
                    }
                    Text(
                        stringResource(if (uiState.isEditing) R.string.project_currency_locked else R.string.project_form_note),
                        style = ZarType.bodyS,
                        color = colors.textSecondary,
                    )
                }
                uiState.message?.let { ProjectFieldError(it.textRes()) }
                uiState.project?.let { project ->
                    ZarButton(
                        text = stringResource(if (project.isArchived) R.string.project_unarchive else R.string.project_archive),
                        onClick = { viewModel.setArchived(!project.isArchived) },
                        modifier = Modifier.fillMaxWidth().testTag("project-archive"),
                        style = ZarButtonStyle.Soft,
                        enabled = !uiState.isSaving,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(stringResource(R.string.account_form_cancel), onBack, Modifier.weight(1f).testTag("project-cancel"), ZarButtonStyle.Soft)
            ZarButton(
                text = stringResource(R.string.income_save),
                onClick = viewModel::save,
                modifier = Modifier.weight(1f).testTag("project-save"),
                enabled = !uiState.isSaving && !uiState.isLoading && !notFound,
            )
        }
    }
}

@Composable
private fun ProjectFieldError(@androidx.annotation.StringRes res: Int) {
    Text(stringResource(res), style = ZarType.bodyCaption, color = ZarTheme.colors.danger)
}
