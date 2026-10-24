package com.example.zar.features.export.presentation.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonSize
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.export.domain.model.ExportKind
import com.example.zar.features.export.presentation.viewmodel.ExportMessage
import com.example.zar.features.export.presentation.viewmodel.ExportUiState
import com.example.zar.features.export.presentation.viewmodel.ExportViewModel

/** Export data: two separate CSV files, saved wherever the user chooses. */
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        viewModel.onDestinationPicked(uri?.toString())
    }
    // The file is built first, then the system picker opens so the user chooses where it goes.
    val pending = state.pending
    LaunchedEffect(pending) { if (pending != null) picker.launch(pending.fileName) }
    ExportContent(
        state = state,
        onBack = onBack,
        onExport = viewModel::export,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun ExportContent(
    state: ExportUiState,
    onBack: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = ZarTheme.colors
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            ZarBackButton(onBack, stringResource(R.string.export_back), Modifier.padding(start = 10.dp, top = backTop))
            ZarTitleBlock(
                title = stringResource(R.string.export_title),
                subtitle = stringResource(R.string.export_subtitle),
                modifier = Modifier.padding(top = 6.dp),
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                state.loadFailed || state.counts == null -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.export_load_failed),
                        style = ZarType.bodyS,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    ZarButton(
                        stringResource(R.string.action_retry), onRetry,
                        Modifier.testTag("export-retry"), style = ZarButtonStyle.Soft, size = ZarButtonSize.Medium,
                    )
                }
                else -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExportCard(
                        kind = ExportKind.TRANSACTIONS,
                        title = R.string.export_transactions_title,
                        detail = R.string.export_transactions_detail,
                        rows = state.counts.transactions,
                        busy = state.isWorking || state.pending != null,
                        onExport = onExport,
                        tag = "export-transactions",
                    )
                    ExportCard(
                        kind = ExportKind.EXPECTED_PAYMENTS,
                        title = R.string.export_expected_title,
                        detail = R.string.export_expected_detail,
                        rows = state.counts.expectedPayments,
                        busy = state.isWorking || state.pending != null,
                        onExport = onExport,
                        tag = "export-expected",
                    )
                    ExportStatus(state)
                    Text(
                        stringResource(R.string.export_footnote),
                        style = ZarType.bodyCaption,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ExportCard(
    kind: ExportKind,
    title: Int,
    detail: Int,
    rows: Int,
    busy: Boolean,
    onExport: (ExportKind) -> Unit,
    tag: String,
) {
    val colors = ZarTheme.colors
    ZarCard(Modifier.fillMaxWidth().testTag(tag)) {
        ZarMonoLabel(stringResource(title).uppercase())
        Text(
            stringResource(detail),
            style = ZarType.bodyS,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            if (rows == 0) stringResource(R.string.export_empty) else pluralStringResource(R.plurals.export_rows, rows, rows),
            style = ZarType.bodyL,
            color = if (rows == 0) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.padding(top = 10.dp).testTag("$tag-count"),
        )
        ZarButton(
            text = stringResource(if (busy) R.string.export_working else R.string.export_action),
            onClick = { onExport(kind) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("$tag-button"),
            style = ZarButtonStyle.Soft,
            enabled = rows > 0 && !busy,
            size = ZarButtonSize.Medium,
        )
    }
}

/** The result of the last attempt, in plain words under the cards. */
@Composable
private fun ExportStatus(state: ExportUiState) {
    val colors = ZarTheme.colors
    val saved = state.saved
    val text = when {
        state.message == ExportMessage.NOTHING_TO_EXPORT -> stringResource(R.string.export_nothing)
        state.message == ExportMessage.BUILD_FAILED -> stringResource(R.string.export_build_failed)
        state.message == ExportMessage.SAVE_FAILED -> stringResource(R.string.export_save_failed)
        saved != null -> stringResource(R.string.export_saved, saved.fileName, saved.rowCount)
        else -> return
    }
    Text(
        text,
        style = ZarType.bodyS,
        color = if (state.message != null) colors.danger else colors.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("export-status"),
    )
}
