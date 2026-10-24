package com.example.zar.features.capture.presentation.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonSize
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.capture.presentation.viewmodel.AutoCaptureUiState
import com.example.zar.features.capture.presentation.viewmodel.AutoCaptureViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat

/** The SMS-only feature flag exists from Android 13; before that, telephony implies SMS. */
private fun Context.canReceiveSms() = packageManager.hasSystemFeature(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PackageManager.FEATURE_TELEPHONY_MESSAGING else PackageManager.FEATURE_TELEPHONY,
)

private fun Context.canReadSms() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

/**
 * The opt-in for automatic SMS capture. SMS access is asked for only when it's switched on, and
 * a refusal leaves it off; sharing and pasting keep working either way.
 */
@Composable
fun AutoCaptureSection(onAddManually: (() -> Unit)? = null, viewModel: AutoCaptureViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canReceive = remember { context.canReceiveSms() }
    var granted by remember { mutableStateOf(context.canReadSms()) }
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
        if (ok) viewModel.setEnabled(true)
    }
    LifecycleResumeEffect(Unit) {
        granted = context.canReadSms()
        // Allowed from Zar's settings page after a refusal: the refusal no longer applies.
        if (granted) refused = false
        onPauseOrDispose { }
    }
    AutoCaptureCard(
        state = state,
        canReceive = canReceive,
        granted = granted,
        refused = refused,
        onToggle = { on ->
            when {
                !on -> viewModel.setEnabled(false)
                granted -> viewModel.setEnabled(true)
                else -> launcher.launch(Manifest.permission.RECEIVE_SMS)
            }
        },
        onAllow = {
            // After a refusal Android may not ask again, so open Zar's settings page instead.
            if (refused) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            } else {
                launcher.launch(Manifest.permission.RECEIVE_SMS)
            }
        },
        onDismissExpired = viewModel::dismissExpired,
        onAddManually = onAddManually,
    )
}

/** Stateless, so the UI tests can render every state with fixed data. */
@Composable
internal fun AutoCaptureCard(
    state: AutoCaptureUiState,
    canReceive: Boolean,
    granted: Boolean,
    refused: Boolean,
    onToggle: (Boolean) -> Unit,
    onAllow: () -> Unit,
    onDismissExpired: () -> Unit,
    /** Offered next to an expired-message notice, so a payment that was lost can still be added. */
    onAddManually: (() -> Unit)? = null,
) {
    val colors = ZarTheme.colors
    val status = state.status
    val on = status.enabled
    ZarCard(Modifier.fillMaxWidth().testTag("auto-capture")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZarMonoLabel(stringResource(R.string.auto_capture_label))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ZarIconChip(R.drawable.ic_ph_chat_text)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.auto_capture_title), style = ZarType.bodyM, color = colors.textPrimary)
                    Text(
                        stringResource(
                            when {
                                !canReceive -> R.string.auto_capture_state_unavailable
                                on && !granted -> R.string.auto_capture_state_no_access
                                on -> R.string.auto_capture_state_on
                                else -> R.string.auto_capture_state_off
                            },
                        ),
                        style = ZarType.bodyS,
                        color = if (on && !granted) colors.textAttention else colors.textSecondary,
                    )
                }
                ZarSwitch(
                    checked = on,
                    onCheckedChange = onToggle,
                    enabled = canReceive && !state.isSaving && !state.isLoading,
                    modifier = Modifier.testTag("auto-capture-switch"),
                )
            }
            Text(
                stringResource(if (on) R.string.auto_capture_explain_on else R.string.auto_capture_explain_off),
                style = ZarType.bodyS,
                color = colors.textSecondary,
            )
            if (!on && refused) Text(stringResource(R.string.auto_capture_refused), style = ZarType.bodyS, color = colors.textAttention)
            // Also after a refusal while off: Android may not ask again, so this is the way to settings.
            if (!granted && (on || refused)) {
                ZarButton(
                    stringResource(R.string.auto_capture_allow),
                    onAllow,
                    Modifier.fillMaxWidth(),
                    style = ZarButtonStyle.Soft,
                    size = ZarButtonSize.Medium,
                )
            }
            if (status.queue.waiting > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZarIcon(R.drawable.ic_ph_lock, size = 16.dp, tint = colors.iconDefault)
                    Text(
                        pluralStringResource(R.plurals.auto_capture_waiting, status.queue.waiting, status.queue.waiting),
                        style = ZarType.bodyS,
                        color = colors.textSecondary,
                        modifier = Modifier.testTag("auto-capture-waiting"),
                    )
                }
            }
            if (status.queue.expired.isNotEmpty()) {
                ZarDivider()
                val first = status.queue.expired.min().atZone(ZarTime.ZONE).toLocalDate()
                Text(
                    pluralStringResource(R.plurals.auto_capture_expired, status.queue.expired.size, status.queue.expired.size, IncomeFormat.shortDate(first)),
                    style = ZarType.bodyS,
                    color = colors.textAttention,
                    modifier = Modifier.testTag("auto-capture-expired"),
                )
                if (onAddManually != null) {
                    ZarButton(
                        stringResource(R.string.capture_expired_add),
                        onAddManually,
                        Modifier.testTag("auto-capture-expired-add"),
                        size = ZarButtonSize.Medium,
                    )
                }
                ZarButton(
                    stringResource(R.string.auto_capture_expired_dismiss),
                    onDismissExpired,
                    style = ZarButtonStyle.Soft,
                    size = ZarButtonSize.Medium,
                    enabled = !state.isSaving,
                )
            }
            if (state.failed) Text(stringResource(R.string.auto_capture_failed), style = ZarType.bodyS, color = colors.textAttention)
        }
    }
}
