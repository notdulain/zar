package com.example.zar.features.transactions.presentation.activity.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.transactions.domain.activity.EditProblem
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditEntryUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditEntryViewModel
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditMessage
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.DateRow
import com.example.zar.features.transactions.presentation.manual.screen.FieldError
import com.example.zar.features.transactions.presentation.manual.screen.Section
import com.example.zar.features.transactions.presentation.manual.screen.Segmented
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import com.example.zar.features.transactions.presentation.manual.screen.labelRes
import com.example.zar.core.presentation.components.TransactionTimeField
import java.time.LocalTime
import java.time.LocalDate

/**
 * Correct an expense (Figma Z54, and Z55 for an imported one). Saving appends a correction: the original
 * record and everything corrected before stay on record. The account and currency cannot be changed.
 */
@Composable
fun EditEntryScreen(
    onDone: () -> Unit,
    viewModel: EditEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    EditEntryBody(
        state = state,
        actions = EditActions(
            onClose = onDone,
            onAmount = viewModel::onAmountChange,
            onDate = viewModel::onDateSelect,
            onTime = viewModel::onTimeSelect,
            onCategory = viewModel::onCategorySelect,
            onSpendingClass = viewModel::onSpendingClassSelect,
            onTitle = viewModel::onTitleChange,
            onDescription = viewModel::onDescriptionChange,
            onSave = viewModel::save,
        ),
    )
}

internal class EditActions(
    val onClose: () -> Unit,
    val onAmount: (String) -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onCategory: (String) -> Unit,
    val onSpendingClass: (SpendingClass) -> Unit,
    val onTitle: (String) -> Unit,
    val onDescription: (String) -> Unit,
    val onSave: () -> Unit,
    val onTime: (LocalTime?) -> Unit = {},
)

@Composable
internal fun EditEntryBody(state: EditEntryUiState, actions: EditActions) {
    DarkStatusBarIcons()
    val colors = ZarTheme.colors
    val closeLabel = stringResource(R.string.manual_close)
    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.activity_edit_title), style = ZarType.headingSheet, color = colors.textPrimary)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = actions.onClose)
                    .semantics { contentDescription = closeLabel }
                    .testTag("edit-close"),
                Alignment.Center,
            ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
        }
        val input = state.input
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = colors.accentSelected)
            }
            state.notEditable || input == null -> Text(
                stringResource(R.string.activity_edit_not_editable),
                style = ZarType.bodyS,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp).padding(top = 48.dp).testTag("edit-not-editable"),
            )
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val imported = state.entry?.original?.source.let { it == RecordSource.SMS || it == RecordSource.SHARED_TEXT }
                InfoBanner(
                    title = stringResource(if (imported) R.string.activity_edit_imported_title else R.string.activity_edit_note_title),
                    body = stringResource(if (imported) R.string.activity_edit_imported_body else R.string.activity_edit_note_body),
                    icon = if (imported) R.drawable.ic_ph_bank else R.drawable.ic_ph_info,
                    modifier = Modifier.testTag("edit-banner"),
                )
                if (!state.isOnline) {
                    InfoBanner(
                        title = stringResource(R.string.manual_offline_title),
                        body = stringResource(R.string.manual_offline_body),
                        modifier = Modifier.testTag("edit-offline"),
                    )
                }
                EditFields(state, input.amount, actions)
                state.message?.let { FieldError(it.textRes(), Modifier.testTag("edit-message")) }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZarButton(
                text = stringResource(R.string.action_cancel),
                onClick = actions.onClose,
                modifier = Modifier.weight(1f).testTag("edit-cancel"),
                style = ZarButtonStyle.Soft,
            )
            ZarButton(
                text = stringResource(R.string.activity_edit_save),
                onClick = actions.onSave,
                modifier = Modifier.weight(1f).testTag("edit-save"),
                enabled = !state.isSaving && !state.isLoading && input != null && !state.notEditable,
            )
        }
    }
}

