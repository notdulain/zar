package com.example.zar.features.allowance.presentation.screen

import com.example.zar.core.presentation.privacy.amountFieldTransformation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonSize
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.components.ZarSwitch
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.allowance.domain.model.AllowanceStatus
import com.example.zar.features.allowance.domain.model.AllowanceSyncState
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceEditorState
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceMessage
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceUiState
import com.example.zar.features.plans.presentation.screen.PlanEditor
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.presentation.category.LocalExpenseCategoryCatalog
import com.example.zar.features.transactions.presentation.manual.screen.labelText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * The weekly allowance on Plans. Coverage notes sit directly under the remaining amount so a
 * total is never shown as more complete than it is; overspending reads as "Overspent by".
 */
@Composable
fun AllowanceCard(state: AllowanceUiState, onEdit: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    val status = state.overview?.status
    ZarCard(modifier.fillMaxWidth().testTag("allowance-card")) {
        ZarEyebrow(stringResource(R.string.plans_allowance))
        when {
            state.isLoading -> Text(
                stringResource(R.string.allowance_loading),
                style = ZarType.bodyL,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            state.loadFailed -> {
                Text(
                    stringResource(R.string.allowance_load_failed),
                    style = ZarType.bodyL,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ZarButton(
                    stringResource(R.string.action_retry), onRetry,
                    Modifier.padding(top = 12.dp).testTag("allowance-retry"),
                    style = ZarButtonStyle.Soft, size = ZarButtonSize.Medium,
                )
            }
            status == null -> {
                Text(
                    stringResource(R.string.plans_allowance_empty),
                    style = ZarType.bodyL,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp).testTag("allowance-off"),
                )
                Text(
                    stringResource(R.string.allowance_off_note),
                    style = ZarType.bodyCaption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                ZarButton(
                    stringResource(R.string.allowance_set_action), onEdit,
                    Modifier.fillMaxWidth().padding(top = 16.dp).testTag("allowance-set"),
                    style = ZarButtonStyle.Soft, size = ZarButtonSize.Medium,
                )
            }
            else -> {
                AllowanceStatusBlock(status)
                ZarButton(
                    stringResource(R.string.action_edit), onEdit,
                    Modifier.fillMaxWidth().padding(top = 16.dp).testTag("allowance-edit"),
                    style = ZarButtonStyle.Soft, size = ZarButtonSize.Medium,
                )
            }
        }
        state.overview?.let { AllowanceSyncNotice(it.syncState) }
    }
}

@Composable
internal fun AllowanceSyncNotice(state: AllowanceSyncState) {
    if (state != AllowanceSyncState.SAVED) Text(
        stringResource(if (state == AllowanceSyncState.QUEUED) R.string.allowance_sync_queued else R.string.allowance_sync_failed),
        style = ZarType.bodyCaption, color = ZarTheme.colors.textPrimary,
        modifier = Modifier.padding(top = 8.dp).testTag("allowance-sync"),
    )
}

@Composable
private fun AllowanceStatusBlock(status: AllowanceStatus) {
    val colors = ZarTheme.colors
    Row(
        Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status.isOverspent) Box(Modifier.size(8.dp).background(colors.attention, CircleShape))
        Text(
            allowanceHeadline(status),
            style = ZarType.amountM,
            color = colors.textPrimary,
            modifier = Modifier.testTag("allowance-remaining"),
        )
    }
    Text(
        stringResource(R.string.allowance_spent_of, status.spent.amount.formatWithCode(), status.allowance.formatWithCode()),
        style = ZarType.bodyS,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 8.dp).testTag("allowance-spent"),
    )
    Text(
        weekRange(status.week),
        style = ZarType.bodyCaption,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = 4.dp).testTag("allowance-week"),
    )
    AllowanceCoverage(status)
    Text(
        stringResource(R.string.allowance_rules_note),
        style = ZarType.bodyCaption,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** Each gap in the data is its own bold "Check:" line, directly under the totals it affects. */
@Composable
internal fun AllowanceCoverage(status: AllowanceStatus) {
    val prefix = stringResource(R.string.home_warning_prefix) + " "
    val notes = buildList {
        if (status.missingRateCount > 0) {
            add("allowance-warning-rate" to pluralStringResource(R.plurals.allowance_check_missing_rate, status.missingRateCount, status.missingRateCount))
        }
        if (status.unclassifiedExpenseCount > 0) {
            add("allowance-warning-unclassified" to pluralStringResource(R.plurals.allowance_check_unclassified, status.unclassifiedExpenseCount, status.unclassifiedExpenseCount))
        }
        if (status.adjustmentCount > 0) {
            add("allowance-warning-adjustment" to pluralStringResource(R.plurals.allowance_check_adjustments, status.adjustmentCount, status.adjustmentCount))
        }
    }
    notes.forEach { (tag, text) ->
        Text(
            prefix + text,
            style = ZarType.bodyCaption,
            fontWeight = FontWeight.Bold,
            color = ZarTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp).testTag(tag),
        )
    }
}

