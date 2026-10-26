package com.example.zar.features.transactions.presentation.income.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.HeroScrollOffset
import com.example.zar.core.presentation.components.heroDisplacement
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.income.model.IncomeCategory
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListViewModel
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeMonth
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeRow
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun IncomeListScreen(
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenIncome: (String) -> Unit,
    viewModel: IncomeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    IncomeListContent(uiState, onBack, onAddIncome, onOpenIncome)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeListContent(
    uiState: IncomeListUiState,
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenIncome: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.income_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddIncome,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text("+", fontSize = 28.sp)
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.loadFailed -> CenteredMessage(stringResource(R.string.income_load_failed), Modifier.padding(innerPadding))
            else -> {
                val listState = rememberLazyListState()
                val heroOffset = listState.heroDisplacement()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { ReceivedHeader(uiState.receivedThisMonth ?: ReportingTotal.zero(), heroOffset) }
                    if (uiState.months.isEmpty()) {
                        item { CenteredMessage(stringResource(R.string.income_empty), Modifier.padding(top = 48.dp)) }
                    }
                    uiState.months.forEach { month ->
                        item(key = month.month.toString()) {
                            Text(
                                IncomeFormat.month(month.month),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ZarMono,
                                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                            )
                        }
                        items(month.rows, key = { it.id }) { row -> IncomeRowCard(row, onClick = { onOpenIncome(row.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedHeader(total: ReportingTotal, heroOffset: HeroScrollOffset) {
    SummaryHeaderCard(
        label = stringResource(R.string.income_header_label),
        amount = IncomeFormat.withCode(total.amount),
        caption = if (total.isComplete) {
            stringResource(R.string.income_header_caption)
        } else {
            pluralStringResource(R.plurals.income_header_unresolved, total.unresolvedCount, total.unresolvedCount)
        },
        modifier = Modifier.heroParallax(heroOffset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap),
    )
}

@Composable
private fun IncomeRowCard(row: IncomeRow, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.receivedFrom ?: row.category?.let { stringResource(it.labelRes()) }
                        ?: stringResource(R.string.income_default_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = ZarMono,
                )
                val details = listOfNotNull(
                    row.category?.let { stringResource(it.labelRes()) },
                    IncomeFormat.shortDate(row.date),
                    row.accountName,
                    if (row.isEdited) stringResource(R.string.income_edited) else null,
                )
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = ZarMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(IncomeFormat.received(row.amount), style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
                if (row.missingRate) {
                    Text(
                        stringResource(R.string.income_missing_rate),
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
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontFamily = ZarMono)
    }
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
        )
    }
}
