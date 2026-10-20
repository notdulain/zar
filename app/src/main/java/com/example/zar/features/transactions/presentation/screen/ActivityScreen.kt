package com.example.zar.features.transactions.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.Account
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.features.accounts.presentation.screen.iconRes
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivityList
import com.example.zar.features.transactions.domain.activity.model.ActivityRow
import com.example.zar.features.transactions.domain.activity.model.DatePreset
import com.example.zar.features.transactions.presentation.activity.EntryDisplay
import com.example.zar.features.transactions.presentation.activity.entrySubtitle
import com.example.zar.features.transactions.presentation.activity.entryTitle
import com.example.zar.features.transactions.presentation.activity.screen.EmptyCard
import com.example.zar.features.transactions.presentation.activity.screen.FilterPill
import com.example.zar.features.transactions.presentation.activity.screen.InfoBanner
import com.example.zar.features.transactions.presentation.activity.screen.TagPill
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * Activity: every recorded entry for a month or a chosen range, filterable by account and date, with
 * Received and Spent for the same selection. Tapping a row opens its detail.
 */
@Composable
fun ActivityScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ZarScreenScaffold(
        destination = TopLevelDestination.Activity,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        ActivityBody(
            state = state,
            actions = ActivityActions(
                onOpenEntry = onOpenEntry,
                onAddEntry = { onAddOption(AddEntryOption.Manual) },
                onApplyFilter = viewModel::applyFilter,
                onClearFilters = viewModel::clearFilters,
                onQuery = viewModel::setQuery,
                onRetry = viewModel::retry,
                previewCount = viewModel::previewCount,
            ),
            modifier = Modifier.padding(padding),
        )
    }
}

internal class ActivityActions(
    val onOpenEntry: (String) -> Unit,
    val onAddEntry: () -> Unit,
    val onApplyFilter: (ActivityFilter) -> Unit,
    val onClearFilters: () -> Unit,
    val onQuery: (String) -> Unit,
    val onRetry: () -> Unit,
    val previewCount: (ActivityFilter) -> Int,
)