@Composable
internal fun allowanceHeadline(status: AllowanceStatus): String = if (status.isOverspent) {
    stringResource(R.string.allowance_overspent, status.overspentBy.formatWithCode())
} else {
    stringResource(R.string.allowance_left, status.remaining.formatWithCode())
}

internal fun weekRange(week: ClosedRange<LocalDate>): String =
    "${week.start.format(DayFormat)} – ${week.endInclusive.format(DayFormat)}"

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AllowanceEditorDialog(
    editor: AllowanceEditorState,
    isSaving: Boolean,
    message: AllowanceMessage?,
    onEnabledChange: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onWeekStartChange: (DayOfWeek) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    categories: ExpenseCategoryCatalog = LocalExpenseCategoryCatalog.current,
) {
    val colors = ZarTheme.colors
    PlanEditor(stringResource(R.string.allowance_editor_title), isSaving, onDismiss, onSave) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.allowance_switch_label),
                style = ZarType.bodyL,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            val switchLabel = stringResource(R.string.allowance_switch_label)
            ZarSwitch(editor.enabled, onEnabledChange, Modifier.testTag("allowance-switch").semantics { contentDescription = switchLabel }, enabled = !isSaving)
        }
        OutlinedTextField(
            editor.amount, onAmountChange, modifier = Modifier.fillMaxWidth().testTag("allowance-amount"),
            label = { Text(stringResource(R.string.allowance_amount_field)) }, prefix = { Text("LKR ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, visualTransformation = amountFieldTransformation(),
            shape = ZarShapes.md,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.textSelected, focusedLabelColor = colors.textSelected, cursorColor = colors.textSelected,
            ),
            enabled = !isSaving,
            isError = message == AllowanceMessage.INVALID_AMOUNT,
        )
        ZarEyebrow(stringResource(R.string.allowance_categories_heading))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.choices.forEach { category ->
                ZarChip(
                    label = category.labelText(),
                    selected = category.id in editor.categoryIds,
                    onClick = { onToggleCategory(category.id) },
                    modifier = Modifier.testTag("allowance-category-${category.id}"),
                    multiSelect = true,
                    enabled = !isSaving,
                )
            }
        }
        Text(stringResource(R.string.allowance_categories_note), style = ZarType.bodyCaption, color = colors.textTertiary)
        ZarEyebrow(stringResource(R.string.allowance_week_start_heading))
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                ZarChip(
                    label = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    selected = day == editor.weekStart,
                    onClick = { onWeekStartChange(day) },
                    modifier = Modifier.testTag("allowance-week-${day.name.lowercase()}"),
                    enabled = !isSaving,
                )
            }
        }
        if (message != null) {
            Text(
                stringResource(
                    when (message) {
                        AllowanceMessage.INVALID_AMOUNT -> R.string.allowance_invalid_amount
                        AllowanceMessage.SAVE_FAILED -> R.string.allowance_save_failed
                    },
                ),
                color = colors.danger,
                style = ZarType.bodyS,
                modifier = Modifier.testTag("allowance-error"),
            )
        }
    }
}