@Composable
private fun EditFields(state: EditEntryUiState, amount: String, actions: EditActions) {
    val colors = ZarTheme.colors
    val input = state.input ?: return
    val entry = state.entry ?: return
    val problems = state.problems

    ZarFormRow(stringResource(R.string.manual_field_amount)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = amount,
                onValueChange = actions.onAmount,
                placeholder = "0",
                textStyle = ZarType.amountM,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).testTag("edit-amount"),
            )
            Row(
                Modifier.padding(start = 8.dp).clip(ZarShapes.sm).background(colors.subtle).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.currencyCode, style = ZarType.labelChip, color = colors.textSecondary, modifier = Modifier.testTag("edit-currency"))
                ZarIcon(R.drawable.ic_ph_lock, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
    Text(
        stringResource(R.string.activity_edit_original_amount, IncomeFormat.withCode(entry.original.amount)),
        style = ZarType.bodyCaption,
        color = colors.textSecondary,
        modifier = Modifier.testTag("edit-original-amount"),
    )
    FieldError(
        if (EditProblem.AMOUNT_MISSING in problems || EditProblem.AMOUNT_INVALID in problems) R.string.manual_error_amount else null,
        Modifier.testTag("edit-error-amount"),
    )
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_account)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.account?.name ?: stringResource(R.string.activity_none), style = ZarType.bodyM, color = colors.textPrimary)
            ZarIcon(R.drawable.ic_ph_lock, size = 14.dp, tint = colors.iconMuted)
            Text(stringResource(R.string.activity_edit_locked), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }

    // The chips scroll sideways, so bring the chosen category into view when the form opens.
    val chipPositions = remember { ExpenseCategory.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(entry.id) { ExpenseCategory.fromId(entry.current.categoryId)?.let { chipPositions[it]?.bringIntoView() } }
    Section(stringResource(R.string.manual_field_category)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { category ->
                ZarChip(
                    label = stringResource(category.labelRes()),
                    selected = category.id == input.categoryId,
                    onClick = { actions.onCategory(category.id) },
                    modifier = Modifier.bringIntoViewRequester(chipPositions.getValue(category)).testTag("edit-category-${category.id}"),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ZarMonoLabel(stringResource(R.string.manual_field_counts_as))
        }
        val classes = listOf(SpendingClass.COMMITTED, SpendingClass.DISCRETIONARY, SpendingClass.UNKNOWN)
        Segmented(
            labels = classes.map { stringResource(it.labelRes()) },
            selectedIndex = classes.indexOf(input.spendingClass),
            onSelect = { actions.onSpendingClass(classes[it]) },
            segmentHeight = 32.dp,
            tagPrefix = "edit-class",
            tags = classes.map { it.name.lowercase() },
        )
    }
    ZarDivider()

    DateRow(input.date, actions.onDate)
    TransactionTimeField(input.time, tag = "edit-time", onSelect = actions.onTime)
    FieldError(if (EditProblem.DATE_IN_FUTURE in problems) R.string.manual_error_date_future else null, Modifier.testTag("edit-error-date"))
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_note)) {
        TextInput(
            value = input.title,
            onValueChange = actions.onTitle,
            placeholder = stringResource(R.string.manual_note_hint),
            modifier = Modifier.weight(1f).testTag("edit-title"),
        )
    }
    FieldError(if (EditProblem.TITLE_TOO_LONG in problems) R.string.manual_error_title_too_long else null, Modifier.testTag("edit-error-title"))
    ZarDivider()

    ZarFormRow(stringResource(R.string.manual_field_description)) {
        TextInput(
            value = input.description,
            onValueChange = actions.onDescription,
            placeholder = stringResource(R.string.manual_description_hint),
            modifier = Modifier.weight(1f).testTag("edit-description"),
        )
    }
    FieldError(
        if (EditProblem.DESCRIPTION_TOO_LONG in problems) R.string.manual_error_description_too_long else null,
        Modifier.testTag("edit-error-description"),
    )
    FieldError(if (EditProblem.NOTHING_CHANGED in problems) R.string.activity_edit_nothing_changed else null, Modifier.testTag("edit-error-nothing"))
}

private fun EditMessage.textRes(): Int = when (this) {
    EditMessage.REFUNDED_AMOUNT -> R.string.activity_edit_refunded_amount
    EditMessage.NOT_SIGNED_IN -> R.string.manual_message_not_signed_in
    EditMessage.ALREADY_VOIDED -> R.string.activity_edit_voided
    EditMessage.FAILED -> R.string.activity_edit_failed
}
