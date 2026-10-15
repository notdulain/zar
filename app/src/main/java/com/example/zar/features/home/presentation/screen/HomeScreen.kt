package com.example.zar.features.home.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
import com.example.zar.core.presentation.theme.ZarTheme
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
                .padding(padding)
                .padding(horizontal = ZarDimens.ScreenGutter, vertical = ZarDimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
        ) {
            // Home has no navbar, so the month picker row also carries the link to More.
            MonthRow(
                month = state.month,
                canGoForward = state.canGoForward,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onMore = { onNavigate(TopLevelDestination.More) },
            )
            when (val content = state.content) {
                HomeContent.Loading -> LoadingBlock()
                HomeContent.Error -> ErrorBlock(onRetry)
                is HomeContent.Ready -> OverviewBlock(content.overview, onNavigate)
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
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, modifier = Modifier.testTag("month-previous")) {
            Text("‹", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            month.format(MonthFormat).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag("month-label"),
        )
        TextButton(onClick = onNext, enabled = canGoForward, modifier = Modifier.testTag("month-next")) {
            Text("›", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onMore, modifier = Modifier.testTag("nav-more")) {
                Text(stringResource(R.string.home_more_link).uppercase(), style = MaterialTheme.typography.labelLarge)
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
private fun OverviewBlock(overview: HomeOverview, onNavigate: (TopLevelDestination) -> Unit) {
    val summary = overview.summary
    val month = overview.month.format(MonthFormat)

    // Received is the headline number. The month is shown separately, not in the label.
    ZarCard(Modifier.fillMaxWidth().testTag("received-card")) {
        ZarLabel(stringResource(R.string.home_received), color = MaterialTheme.colorScheme.onSurface)
        ZarLabel(month)
        Text(
            summary.received.amount.formatWithCode(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = ZarDimens.SpaceSm).testTag("received-amount"),
        )
        CoverageNote(stringResource(R.string.home_received_note), isWarning = false)
        if (summary.received.unresolvedCount > 0) {
            CoverageNote(
                stringResource(R.string.home_coverage_missing_rate, summary.received.unresolvedCount),
                isWarning = true,
                tag = "received-warning",
            )
        }
    }

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
