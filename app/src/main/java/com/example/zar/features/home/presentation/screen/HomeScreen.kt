package com.example.zar.features.home.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.components.ZarSplitBar
import com.example.zar.core.presentation.format.formatForRow
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarHighlight
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.viewmodel.HomeAllowanceContent
import com.example.zar.features.home.presentation.viewmodel.HomeCommitmentsContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import com.example.zar.features.home.presentation.viewmodel.HomeViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val AxisFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenReview: () -> Unit,
    onOpenFreelance: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBill: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onRetry = viewModel::retry,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        onOpenReview = onOpenReview,
        onOpenFreelance = onOpenFreelance,
        onOpenBills = onOpenBills,
        onOpenBill = onOpenBill,
    )
}

@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenReview: () -> Unit = {},
    onOpenFreelance: () -> Unit = {},
    onOpenBills: () -> Unit = {},
    onOpenBill: (String) -> Unit = {},
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Home,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when (val content = state.content) {
                HomeContent.Loading -> HomeStateBlock(state.month, state.canGoForward, onPreviousMonth, onNextMonth) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.home_loading), style = ZarType.bodyS)
                }
                HomeContent.Error -> HomeStateBlock(state.month, state.canGoForward, onPreviousMonth, onNextMonth) {
                    ZarCard(Modifier.fillMaxWidth().testTag("home-error")) {
                        Text(stringResource(R.string.home_error), style = ZarType.bodyS)
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("home-retry")) {
                            Text(stringResource(R.string.home_retry))
                        }
                    }
                }
                is HomeContent.Ready -> OverviewBlock(
                    overview = content.overview,
                    canGoForward = state.canGoForward,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onNavigate = onNavigate,
                    onOpenReview = onOpenReview,
                    commitments = state.commitments,
                    allowance = state.allowance,
                    onRetry = onRetry,
                    onOpenFreelance = onOpenFreelance,
                    onOpenBills = onOpenBills,
                    onOpenBill = onOpenBill,
                )
            }
        }
    }
}

@Composable
private fun HomeStateBlock(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 50.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MonthPicker(month, canGoForward, onPrevious, onNext, onDark = false)
        content()
    }
}

