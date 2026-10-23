package com.example.zar.features.quicknote.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.accounts.presentation.screen.iconRes
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import com.example.zar.features.quicknote.domain.review.ProposalBlocker
import com.example.zar.features.quicknote.domain.review.ProposalInput
import com.example.zar.features.quicknote.presentation.viewmodel.ProposalState
import com.example.zar.features.quicknote.presentation.viewmodel.ProposalStatus
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteFailure
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteStage
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteUiState
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteViewModel
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.Segmented
import com.example.zar.features.transactions.presentation.manual.screen.TextInput
import com.example.zar.features.transactions.presentation.manual.screen.labelRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Write a rough note, ask for it to be read, review each proposal and save the ones that are right. Laid out like
 * Figma Z125 to Z131. Nothing is recorded until a proposal is confirmed.
 */
@Composable
fun QuickNoteScreen(
    onDone: () -> Unit,
    onFillForm: (String) -> Unit,
    viewModel: QuickNoteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    QuickNoteContent(
        state = state,
        actions = QuickNoteActions(
            onClose = onDone,
            onNote = viewModel::onNoteChange,
            onInterpret = viewModel::interpret,
            onCancelReading = viewModel::cancelReading,
            onFillForm = onFillForm,
            onEditNote = viewModel::editNote,
            onOpenEditor = viewModel::openEditor,
            onCloseEditor = viewModel::closeEditor,
            onUpdate = viewModel::update,
            onConfirm = viewModel::confirm,
            onDiscard = viewModel::discard,
            onDiscardAll = viewModel::discardAll,
        ),
    )
}

internal class QuickNoteActions(
    val onClose: () -> Unit,
    val onNote: (String) -> Unit,
    val onInterpret: () -> Unit,
    val onCancelReading: () -> Unit,
    val onFillForm: (String) -> Unit,
    val onEditNote: () -> Unit,
    val onOpenEditor: (Int) -> Unit,
    val onCloseEditor: () -> Unit,
    val onUpdate: (Int, (ProposalInput) -> ProposalInput) -> Unit,
    val onConfirm: (Int) -> Unit,
    val onDiscard: (Int) -> Unit,
    val onDiscardAll: () -> Unit,
)

@Composable
internal fun QuickNoteContent(state: QuickNoteUiState, actions: QuickNoteActions) {
    val colors = ZarTheme.colors
    val closeLabel = stringResource(R.string.manual_close)
    val editing = state.editing?.let { index -> state.proposals.firstOrNull { it.index == index } }
    BackHandler(enabled = editing != null) { actions.onCloseEditor() }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.64f)).clickable(onClick = actions.onClose))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight - 48.dp)
                .clip(ZarShapes.sheet).background(colors.surface).pointerInput(Unit) { detectTapGestures {} }.padding(top = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.trackOff))
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        when {
                            editing != null -> R.string.quicknote_edit_title
                            state.stage == QuickNoteStage.REVIEWING -> R.string.quicknote_review_title
                            else -> R.string.quicknote_title
                        },
                    ),
                    style = ZarType.headingSheet,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag("quicknote-title"),
                )
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = actions.onClose)
                        .semantics { contentDescription = closeLabel }.testTag("quicknote-close"),
                    Alignment.Center,
                ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
            }
            when {
                editing != null -> EditStage(editing, state, actions)
                state.stage == QuickNoteStage.WRITING -> WritingStage(state, actions)
                state.stage == QuickNoteStage.READING -> ReadingStage(state, actions)
                else -> ReviewStage(state, actions)
            }
        }
    }
}

// --- Write and read -------------------------------------------------------------------------

@Composable
private fun SheetBody(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { content() }
}

@Composable
private fun SheetButtons(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun ColumnScope.WritingStage(state: QuickNoteUiState, actions: QuickNoteActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.note.isEmpty()) runCatching { focus.requestFocus() } }
    val failure = state.failure
    SheetBody(Modifier.weight(1f, fill = false)) {
        NoteBox(state.note, actions.onNote, enabled = true, dim = false, focus = focus)
        when {
            !state.isOnline -> Notice(
                title = stringResource(R.string.quicknote_offline_title),
                body = stringResource(R.string.quicknote_offline_body),
                tag = "quicknote-offline",
            )
            failure != null -> Notice(
                title = stringResource(failure.titleRes()),
                body = stringResource(failure.bodyRes()),
                tag = "quicknote-failure",
            )
            else -> Text(stringResource(R.string.quicknote_privacy), style = ZarType.bodyCaption, color = ZarTheme.colors.textSecondary)
        }
    }
    SheetButtons {
        // Offline the way forward is the form; after a failed read it is another try; otherwise it is the read.
        val fillFirst = !state.isOnline
        val tryAgain = failure != null
        val fillLabel = stringResource(if (tryAgain || fillFirst) R.string.quicknote_fill_form_myself else R.string.quicknote_fill_form)
        val readLabel = stringResource(if (tryAgain || fillFirst) R.string.quicknote_try_again else R.string.quicknote_interpret)
        val readEnabled = state.canInterpret && (failure?.canRetry ?: true)
        if (fillFirst) {
            ZarButton(readLabel, actions.onInterpret, Modifier.weight(1f).testTag("quicknote-interpret"), style = ZarButtonStyle.Soft, enabled = false)
            ZarButton(fillLabel, { actions.onFillForm(state.note) }, Modifier.weight(1f).testTag("quicknote-fill-form"))
        } else {
            ZarButton(fillLabel, { actions.onFillForm(state.note) }, Modifier.weight(1f).testTag("quicknote-fill-form"), style = ZarButtonStyle.Soft)
            ZarButton(readLabel, actions.onInterpret, Modifier.weight(1f).testTag("quicknote-interpret"), enabled = readEnabled)
        }
    }
}

