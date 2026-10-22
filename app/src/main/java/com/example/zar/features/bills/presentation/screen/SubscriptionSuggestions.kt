package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.example.zar.features.bills.presentation.viewmodel.SubscriptionSuggestionsViewModel
import com.example.zar.features.freelance.presentation.screen.SectionLabel
import com.example.zar.features.freelance.presentation.screen.display
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.income.screen.ZarMono

/**
 * Possible subscriptions waiting for a decision. Shown on Bills and in the weekly review;
 * shows nothing when there are none.
 */
@Composable
fun SubscriptionSuggestionsSection(
    modifier: Modifier = Modifier,
    viewModel: SubscriptionSuggestionsViewModel = hiltViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    if (suggestions.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag("subscription-suggestions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.subs_label))
        Text(stringResource(R.string.subs_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed) Text(stringResource(R.string.subs_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        suggestions.forEach { suggestion ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(suggestion.merchant, style = MaterialTheme.typography.bodyLarge, fontFamily = ZarMono)
                    Text(
                        stringResource(
                            R.string.subs_seen,
                            suggestion.amount.display(),
                            suggestion.account?.name.orEmpty(),
                            IncomeFormat.shortDate(suggestion.previousDate),
                            IncomeFormat.shortDate(suggestion.lastDate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = ZarMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.subs_next, IncomeFormat.shortDate(suggestion.nextExpected)),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = ZarMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewModel.confirm(suggestion) }) { Text(stringResource(R.string.subs_confirm)) }
                        TextButton(onClick = { viewModel.dismiss(suggestion) }) { Text(stringResource(R.string.subs_dismiss)) }
                    }
                }
            }
        }
    }
}
