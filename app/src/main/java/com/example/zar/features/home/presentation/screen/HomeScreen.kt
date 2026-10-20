package com.example.zar.features.home.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.zar.core.presentation.components.ZarActivityRow
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.format.formatForRow
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarHighlight
import com.example.zar.core.presentation.theme.ZarNavy
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarWhite
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import com.example.zar.features.home.presentation.viewmodel.HomeViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
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
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Home,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
        ) {
            when (val content = state.content) {
                HomeContent.Loading -> Column(Modifier.padding(ZarDimens.ScreenGutter)) {
                    MonthRow(
                        month = state.month,
                        canGoForward = state.canGoForward,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth,
                        onMore = { onNavigate(TopLevelDestination.More) },
                    )
                    LoadingBlock()
                }
                HomeContent.Error -> Column(Modifier.padding(ZarDimens.ScreenGutter)) {
                    MonthRow(
                        month = state.month,
                        canGoForward = state.canGoForward,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth,
                        onMore = { onNavigate(TopLevelDestination.More) },
                    )
                    ErrorBlock(onRetry)
                }
                is HomeContent.Ready -> OverviewBlock(
                    overview = content.overview,
                    canGoForward = state.canGoForward,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun MonthRow(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onDark: Boolean = false,
) {
    val foreground = if (onDark) ZarWhite else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, modifier = Modifier.testTag("month-previous")) {
            Text("‹", style = MaterialTheme.typography.titleMedium, color = foreground)
        }
        Text(
            month.format(MonthFormat).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            modifier = Modifier.testTag("month-label"),
        )
        TextButton(onClick = onNext, enabled = canGoForward, modifier = Modifier.testTag("month-next")) {
            Text("›", style = MaterialTheme.typography.titleMedium, color = foreground)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onMore, modifier = Modifier.testTag("nav-more")) {
                Text(
                    stringResource(R.string.home_more_link).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = foreground,
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZarDimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorBlock(onRetry: () -> Unit) {
    ZarCard(Modifier.fillMaxWidth().testTag("home-error")) {
        Text(stringResource(R.string.home_error), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("home-retry")) {
            Text(stringResource(R.string.home_retry).uppercase(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OverviewBlock(
    overview: HomeOverview,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val summary = overview.summary
    val month = overview.month.format(MonthFormat)

    ReceivedHero(
        overview = overview,
        canGoForward = canGoForward,
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        onMore = { onNavigate(TopLevelDestination.More) },
    )

    Column(
        modifier = Modifier.padding(horizontal = ZarDimens.ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
    ) {
        ZarCard(Modifier.fillMaxWidth().testTag("spent-card")) {
            ZarLabel(stringResource(R.string.home_spent), color = MaterialTheme.colorScheme.onSurface)
            ZarLabel(month)
            Text(
                summary.spent.total.amount.formatWithCode(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = ZarDimens.SpaceSm).testTag("spent-amount"),
            )
            Column(
                modifier = Modifier.padding(top = ZarDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceXs),
            ) {
                SplitLine(R.string.home_spent_committed, summary.spent.committed, "spent-committed")
                SplitLine(R.string.home_spent_discretionary, summary.spent.discretionary, "spent-discretionary")
                SplitLine(R.string.home_spent_unknown, summary.spent.unknown, "spent-unknown")
            }
            if (summary.spent.total.unresolvedCount > 0) {
                CoverageNote(
                    stringResource(R.string.home_coverage_missing_rate, summary.spent.total.unresolvedCount),
                    isWarning = true,
                    tag = "spent-warning",
                )
            }
            if (overview.hasUnknownSpending) {
                CoverageNote(stringResource(R.string.home_coverage_unknown), isWarning = true, tag = "unknown-warning")
            }
            if (overview.adjustmentCount > 0) {
                CoverageNote(
                    stringResource(R.string.home_coverage_adjustments, overview.adjustmentCount),
                    isWarning = true,
                    tag = "adjustment-warning",
                )
            }
            if (overview.hasRecordsInMonth && !overview.hasIncompleteData) {
                CoverageNote(stringResource(R.string.home_coverage_complete), isWarning = false)
            }
        }

        if (!overview.hasRecordsInMonth) {
            Text(
                stringResource(R.string.home_month_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("month-empty"),
            )
        }

        ZarLabel(
            stringResource(R.string.home_recent_activity),
            modifier = Modifier.padding(top = ZarDimens.SpaceSm),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (overview.recent.isEmpty()) {
            Text(
                stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            overview.recent.forEach { entry -> RecentRow(entry) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd)) {
            StatCard(
                title = stringResource(R.string.home_bills),
                value = stringResource(R.string.home_bills_empty),
                onClick = { onNavigate(TopLevelDestination.Plans) },
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = stringResource(R.string.home_weekly_limit),
                value = stringResource(R.string.home_weekly_limit_empty),
                onClick = { onNavigate(TopLevelDestination.Plans) },
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
    onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(bottomStart = ZarDimens.HeroRadius, bottomEnd = ZarDimens.HeroRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070B), ZarNavy, Color(0xFF14304A)),
                ),
            )
            .testTag("received-card"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = ZarDimens.SpaceSm),
        ) {
            MonthRow(
                month = overview.month,
                canGoForward = canGoForward,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onMore = onMore,
                onDark = true,
            )
            ZarLabel(stringResource(R.string.home_received_this_month), color = ZarWhite)
            Text(
                overview.summary.received.amount.formatWithCode(),
                style = MaterialTheme.typography.headlineLarge,
                color = ZarWhite,
                modifier = Modifier.padding(top = ZarDimens.SpaceSm).testTag("received-amount"),
            )
            Text(
                stringResource(R.string.home_received_note_short),
                style = MaterialTheme.typography.bodySmall,
                color = ZarSubtle,
                modifier = Modifier.padding(top = ZarDimens.SpaceSm),
            )
            if (overview.summary.received.unresolvedCount > 0) {
                Text(
                    stringResource(R.string.home_coverage_missing_rate, overview.summary.received.unresolvedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZarHighlight,
                    modifier = Modifier.padding(top = ZarDimens.SpaceXs).testTag("received-warning"),
                )
            }
            ReceivedBarChart(
                hasRecords = overview.hasRecordsInMonth,
                modifier = Modifier.fillMaxWidth().height(190.dp).padding(top = ZarDimens.SpaceLg),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ZarLabel(stringResource(R.string.home_chart_start), color = ZarSubtle)
                ZarLabel(stringResource(R.string.home_chart_today), color = ZarWhite)
            }
        }
    }
}

@Composable
private fun ReceivedBarChart(hasRecords: Boolean, modifier: Modifier = Modifier) {
    val levels = if (hasRecords) {
        listOf(0.24f, 0.32f, 0.38f, 0.42f, 0.36f, 0.31f, 0.45f, 0.55f, 0.51f, 0.58f,
            0.66f, 0.63f, 0.72f, 0.76f, 0.82f, 0.74f, 0.68f, 0.79f, 0.88f, 0.84f,
            0.77f, 0.86f, 0.94f, 0.89f, 0.96f, 1f, 0.92f, 0.98f, 0.95f, 0.9f)
    } else {
        List(30) { 0.08f }
    }
    Canvas(modifier) {
        val gap = 5.dp.toPx()
        val barWidth = ((size.width - gap * (levels.size - 1)) / levels.size).coerceAtLeast(3.dp.toPx())
        levels.forEachIndexed { index, level ->
            val height = size.height * level
            val color = when {
                index == levels.lastIndex -> ZarHighlight
                index >= levels.lastIndex - 5 -> ZarWhite
                else -> ZarWhite.copy(alpha = 0.24f)
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun SplitLine(@StringRes label: Int, total: ReportingTotal, tag: String) {
    Row(Modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(total.amount.formatWithCode(), style = MaterialTheme.typography.labelLarge)
    }
}

/** Coverage text sits directly under the totals it explains. Warnings are bold and prefixed. */
@Composable
private fun CoverageNote(text: String, isWarning: Boolean, tag: String = "") {
    val prefix = if (isWarning) stringResource(R.string.home_warning_prefix) + " " else ""
    Text(
        prefix + text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isWarning) FontWeight.Bold else FontWeight.Normal,
        color = if (isWarning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = ZarDimens.SpaceSm).testTag(tag),
    )
}

@Composable
private fun RecentRow(entry: LedgerEntry) {
    val tx = entry.current
    val typeLabel = stringResource(typeLabelRes(tx.type))
    val flags = buildList {
        if (entry.isCorrected) add(stringResource(R.string.home_activity_corrected))
    }
    val detail = (listOf(typeLabel, tx.date.format(DayFormat)) + flags).joinToString(" · ")
    val sign = if (tx.direction == Direction.IN) "+" else "−"
    ZarActivityRow(
        title = tx.merchant?.takeIf { it.isNotBlank() } ?: typeLabel,
        detail = detail,
        amount = "$sign ${tx.amount.formatForRow()}",
    )
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

@Composable
private fun StatCard(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ZarCard(modifier, onClick = onClick) {
        ZarLabel(title)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = ZarDimens.SpaceLg),
        )
    }
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