@Composable
private fun ColumnScope.ReadingStage(state: QuickNoteUiState, actions: QuickNoteActions) {
    val colors = ZarTheme.colors
    SheetBody(Modifier.weight(1f, fill = false)) {
        NoteBox(state.note, {}, enabled = false, dim = true, focus = null)
        Column(Modifier.fillMaxWidth().testTag("quicknote-reading"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accentSelected, trackColor = colors.trackOff)
            Text(stringResource(R.string.quicknote_reading), style = ZarType.bodyM, color = colors.textPrimary)
            Text(stringResource(R.string.quicknote_reading_hint), style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
    SheetButtons {
        ZarButton(stringResource(R.string.quicknote_cancel), actions.onCancelReading, Modifier.weight(1f).testTag("quicknote-cancel"), style = ZarButtonStyle.Soft)
    }
}

@Composable
private fun NoteBox(value: String, onChange: (String) -> Unit, enabled: Boolean, dim: Boolean, focus: FocusRequester?) {
    val colors = ZarTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.subtle).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            textStyle = ZarType.bodyM.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textSelected),
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier).testTag("quicknote-note"),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(stringResource(R.string.quicknote_note_placeholder), style = ZarType.bodyM, color = colors.textPlaceholder)
                    inner()
                }
            },
        )
        Text(
            stringResource(R.string.quicknote_counter, value.length, QuickNoteLimits.MAX_NOTE_LENGTH),
            style = ZarType.monoCaption,
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().testTag("quicknote-counter"),
        )
    }
}

@Composable
private fun Notice(title: String, body: String, tag: String) {
    val colors = ZarTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.textAttention.copy(alpha = 0.10f)).padding(horizontal = 16.dp, vertical = 14.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.textAttention))
            Text(title, style = ZarType.bodyM, color = colors.textPrimary)
        }
        Text(body, style = ZarType.bodyCaption, color = colors.textSecondary)
    }
}

private fun QuickNoteFailure.titleRes(): Int = when (this) {
    QuickNoteFailure.TIMED_OUT -> R.string.quicknote_failure_timed_out_title
    QuickNoteFailure.RATE_LIMITED -> R.string.quicknote_failure_rate_limited_title
    QuickNoteFailure.UNAVAILABLE -> R.string.quicknote_failure_unavailable_title
    QuickNoteFailure.INVALID_RESPONSE -> R.string.quicknote_failure_invalid_title
    QuickNoteFailure.NOTHING_FOUND -> R.string.quicknote_failure_nothing_title
    QuickNoteFailure.NOT_SIGNED_IN -> R.string.quicknote_failure_signed_out_title
    QuickNoteFailure.APP_NOT_VERIFIED -> R.string.quicknote_failure_unverified_title
    QuickNoteFailure.REJECTED -> R.string.quicknote_failure_rejected_title
}

private fun QuickNoteFailure.bodyRes(): Int = when (this) {
    QuickNoteFailure.TIMED_OUT -> R.string.quicknote_failure_timed_out_body
    QuickNoteFailure.RATE_LIMITED -> R.string.quicknote_failure_rate_limited_body
    QuickNoteFailure.UNAVAILABLE -> R.string.quicknote_failure_unavailable_body
    QuickNoteFailure.INVALID_RESPONSE -> R.string.quicknote_failure_invalid_body
    QuickNoteFailure.NOTHING_FOUND -> R.string.quicknote_failure_nothing_body
    QuickNoteFailure.NOT_SIGNED_IN -> R.string.quicknote_failure_signed_out_body
    QuickNoteFailure.APP_NOT_VERIFIED -> R.string.quicknote_failure_unverified_body
    QuickNoteFailure.REJECTED -> R.string.quicknote_failure_rejected_body
}

// --- Review ---------------------------------------------------------------------------------

