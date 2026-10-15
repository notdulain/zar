package com.example.zar.features.settings.presentation.screen

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.zar.R
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarHeroCard
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarSubtle
import com.example.zar.core.presentation.theme.ZarTheme

/**
 * The More tab: a list of option cards. Settings opens today; the other cards stay inactive
 * until the Accounts, Capture, Privacy and Export features own their destinations.
 */
@Composable
fun MoreScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenSettings: () -> Unit,
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.More,
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
                Text(stringResource(R.string.nav_more).uppercase(), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.more_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZarSubtle,
                )
            }
            MoreOption(R.string.more_accounts, R.string.more_accounts_detail, onClick = null)
            MoreOption(R.string.more_capture, R.string.more_capture_detail, onClick = null)
            MoreOption(R.string.more_privacy, R.string.more_privacy_detail, onClick = null)
            MoreOption(R.string.more_export, R.string.more_export_detail, onClick = null)
            MoreOption(R.string.settings_title, R.string.more_settings_detail, onClick = onOpenSettings, tag = "open-settings")
        }
    }
}

@Composable
private fun MoreOption(title: Int, detail: Int, onClick: (() -> Unit)?, tag: String = "") {
    ZarCard(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = ZarDimens.SpaceLg + ZarDimens.SpaceXs,
            vertical = ZarDimens.SpaceMd,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title).uppercase(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenPreview() {
    ZarTheme { MoreScreen(onNavigate = {}, onAddOption = {}, onOpenSettings = {}) }
}
