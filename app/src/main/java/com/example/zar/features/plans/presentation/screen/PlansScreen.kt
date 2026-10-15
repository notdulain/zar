package com.example.zar.features.plans.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarHeroCard
import com.example.zar.core.presentation.components.ZarLabel
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarTheme

// Layout shell for Plans: goal hero, then allowance and bills cards. The Plans, Allowance and
// Bills features fill in the real content.
@Composable
fun PlansScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Plans,
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
                ZarLabel(stringResource(R.string.nav_plans), color = ZarSubtle)
                Text(stringResource(R.string.plans_goal_empty), style = MaterialTheme.typography.headlineMedium)
            }
            ZarCard(Modifier.fillMaxWidth()) {
                ZarLabel(stringResource(R.string.plans_allowance))
                Text(
                    stringResource(R.string.plans_allowance_empty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = ZarDimens.SpaceSm),
                )
            }
            ZarCard(Modifier.fillMaxWidth()) {
                ZarLabel(stringResource(R.string.home_bills))
                Text(
                    stringResource(R.string.plans_bills_empty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = ZarDimens.SpaceSm),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlansScreenPreview() {
    ZarTheme { PlansScreen(onNavigate = {}, onAddOption = {}) }
}