@Composable
private fun ColumnScope.ReviewStage(state: QuickNoteUiState, actions: QuickNoteActions) {
    val colors = ZarTheme.colors
    val single = state.proposals.singleOrNull()
    SheetBody(Modifier.weight(1f, fill = false)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(
                if (single != null) stringResource(R.string.quicknote_review_one) else stringResource(R.string.quicknote_review_many, state.proposals.size),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).testTag("quicknote-review-intro"),
            )
            if (!state.anySaved) {
                Text(
                    stringResource(R.string.quicknote_edit_note),
                    style = ZarType.labelAction,
                    color = colors.textSelected,
                    modifier = Modifier.padding(start = 12.dp).clickable(role = Role.Button, onClick = actions.onEditNote).testTag("quicknote-edit-note"),
                )
            }
        }
        if (single != null) {
            ProposalRows(single, state.accounts, actions)
            ProposalHint(single)
        } else {
            state.proposals.forEach { ProposalCard(it, state.accounts, actions) }
        }
    }
    SheetButtons {
        if (single != null) {
            ZarButton(stringResource(R.string.quicknote_discard), { actions.onDiscard(single.index) }, Modifier.weight(1f).testTag("quicknote-discard-${single.index}"), style = ZarButtonStyle.Soft)
            ZarButton(
                stringResource(if (single.status == ProposalStatus.SAVING) R.string.quicknote_saving else R.string.quicknote_save),
                { actions.onConfirm(single.index) },
                Modifier.weight(1f).testTag("quicknote-save-${single.index}"),
                enabled = single.canSave,
            )
        } else {
            ZarButton(stringResource(R.string.quicknote_discard_all), actions.onDiscardAll, Modifier.weight(1f).testTag("quicknote-discard-all"), style = ZarButtonStyle.Soft)
        }
    }
}

/** The first thing stopping a save, in words, or a note that the last save failed. */
@Composable
private fun ProposalHint(item: ProposalState) {
    val colors = ZarTheme.colors
    val text = when {
        item.saveFailed -> stringResource(R.string.quicknote_save_failed)
        item.blockers.isNotEmpty() -> stringResource(item.blockers.first().hintRes())
        else -> return
    }
    Text(text, style = ZarType.bodyCaption, color = colors.textAttention, modifier = Modifier.testTag("quicknote-hint-${item.index}"))
}

private data class RowSpec(val key: String, val label: String, val value: String, val missing: Boolean, val guessed: Boolean)

private fun Set<ProposalBlocker>.hit(vararg blockers: ProposalBlocker) = any { it in blockers }

