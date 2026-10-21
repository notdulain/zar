package com.example.zar.features.transactions.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
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
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.core.presentation.theme.ZarWhite
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
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim
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
        heroUnderStatusBar = true,
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
            contentPadding = padding,
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
internal fun ActivityBody(
    state: ActivityUiState,
    actions: ActivityActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(state.filter.query.isNotEmpty()) }
    // Only opening the search with its button takes focus; coming back to a screen that already has it open does not.
    var focusSearch by remember { mutableStateOf(false) }
    val list = (state.content as? ActivityContent.Ready)?.list
    val layoutDirection = LocalLayoutDirection.current
    val gutter = Modifier.padding(horizontal = ListGutter)

    // The hero runs under the status bar and the list scrolls behind the floating nav, so only the sides are
    // inset here; the bottom inset becomes scroll padding that lets the last row clear the nav.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + ZarDimens.SpaceXl),
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
                modifier = gutter.padding(top = 10.dp),
            )
        }
        if (searchOpen) {
            item { SearchField(state.filter.query, actions.onQuery, focusSearch, gutter) }
        }
        item { RangeLine(state, list, actions.onClearFilters, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)) }
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
            if (list.summary.unclassifiedAdjustmentCount > 0) {
                item {
                    Text(
                        stringResource(R.string.activity_has_adjustments_note),
                        style = ZarType.bodyCaption,
                        color = ZarTheme.colors.textSecondary,
                        modifier = gutter.testTag("activity-adjustments"),
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

private val ListGutter = 10.dp
private val HeroHeight = 224.dp
private val HeroRadius = 40.dp
private val FilterBorder = Color(0xFFE6E6E9)

// Figma's gradients run past the bottom of the hero, so only the dark part of each shows. The stops are
// scaled to a gradient that is [Received/SpentSpan] times the hero height.
private const val ReceivedSpan = 1.2868f
private val ReceivedStops = arrayOf(
    0f to Color(0xFF05070B),
    0.45f to Color(0xFF0C1A2B),
    0.8f to Color(0xFF174257),
    1f to Color(0xFF2E7F86),
)
private const val SpentSpan = 1.2445f
private val SpentStops = arrayOf(
    0f to Color(0xFF0A070C),
    0.3818f to Color(0xFF2A1828),
    0.8f to Color(0xFF7E3D52),
    1f to Color(0xFFC9685A),
)

/** The hero is 224dp from the very top of the screen (the status bar sits on it); Received takes 277 of 412 and Spent the rest. */
@Composable
private fun ActivityHero(received: String, spent: String, rangeLabel: String, onRange: () -> Unit) {
    // Figma's hero spans the status bar. Pulling the list down past its top reveals the area above the hero,
    // so each half's first colour is also painted far above it and the gradient never ends.
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .drawBehind {
                val split = size.width * 277f / 412f
                val reach = 4000f
                drawRect(Color(0xFF05070B), Offset(0f, -reach), Size(split, reach))
                drawRect(Color(0xFF0A070C), Offset(split, -reach), Size(size.width - split, reach))
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(bottomStart = HeroRadius, bottomEnd = HeroRadius)),
        ) {
            Box(Modifier.weight(277f).fillMaxSize().gradientBackground(ReceivedStops, ReceivedSpan))
            Box(Modifier.weight(135f).fillMaxSize().gradientBackground(SpentStops, SpentSpan))
        }
        Row(
            Modifier
                .padding(start = 22.dp, end = 22.dp, top = 62.dp)
                .fillMaxWidth()
                .height(42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.nav_activity),
                style = ZarType.displayTitle.copy(lineHeight = 42.sp),
                color = ZarWhite,
            )
            val changeRange = stringResource(R.string.activity_change_range)
            Row(
                Modifier
                    // The padding makes a roomy touch target; the offset pulls the label back to Figma's 22dp edge.
                    .offset(x = 8.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = changeRange, onClick = onRange)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("activity-range"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rangeLabel.uppercase(), style = ZarType.monoLabel.copy(lineHeight = 14.sp), color = ZarWhite)
                ZarIcon(R.drawable.ic_ph_caret_down, size = 12.dp, tint = ZarWhite)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            HeroTotal(received, stringResource(R.string.home_received), Alignment.Start, "activity-received")
            HeroTotal(spent, stringResource(R.string.home_spent), Alignment.End, "activity-spent")
        }
    }
}

@Composable
private fun HeroTotal(amount: String, label: String, align: Alignment.Horizontal, tag: String) {
    Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            amount,
            style = ZarType.amountM.copy(lineHeight = 36.sp),
            color = ZarWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(tag),
        )
        Text(
            label.uppercase(),
            style = ZarType.monoCaption.copy(lineHeight = 13.sp, letterSpacing = 1.sp),
            color = ZarWhite,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

/** A vertical gradient whose [stops] are fractions of a gradient [span] times as tall as the box; the rest is cut off. */
private fun Modifier.gradientBackground(stops: Array<Pair<Float, Color>>, span: Float): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height * span))
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
                    removable = true,
                    modifier = Modifier.testTag("activity-chip-account"),
                )
            }
            if (filter.preset != DatePreset.THIS_MONTH) {
                FilterPill(
                    label = presetChipLabel(state),
                    selected = true,
                    onClick = { onApplyFilter(filter.copy(preset = DatePreset.THIS_MONTH, customFrom = null, customTo = null)) },
                    removeLabel = remove,
                    removable = true,
                    modifier = Modifier.testTag("activity-chip-date"),
                )
            }
            if (filter.showVoided) {
                FilterPill(
                    label = stringResource(R.string.activity_filter_voided),
                    selected = true,
                    onClick = { onApplyFilter(filter.copy(showVoided = false)) },
                    removeLabel = remove,
                    removable = true,
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
                .border(1.dp, FilterBorder, CircleShape)
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
                .border(1.dp, FilterBorder, CircleShape)
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
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            rangeLabel(state).uppercase(),
            style = ZarType.monoLabel.copy(lineHeight = 14.sp),
            color = ZarTheme.colors.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (list != null) {
                Text(
                    pluralStringResource(R.plurals.activity_count, list.rows.size, list.rows.size),
                    style = ZarType.bodyCaption.copy(lineHeight = 16.sp),
                    color = ZarTheme.colors.textTertiary,
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
    val inflow = EntryDisplay.isInflow(tx)
    // Figma's card is 70dp: a 14/18dp inner padding plus the 1dp border.
    ZarCard(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (voided) 0.6f else 1f)
            .testTag("activity-row-${row.entry.id.value}"),
        onClick = onClick,
        radius = 24.dp,
        contentPadding = PaddingValues(start = 15.dp, end = 19.dp, top = 15.dp, bottom = 15.dp),
        soft = true,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIconChip(
                EntryDisplay.icon(tx),
                background = if (inflow) colors.incomeTint else colors.subtle,
                tint = if (inflow) colors.textIncome else colors.iconStrong,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entryTitle(tx, row.account?.type),
                        style = ZarType.bodyM.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = (-0.15).sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (voided) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (voided) TagPill(stringResource(R.string.activity_tag_void), attention = true)
                    else if (row.entry.isCorrected) TagPill(stringResource(R.string.activity_tag_edited))
                    else if (tx.type == TransactionType.ADJUSTMENT_OUT) TagPill(stringResource(R.string.activity_tag_unknown))
                }
                Text(
                    entrySubtitle(tx, row.account?.name),
                    style = ZarType.bodyCaption.copy(lineHeight = 16.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                EntryDisplay.signed(tx),
                style = ZarType.bodyL.copy(lineHeight = 21.sp),
                color = if (inflow) colors.textIncome else colors.textPrimary,
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
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        // Figma Z45: 24dp margins, 20dp between blocks, 12dp from a label to its chips, 8dp between chip rows.
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 4.dp)
                .testTag("activity-filter-sheet"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SheetHeader(stringResource(R.string.activity_filter_title), onClose = onDismiss)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetLabel(stringResource(R.string.manual_field_account))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(
                        label = stringResource(R.string.activity_filter_all_accounts),
                        selected = draft.accountId == null,
                        onClick = { draft = draft.copy(accountId = null) },
                        modifier = Modifier.testTag("activity-filter-account-all"),
                    )
                    accounts.forEach { account ->
                        FilterPill(
                            label = account.name,
                            selected = draft.accountId == account.id,
                            onClick = { draft = draft.copy(accountId = account.id) },
                            modifier = Modifier.testTag("activity-filter-account-${account.id.value}"),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetLabel(stringResource(R.string.manual_field_date))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DatePreset.THIS_MONTH to R.string.activity_preset_this_month,
                        DatePreset.LAST_7_DAYS to R.string.activity_preset_7_days,
                        DatePreset.LAST_MONTH to R.string.activity_preset_last_month,
                        DatePreset.CUSTOM to R.string.activity_preset_custom,
                    ).forEach { (preset, label) ->
                        FilterPill(
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
                    large = true,
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

/** The small mono caption above a group of chips, with Figma's 14dp line. */
@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), style = ZarType.monoLabel.copy(lineHeight = 14.sp), color = ZarTheme.colors.textTertiary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(colors.subtle).clickable(role = Role.Button) { open = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = ZarType.monoLabel.copy(lineHeight = 14.sp), color = colors.textTertiary)
        Text(EntryDisplay.dayMonth(date), style = ZarType.bodyM.copy(lineHeight = 20.sp), color = colors.textPrimary, maxLines = 1)
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

/** The line above the list: OCTOBER (with the year only when it is not this year), or the exact days of a range. */
@Composable
private fun rangeLabel(state: ActivityUiState): String = when (state.filter.preset) {
    DatePreset.THIS_MONTH -> monthLine(YearMonth.from(state.today), state.today)
    DatePreset.LAST_MONTH -> monthLine(YearMonth.from(state.today).minusMonths(1), state.today)
    else -> rangeText(ActivityQuery.rangeOf(state.filter, state.today))
}

private fun monthLine(month: YearMonth, today: LocalDate): String =
    if (month.year == today.year) monthName(month, short = false) else IncomeFormat.month(month)

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
