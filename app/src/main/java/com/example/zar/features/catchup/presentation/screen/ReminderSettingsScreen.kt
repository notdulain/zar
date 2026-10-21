package com.example.zar.features.catchup.presentation.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.catchup.presentation.viewmodel.ReminderSettingsViewModel
import com.example.zar.features.catchup.presentation.viewmodel.ReminderUiState
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The weekly catch-up reminder (Figma Z78 to Z80, Z82). It defaults to Sunday at 7 pm in Sri Lanka time and can be
 * moved, skipped for one week, or turned off. If notifications are off for the app the screen says so, because
 * the reminder can't reach the person until they are turned on.
 */
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    viewModel: ReminderSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    // The person may turn notifications on or off in system settings and come back.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    ReminderBody(
        state = state,
        notificationsAllowed = notificationsAllowed,
        actions = ReminderActions(
            onBack = onBack,
            onEnabled = { enabled ->
                viewModel.onEnabledChange(enabled)
                if (enabled && !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onMove = viewModel::onMove,
            onSkip = viewModel::skipThisWeek,
            onUndoSkip = viewModel::undoSkip,
            onOpenNotificationSettings = { context.openNotificationSettings() },
        ),
    )
}

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

internal class ReminderActions(
    val onBack: () -> Unit,
    val onEnabled: (Boolean) -> Unit,
    val onMove: (DayOfWeek, LocalTime) -> Unit,
    val onSkip: () -> Unit,
    val onUndoSkip: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
)

@Composable
internal fun ReminderBody(state: ReminderUiState, notificationsAllowed: Boolean, actions: ReminderActions) {
    val colors = ZarTheme.colors
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val blocked = settings.enabled && !notificationsAllowed
    CatchUpScaffold(
        title = stringResource(R.string.reminder_title),
        subtitle = stringResource(R.string.reminder_subtitle),
        onBack = actions.onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = Modifier.testTag("reminder-screen"),
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) { CircularProgressIndicator(color = colors.accentSelected) }
            return@CatchUpScaffold
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.reminder_save_failed), style = ZarType.bodyS, color = colors.danger,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("reminder-save-error"))
        }
        if (blocked) {
            CatchUpCard(Modifier.testTag("reminder-blocked-card")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZarIconChip(R.drawable.ic_catchup_bell_slash)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.reminder_notifications_off_title), style = ZarType.bodyM, color = colors.textPrimary)
                        Text(stringResource(R.string.reminder_notifications_off_body), style = ZarType.bodyS, color = colors.textSecondary)
                    }
                }
                ZarButton(
                    stringResource(R.string.reminder_open_notification_settings),
                    actions.onOpenNotificationSettings,
                    Modifier.fillMaxWidth().testTag("reminder-open-settings"),
                    style = ZarButtonStyle.Soft,
                )
            }
        }
        ZarCard(Modifier.fillMaxWidth().testTag("reminder-card"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reminder_weekly), style = ZarType.bodyM, color = colors.textPrimary)
                ZarSwitch(
                    checked = settings.enabled,
                    onCheckedChange = actions.onEnabled,
                    modifier = Modifier.alpha(if (blocked) 0.45f else 1f).testTag("reminder-switch"),
                )
            }
            ZarDivider()
            ValueRow(stringResource(R.string.reminder_day), CatchUpFormat.day(settings.day), enabled = settings.enabled, tag = "reminder-day") { sheetOpen = true }
            ZarDivider()
            ValueRow(stringResource(R.string.reminder_time), CatchUpFormat.time(settings.time), enabled = settings.enabled, tag = "reminder-time") { sheetOpen = true }
            ZarDivider()
            ValueRow(stringResource(R.string.reminder_zone), stringResource(R.string.reminder_zone_value), enabled = false, tag = "reminder-zone", onClick = null)
        }
        val next = state.nextReminder
        when {
            !settings.enabled -> CatchUpNote(
                title = stringResource(R.string.reminder_off_title),
                body = stringResource(R.string.reminder_off_body),
                modifier = Modifier.testTag("reminder-note-off"),
            )
            blocked -> CatchUpNote(
                title = stringResource(R.string.reminder_blocked_title),
                body = stringResource(R.string.reminder_blocked_body),
                modifier = Modifier.testTag("reminder-note-blocked"),
            )
            state.isNextSkipped && next != null -> CatchUpNote(
                title = stringResource(R.string.reminder_skipped_title),
                body = stringResource(R.string.reminder_skipped_body, CatchUpFormat.next(next)),
                modifier = Modifier.testTag("reminder-note-skipped"),
            )
            next != null -> CatchUpNote(
                title = stringResource(R.string.reminder_next_title, CatchUpFormat.next(next)),
                body = stringResource(R.string.reminder_next_body),
                modifier = Modifier.testTag("reminder-note-next"),
            )
        }
        if (settings.enabled && !blocked) {
            ZarButton(
                text = stringResource(if (state.isNextSkipped) R.string.reminder_undo_skip else R.string.reminder_skip_week),
                onClick = if (state.isNextSkipped) actions.onUndoSkip else actions.onSkip,
                modifier = Modifier.fillMaxWidth().testTag(if (state.isNextSkipped) "reminder-undo-skip" else "reminder-skip"),
                style = ZarButtonStyle.Soft,
            )
        }
    }
    if (sheetOpen) {
        ReminderTimeSheet(
            day = settings.day,
            time = settings.time,
            onDismiss = { sheetOpen = false },
            onSave = { day, time ->
                actions.onMove(day, time)
                sheetOpen = false
            },
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, enabled: Boolean, tag: String, onClick: (() -> Unit)?) {
    val colors = ZarTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 16.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = ZarType.bodyS, color = colors.textSecondary)
        Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), textAlign = TextAlign.End, style = ZarType.bodyM, color = if (enabled || onClick == null) colors.textPrimary else colors.textTertiary)
            if (onClick != null) ZarIcon(R.drawable.ic_ph_caret_right, size = 14.dp, tint = colors.iconMuted)
        }
    }
}

