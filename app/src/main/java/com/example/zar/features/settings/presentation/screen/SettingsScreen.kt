package com.example.zar.features.settings.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onDismissWarning: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_currency)) },
                supportingContent = { Text(uiState.currencyCode) },
            )
            uiState.email?.let { email ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_signed_in_as)) },
                    supportingContent = { Text(email) },
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier
                    .testTag("sign-out")
                    .clickable(enabled = !uiState.isSigningOut, onClick = onSignOut),
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

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    ZarTheme {
        SettingsContent(
            uiState = SettingsUiState(isLoading = false, currencyCode = "LKR", email = "dulain@example.com"),
            onBack = {}, onSignOut = {}, onConfirmSignOut = {}, onDismissWarning = {},
        )
    }
}