@Composable
private fun ProposalRows(item: ProposalState, accounts: List<Account>, actions: QuickNoteActions) {
    val colors = ZarTheme.colors
    val rows = rowsFor(item, accounts)
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.lg).border(BorderStroke(1.dp, colors.borderDefault), ZarShapes.lg).background(colors.surface)
            .padding(horizontal = 20.dp),
    ) {
        rows.forEachIndexed { position, row ->
            if (position > 0) Box(Modifier.fillMaxWidth().size(width = 1.dp, height = 1.dp).background(colors.borderHairline))
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClick = { actions.onOpenEditor(item.index) })
                    .padding(vertical = 15.dp).testTag("quicknote-row-${item.index}-${row.key}"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZarMonoLabel(row.label, Modifier.width(86.dp))
                Text(
                    row.value,
                    style = ZarType.bodyM,
                    color = if (row.missing) colors.textAttention else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (row.missing) FieldTag(stringResource(if (row.key == "purchase") R.string.quicknote_tag_needed else R.string.quicknote_tag_missing), "quicknote-missing-${row.key}")
                else if (row.guessed) FieldTag(stringResource(R.string.quicknote_tag_guessed), "quicknote-guessed-${row.key}")
                ZarIcon(R.drawable.ic_ph_caret_right, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
}

@Composable
private fun FieldTag(text: String, tag: String) {
    val colors = ZarTheme.colors
    Text(
        text.uppercase(),
        style = ZarType.monoCaption,
        color = colors.textAttention,
        modifier = Modifier.clip(ZarShapes.xs).background(colors.textAttention.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp).testTag(tag),
    )
}

@Composable
private fun rowsFor(item: ProposalState, accounts: List<Account>): List<RowSpec> {
    val input = item.input
    val blockers = item.blockers
    val none = stringResource(R.string.quicknote_not_set)
    fun accountName(id: AccountId?) = accounts.firstOrNull { it.id == id }?.name
    fun money(text: String, currency: Currency?, account: Account?): String? {
        val code = account?.currency ?: currency ?: Currencies.REPORTING
        if (text.isBlank()) return null
        return Money.parse(text, code)?.let { IncomeFormat.withCode(it) } ?: text
    }
    return when (input) {
        is ProposalInput.Actual -> {
            val account = accounts.firstOrNull { it.id == (item.purchase?.current?.accountId ?: input.accountId) }
            buildList {
                add(RowSpec("type", stringResource(R.string.quicknote_field_type), input.type?.labelText() ?: stringResource(R.string.quicknote_pick_type),
                    blockers.hit(ProposalBlocker.TYPE_MISSING), DraftField.TYPE in input.uncertain))
                add(RowSpec("amount", stringResource(R.string.quicknote_field_amount), money(input.amount, input.currency, account) ?: stringResource(R.string.quicknote_add_amount),
                    blockers.hit(ProposalBlocker.AMOUNT_MISSING, ProposalBlocker.AMOUNT_INVALID, ProposalBlocker.REFUND_TOO_HIGH), DraftField.AMOUNT in input.uncertain))
                add(RowSpec("account", stringResource(R.string.quicknote_field_account), account?.name ?: stringResource(R.string.quicknote_pick_account),
                    blockers.hit(ProposalBlocker.ACCOUNT_MISSING, ProposalBlocker.CURRENCY_MISMATCH), DraftField.ACCOUNT in input.uncertain))
                if (input.type == TransactionType.EXPENSE) {
                    add(RowSpec("category", stringResource(R.string.quicknote_field_category), ExpenseCategory.fromId(input.categoryId)?.let { stringResource(it.labelRes()) } ?: none,
                        false, DraftField.CATEGORY in input.uncertain && ExpenseCategory.fromId(input.categoryId) != null))
                }
                add(RowSpec("date", stringResource(R.string.quicknote_field_date), input.date?.let { IncomeFormat.fullDate(it) } ?: stringResource(R.string.quicknote_pick_date),
                    blockers.hit(ProposalBlocker.DATE_MISSING, ProposalBlocker.DATE_IN_FUTURE), DraftField.DATE in input.uncertain))
                if (input.type == TransactionType.REFUND) {
                    val purchase = item.purchase?.current
                    add(RowSpec("purchase", stringResource(R.string.quicknote_field_purchase),
                        purchase?.let { "${it.merchant ?: stringResource(R.string.quicknote_type_expense)} · ${IncomeFormat.withCode(it.amount)}" } ?: stringResource(R.string.quicknote_pick_purchase),
                        blockers.hit(ProposalBlocker.PURCHASE_MISSING), false))
                }
                add(RowSpec("title", stringResource(R.string.quicknote_field_title), input.title.ifBlank { none }, false, DraftField.MERCHANT in input.uncertain && input.title.isNotBlank()))
            }
        }
        is ProposalInput.Expected -> {
            val account = accounts.firstOrNull { it.id == input.accountId }
            listOf(
                RowSpec("type", stringResource(R.string.quicknote_field_type), input.direction?.let { stringResource(if (it == Direction.IN) R.string.quicknote_dir_in else R.string.quicknote_dir_out) } ?: stringResource(R.string.quicknote_pick_type),
                    blockers.hit(ProposalBlocker.DIRECTION_MISSING), DraftField.TYPE in input.uncertain),
                RowSpec("amount", stringResource(R.string.quicknote_field_amount), money(input.amount, input.currency, account) ?: stringResource(R.string.quicknote_add_amount),
                    blockers.hit(ProposalBlocker.AMOUNT_MISSING, ProposalBlocker.AMOUNT_INVALID, ProposalBlocker.CURRENCY_MISMATCH), DraftField.AMOUNT in input.uncertain),
                RowSpec("date", stringResource(R.string.quicknote_field_due), input.dueDate?.let { IncomeFormat.fullDate(it) } ?: stringResource(R.string.quicknote_pick_date),
                    blockers.hit(ProposalBlocker.DATE_MISSING), DraftField.DATE in input.uncertain),
                RowSpec("title", stringResource(R.string.quicknote_field_title), input.title.ifBlank { stringResource(R.string.quicknote_add_title) },
                    blockers.hit(ProposalBlocker.TITLE_MISSING), DraftField.TITLE in input.uncertain && input.title.isNotBlank()),
                RowSpec("account", stringResource(R.string.quicknote_field_account), account?.name ?: none, false, DraftField.ACCOUNT in input.uncertain && account != null),
            )
        }
        is ProposalInput.Transfer -> {
            val from = accounts.firstOrNull { it.id == input.fromId }
            val to = accounts.firstOrNull { it.id == input.toId }
            buildList {
                add(RowSpec("from", stringResource(R.string.quicknote_field_from), from?.name ?: stringResource(R.string.quicknote_pick_account),
                    blockers.hit(ProposalBlocker.FROM_MISSING, ProposalBlocker.SAME_ACCOUNT, ProposalBlocker.CURRENCY_MISMATCH), DraftField.ACCOUNT in input.uncertain && from != null))
                add(RowSpec("to", stringResource(R.string.quicknote_field_to), to?.name ?: stringResource(R.string.quicknote_pick_account),
                    blockers.hit(ProposalBlocker.TO_MISSING), DraftField.COUNTERPART_ACCOUNT in input.uncertain && to != null))
                add(RowSpec("amount", stringResource(R.string.quicknote_field_amount), money(input.amount, input.currency, from) ?: stringResource(R.string.quicknote_add_amount),
                    blockers.hit(ProposalBlocker.AMOUNT_MISSING, ProposalBlocker.AMOUNT_INVALID), DraftField.AMOUNT in input.uncertain))
                if (from != null && to != null && from.currency != to.currency) {
                    add(RowSpec("received", stringResource(R.string.quicknote_field_received), money(input.received, to.currency, to) ?: stringResource(R.string.quicknote_add_amount),
                        blockers.hit(ProposalBlocker.RECEIVED_MISSING), false))
                }
                add(RowSpec("date", stringResource(R.string.quicknote_field_date), input.date?.let { IncomeFormat.fullDate(it) } ?: stringResource(R.string.quicknote_pick_date),
                    blockers.hit(ProposalBlocker.DATE_MISSING, ProposalBlocker.DATE_IN_FUTURE), DraftField.DATE in input.uncertain))
            }
        }
    }
}

@Composable
private fun TransactionType.labelText(): String = stringResource(
    when (this) {
        TransactionType.EXPENSE -> R.string.quicknote_type_expense
        TransactionType.INCOME -> R.string.quicknote_type_income
        TransactionType.REFUND -> R.string.quicknote_type_refund
        else -> R.string.quicknote_pick_type
    },
)

/** One proposal as a card, for a note that produced several. Each is saved or dropped on its own. */
@Composable
private fun ProposalCard(item: ProposalState, accounts: List<Account>, actions: QuickNoteActions) {
    val colors = ZarTheme.colors
    val input = item.input
    val done = item.status == ProposalStatus.SAVED || item.status == ProposalStatus.DISCARDED
    val tag = when (input) {
        is ProposalInput.Actual -> when (input.type) {
            TransactionType.INCOME -> R.string.quicknote_tag_received
            TransactionType.REFUND -> R.string.quicknote_tag_refund
            else -> R.string.quicknote_tag_paid
        }
        is ProposalInput.Expected -> if (input.direction == Direction.IN) R.string.quicknote_tag_expected_in else R.string.quicknote_tag_expected_out
        is ProposalInput.Transfer -> R.string.quicknote_tag_transfer
    }
    val account = accounts.firstOrNull { it.id == (item.purchase?.current?.accountId ?: (input as? ProposalInput.Actual)?.accountId ?: (input as? ProposalInput.Expected)?.accountId ?: (input as? ProposalInput.Transfer)?.fromId) }
    val title = when (input) {
        is ProposalInput.Actual -> input.title.ifBlank { input.type?.labelText() ?: stringResource(R.string.quicknote_pick_type) }
        is ProposalInput.Expected -> input.title.ifBlank { stringResource(R.string.quicknote_add_title) }
        is ProposalInput.Transfer -> "${accounts.firstOrNull { it.id == input.fromId }?.name ?: "?"} → ${accounts.firstOrNull { it.id == input.toId }?.name ?: "?"}"
    }
    val amountText = when (input) {
        is ProposalInput.Actual -> input.amount
        is ProposalInput.Expected -> input.amount
        is ProposalInput.Transfer -> input.amount
    }
    val currency = account?.currency ?: when (input) {
        is ProposalInput.Actual -> input.currency
        is ProposalInput.Expected -> input.currency
        is ProposalInput.Transfer -> input.currency
    } ?: Currencies.REPORTING
    val amount = if (amountText.isBlank()) stringResource(R.string.quicknote_add_amount) else (Money.parse(amountText, currency)?.let { IncomeFormat.withCode(it) } ?: amountText)
    val sign = if (input is ProposalInput.Actual && input.type == TransactionType.EXPENSE && amountText.isNotBlank()) "− " else ""
    val when_ = when (input) {
        is ProposalInput.Actual -> input.date
        is ProposalInput.Expected -> input.dueDate
        is ProposalInput.Transfer -> input.date
    }
    val meta = listOfNotNull(
        account?.name,
        (input as? ProposalInput.Actual)?.categoryId?.let { ExpenseCategory.fromId(it) }?.let { stringResource(it.labelRes()) },
        when_?.let { (if (input is ProposalInput.Expected) stringResource(R.string.quicknote_field_due) + " " else "") + IncomeFormat.fullDate(it) },
    ).joinToString(" · ")
    Column(
        Modifier.fillMaxWidth().clip(ZarShapes.lg).border(BorderStroke(1.dp, colors.borderDefault), ZarShapes.lg).background(colors.surface)
            .alpha(if (item.status == ProposalStatus.DISCARDED) 0.55f else 1f).padding(20.dp).testTag("quicknote-card-${item.index}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(tag).uppercase(),
                style = ZarType.monoCaption,
                color = if (input is ProposalInput.Expected) colors.textAttention else colors.textSecondary,
                modifier = Modifier.clip(ZarShapes.xs)
                    .background((if (input is ProposalInput.Expected) colors.textAttention else colors.textSecondary).copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            when (item.status) {
                ProposalStatus.SAVED -> Text(stringResource(R.string.quicknote_tag_saved).uppercase(), style = ZarType.monoCaption, color = colors.textIncome, modifier = Modifier.testTag("quicknote-saved-${item.index}"))
                ProposalStatus.DISCARDED -> Text(stringResource(R.string.quicknote_tag_discarded).uppercase(), style = ZarType.monoCaption, color = colors.textTertiary)
                else -> Text(
                    stringResource(R.string.quicknote_edit),
                    style = ZarType.labelAction,
                    color = colors.textSelected,
                    modifier = Modifier.clickable(role = Role.Button) { actions.onOpenEditor(item.index) }.testTag("quicknote-edit-${item.index}"),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(sign + amount, style = ZarType.bodyM, color = if (item.blockers.hit(ProposalBlocker.AMOUNT_MISSING, ProposalBlocker.AMOUNT_INVALID)) colors.textAttention else colors.textPrimary)
        }
        if (meta.isNotEmpty()) Text(meta, style = ZarType.bodyCaption, color = colors.textSecondary)
        if (input is ProposalInput.Expected) {
            Text(
                stringResource(if (input.direction == Direction.IN) R.string.quicknote_expected_in_note else R.string.quicknote_expected_out_note),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
            )
        }
        if (!done) {
            ProposalHint(item)
            val saving = item.status == ProposalStatus.SAVING
            Text(
                stringResource(
                    when {
                        saving -> R.string.quicknote_saving
                        input is ProposalInput.Expected -> R.string.quicknote_add_expected
                        else -> R.string.quicknote_save_record
                    },
                ),
                style = ZarType.labelAction,
                color = if (item.canSave) colors.textSelected else colors.textDisabled,
                modifier = Modifier.clickable(enabled = item.canSave, role = Role.Button) { actions.onConfirm(item.index) }.testTag("quicknote-save-${item.index}"),
            )
        }
    }
}

private fun ProposalBlocker.hintRes(): Int = when (this) {
    ProposalBlocker.TYPE_MISSING -> R.string.quicknote_hint_type_missing
    ProposalBlocker.DIRECTION_MISSING -> R.string.quicknote_hint_direction_missing
    ProposalBlocker.AMOUNT_MISSING -> R.string.quicknote_hint_amount_missing
    ProposalBlocker.AMOUNT_INVALID -> R.string.quicknote_hint_amount_invalid
    ProposalBlocker.ACCOUNT_MISSING -> R.string.quicknote_hint_account_missing
    ProposalBlocker.CURRENCY_MISMATCH -> R.string.quicknote_hint_currency_mismatch
    ProposalBlocker.DATE_MISSING -> R.string.quicknote_hint_date_missing
    ProposalBlocker.DATE_IN_FUTURE -> R.string.quicknote_hint_date_in_future
    ProposalBlocker.TITLE_MISSING -> R.string.quicknote_hint_title_missing
    ProposalBlocker.PURCHASE_MISSING -> R.string.quicknote_hint_purchase_missing
    ProposalBlocker.REFUND_TOO_HIGH -> R.string.quicknote_hint_refund_too_high
    ProposalBlocker.FROM_MISSING -> R.string.quicknote_hint_from_missing
    ProposalBlocker.TO_MISSING -> R.string.quicknote_hint_to_missing
    ProposalBlocker.SAME_ACCOUNT -> R.string.quicknote_hint_same_account
    ProposalBlocker.RECEIVED_MISSING -> R.string.quicknote_hint_received_missing
}

// --- Edit -----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.EditStage(item: ProposalState, state: QuickNoteUiState, actions: QuickNoteActions) {
    val colors = ZarTheme.colors
    val index = item.index
    SheetBody(Modifier.weight(1f, fill = false)) {
        when (val input = item.input) {
            is ProposalInput.Actual -> ActualFields(index, input, item, state, actions)
            is ProposalInput.Expected -> ExpectedFields(index, input, state.accounts, actions)
            is ProposalInput.Transfer -> TransferFields(index, input, state.accounts, actions)
        }
        ProposalHint(item)
    }
    SheetButtons {
        ZarButton(stringResource(R.string.quicknote_discard), { actions.onDiscard(index) }, Modifier.weight(1f).testTag("quicknote-editor-discard"), style = ZarButtonStyle.Soft)
        ZarButton(stringResource(R.string.quicknote_done), actions.onCloseEditor, Modifier.weight(1f).testTag("quicknote-done"))
    }
}

@Composable
private fun ActualFields(index: Int, input: ProposalInput.Actual, item: ProposalState, state: QuickNoteUiState, actions: QuickNoteActions) {
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND)
    FieldGroup(stringResource(R.string.quicknote_field_type), input.uncertain.contains(DraftField.TYPE)) {
        Segmented(
            labels = types.map { it.labelText() },
            selectedIndex = types.indexOf(input.type),
            onSelect = { position ->
                val type = types[position]
                actions.onUpdate(index) {
                    (it as ProposalInput.Actual).copy(
                        type = type,
                        refundOf = if (type == TransactionType.REFUND) it.refundOf else null,
                        spendingClass = if (type == TransactionType.EXPENSE) it.spendingClass ?: SpendingClass.UNKNOWN else null,
                        categoryId = if (type == TransactionType.EXPENSE) it.categoryId else null,
                    )
                }
            },
            segmentHeight = 38.dp,
            tagPrefix = "quicknote-type",
            tags = listOf("expense", "income", "refund"),
        )
    }
    AmountField(input.amount, state.accounts.firstOrNull { it.id == (item.purchase?.current?.accountId ?: input.accountId) }?.currency ?: input.currency, input.uncertain.contains(DraftField.AMOUNT)) { text ->
        actions.onUpdate(index) { (it as ProposalInput.Actual).copy(amount = text) }
    }
    if (input.type == TransactionType.REFUND) {
        FieldGroup(stringResource(R.string.quicknote_field_purchase), false) {
            if (state.purchases.isEmpty()) {
                Text(stringResource(R.string.quicknote_pick_purchase_empty), style = ZarType.bodyCaption, color = ZarTheme.colors.textSecondary)
            }
            state.purchases.forEach { entry ->
                val purchase = entry.current
                Row(
                    Modifier.fillMaxWidth().clip(ZarShapes.sm)
                        .background(if (purchase.id == input.refundOf) ZarTheme.colors.selectedTint else ZarTheme.colors.subtle)
                        .clickable(role = Role.RadioButton) {
                            actions.onUpdate(index) { (it as ProposalInput.Actual).copy(refundOf = purchase.id, accountId = purchase.accountId) }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("quicknote-purchase-${purchase.id.value}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(purchase.merchant ?: stringResource(R.string.quicknote_type_expense), style = ZarType.labelChip, color = ZarTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${IncomeFormat.withCode(purchase.amount)} · ${IncomeFormat.shortDate(purchase.date)}", style = ZarType.bodyCaption, color = ZarTheme.colors.textSecondary)
                }
            }
        }
    } else {
        FieldGroup(stringResource(R.string.quicknote_field_account), input.uncertain.contains(DraftField.ACCOUNT)) {
            AccountChips(state.accounts, input.accountId, "quicknote-account") { id -> actions.onUpdate(index) { (it as ProposalInput.Actual).copy(accountId = id) } }
        }
    }
    if (input.type == TransactionType.EXPENSE) {
        FieldGroup(stringResource(R.string.quicknote_field_category), input.uncertain.contains(DraftField.CATEGORY)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { category ->
                    ZarChip(
                        label = stringResource(category.labelRes()),
                        selected = input.categoryId == category.id,
                        onClick = { actions.onUpdate(index) { (it as ProposalInput.Actual).copy(categoryId = category.id) } },
                        modifier = Modifier.testTag("quicknote-category-${category.id}"),
                    )
                }
            }
        }
    }
    DateField(input.date, stringResource(R.string.quicknote_field_date), allowFuture = false, input.uncertain.contains(DraftField.DATE), "quicknote-date") { date ->
        actions.onUpdate(index) { (it as ProposalInput.Actual).copy(date = date) }
    }
    ZarFormRow(stringResource(R.string.quicknote_field_title)) {
        TextInput(input.title, { text -> actions.onUpdate(index) { (it as ProposalInput.Actual).copy(title = text.take(120)) } }, stringResource(R.string.quicknote_add_title), Modifier.weight(1f).testTag("quicknote-title-input"))
    }
    ZarFormRow(stringResource(R.string.quicknote_field_note)) {
        TextInput(input.note, { text -> actions.onUpdate(index) { (it as ProposalInput.Actual).copy(note = text.take(500)) } }, stringResource(R.string.quicknote_not_set), Modifier.weight(1f).testTag("quicknote-note-input"))
    }
}

@Composable
private fun ExpectedFields(index: Int, input: ProposalInput.Expected, accounts: List<Account>, actions: QuickNoteActions) {
    val directions = listOf(Direction.IN, Direction.OUT)
    FieldGroup(stringResource(R.string.quicknote_field_type), input.uncertain.contains(DraftField.TYPE)) {
        Segmented(
            labels = listOf(stringResource(R.string.quicknote_dir_in), stringResource(R.string.quicknote_dir_out)),
            selectedIndex = directions.indexOf(input.direction),
            onSelect = { position -> actions.onUpdate(index) { (it as ProposalInput.Expected).copy(direction = directions[position]) } },
            segmentHeight = 38.dp,
            tagPrefix = "quicknote-direction",
            tags = listOf("in", "out"),
        )
    }
    AmountField(input.amount, accounts.firstOrNull { it.id == input.accountId }?.currency ?: input.currency, input.uncertain.contains(DraftField.AMOUNT)) { text ->
        actions.onUpdate(index) { (it as ProposalInput.Expected).copy(amount = text) }
    }
    DateField(input.dueDate, stringResource(R.string.quicknote_field_due), allowFuture = true, input.uncertain.contains(DraftField.DATE), "quicknote-date") { date ->
        actions.onUpdate(index) { (it as ProposalInput.Expected).copy(dueDate = date) }
    }
    ZarFormRow(stringResource(R.string.quicknote_field_title)) {
        TextInput(input.title, { text -> actions.onUpdate(index) { (it as ProposalInput.Expected).copy(title = text.take(120)) } }, stringResource(R.string.quicknote_add_title), Modifier.weight(1f).testTag("quicknote-title-input"))
    }
    FieldGroup(stringResource(R.string.quicknote_field_account), input.uncertain.contains(DraftField.ACCOUNT)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZarChip(
                label = stringResource(R.string.quicknote_not_set),
                selected = input.accountId == null,
                onClick = { actions.onUpdate(index) { (it as ProposalInput.Expected).copy(accountId = null) } },
                modifier = Modifier.testTag("quicknote-account-none"),
            )
            accounts.forEach { account ->
                ZarChip(
                    label = account.name,
                    selected = account.id == input.accountId,
                    onClick = { actions.onUpdate(index) { (it as ProposalInput.Expected).copy(accountId = account.id) } },
                    icon = account.type.iconRes(),
                    modifier = Modifier.testTag("quicknote-account-${account.id.value}"),
                )
            }
        }
    }
}

@Composable
private fun TransferFields(index: Int, input: ProposalInput.Transfer, accounts: List<Account>, actions: QuickNoteActions) {
    val from = accounts.firstOrNull { it.id == input.fromId }
    val to = accounts.firstOrNull { it.id == input.toId }
    FieldGroup(stringResource(R.string.quicknote_field_from), input.uncertain.contains(DraftField.ACCOUNT)) {
        AccountChips(accounts, input.fromId, "quicknote-from") { id -> actions.onUpdate(index) { (it as ProposalInput.Transfer).copy(fromId = id) } }
    }
    FieldGroup(stringResource(R.string.quicknote_field_to), input.uncertain.contains(DraftField.COUNTERPART_ACCOUNT)) {
        AccountChips(accounts, input.toId, "quicknote-to") { id -> actions.onUpdate(index) { (it as ProposalInput.Transfer).copy(toId = id) } }
    }
    AmountField(input.amount, from?.currency ?: input.currency, input.uncertain.contains(DraftField.AMOUNT)) { text ->
        actions.onUpdate(index) { (it as ProposalInput.Transfer).copy(amount = text) }
    }
    if (from != null && to != null && from.currency != to.currency) {
        AmountField(input.received, to.currency, false, label = stringResource(R.string.quicknote_field_received), tag = "quicknote-received") { text ->
            actions.onUpdate(index) { (it as ProposalInput.Transfer).copy(received = text) }
        }
    }
    DateField(input.date, stringResource(R.string.quicknote_field_date), allowFuture = false, input.uncertain.contains(DraftField.DATE), "quicknote-date") { date ->
        actions.onUpdate(index) { (it as ProposalInput.Transfer).copy(date = date) }
    }
}

@Composable
private fun FieldGroup(label: String, guessed: Boolean, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ZarMonoLabel(label)
            if (guessed) FieldTag(stringResource(R.string.quicknote_tag_guessed), "quicknote-guessed-group")
        }
        content()
    }
}

@Composable
private fun AmountField(
    value: String,
    currency: Currency?,
    guessed: Boolean,
    label: String = stringResource(R.string.quicknote_field_amount),
    tag: String = "quicknote-amount",
    onChange: (String) -> Unit,
) {
    val colors = ZarTheme.colors
    ZarFormRow(label) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextInput(value, onChange, "0", Modifier.weight(1f).testTag(tag), ZarType.amountM, KeyboardType.Decimal)
            if (guessed) FieldTag(stringResource(R.string.quicknote_tag_guessed), "$tag-guessed")
            Box(Modifier.padding(start = 8.dp).clip(ZarShapes.sm).background(colors.subtle).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text((currency ?: Currencies.REPORTING).code, style = ZarType.labelChip, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun AccountChips(accounts: List<Account>, selected: AccountId?, tagPrefix: String, onSelect: (AccountId) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { account ->
            ZarChip(
                label = account.name,
                selected = account.id == selected,
                onClick = { onSelect(account.id) },
                icon = account.type.iconRes(),
                modifier = Modifier.testTag("$tagPrefix-${account.id.value}"),
            )
        }
    }
}

/** A date that may still be missing. Due dates may be in the future; dates of things that happened may not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, label: String, allowFuture: Boolean, guessed: Boolean, tag: String, onSelect: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }.testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        ZarFormRow(label, Modifier.weight(1f)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date?.let { IncomeFormat.fullDate(it) } ?: stringResource(R.string.quicknote_pick_date),
                    style = ZarType.bodyM,
                    color = if (date == null) colors.textAttention else colors.textPrimary,
                )
                if (guessed) FieldTag(stringResource(R.string.quicknote_tag_guessed), "$tag-guessed")
                ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
            }
        }
    }
    if (!showPicker) return
    val todayMillis = ZarTime.today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (date ?: ZarTime.today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis <= todayMillis
        },
    )
    val pickerColors = DatePickerDefaults.colors(containerColor = colors.surface, selectedDayContainerColor = colors.accentSelected)
    DatePickerDialog(
        colors = pickerColors,
        tonalElevation = 0.dp,
        onDismissRequest = { showPicker = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showPicker = false
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state, colors = pickerColors) }
}