@Composable
internal fun ActivityBody(state: ActivityUiState, actions: ActivityActions, modifier: Modifier = Modifier) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(state.filter.query.isNotEmpty()) }
    // Only opening the search with its button takes focus; coming back to a screen that already has it open does not.
    var focusSearch by remember { mutableStateOf(false) }
    val list = (state.content as? ActivityContent.Ready)?.list
    val gutter = Modifier.padding(horizontal = ZarDimens.ScreenGutter)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ZarDimens.SpaceXl),
        verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceXs),
    ) {
        item {
            val summary = list?.summary
            ActivityHero(
                received = summary?.let { EntryDisplay.money(it.received.amount) } ?: stringResource(R.string.activity_amount_pending),
                spent = summary?.let { EntryDisplay.money(it.spent.total.amount) } ?: stringResource(R.string.activity_amount_pending),
                rangeLabel = heroRangeLabel(state),
                onRange = { showFilters = true },
            )
        }
        item {
            FilterRow(
                state = state,
                onOpenFilters = { showFilters = true },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    focusSearch = searchOpen
                },
                onApplyFilter = actions.onApplyFilter,
                modifier = gutter.padding(top = ZarDimens.SpaceXs),
            )
        }
        if (searchOpen) {
            item { SearchField(state.filter.query, actions.onQuery, focusSearch, gutter) }
        }
        item { RangeLine(state, list, actions.onClearFilters, gutter) }
        if (!state.isOnline) {
            item {
                InfoBanner(
                    title = stringResource(R.string.activity_offline_title),
                    body = stringResource(R.string.activity_offline_body),
                    modifier = gutter.testTag("activity-offline"),
                )
            }
        }
        if (list != null) {
            val unresolved = list.summary.received.unresolvedCount + list.summary.spent.total.unresolvedCount
            if (unresolved > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.income_header_unresolved, unresolved, unresolved),
                        style = ZarType.bodyCaption,
                        color = ZarTheme.colors.textSecondary,
                        modifier = gutter.testTag("activity-unresolved"),
                    )
                }
            }
        }
        when (val content = state.content) {
            ActivityContent.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                    CircularProgressIndicator(color = ZarTheme.colors.accentSelected)
                }
            }
            ActivityContent.Error -> item {
                EmptyCard(
                    icon = R.drawable.ic_ph_info,
                    title = stringResource(R.string.activity_error_title),
                    body = stringResource(R.string.activity_error_body),
                    action = stringResource(R.string.activity_retry),
                    onAction = actions.onRetry,
                    modifier = gutter.testTag("activity-error"),
                )
            }
            is ActivityContent.Ready -> {
                if (content.list.rows.isEmpty()) {
                    item {
                        if (!content.list.hasAnyEntries) {
                            EmptyCard(
                                icon = R.drawable.ic_ph_tray,
                                title = stringResource(R.string.activity_empty_title),
                                body = stringResource(R.string.activity_empty_body),
                                action = stringResource(R.string.activity_empty_action),
                                onAction = actions.onAddEntry,
                                modifier = gutter.testTag("activity-empty"),
                            )
                        } else {
                            EmptyCard(
                                icon = R.drawable.ic_ph_funnel,
                                title = stringResource(R.string.activity_no_results_title),
                                body = stringResource(R.string.activity_no_results_body),
                                action = stringResource(R.string.activity_clear_filters),
                                onAction = actions.onClearFilters,
                                modifier = gutter.testTag("activity-no-results"),
                            )
                        }
                    }
                } else {
                    items(content.list.rows, key = { it.entry.id.value }) { row ->
                        EntryRow(row, onClick = { actions.onOpenEntry(row.entry.id.value) }, modifier = gutter)
                    }
                }
            }
        }
    }

    if (showFilters) {
        ActivityFilterSheet(
            filter = state.filter,
            accounts = state.accounts,
            today = state.today,
            previewCount = actions.previewCount,
            onApply = { actions.onApplyFilter(it); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
}

// --- Hero and controls ------------------------------------------------------------------------

@Composable
private fun ActivityHero(received: String, spent: String, rangeLabel: String, onRange: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(bottomStart = ZarDimens.HeroRadius, bottomEnd = ZarDimens.HeroRadius)),
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF05070B), Color(0xFF0C1A2B), Color(0xFF174257)))),
        ) {
            Text(
                stringResource(R.string.nav_activity),
                style = MaterialTheme.typography.headlineLarge,
                color = ZarWhite,
                modifier = Modifier.padding(start = 22.dp, top = 22.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 20.dp)) {
                Text(
                    received,
                    style = MaterialTheme.typography.headlineLarge,
                    color = ZarWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("activity-received"),
                )
                ZarLabel(stringResource(R.string.home_received), color = ZarWhite)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A070C), Color(0xFF2A1828), Color(0xFF7E3D52)))),
        ) {
            val changeRange = stringResource(R.string.activity_change_range)
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 14.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = changeRange, onClick = onRange)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("activity-range"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZarLabel(rangeLabel, color = ZarWhite)
                ZarIcon(R.drawable.ic_ph_caret_down, size = 12.dp, tint = ZarWhite)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 20.dp)) {
                Text(
                    spent,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ZarWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("activity-spent"),
                )
                ZarLabel(stringResource(R.string.home_spent), color = ZarWhite)
            }
        }
    }
}

@Composable
private fun FilterRow(
    state: ActivityUiState,
    onOpenFilters: () -> Unit,
    onToggleSearch: () -> Unit,
    onApplyFilter: (ActivityFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = state.filter
    val colors = ZarTheme.colors
    val accountName = state.accounts.firstOrNull { it.id == filter.accountId }?.name
    val remove = stringResource(R.string.activity_remove_filter)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chipsActive = filter.accountId != null || filter.preset != DatePreset.THIS_MONTH || filter.showVoided
            if (!chipsActive) {
                FilterPill(stringResource(R.string.activity_filter_all), selected = true, modifier = Modifier.testTag("activity-chip-all"))
            }
            if (filter.accountId != null) {
                FilterPill(
                    label = accountName ?: stringResource(R.string.activity_filter_account),
                    selected = true,
                    onClick = { onApplyFilter(filter.copy(accountId = null)) },
                    removeLabel = remove,
                    modifier = Modifier.testTag("activity-chip-account"),
                )
            }
            if (filter.preset != DatePreset.THIS_MONTH) {
                FilterPill(
                    label = presetChipLabel(state),
                    selected = true,
                    onClick = { onApplyFilter(filter.copy(preset = DatePreset.THIS_MONTH, customFrom = null, customTo = null)) },
                    removeLabel = remove,
                    modifier = Modifier.testTag("activity-chip-date"),
                )
            }
            if (filter.showVoided) {
                FilterPill(
                    label = stringResource(R.string.activity_filter_voided),
                    selected = true,
                    onClick = { onApplyFilter(filter.copy(showVoided = false)) },
                    removeLabel = remove,
                    modifier = Modifier.testTag("activity-chip-voided"),
                )
            }
        }
        val filterLabel = stringResource(R.string.activity_open_filters)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(role = Role.Button, onClickLabel = filterLabel, onClick = onOpenFilters)
                .semantics { contentDescription = filterLabel }
                .testTag("activity-filter-button"),
            Alignment.Center,
        ) {
            ZarIcon(R.drawable.ic_ph_funnel, size = 18.dp)
            if (filter.activeCount > 0) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(colors.accentHighlight))
            }
        }
        val searchLabel = stringResource(R.string.activity_search)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(role = Role.Button, onClickLabel = searchLabel, onClick = onToggleSearch)
                .semantics { contentDescription = searchLabel }
                .testTag("activity-search-button"),
            Alignment.Center,
        ) { ZarIcon(R.drawable.ic_ph_magnifying_glass, size = 18.dp) }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, requestFocus: Boolean, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    // Opening the search is a request to type, so the field takes focus and the keyboard right away.
    val focus = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if (requestFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.surface).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIcon(R.drawable.ic_ph_magnifying_glass, size = 18.dp, tint = colors.iconMuted)
        TextInput(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.activity_search_hint),
            modifier = Modifier.weight(1f).focusRequester(focus).testTag("activity-search-field"),
        )
    }
}

