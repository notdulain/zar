package com.example.zar.features.settings.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.settings.presentation.viewmodel.SettingsUiState
import com.example.zar.features.settings.presentation.viewmodel.SettingsViewModel
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onSignOut = viewModel::onSignOutClick,
        onConfirmSignOut = viewModel::confirmSignOut,
        onDismissWarning = viewModel::dismissUnsyncedWarning,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onDismissWarning: () -> Unit,
) {
    val colors = ZarTheme.colors
    DarkStatusBarIcons()
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Column(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().verticalScroll(rememberScrollState())) {
        ZarBackButton(onBack, stringResource(R.string.action_back), Modifier.padding(start = 10.dp, top = backTop))
        ZarTitleBlock(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle), Modifier.padding(top = 6.dp))
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 9.dp, end = 9.dp, top = 40.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ZarCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                uiState.email?.let { email ->
                    SettingsRow(stringResource(R.string.settings_signed_in_as), email, Modifier.testTag("settings-email"))
                    ZarDivider()
                }
                SettingsRow(stringResource(R.string.settings_currency), uiState.currencyCode, Modifier.testTag("settings-currency"))
            }
            SettingsNote(stringResource(R.string.settings_where_title), stringResource(R.string.settings_where_body))
            ZarButton(
                text = stringResource(R.string.settings_sign_out),
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().testTag("sign-out"),
                style = ZarButtonStyle.Soft,
            )
        }
    }
    if (uiState.showUnsyncedWarning) {
        AlertDialog(
            onDismissRequest = onDismissWarning,
            title = { Text(stringResource(R.string.sign_out_unsynced_title)) },
            text = { Text(stringResource(R.string.sign_out_unsynced_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmSignOut) { Text(stringResource(R.string.sign_out_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissWarning) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** A label on the left and its value on the right, 52dp tall as in Figma Z87. */
@Composable
private fun SettingsRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
        Text(value, Modifier.weight(1f), style = ZarType.bodyM, color = ZarTheme.colors.textPrimary, textAlign = TextAlign.End)
    }
}

/** The soft grey note under the card, laid out like the reminder screen's "Next reminder" note. */
@Composable
private fun SettingsNote(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZarTheme.colors.subtle).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = ZarType.labelAction, color = ZarTheme.colors.textPrimary)
        Text(body, style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
    }
}
