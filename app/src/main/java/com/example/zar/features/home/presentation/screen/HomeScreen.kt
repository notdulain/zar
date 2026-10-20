package com.example.zar.features.home.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.zar.R
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarTheme

// Layout shell for Home: centred Add button, no navbar. The Home feature fills in real data.
@Composable
fun HomeScreen(
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
                .padding(horizontal = ZarDimens.ScreenGutter, vertical = ZarDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
        ) {
            // Home has no navbar, so it links to the other areas itself.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onNavigate(TopLevelDestination.More) },
                    modifier = Modifier.testTag("nav-more"),
                ) {
                    Text(stringResource(R.string.nav_more).uppercase() + " ›", style = MaterialTheme.typography.labelLarge)
                }
            }
            SummaryCard(
                label = stringResource(R.string.home_spent),
                period = stringResource(R.string.home_this_month),
                amount = stringResource(R.string.home_amount_placeholder),
                onClick = { onNavigate(TopLevelDestination.Activity) },
            )
            ZarCard(Modifier.fillMaxWidth(), onClick = { onNavigate(TopLevelDestination.Plans) }) {
                ZarLabel(stringResource(R.string.home_goal))
                Text(
                    stringResource(R.string.home_goal_empty),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = ZarDimens.SpaceSm),
                )
            }
            ZarLabel(
                stringResource(R.string.home_recent_activity),
                modifier = Modifier.padding(top = ZarDimens.SpaceSm),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd)) {
                StatCard(
                    title = stringResource(R.string.home_bills),
                    value = stringResource(R.string.home_bills_empty),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(TopLevelDestination.Plans) },
                )
                StatCard(
                    title = stringResource(R.string.home_weekly_limit),
                    value = stringResource(R.string.home_weekly_limit_empty),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(TopLevelDestination.Plans) },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, period: String, amount: String, onClick: () -> Unit) {
    ZarCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                ZarLabel(label, color = MaterialTheme.colorScheme.onSurface)
                ZarLabel(period)
            }
            Text(amount, style = MaterialTheme.typography.headlineMedium)
        }
    }
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
private fun HomeScreenPreview() {
    ZarTheme { HomeScreen(onNavigate = {}, onAddOption = {}) }
}