@Composable
private fun RangeLine(state: ActivityUiState, list: ActivityList?, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ZarMonoLabel(rangeLabel(state), color = ZarTheme.colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (list != null) {
                Text(
                    pluralStringResource(R.plurals.activity_count, list.rows.size, list.rows.size),
                    style = ZarType.bodyCaption,
                    color = ZarTheme.colors.textPrimary,
                    modifier = Modifier.testTag("activity-count"),
                )
            }
            if (state.filter.activeCount > 0) {
                Text(
                    stringResource(R.string.activity_clear),
                    style = ZarType.labelChip,
                    color = ZarTheme.colors.textSelected,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onClear).padding(vertical = 4.dp).testTag("activity-clear"),
                )
            }
        }
    }
}

// --- Rows -------------------------------------------------------------------------------------

@Composable
private fun EntryRow(row: ActivityRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tx = row.entry.current
    val colors = ZarTheme.colors
    val voided = row.entry.isVoided
    ZarCard(
        modifier = modifier.fillMaxWidth().alpha(if (voided) 0.6f else 1f).testTag("activity-row-${row.entry.id.value}"),
        onClick = onClick,
        radius = ZarDimens.RowCardRadius,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIconChip(EntryDisplay.icon(tx))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entryTitle(tx),
                        style = ZarType.bodyM.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (voided) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (voided) TagPill(stringResource(R.string.activity_tag_void), attention = true)
                    else if (row.entry.isCorrected) TagPill(stringResource(R.string.activity_tag_edited))
                }
                Text(
                    entrySubtitle(tx, row.account?.name),
                    style = ZarType.bodyCaption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                EntryDisplay.signed(tx),
                style = ZarType.bodyL,
                color = if (EntryDisplay.isInflow(tx)) colors.textIncome else colors.textPrimary,
                textDecoration = if (voided) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
        }
    }
}

