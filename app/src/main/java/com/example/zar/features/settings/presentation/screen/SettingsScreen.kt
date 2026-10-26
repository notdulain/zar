package com.example.zar.features.settings.presentation.screen

import com.example.zar.core.presentation.privacy.AmountPrivacyViewModel
import com.example.zar.core.presentation.components.ZarSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.presentation.viewmodel.SettingsUiState
import com.example.zar.features.settings.presentation.viewmodel.SettingsViewModel
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    privacyViewModel: AmountPrivacyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val amountsHidden by privacyViewModel.hidden.collectAsStateWithLifecycle()
    SettingsContent(
        amountsHidden = amountsHidden,
        onAmountsHiddenChange = privacyViewModel::set,
        uiState = uiState,
        onBack = onBack,
        onSignOut = viewModel::onSignOutClick,
        onConfirmSignOut = viewModel::confirmSignOut,
        onDismissWarning = viewModel::dismissUnsyncedWarning,
        onAppearanceSelected = viewModel::onAppearanceSelected,
        onReduceMotionChange = viewModel::onReduceMotionChange,
        onRetryDisplayPreference = viewModel::retryDisplayPreference,
        onDismissDisplayPreferenceError = viewModel::dismissDisplayPreferenceError,
    )
}

@Composable
internal fun SettingsContent(
    amountsHidden: Boolean = false,
    onAmountsHiddenChange: (Boolean) -> Unit = {},
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onDismissWarning: () -> Unit,
    onAppearanceSelected: (AppearanceMode) -> Unit = {},
    onReduceMotionChange: (Boolean) -> Unit = {},
    onRetryDisplayPreference: () -> Unit = {},
    onDismissDisplayPreferenceError: () -> Unit = {},
) {
    val colors = ZarTheme.colors
    DarkStatusBarIcons()
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    // The choice being saved. The picker closes on success and stays open with a retry on failure.
    var pendingAppearance by remember { mutableStateOf<AppearanceMode?>(null) }

    LaunchedEffect(uiState.appearance, uiState.displayPreferenceError, pendingAppearance, showAppearance) {
        if (showAppearance && pendingAppearance != null && !uiState.displayPreferenceError && uiState.appearance == pendingAppearance) {
            pendingAppearance = null
            showAppearance = false
        }
    }

    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Column(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().verticalScroll(rememberScrollState())) {
        ZarBackButton(onBack, stringResource(R.string.action_back), Modifier.padding(start = 10.dp, top = backTop))
        ZarTitleBlock(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle), Modifier.padding(top = 6.dp))
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 9.dp, end = 9.dp, top = 43.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ZarCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                uiState.email?.let { email ->
                    SettingsRow(stringResource(R.string.settings_signed_in_as), email, Modifier.testTag("settings-email"))
                    ZarDivider()
                }
                SettingsRow(stringResource(R.string.settings_currency), uiState.currencyCode, Modifier.testTag("settings-currency"))
                ZarDivider()
                AppearanceRow(
                    value = uiState.appearance.label(),
                    onClick = { showAppearance = true },
                )
            }
            ZarCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.settings_on_this_phone),
                    style = ZarType.labelAction,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                SwitchSettingRow(
                    title = stringResource(R.string.settings_hide_amounts),
                    detail = stringResource(R.string.settings_hide_amounts_detail),
                    checked = amountsHidden,
                    onCheckedChange = onAmountsHiddenChange,
                    testTag = "settings-hide-amounts",
                )
                ZarDivider(Modifier.padding(vertical = 12.dp))
                SwitchSettingRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    detail = stringResource(R.string.settings_reduce_motion_detail),
                    checked = uiState.reduceMotion,
                    onCheckedChange = onReduceMotionChange,
                    testTag = "settings-reduce-motion",
                )
            }
            SettingsNote(stringResource(R.string.settings_where_title), stringResource(R.string.settings_where_body))
            ZarButton(
                text = stringResource(R.string.settings_sign_out),
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().testTag("sign-out"),
                style = ZarButtonStyle.Soft,
                enabled = !uiState.isSigningOut,
            )
        }
    }
    if (showAppearance) {
        AppearanceSheet(
            selected = uiState.appearance,
            saveFailed = uiState.displayPreferenceError,
            onSelect = { mode ->
                pendingAppearance = mode
                onAppearanceSelected(mode)
            },
            onRetry = onRetryDisplayPreference,
            onDismiss = {
                pendingAppearance = null
                showAppearance = false
                onDismissDisplayPreferenceError()
            },
        )
    }
    if (uiState.showUnsyncedWarning) {
        SignOutUnsyncedSheet(onCancel = onDismissWarning, onConfirm = onConfirmSignOut)
    }
}

