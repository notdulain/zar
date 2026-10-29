package com.example.zar.features.transactions.presentation.income.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.ZarAddPill
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarHeroItemKey
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarSectionHeader
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.components.heroDisplacement
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListViewModel
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeMonth
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeRow
import java.time.LocalDate
import java.time.YearMonth

/** Figma Z169 (list), Z170 (empty) and Z170b (couldn't load): Back, title, summary, month groups and the Add income pill. */
@Composable
fun IncomeListScreen(
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenIncome: (String) -> Unit,
    viewModel: IncomeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    IncomeListContent(uiState, onBack, onAddIncome, onOpenIncome, viewModel::retry)
}

@Composable
internal fun IncomeListContent(
    uiState: IncomeListUiState,
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenIncome: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = ZarTheme.colors
    // A plain light or dark screen with no hero: the clock and icons must contrast with the canvas.
    DarkStatusBarIcons()
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        val listState = rememberLazyListState()
        val heroOffset = listState.heroDisplacement()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("income-list"),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = ZarHeroItemKey) {
                Column {
                    ZarBackButton(onBack, stringResource(R.string.action_back), Modifier.padding(start = 10.dp, top = backTop))
                    ZarTitleBlock(
                        title = stringResource(R.string.income_title),
                        subtitle = stringResource(R.string.income_subtitle),
                        modifier = Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap).padding(top = 6.dp),
                    )
                }
            }
            when {
                uiState.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
                }
                uiState.loadFailed -> item { LoadFailedCard(onRetry) }
                else -> {
                    item { ReceivedSummary(uiState.receivedThisMonth ?: ReportingTotal.zero(), Modifier.padding(top = 16.dp)) }
                    if (uiState.months.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.income_empty),
                                style = ZarType.bodyS,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 64.dp).testTag("income-empty"),
                            )
                        }
                    }
                    uiState.months.forEach { month ->
                        item(key = month.month.toString()) { MonthGroup(month, onOpenIncome) }
                    }
                }
            }
        }
        ZarAddPill(
            text = stringResource(R.string.income_add),
            onClick = onAddIncome,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp).testTag("income-add"),
        )
    }
}

@Composable
private fun ReceivedSummary(total: ReportingTotal, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    ZarCard(modifier.padding(horizontal = 10.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)) {
        ZarMonoLabel(stringResource(R.string.income_header_label))
        Text(
            IncomeFormat.withCode(total.amount),
            style = ZarType.amountL,
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = 12.dp).testTag("income-received-total"),
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!total.isComplete) Box(Modifier.size(6.dp).background(colors.attention, CircleShape))
            Text(
                if (total.isComplete) {
                    stringResource(R.string.income_header_caption)
                } else {
                    pluralStringResource(R.plurals.income_header_unresolved, total.unresolvedCount, total.unresolvedCount)
                },
                style = ZarType.bodyS,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MonthGroup(month: IncomeMonth, onOpenIncome: (String) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        ZarSectionHeader(IncomeFormat.month(month.month))
        ZarCard(Modifier.padding(horizontal = 10.dp).padding(top = 10.dp).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                month.rows.forEachIndexed { index, row ->
                    if (index > 0) ZarDivider()
                    IncomeRowItem(row, onClick = { onOpenIncome(row.id) })
                }
            }
        }
    }
}

@Composable
private fun IncomeRowItem(row: IncomeRow, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    // Figma section 25: from 1.3x text the amount drops under the title and nothing is cut, so "Edited" and the
    // account name stay readable. Below that the amount sits on the right.
    val stacked = LocalDensity.current.fontScale >= 1.3f
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp).testTag("income-row-${row.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
    ) {
        ZarIconChip(row.category.iconRes())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.receivedFrom ?: row.category?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.income_default_title),
                style = ZarType.labelButton,
                color = colors.textPrimary,
                maxLines = if (stacked) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                row.category?.let { stringResource(it.labelRes()) },
                IncomeFormat.shortDate(row.date),
                row.accountName,
                if (row.isEdited) stringResource(R.string.income_edited) else null,
            )
            Text(
                details.joinToString(" · "),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
                maxLines = if (stacked) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (stacked) IncomeAmount(row, Alignment.Start)
        }
        if (!stacked) IncomeAmount(row, Alignment.End)
    }
}

@Composable
private fun IncomeAmount(row: IncomeRow, alignment: Alignment.Horizontal) {
    val colors = ZarTheme.colors
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(IncomeFormat.received(row.amount), style = ZarType.bodyM, color = colors.textIncome)
        if (row.missingRate) {
            Text(stringResource(R.string.income_missing_rate), style = ZarType.bodyCaption, color = colors.textAttentionStrong)
        }
    }
}

@Composable
private fun LoadFailedCard(onRetry: () -> Unit) {
    val colors = ZarTheme.colors
    Column(
        Modifier.padding(horizontal = 10.dp).padding(top = 16.dp).fillMaxWidth().background(colors.subtle, ZarShapes.lg).padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("income-load-failed"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarIcon(R.drawable.ic_ph_wifi_high, size = 20.dp, tint = colors.iconMuted)
            Text(stringResource(R.string.income_load_failed), style = ZarType.bodyS, color = colors.textPrimary)
        }
        Text(
            stringResource(R.string.income_retry),
            style = ZarType.labelAction,
            color = colors.textSelected,
            modifier = Modifier.clickable(role = Role.Button, onClick = onRetry).padding(vertical = 8.dp).testTag("income-retry"),
        )
    }
}

private fun IncomeCategory?.iconRes(): Int = when (this) {
    IncomeCategory.FREELANCE -> R.drawable.ic_ph_briefcase
    IncomeCategory.SALARY -> R.drawable.ic_ph_bank
    IncomeCategory.ALLOWANCE -> R.drawable.ic_ph_piggy_bank
    IncomeCategory.GIFT, IncomeCategory.OTHER, null -> R.drawable.ic_ph_wallet
}

@Preview(showBackground = true)
@Composable
private fun IncomeListPreview() {
    ZarTheme {
        IncomeListContent(
            uiState = IncomeListUiState(
                isLoading = false,
                receivedThisMonth = ReportingTotal(FiatMoney(12_500_000, Currencies.LKR), unresolvedCount = 1),
                months = listOf(
                    IncomeMonth(
                        YearMonth.of(2026, 10),
                        listOf(
                            IncomeRow("1", "Client payment", IncomeCategory.FREELANCE, "Bank", LocalDate.of(2026, 10, 24), FiatMoney(12_500_000, Currencies.LKR), missingRate = false, isEdited = false),
                            IncomeRow("2", "Upwork", IncomeCategory.FREELANCE, "Payoneer", LocalDate.of(2026, 10, 20), FiatMoney(40_000, Currencies.USD), missingRate = true, isEdited = true),
                        ),
                    ),
                ),
            ),
            onBack = {},
            onAddIncome = {},
            onOpenIncome = {},
            onRetry = {},
        )
    }
}