// --- Filter sheet -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ActivityFilterSheet(
    filter: ActivityFilter,
    accounts: List<Account>,
    today: LocalDate,
    previewCount: (ActivityFilter) -> Int,
    onApply: (ActivityFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(filter) }
    val count = previewCount(draft)
    val close = stringResource(R.string.action_close)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = ZarTheme.colors.surface,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 22.dp)
                .testTag("activity-filter-sheet"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity_filter_title), style = ZarType.headingSheet, color = ZarTheme.colors.textPrimary)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = close, onClick = onDismiss)
                        .semantics { contentDescription = close },
                    Alignment.Center,
                ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarMonoLabel(stringResource(R.string.manual_field_account))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZarChip(
                        label = stringResource(R.string.activity_filter_all_accounts),
                        selected = draft.accountId == null,
                        onClick = { draft = draft.copy(accountId = null) },
                        modifier = Modifier.testTag("activity-filter-account-all"),
                    )
                    accounts.forEach { account ->
                        ZarChip(
                            label = account.name,
                            selected = draft.accountId == account.id,
                            onClick = { draft = draft.copy(accountId = account.id) },
                            icon = account.type.iconRes(),
                            modifier = Modifier.testTag("activity-filter-account-${account.id.value}"),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZarMonoLabel(stringResource(R.string.manual_field_date))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DatePreset.THIS_MONTH to R.string.activity_preset_this_month,
                        DatePreset.LAST_7_DAYS to R.string.activity_preset_7_days,
                        DatePreset.LAST_MONTH to R.string.activity_preset_last_month,
                        DatePreset.CUSTOM to R.string.activity_preset_custom,
                    ).forEach { (preset, label) ->
                        ZarChip(
                            label = stringResource(label),
                            selected = draft.preset == preset,
                            onClick = {
                                draft = if (preset == DatePreset.CUSTOM) {
                                    draft.copy(
                                        preset = preset,
                                        customFrom = draft.customFrom ?: today.withDayOfMonth(1),
                                        customTo = draft.customTo ?: today,
                                    )
                                } else {
                                    draft.copy(preset = preset)
                                }
                            },
                            modifier = Modifier.testTag("activity-filter-preset-${preset.name.lowercase()}"),
                        )
                    }
                }
                if (draft.preset == DatePreset.CUSTOM) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField(
                            label = stringResource(R.string.activity_range_from),
                            date = draft.customFrom ?: today,
                            onPick = { draft = draft.copy(customFrom = it) },
                            modifier = Modifier.weight(1f).testTag("activity-filter-from"),
                        )
                        DateField(
                            label = stringResource(R.string.activity_range_to),
                            date = draft.customTo ?: today,
                            onPick = { draft = draft.copy(customTo = it) },
                            modifier = Modifier.weight(1f).testTag("activity-filter-to"),
                        )
                    }
                }
            }

            ZarDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.activity_show_voided), style = ZarType.labelButton, color = ZarTheme.colors.textPrimary)
                    Text(stringResource(R.string.activity_show_voided_hint), style = ZarType.bodyCaption, color = ZarTheme.colors.textSecondary)
                }
                ZarSwitch(
                    checked = draft.showVoided,
                    onCheckedChange = { draft = draft.copy(showVoided = it) },
                    modifier = Modifier.testTag("activity-filter-voided"),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(
                    text = stringResource(R.string.activity_filter_reset),
                    onClick = { draft = ActivityFilter(query = draft.query) },
                    modifier = Modifier.weight(1f).testTag("activity-filter-reset"),
                    style = ZarButtonStyle.Soft,
                )
                ZarButton(
                    text = pluralStringResource(R.plurals.activity_filter_apply, count, count),
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f).testTag("activity-filter-apply"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Column(
        modifier.clip(ZarShapes.sm).background(colors.subtle).clickable(role = Role.Button) { open = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ZarMonoLabel(label)
        Text(IncomeFormat.fullDate(date), style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1)
    }
    if (!open) return
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

// --- Labels -----------------------------------------------------------------------------------

/** The short label in the hero: OCT, SEP, 7 DAYS or CUSTOM. */
@Composable
private fun heroRangeLabel(state: ActivityUiState): String = when (state.filter.preset) {
    DatePreset.THIS_MONTH -> monthName(YearMonth.from(state.today), short = true)
    DatePreset.LAST_MONTH -> monthName(YearMonth.from(state.today).minusMonths(1), short = true)
    DatePreset.LAST_7_DAYS -> stringResource(R.string.activity_hero_7_days)
    DatePreset.CUSTOM -> stringResource(R.string.activity_hero_custom)
}

/** The line above the list: OCTOBER 2026, or the exact days when a range is chosen. */
@Composable
private fun rangeLabel(state: ActivityUiState): String = when (state.filter.preset) {
    DatePreset.THIS_MONTH -> IncomeFormat.month(YearMonth.from(state.today))
    DatePreset.LAST_MONTH -> IncomeFormat.month(YearMonth.from(state.today).minusMonths(1))
    else -> rangeText(ActivityQuery.rangeOf(state.filter, state.today))
}

/** The text of a date chip: "Last month", "Last 7 days" or "1–24 Oct". */
@Composable
private fun presetChipLabel(state: ActivityUiState): String = when (state.filter.preset) {
    DatePreset.LAST_MONTH -> stringResource(R.string.activity_preset_last_month)
    DatePreset.LAST_7_DAYS -> stringResource(R.string.activity_preset_7_days)
    else -> rangeText(ActivityQuery.rangeOf(state.filter, state.today), upper = false)
}

private fun rangeText(range: ClosedRange<LocalDate>, upper: Boolean = true): String {
    val from = range.start
    val to = range.endInclusive
    val text = if (YearMonth.from(from) == YearMonth.from(to)) {
        "${from.dayOfMonth}–${to.dayOfMonth} ${monthName(YearMonth.from(to), short = true, upper = false)}"
    } else {
        "${EntryDisplay.dayMonth(from)} – ${EntryDisplay.dayMonth(to)}"
    }
    return if (upper) text.uppercase(Locale.ENGLISH) else text
}

private fun monthName(month: YearMonth, short: Boolean, upper: Boolean = true): String {
    val name = month.month.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.ENGLISH)
    return if (upper) name.uppercase(Locale.ENGLISH) else name
}