private fun AppearanceMode.label(): String = when (this) {
    AppearanceMode.LIGHT -> "Light"
    AppearanceMode.DARK -> "Dark"
    AppearanceMode.SYSTEM -> "System"
}

/**
 * Z155/Z158: the "Appearance" row after Currency. It shows the saved choice and a caret that opens the picker; the
 * label and value are one selectable node for a screen reader.
 */
@Composable
private fun AppearanceRow(value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp)
            .testTag("appearance-row")
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_appearance), style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
        Text(
            value,
            Modifier.weight(1f),
            style = ZarType.bodyM,
            color = ZarTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
        ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = ZarTheme.colors.iconMuted)
    }
}

/** Z156–Z160: the Appearance picker. A radio group; a tap saves and the sheet closes once the write lands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(
    selected: AppearanceMode,
    saveFailed: Boolean,
    onSelect: (AppearanceMode) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = ZarTheme.colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 20.dp)
                .testTag("appearance-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(R.string.settings_appearance), onClose = onDismiss)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                AppearanceOption(AppearanceMode.LIGHT, stringResource(R.string.settings_appearance_light), null, selected, onSelect)
                AppearanceOption(AppearanceMode.DARK, stringResource(R.string.settings_appearance_dark), null, selected, onSelect)
                AppearanceOption(
                    AppearanceMode.SYSTEM,
                    stringResource(R.string.settings_appearance_system),
                    stringResource(R.string.settings_appearance_system_detail),
                    selected,
                    onSelect,
                )
            }
            if (saveFailed) AppearanceSaveFailedRow(current = selected.label(), onRetry = onRetry)
        }
    }
}

/** One radio row. Selected shows a filled check, a Medium label, an "On" mark and a tinted background, not colour alone. */
@Composable
private fun AppearanceOption(
    mode: AppearanceMode,
    label: String,
    detail: String?,
    selected: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
) {
    val colors = ZarTheme.colors
    val isSelected = mode == selected
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.selectedTint else Color.Transparent)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) })
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("appearance-option-${mode.name.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIcon(
            if (isSelected) R.drawable.ic_ph_check_circle else R.drawable.ic_ph_circle,
            size = 20.dp,
            tint = if (isSelected) colors.accentSelected else colors.iconMuted,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = ZarType.bodyM.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            if (detail != null) Text(detail, style = ZarType.bodyCaption, color = colors.textSecondary)
        }
        if (isSelected) {
            Text(stringResource(R.string.settings_appearance_on), style = ZarType.labelChip, color = colors.accentSelected)
        }
    }
}

/** Z160: the write failed. The previous choice is still in effect; "Try again" repeats the failed choice. */
@Composable
private fun AppearanceSaveFailedRow(current: String, onRetry: () -> Unit) {
    val colors = ZarTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.subtle)
            .padding(14.dp)
            .testTag("appearance-save-failed"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_appearance_save_failed, current),
            Modifier.weight(1f),
            style = ZarType.bodyS,
            color = colors.textPrimary,
        )
        Text(
            stringResource(R.string.settings_appearance_retry),
            style = ZarType.labelAction,
            color = colors.textSelected,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("appearance-save-retry"),
        )
    }
}

/** Z88: the sign-out warning. Cancel, the cross and a swipe down all close it; "Sign out anyway" signs out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignOutUnsyncedSheet(onCancel: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = ZarTheme.colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = ZarDimens.SheetGutter)
                .padding(bottom = 14.dp)
                .testTag("sign-out-sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(R.string.sign_out_unsynced_title), onClose = onCancel)
            Text(stringResource(R.string.sign_out_unsynced_body), style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f).testTag("sign-out-cancel"), style = ZarButtonStyle.Soft)
                ZarButton(stringResource(R.string.sign_out_anyway), onConfirm, Modifier.weight(1f).testTag("sign-out-anyway"))
            }
        }
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

/** A switch about this phone with a title and one line of detail (Hide amounts, Reduce motion). */
@Composable
private fun SwitchSettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ZarType.bodyM, color = ZarTheme.colors.textPrimary)
            Text(detail, style = ZarType.bodyCaption, color = ZarTheme.colors.textSecondary)
        }
        ZarSwitch(checked, onCheckedChange, Modifier.testTag(testTag))
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
