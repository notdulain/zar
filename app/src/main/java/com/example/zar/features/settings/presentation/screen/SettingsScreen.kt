package com.example.zar.features.settings.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.settings.presentation.viewmodel.SettingsUiState
import com.example.zar.features.settings.presentation.viewmodel.SettingsViewModel

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
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            ZarBackButton(onBack, stringResource(R.string.action_back), Modifier.padding(start = 10.dp, top = backTop))
            ZarTitleBlock(stringResource(R.string.settings_title), null, Modifier.padding(top = 6.dp))
            ZarCard(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 30.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SettingsRow(stringResource(R.string.settings_currency), uiState.currencyCode)
                uiState.email?.let { email ->
                    ZarDivider()
                    SettingsRow(stringResource(R.string.settings_signed_in_as), email)
                }
            }
            ZarCard(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp).testTag("sign-out"),
                onClick = if (uiState.isSigningOut) null else onSignOut,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            ) {
                Text(stringResource(R.string.settings_sign_out), style = ZarType.labelButton, color = colors.danger)
            }
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

@Composable
private fun SettingsRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.Start) {
        ZarMonoLabel(label)
        Text(value, style = ZarType.bodyM, color = ZarTheme.colors.textPrimary, modifier = Modifier.padding(top = 3.dp))
    }
}