private val PRESET_TIMES = listOf(LocalTime.of(18, 0), LocalTime.of(19, 0), LocalTime.of(20, 0))

/** Z79: pick a day and a time. Three common evening times are one tap; Other… opens a time picker. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReminderTimeSheet(day: DayOfWeek, time: LocalTime, onDismiss: () -> Unit, onSave: (DayOfWeek, LocalTime) -> Unit) {
    val colors = ZarTheme.colors
    var pickedDay by rememberSaveable { mutableStateOf(day) }
    var pickedTime by rememberSaveable { mutableStateOf(time) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 14.dp)
                .testTag("reminder-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(R.string.reminder_sheet_title), onClose = onDismiss)
            ZarMonoLabel(stringResource(R.string.reminder_sheet_day))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { option ->
                    ZarChip(
                        label = CatchUpFormat.dayShort(option),
                        selected = option == pickedDay,
                        onClick = { pickedDay = option },
                        modifier = Modifier.testTag("reminder-day-${option.name}"),
                    )
                }
            }
            ZarMonoLabel(stringResource(R.string.reminder_sheet_time))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_TIMES.forEach { option ->
                    ZarChip(
                        label = CatchUpFormat.time(option),
                        selected = option == pickedTime,
                        onClick = { pickedTime = option },
                        modifier = Modifier.testTag("reminder-time-${option.hour}"),
                    )
                }
                val custom = pickedTime !in PRESET_TIMES
                ZarChip(
                    label = if (custom) CatchUpFormat.time(pickedTime) else stringResource(R.string.reminder_sheet_other),
                    selected = custom,
                    onClick = { pickerOpen = true },
                    modifier = Modifier.testTag("reminder-time-other"),
                )
            }
            Text(stringResource(R.string.reminder_sheet_note), style = ZarType.bodyS, color = colors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(stringResource(R.string.action_cancel), onDismiss, Modifier.weight(1f).testTag("reminder-sheet-cancel"), style = ZarButtonStyle.Soft)
                ZarButton(stringResource(R.string.action_save), { onSave(pickedDay, pickedTime) }, Modifier.weight(1f).testTag("reminder-sheet-save"))
            }
        }
    }
    if (pickerOpen) {
        TimeDialog(
            initial = pickedTime,
            onDismiss = { pickerOpen = false },
            onConfirm = {
                pickedTime = it
                pickerOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_dialog_title)) },
        containerColor = ZarTheme.colors.surface,
        titleContentColor = ZarTheme.colors.textPrimary,
        text = { TimePicker(state, colors = TimePickerDefaults.colors(
            clockDialColor = ZarTheme.colors.subtle,
            selectorColor = ZarTheme.colors.accentSelected,
            clockDialSelectedContentColor = ZarTheme.colors.textOnInverse,
            clockDialUnselectedContentColor = ZarTheme.colors.textPrimary,
            periodSelectorSelectedContainerColor = ZarTheme.colors.selectedTint,
            periodSelectorUnselectedContainerColor = ZarTheme.colors.surface,
            periodSelectorSelectedContentColor = ZarTheme.colors.textSelected,
            periodSelectorUnselectedContentColor = ZarTheme.colors.textPrimary,
            timeSelectorSelectedContainerColor = ZarTheme.colors.selectedTint,
            timeSelectorUnselectedContainerColor = ZarTheme.colors.subtle,
            timeSelectorSelectedContentColor = ZarTheme.colors.textSelected,
            timeSelectorUnselectedContentColor = ZarTheme.colors.textPrimary,
        )) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.reminder_time_dialog_ok), color = ZarTheme.colors.textSelected) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = ZarTheme.colors.textSelected) } },
    )
}