@Composable
private fun OverviewBlock(
    overview: HomeOverview,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNavigate: (TopLevelDestination) -> Unit,
    onOpenReview: () -> Unit,
    commitments: HomeCommitmentsContent,
    allowance: HomeAllowanceContent,
    onRetry: () -> Unit,
    onOpenFreelance: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBill: (String) -> Unit,
) {
    ReceivedHero(overview, canGoForward, onPreviousMonth, onNextMonth)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 9.dp).padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickNavigation(onNavigate)
        if (overview.hasIncompleteData) {
            ReviewPrompt(overview, onClick = onOpenReview)
        }
        SpendingCard(overview, onClick = { onNavigate(TopLevelDestination.Activity) })
        GoalLinkCard(onClick = { onNavigate(TopLevelDestination.Plans) })
        RecentActivity(overview.recent)
        ClientsOweRow(commitments, onClick = onOpenFreelance, onRetry = onRetry)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NextBillCard(
                commitments = commitments,
                onOpenBill = onOpenBill,
                onOpenBills = onOpenBills,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            WeeklyLimitCard(
                allowance = allowance,
                onOpenPlans = { onNavigate(TopLevelDestination.Plans) },
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReceivedHero(
    overview: HomeOverview,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val colors = ZarTheme.colors
    var chartMode by rememberSaveable { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxWidth().height(507.dp)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(ZarGradients.dusk)
            .testTag("received-card"),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp)) {
            Spacer(Modifier.height(54.dp))
            ChartModeTabs(selected = chartMode, onSelect = { chartMode = it })
            Spacer(Modifier.height(4.dp))
            MonthPicker(
                month = overview.month,
                canGoForward = canGoForward,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onDark = true,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.home_received_this_month).uppercase(),
                style = ZarType.monoLabel,
                color = colors.textOnInverse,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                overview.summary.received.amount.formatWithCode(),
                style = ZarType.amountL,
                color = colors.textOnInverse,
                modifier = Modifier.testTag("received-amount"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            HeroStatus(overview)
            Spacer(Modifier.weight(1f))
            val chartModifier = Modifier.fillMaxWidth().height(165.dp).padding(horizontal = 18.dp)
            when (chartMode) {
                0 -> ReceivedBarChart(overview, chartModifier)
                1 -> SpendingDonutChart(overview, chartModifier)
                else -> ReceivedLineChart(overview, chartModifier)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(overview.month.atDay(1).format(AxisFormat).uppercase(), style = ZarType.monoLabel, color = colors.textOnInverse)
                Text(overview.month.atEndOfMonth().format(AxisFormat).uppercase(), style = ZarType.monoLabel, color = colors.textOnInverse)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ChartModeTabs(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(R.drawable.ic_ph_chartbar, R.drawable.ic_ph_chartdonut, R.drawable.ic_ph_chartlineup)
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.Center) {
        icons.forEachIndexed { index, icon ->
            Column(
                Modifier.width(48.dp).height(40.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(icon), contentDescription = when (index) {
                    0 -> "Bar chart"
                    1 -> "Spending donut"
                    else -> "Received trend"
                }, modifier = Modifier.size(20.dp), tint = Color.Unspecified)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(22.dp).height(2.dp).background(if (selected == index) Color.White else Color.Transparent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
private fun SpendingDonutChart(overview: HomeOverview, modifier: Modifier = Modifier) {
    val spent = overview.summary.spent
    val values = listOf(
        spent.committed.amount.minorUnits.coerceAtLeast(0),
        spent.discretionary.amount.minorUnits.coerceAtLeast(0),
        spent.unknown.amount.minorUnits.coerceAtLeast(0),
    )
    val total = values.sum().coerceAtLeast(1L).toFloat()
    val colors = listOf(ZarTheme.colors.accentSelected, ZarTheme.colors.attention, Color(0xFFE6E6EA))
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(148.dp)) {
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = value / total * 360f
                drawArc(colors[index], start, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(18.dp.toPx()))
                start += sweep
            }
        }
        Text(spent.total.amount.formatWithCode(), style = ZarType.bodyS, color = Color.White)
    }
}

@Composable
private fun ReceivedLineChart(overview: HomeOverview, modifier: Modifier = Modifier) {
    val days = overview.receivedByDay
    val maximum = days.maxOfOrNull { it.amount.minorUnits }?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier) {
        if (days.size < 2) return@Canvas
        val line = Path()
        days.forEachIndexed { index, total ->
            val x = size.width * index / (days.size - 1)
            val y = size.height * (1f - total.amount.minorUnits.toFloat() / maximum)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        drawPath(line, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

@Composable
private fun MonthPicker(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDark: Boolean,
) {
    val color = if (onDark) Color.White else ZarTheme.colors.textPrimary
    val previous = stringResource(R.string.home_previous_month)
    val next = stringResource(R.string.home_next_month)
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp).testTag("month-previous").semantics { contentDescription = previous }) {
            Text("‹", style = ZarType.headingSheet, color = color)
        }
        Text(
            month.format(MonthFormat).uppercase(),
            modifier = Modifier.width(154.dp).testTag("month-label"),
            textAlign = TextAlign.Center,
            style = ZarType.monoLabel,
            color = color,
            maxLines = 1,
        )
        IconButton(
            onClick = onNext,
            enabled = canGoForward,
            modifier = Modifier.size(40.dp).testTag("month-next").semantics { contentDescription = next },
        ) {
            Text("›", style = ZarType.headingSheet, color = color.copy(alpha = if (canGoForward) 1f else 0.35f))
        }
    }
}

@Composable
private fun HeroStatus(overview: HomeOverview) {
    val colors = ZarTheme.colors
    val hasMissingRate = overview.missingRateCount > 0
    val note = when {
        hasMissingRate -> stringResource(R.string.home_warning_prefix) + " " +
            stringResource(R.string.home_coverage_missing_rate, overview.missingRateCount)
        overview.adjustmentCount > 0 -> stringResource(R.string.home_coverage_adjustments, overview.adjustmentCount)
        else -> stringResource(R.string.home_received_note_short)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(5.dp).background(colors.fresh, CircleShape))
        Text(
            note,
            style = ZarType.bodyCaption,
            color = colors.textOnInverse.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(if (hasMissingRate) "received-warning" else "received-status"),
        )
    }
}

@Composable
private fun ReceivedBarChart(overview: HomeOverview, modifier: Modifier = Modifier) {
    val days = overview.receivedByDay
    val maxAmount = days.maxOfOrNull { it.amount.minorUnits } ?: 0L
    val description = stringResource(R.string.home_received_chart, overview.month.format(MonthFormat)) + " " +
        if (maxAmount == 0L) stringResource(R.string.home_received_chart_empty) else {
            days.mapIndexedNotNull { index, total ->
                if (total.amount.isZero) null else "${overview.month.atDay(index + 1).format(DayFormat)}: ${total.amount.formatWithCode()}"
            }.joinToString("; ")
        }
    Box(modifier.semantics { contentDescription = description }.testTag("received-chart")) {
        if (maxAmount == 0L || days.isEmpty()) {
            Text(
                stringResource(R.string.home_received_chart_empty),
                color = Color.White.copy(alpha = 0.7f),
                style = ZarType.bodyCaption,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val slotWidth = size.width / days.size
                val barWidth = 5.dp.toPx().coerceAtMost(slotWidth * 0.7f)
                days.forEachIndexed { index, total ->
                    val barHeight = size.height * (total.amount.minorUnits.toDouble() / maxAmount).toFloat()
                    val progress = index.toFloat() / days.lastIndex.coerceAtLeast(1)
                    val opacity = 0.18f + 0.82f * progress * progress * progress
                    drawRoundRect(
                        color = if (index == days.lastIndex) ZarHighlight else Color.White.copy(alpha = opacity),
                        topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickNavigation(onNavigate: (TopLevelDestination) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        QuickCard(stringResource(R.string.nav_activity), stringResource(R.string.home_activity_summary), R.drawable.ic_ph_chartline_home, Modifier.weight(1f)) {
            onNavigate(TopLevelDestination.Activity)
        }
        QuickCard(stringResource(R.string.nav_plans), stringResource(R.string.home_plans_summary), R.drawable.ic_ph_flag_home, Modifier.weight(1f)) {
            onNavigate(TopLevelDestination.Plans)
        }
        QuickCard(stringResource(R.string.nav_more), stringResource(R.string.home_accounts_summary), R.drawable.ic_ph_gearsix_home, Modifier.weight(1f).testTag("nav-more")) {
            onNavigate(TopLevelDestination.More)
        }
    }
}

@Composable
private fun QuickCard(title: String, detail: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    ZarCard(modifier.height(114.dp), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(colors.subtle, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Unspecified)
            }
            Icon(painterResource(R.drawable.ic_ph_arrowupright), contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Unspecified)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = ZarType.bodyM, color = colors.textPrimary)
        Text(detail, style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReviewPrompt(overview: HomeOverview, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val count = overview.missingRateCount + overview.adjustmentCount + if (overview.hasUnknownSpending) 1 else 0
    ZarCard(Modifier.fillMaxWidth().height(60.dp).testTag("home-review"), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp).background(colors.attention, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_needs_review, count), style = ZarType.bodyM, color = colors.textPrimary)
                Text(stringResource(R.string.home_review_detail), style = ZarType.bodyCaption, color = colors.textSecondary)
            }
            Text(stringResource(R.string.home_review_action), style = ZarType.labelAction, color = colors.textSelected)
        }
    }
}

@Composable
private fun SpendingCard(overview: HomeOverview, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val summary = overview.summary.spent
    ZarCard(Modifier.fillMaxWidth().testTag("spent-card"), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(stringResource(R.string.home_spent).uppercase(), style = ZarType.monoLabel, color = colors.textPrimary)
                Text(stringResource(R.string.home_this_month).uppercase(), style = ZarType.monoLabel, color = colors.textTertiary)
            }
            Text(summary.total.amount.formatWithCode(), style = ZarType.amountM, color = colors.textPrimary, modifier = Modifier.testTag("spent-amount"))
        }
        Spacer(Modifier.height(8.dp))
        SplitLine(R.string.home_spent_committed, summary.committed, "spent-committed", colors.accentSelected)
        SplitLine(R.string.home_spent_discretionary, summary.discretionary, "spent-discretionary", colors.attention)
        SplitLine(R.string.home_spent_unknown, summary.unknown, "spent-unknown", Color(0xFFE6E6EA))
        Spacer(Modifier.height(10.dp))
        ZarSplitBar(
            committed = summary.committed.amount.minorUnits.toFloat(),
            discretionary = summary.discretionary.amount.minorUnits.toFloat(),
            unknown = summary.unknown.amount.minorUnits.toFloat(),
        )
        if (overview.hasIncompleteData) {
            Text(
                stringResource(R.string.home_coverage_unknown),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("unknown-warning"),
            )
        }
        Text(
            stringResource(R.string.home_see_breakdown),
            style = ZarType.labelAction,
            color = colors.textSelected,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun SplitLine(@StringRes label: Int, total: ReportingTotal, tag: String, dotColor: Color) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth().height(17.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label).uppercase(), style = ZarType.monoLabel, color = colors.textTertiary)
        Spacer(Modifier.width(8.dp))
        Text(total.amount.formatWithCode(), style = ZarType.monoLabel, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun GoalLinkCard(onClick: () -> Unit) {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(19.dp)) {
        Text(stringResource(R.string.home_goal).uppercase(), style = ZarType.monoLabel, color = colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.home_goal_preview), style = ZarType.bodyM, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).background(colors.borderHairline, RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_view_goal), style = ZarType.labelAction, color = colors.textSelected, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun RecentActivity(recent: List<LedgerEntry>) {
    val colors = ZarTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp).padding(top = 2.dp)) {
        Text(stringResource(R.string.home_recent_activity).uppercase(), style = ZarType.monoLabel, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        if (recent.isEmpty()) {
            Text(stringResource(R.string.home_recent_empty), style = ZarType.bodyCaption, color = colors.textSecondary)
        } else {
            recent.forEach { entry ->
                RecentRow(entry)
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderHairline))
            }
        }
    }
}

@Composable
private fun RecentRow(entry: LedgerEntry) {
    val tx = entry.current
    val typeLabel = stringResource(typeLabelRes(tx.type))
    val sign = if (tx.direction == Direction.IN) "+" else "−"
    Row(Modifier.fillMaxWidth().height(39.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tx.merchant?.takeIf { it.isNotBlank() } ?: typeLabel,
            modifier = Modifier.weight(1f),
            style = ZarType.bodyS,
            color = ZarTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$sign ${tx.amount.formatForRow()}",
            style = ZarType.bodyS,
            color = if (tx.direction == Direction.IN) ZarTheme.colors.fresh else ZarTheme.colors.textPrimary,
        )
    }
}

@StringRes
private fun typeLabelRes(type: TransactionType): Int = when (type) {
    TransactionType.INCOME -> R.string.home_activity_income
    TransactionType.EXPENSE -> R.string.home_activity_expense
    TransactionType.TRANSFER_IN -> R.string.home_activity_transfer_in
    TransactionType.TRANSFER_OUT -> R.string.home_activity_transfer_out
    TransactionType.REFUND -> R.string.home_activity_refund
    TransactionType.ADJUSTMENT_IN -> R.string.home_activity_adjustment_in
    TransactionType.ADJUSTMENT_OUT -> R.string.home_activity_adjustment_out
}

@Preview(showBackground = true)
@Composable
private fun HomeIncompletePreview() {
    val zero = ReportingTotal.zero()
    val overview = HomeOverview(
        month = YearMonth.of(2026, 10),
        summary = PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), unclassifiedAdjustmentCount = 1),
        recent = emptyList(),
        hasRecordsInMonth = true,
    )
    ZarTheme {
        HomeScreenContent(
            state = HomeUiState(YearMonth.of(2026, 10), canGoForward = false, content = HomeContent.Ready(overview)),
            onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
        )
    }
}
