package com.example.zar.features.spending.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarSplitBar
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.spending.domain.model.BreakdownRow
import com.example.zar.features.spending.domain.model.ClassBreakdown
import com.example.zar.features.spending.domain.model.SpendingBreakdownReport
import com.example.zar.features.spending.presentation.viewmodel.SpendingContent
import com.example.zar.features.spending.presentation.viewmodel.SpendingUiState
import com.example.zar.features.spending.presentation.viewmodel.SpendingViewModel
import com.example.zar.features.transactions.domain.category.SpendingKeys
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.transactions.presentation.category.ProvideExpenseCategoryCatalog
import com.example.zar.features.transactions.presentation.category.categoryDisplayNameOrFallback
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** Z15: where one month's spending went, by class then category. Read-only; rows open the entries behind them. */
@Composable
fun SpendingScreen(
    onBack: () -> Unit,
    onOpenEntries: (month: YearMonth, spendingClass: SpendingClass, categoryKey: String?) -> Unit,
    onOpenReview: () -> Unit,
    viewModel: SpendingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProvideExpenseCategoryCatalog {
        SpendingBody(
            state = state,
            onBack = onBack,
            onPreviousMonth = viewModel::showPreviousMonth,
            onNextMonth = viewModel::showNextMonth,
            onRetry = viewModel::retry,
            onOpenEntries = { cls, key -> onOpenEntries(state.month, cls, key) },
            onOpenReview = onOpenReview,
        )
    }
}

@Composable
internal fun SpendingBody(
    state: SpendingUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
    onOpenEntries: (SpendingClass, String?) -> Unit,
    onOpenReview: () -> Unit,
) {
    val colors = ZarTheme.colors
    DarkStatusBarIcons()
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Column(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().testTag("spending-screen")) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = backTop), verticalAlignment = Alignment.CenterVertically) {
            ZarBackButton(onBack, stringResource(R.string.action_back))
            Spacer(Modifier.weight(1f))
            MonthControl(state, onPreviousMonth, onNextMonth)
        }
        ZarTitleBlock(
            stringResource(R.string.spending_title),
            if (state.isCurrentMonth) stringResource(R.string.spending_subtitle_current)
            else stringResource(R.string.spending_subtitle_past, state.month.format(MonthYear)),
            Modifier.padding(top = 6.dp),
        )
        Column(
            Modifier.weight(1f).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val content = state.content) {
                SpendingContent.Loading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                SpendingContent.Error -> ZarCard(Modifier.fillMaxWidth().testTag("spending-error")) {
                    Text(stringResource(R.string.spending_error), style = ZarType.bodyS, color = colors.textPrimary)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("spending-retry")) { Text(stringResource(R.string.home_retry)) }
                }
                is SpendingContent.Ready -> ReportContent(content.report, onOpenEntries, onOpenReview)
            }
        }
    }
}

@Composable
private fun MonthControl(state: SpendingUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = ZarTheme.colors
    val previous = stringResource(R.string.spending_previous_month)
    val next = stringResource(R.string.spending_next_month)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp).testTag("spending-month-previous").semantics { contentDescription = previous }) {
            Text("‹", style = ZarType.headingSheet, color = colors.textSelected)
        }
        Text(
            state.month.format(MonthName),
            style = ZarType.labelAction,
            color = colors.textSelected,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("spending-month-label"),
        )
        IconButton(
            onClick = onNext,
            enabled = state.canGoForward,
            modifier = Modifier.size(44.dp).testTag("spending-month-next").semantics { contentDescription = next },
        ) {
            Text("›", style = ZarType.headingSheet, color = colors.textSelected.copy(alpha = if (state.canGoForward) 1f else 0.35f))
        }
    }
}

@Composable
private fun ReportContent(report: SpendingBreakdownReport, onOpenEntries: (SpendingClass, String?) -> Unit, onOpenReview: () -> Unit) {
    val colors = ZarTheme.colors
    if (!report.hasAnySpending && !report.hasUnresolved) {
        ZarCard(Modifier.fillMaxWidth().testTag("spending-empty")) {
            Text(stringResource(R.string.spending_empty, report.month.format(MonthYear)), style = ZarType.bodyS, color = colors.textSecondary)
        }
        return
    }
    SummaryCard(report)
    ClassCard(report.committed, R.string.spending_committed, R.string.spending_committed_hint, "committed", colors.accentSelected, onOpenEntries)
    ClassCard(report.discretionary, R.string.spending_discretionary, R.string.spending_discretionary_hint, "discretionary", colors.attention, onOpenEntries)
    UnknownCard(report, onOpenEntries, onOpenReview)
}

