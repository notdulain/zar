package com.example.zar.features.transactions.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.zar.R
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarHeroCard
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarTheme

// Layout shell for Activity: dark summary block, then the transactions list. The Transactions
// feature adds the filters and rows (use ZarActivityRow for each transaction).
@Composable
fun ActivityScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Activity,
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
            ZarHeroCard {
                ZarLabel(stringResource(R.string.nav_activity), color = ZarSubtle)
                ZarLabel(stringResource(R.string.home_spent_this_month), color = ZarSubtle)
                Text(stringResource(R.string.home_amount_placeholder), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.activity_based_on_records),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZarSubtle,
                )
            }
            Text(
                stringResource(R.string.activity_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActivityScreenPreview() {
    ZarTheme { ActivityScreen(onNavigate = {}, onAddOption = {}) }
}
