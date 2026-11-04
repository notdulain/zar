package com.example.zar.features.spending.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.spending.presentation.viewmodel.SpendingEntriesContent
import com.example.zar.features.spending.presentation.viewmodel.SpendingEntriesUiState
import com.example.zar.features.spending.presentation.viewmodel.SpendingEntriesViewModel
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.transactions.presentation.category.ProvideExpenseCategoryCatalog
import com.example.zar.features.transactions.presentation.screen.EntryRow
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** The entries behind one breakdown row, newest first. Read-only; Back returns to the breakdown. */
@Composable
fun SpendingEntriesScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: SpendingEntriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProvideExpenseCategoryCatalog {
        SpendingEntriesBody(state, onBack, onOpenEntry, viewModel::retry)
    }
}

@Composable
internal fun SpendingEntriesBody(
    state: SpendingEntriesUiState,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = ZarTheme.colors
    DarkStatusBarIcons()
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    val className = stringResource(
        when (state.spendingClass) {
            SpendingClass.COMMITTED -> R.string.spending_committed
            SpendingClass.DISCRETIONARY -> R.string.spending_discretionary
            SpendingClass.UNKNOWN -> R.string.spending_unknown
        },
    )
    val title = state.categoryKey?.let { breakdownRowName(it) } ?: className
    Column(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().testTag("spending-entries-screen")) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = backTop)) { ZarBackButton(onBack, stringResource(R.string.action_back)) }
        ZarTitleBlock(title, stringResource(R.string.spending_entries_subtitle, className, state.month.format(MonthYear)), Modifier.padding(top = 6.dp))
        Column(
            Modifier.weight(1f).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (val content = state.content) {
                SpendingEntriesContent.Loading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                SpendingEntriesContent.Error -> ZarCard(Modifier.fillMaxWidth().testTag("spending-entries-error")) {
                    Text(stringResource(R.string.spending_entries_error), style = ZarType.bodyS, color = colors.textPrimary)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                }
                is SpendingEntriesContent.Ready -> if (content.list.rows.isEmpty()) {
                    ZarCard(Modifier.fillMaxWidth().testTag("spending-entries-empty")) {
                        Text(stringResource(R.string.spending_entries_empty), style = ZarType.bodyS, color = colors.textSecondary)
                    }
                } else {
                    content.list.rows.forEach { row -> EntryRow(row, onClick = { onOpenEntry(row.entry.id.value) }) }
                }
            }
        }
    }
}