@Composable
private fun SummaryCard(report: SpendingBreakdownReport) {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth().testTag("spending-summary"), contentPadding = PaddingValues(19.dp)) {
        Text(report.total.amount.formatWithCode(), style = ZarType.amountM, color = colors.textPrimary, modifier = Modifier.testTag("spending-total"))
        Spacer(Modifier.height(12.dp))
        ZarSplitBar(
            committed = report.committed.subtotal.amount.minorUnits.toFloat(),
            discretionary = report.discretionary.subtotal.amount.minorUnits.toFloat(),
            unknown = report.unknown.subtotal.amount.minorUnits.toFloat(),
        )
        Spacer(Modifier.height(12.dp))
        SummaryLine(R.string.spending_committed, report.committed, colors.accentSelected, "committed")
        SummaryLine(R.string.spending_discretionary, report.discretionary, colors.attention, "discretionary")
        SummaryLine(R.string.spending_unknown, report.unknown, colors.chartUnknown, "unknown")
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.spending_hint), style = ZarType.bodyCaption, color = colors.textSecondary)
        if (report.hasUnresolved) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.spending_unresolved), style = ZarType.bodyCaption, color = colors.textSecondary, modifier = Modifier.testTag("spending-unresolved"))
        }
    }
}

@Composable
private fun SummaryLine(label: Int, cls: ClassBreakdown, dot: Color, tag: String) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth().height(30.dp).semantics(mergeDescendants = true) {}.testTag("spending-summary-$tag"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(label), style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(cls.subtotal.amount.formatWithCode(), style = ZarType.bodyS, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun ClassCard(
    cls: ClassBreakdown,
    label: Int,
    hint: Int,
    tag: String,
    barColor: Color,
    onOpenEntries: (SpendingClass, String?) -> Unit,
) {
    if (cls.rows.isEmpty()) return
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth().testTag("spending-class-$tag"), contentPadding = PaddingValues(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label).uppercase(), style = ZarType.label, color = colors.textPrimary)
            Text(cls.subtotal.amount.formatWithCode(), style = ZarType.label, color = colors.textPrimary, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(hint), style = ZarType.bodyCaption, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        cls.rows.forEach { row ->
            CategoryRow(cls, row, barColor) { onOpenEntries(cls.spendingClass, row.key) }
        }
    }
}

@Composable
private fun UnknownCard(report: SpendingBreakdownReport, onOpenEntries: (SpendingClass, String?) -> Unit, onOpenReview: () -> Unit) {
    val unknown = report.unknown
    if (unknown.rows.isEmpty()) return
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth().testTag("spending-class-unknown"), contentPadding = PaddingValues(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.spending_unknown).uppercase(), style = ZarType.label, color = colors.textPrimary)
            Text(unknown.subtotal.amount.formatWithCode(), style = ZarType.label, color = colors.textPrimary, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.spending_review_count, report.needsReviewCount, report.needsReviewCount),
                style = ZarType.bodyS,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).testTag("spending-review-count"),
            )
            Text(
                stringResource(R.string.spending_fix_now),
                style = ZarType.labelAction,
                color = colors.textSelected,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenReview).padding(vertical = 8.dp).testTag("spending-fix-now"),
            )
        }
        Spacer(Modifier.height(4.dp))
        unknown.rows.forEach { row -> CategoryRow(unknown, row, colors.chartUnknown) { onOpenEntries(SpendingClass.UNKNOWN, row.key) } }
    }
}

@Composable
private fun CategoryRow(cls: ClassBreakdown, row: BreakdownRow, barColor: Color, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val name = breakdownRowName(row.key)
    val share = cls.shareOf(row)
    val amount = row.total.amount.formatWithCode()
    val trailing = when {
        share != null -> stringResource(R.string.spending_row_amount_share, amount, share)
        !row.total.isComplete -> stringResource(R.string.spending_row_rate_missing, amount)
        else -> amount
    }
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp).testTag("spending-row-${cls.spendingClass.name}-${row.key}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = ZarType.bodyS, color = colors.textSecondary, maxLines = 1, modifier = Modifier.testTag("spending-row-value"))
            Spacer(Modifier.width(6.dp))
            Text("›", style = ZarType.bodyM, color = colors.textTertiary)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.borderHairline, RoundedCornerShape(2.dp))) {
            val fraction = ((share ?: 0) / 100f).coerceIn(0f, 1f)
            if (fraction > 0f && !com.example.zar.core.presentation.privacy.AmountMask.isHidden) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(barColor, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** The readable name of a breakdown row: the markers, a built-in, a custom name or a safe fallback. */
@Composable
internal fun breakdownRowName(key: String): String = when (key) {
    SpendingKeys.UNCATEGORISED -> stringResource(R.string.spending_uncategorised)
    SpendingKeys.ADJUSTMENTS -> stringResource(R.string.spending_adjustments)
    else -> categoryDisplayNameOrFallback(key)
}
