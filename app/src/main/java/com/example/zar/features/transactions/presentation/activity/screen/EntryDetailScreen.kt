package com.example.zar.features.transactions.presentation.activity.screen

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.transactions.domain.activity.model.CorrectedField
import com.example.zar.features.transactions.domain.activity.model.EditKind
import com.example.zar.features.transactions.domain.activity.model.EntryDetail
import com.example.zar.features.transactions.domain.activity.model.FieldValue
import com.example.zar.features.transactions.presentation.activity.EntryDisplay
import com.example.zar.features.transactions.presentation.activity.entryTitle
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailViewModel
import com.example.zar.features.transactions.presentation.activity.viewmodel.VoidProblem
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.labelRes

/**
 * One entry in full: what was recorded, what has been corrected since, and whether it is voided.
 * Laid out like Figma Z14 and the Z51–Z53 states, with the void sheet from Z56–Z58.
 */
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onEditIncome: (String) -> Unit,
    onAddAgain: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EntryDetailBody(
        state = state,
        actions = DetailActions(
            onBack = onBack,
            onEdit = onEdit,
            onEditIncome = onEditIncome,
            onAddAgain = onAddAgain,
            onVoid = viewModel::showVoidSheet,
            onDismissVoid = viewModel::dismissVoidSheet,
            onConfirmVoid = viewModel::confirmVoid,
        ),
    )
}

internal class DetailActions(
    val onBack: () -> Unit,
    val onEdit: (String) -> Unit,
    val onEditIncome: (String) -> Unit,
    val onAddAgain: () -> Unit,
    val onVoid: () -> Unit,
    val onDismissVoid: () -> Unit,
    val onConfirmVoid: () -> Unit,
)

@Composable
internal fun EntryDetailBody(state: EntryDetailUiState, actions: DetailActions) {
    DarkStatusBarIcons()
    val colors = ZarTheme.colors
    val detail = (state.content as? EntryDetailContent.Ready)?.detail
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            ZarBackButton(actions.onBack, stringResource(R.string.activity_back), Modifier.padding(start = 10.dp, top = backTop))
            when (state.content) {
                EntryDetailContent.Loading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                EntryDetailContent.NotFound -> Message(stringResource(R.string.activity_detail_not_found), "activity-detail-not-found")
                EntryDetailContent.Error -> Message(stringResource(R.string.activity_detail_failed), "activity-detail-error")
                is EntryDetailContent.Ready -> DetailContent(state, (state.content as EntryDetailContent.Ready).detail)
            }
            // Room so the last card clears the action buttons.
            Spacer(Modifier.height(168.dp))
        }
        if (detail != null) DetailActionsBar(detail, actions, Modifier.align(Alignment.BottomCenter))
    }
    if (detail != null && state.showVoidSheet) VoidSheet(state, detail, actions)
}

@Composable
private fun Message(text: String, tag: String) {
    Text(
        text,
        style = ZarType.bodyS,
        color = ZarTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 72.dp).testTag(tag),
    )
}

// --- Content ----------------------------------------------------------------------------------

@Composable
private fun DetailContent(state: EntryDetailUiState, detail: EntryDetail) {
    val entry = detail.entry
    val tx = entry.current
    val colors = ZarTheme.colors
    val voided = entry.isVoided
    val transferTitle = transferTitle(detail)

    // Figma: icon chip at y108, title at 180, amount at 206, date line at 246, details card at 282.
    Column(Modifier.padding(horizontal = 22.dp).padding(top = 10.dp)) {
        ZarIconChip(EntryDisplay.icon(tx), size = 56.dp, iconSize = 26.dp, modifier = Modifier.alpha(if (voided) 0.6f else 1f))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                transferTitle ?: entryTitle(tx),
                style = ZarType.headingSheet.copy(lineHeight = 23.sp),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (voided) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f, fill = false).testTag("detail-title"),
            )
            if (voided) TagPill(stringResource(R.string.activity_tag_void), attention = true)
            else if (entry.isCorrected) TagPill(stringResource(R.string.activity_tag_edited))
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.textTertiary)) {
                    if (!detail.isTransfer) append(if (tx.direction == Direction.IN) "+ " else "− ")
                    append("${tx.amount.currency.code} ")
                }
                append(IncomeFormat.amount(tx.amount))
            },
            style = ZarType.amountM.copy(lineHeight = 36.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (voided) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(top = 2.dp).testTag("detail-amount"),
        )
        Text(
            "${stringResource(EntryDisplay.typeLabel(tx.type))} · ${EntryDisplay.day(tx.date)}",
            style = ZarType.bodyCaption.copy(lineHeight = 16.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (entry.isCorrected) {
            val amountChanged = entry.original.amount != tx.amount
            Text(
                buildString {
                    append(pluralText(R.plurals.activity_edited_times, entry.corrections.size))
                    if (amountChanged) append(" · ").append(stringResource(R.string.activity_originally, IncomeFormat.withCode(entry.original.amount)))
                },
                style = ZarType.bodyCaption.copy(lineHeight = 16.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp).testTag("detail-edited"),
            )
        }
    }

    entry.voidMarker?.let { marker ->
        InfoBanner(
            title = stringResource(R.string.activity_voided_title, EntryDisplay.stamp(marker.voidedAt)),
            body = stringResource(R.string.activity_voided_body),
            icon = R.drawable.ic_ph_prohibit,
            modifier = Modifier.padding(horizontal = ZarDimens.ScreenGutter).padding(top = 20.dp).testTag("detail-voided"),
        )
    }

    DetailsCard(state, detail, Modifier.padding(horizontal = ZarDimens.ScreenGutter).padding(top = 19.dp))

    if (detail.isTransfer && detail.counterpart != null) {
        LinkedEntriesCard(detail, Modifier.padding(horizontal = ZarDimens.ScreenGutter).padding(top = 8.dp))
    }
    if (detail.history.isNotEmpty()) {
        HistoryCard(detail, Modifier.padding(horizontal = ZarDimens.ScreenGutter).padding(top = 8.dp))
    }
}

@Composable
private fun pluralText(@PluralsRes id: Int, count: Int): String = pluralStringResource(id, count, count)

@Composable
private fun transferTitle(detail: EntryDetail): String? {
    if (!detail.isTransfer) return null
    val mine = detail.account?.name
    val other = detail.counterpartAccount?.name
    val isOut = detail.entry.original.type == TransactionType.TRANSFER_OUT
    return when {
        mine == null || other == null -> null
        isOut -> "$mine → $other"
        else -> "$other → $mine"
    }
}

@Composable
private fun DetailsCard(state: EntryDetailUiState, detail: EntryDetail, modifier: Modifier = Modifier) {
    val entry = detail.entry
    val tx = entry.current
    val colors = ZarTheme.colors
    val none = stringResource(R.string.activity_none)
    val rows = buildList<@Composable () -> Unit> {
        if (detail.isTransfer) {
            val isOut = entry.original.type == TransactionType.TRANSFER_OUT
            val from = if (isOut) detail.account else detail.counterpartAccount
            val to = if (isOut) detail.counterpartAccount else detail.account
            add { ValueRow(stringResource(R.string.manual_field_from), from?.name ?: none) }
            add { ValueRow(stringResource(R.string.manual_field_to), to?.name ?: none) }
            detail.fee?.let { fee ->
                add { ValueRow(stringResource(R.string.manual_field_fees), stringResource(R.string.activity_fee_value, IncomeFormat.withCode(fee.current.amount))) }
            }
            add { ValueRow(stringResource(R.string.manual_field_note), tx.note ?: none, placeholder = tx.note == null) }
        } else {
            add { ValueRow(stringResource(R.string.manual_field_account), detail.account?.name ?: none) }
            EntryDisplay.categoryLabel(tx.categoryId)?.let { label ->
                add { ValueRow(stringResource(R.string.manual_field_category), stringResource(label)) }
            }
            if (tx.type == TransactionType.EXPENSE) {
                add { ClassRow(tx.spendingClass) }
            }
            add { ValueRow(stringResource(R.string.manual_field_description), tx.note ?: none, placeholder = tx.note == null) }
            add { ValueRow(stringResource(R.string.activity_field_added), stringResource(sourceLabel(entry.original.source))) }
        }
        add {
            val status = when {
                entry.isVoided -> R.string.activity_status_voided
                !state.isOnline -> R.string.activity_status_saved_offline
                else -> R.string.activity_status_saved
            }
            ValueRow(stringResource(R.string.activity_field_status), stringResource(status), dot = if (entry.isVoided) colors.textAttention else colors.fresh)
        }
    }
    ZarCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), soft = true) {
        rows.forEachIndexed { index, row ->
            if (index > 0) ZarDivider()
            Box(Modifier.padding(vertical = 14.dp)) { row() }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, placeholder: Boolean = false, dot: Color? = null) {
    val colors = ZarTheme.colors
    ZarFormRow(label, labelWidth = 110.dp) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(value, style = ZarType.bodyM, color = if (placeholder) colors.textPlaceholder else colors.textPrimary)
        }
    }
}

@Composable
private fun ClassRow(spendingClass: SpendingClass?) {
    val colors = ZarTheme.colors
    val cls = spendingClass ?: SpendingClass.UNKNOWN
    val dot = when (cls) {
        SpendingClass.COMMITTED -> colors.chartCommitted
        SpendingClass.DISCRETIONARY -> colors.chartDiscretionary
        SpendingClass.UNKNOWN -> colors.chartUnknown
    }
    ValueRow(stringResource(R.string.activity_field_type), stringResource(cls.labelRes()), dot = dot)
}

@Composable
private fun LinkedEntriesCard(detail: EntryDetail, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    val legs = listOfNotNull(detail.entry, detail.counterpart).sortedBy { if (it.original.type == TransactionType.TRANSFER_OUT) 0 else 1 }
    ZarCard(modifier.fillMaxWidth().testTag("detail-linked"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), soft = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZarMonoLabel(stringResource(R.string.activity_linked_title))
            legs.forEach { leg ->
                val tx = leg.current
                val isOut = leg.original.type == TransactionType.TRANSFER_OUT
                val name = (if (leg.id == detail.entry.id) detail.account else detail.counterpartAccount)?.name.orEmpty()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZarIconChip(EntryDisplay.icon(tx), size = 36.dp, iconSize = 18.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(if (isOut) R.string.activity_linked_out else R.string.activity_linked_in, name),
                            style = ZarType.bodyM.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary,
                        )
                        Text(
                            "${IncomeFormat.shortDate(tx.date)} · ${stringResource(R.string.activity_saved_together)}",
                            style = ZarType.bodyCaption,
                            color = colors.textSecondary,
                        )
                    }
                    Text(EntryDisplay.signed(tx), style = ZarType.bodyM, color = colors.textPrimary)
                }
            }
            Text(
                detail.fee?.let { stringResource(R.string.activity_linked_note_fee, IncomeFormat.withCode(it.current.amount)) }
                    ?: stringResource(R.string.activity_linked_note),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HistoryCard(detail: EntryDetail, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    val entry = detail.entry
    // Newest first, one line per field a correction changed, then the original record at the bottom.
    val lines = detail.history.reversed().flatMap { event -> event.changes.map { it to event.correction.correctedAt } }
    ZarCard(modifier.fillMaxWidth().testTag("detail-history"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), soft = true) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ZarMonoLabel(stringResource(R.string.activity_history_title))
            lines.forEach { (change, at) ->
                HistoryLine(
                    title = stringResource(historyTitle(change.field)),
                    detail = "${historyValue(change.before)} → ${historyValue(change.after)} · ${EntryDisplay.stamp(at)}",
                    dot = colors.accentFresh,
                )
            }
            HistoryLine(
                title = stringResource(R.string.activity_history_added),
                detail = "${stringResource(sourceLabel(entry.original.source))} · ${EntryDisplay.stamp(entry.original.createdAt)}",
                dot = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun HistoryLine(title: String, detail: String, dot: Color) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dot))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZarType.bodyS, color = colors.textPrimary)
            Text(detail, style = ZarType.bodyCaption, color = colors.textSecondary)
        }
    }
}

@StringRes
private fun historyTitle(field: CorrectedField): Int = when (field) {
    CorrectedField.AMOUNT -> R.string.activity_history_amount
    CorrectedField.DATE -> R.string.activity_history_date
    CorrectedField.CATEGORY -> R.string.activity_history_category
    CorrectedField.SPENDING_CLASS -> R.string.activity_history_class
    CorrectedField.TITLE -> R.string.activity_history_title_field
    CorrectedField.NOTE -> R.string.activity_history_note
}

@Composable
private fun historyValue(value: FieldValue): String = when (value) {
    is FieldValue.Amount -> IncomeFormat.withCode(value.money)
    is FieldValue.Day -> IncomeFormat.shortDate(value.date)
    is FieldValue.Class -> stringResource((value.spendingClass ?: SpendingClass.UNKNOWN).labelRes())
    is FieldValue.Text -> value.text?.let { text -> EntryDisplay.categoryLabel(text)?.let { stringResource(it) } ?: text }
        ?: stringResource(R.string.activity_none)
}

@StringRes
private fun sourceLabel(source: RecordSource): Int = when (source) {
    RecordSource.MANUAL -> R.string.activity_source_manual
    RecordSource.SMS -> R.string.activity_source_sms
    RecordSource.SHARED_TEXT -> R.string.activity_source_shared
    RecordSource.QUICK_NOTE -> R.string.activity_source_quick_note
    RecordSource.CASH_CATCH_UP -> R.string.activity_source_catch_up
    RecordSource.RECONCILIATION -> R.string.activity_source_reconciliation
}

// --- Actions and the void sheet ---------------------------------------------------------------

@Composable
private fun DetailActionsBar(detail: EntryDetail, actions: DetailActions, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    val canvas = colors.canvas
    // Figma: the caption sits at y779 and the 58dp buttons at y811, 46dp above the screen edge.
    Column(
        modifier
            .fillMaxWidth()
            // Fades the content out under the buttons so text never runs through them.
            .background(Brush.verticalGradient(listOf(Color.Transparent, canvas, canvas)))
            .navigationBarsPadding()
            .padding(top = 28.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(
                when {
                    detail.entry.isVoided -> R.string.activity_void_final
                    detail.isTransfer -> R.string.activity_void_caption_transfer
                    else -> R.string.activity_void_caption
                },
            ),
            style = ZarType.bodyS.copy(lineHeight = 18.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        Row(Modifier.padding(horizontal = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (detail.entry.isVoided) {
                ZarButton(
                    text = stringResource(R.string.activity_add_again),
                    onClick = actions.onAddAgain,
                    modifier = Modifier.weight(1f).testTag("detail-add-again"),
                )
                return@Row
            }
            ZarButton(
                text = stringResource(if (detail.isTransfer) R.string.activity_void_transfer else R.string.activity_void_entry),
                onClick = actions.onVoid,
                style = ZarButtonStyle.Outline,
                icon = R.drawable.ic_ph_trash,
                contentColor = colors.textAttention,
                modifier = Modifier.weight(1f).testTag("detail-void"),
            )
            when (detail.editKind) {
                EditKind.EXPENSE -> ZarButton(
                    text = stringResource(R.string.activity_edit),
                    onClick = { actions.onEdit(detail.entry.id.value) },
                    modifier = Modifier.weight(1f).testTag("detail-edit"),
                )
                EditKind.INCOME -> ZarButton(
                    text = stringResource(R.string.activity_edit),
                    onClick = { actions.onEditIncome(detail.entry.id.value) },
                    modifier = Modifier.weight(1f).testTag("detail-edit"),
                )
                EditKind.NONE -> Unit
            }
        }
    }
}

/** Z56–Z58: confirms the undo. A void is final, so the wording says so and points to "add it again". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoidSheet(state: EntryDetailUiState, detail: EntryDetail, actions: DetailActions) {
    val colors = ZarTheme.colors
    val tx = detail.entry.current
    ModalBottomSheet(
        onDismissRequest = actions.onDismissVoid,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 14.dp).testTag("void-sheet"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SheetHeader(
                stringResource(if (detail.isTransfer) R.string.activity_void_title_transfer else R.string.activity_void_title),
                onClose = actions.onDismissVoid,
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.subtle).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZarIconChip(EntryDisplay.icon(tx), size = 40.dp, iconSize = 19.dp, background = colors.surface)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        transferTitle(detail) ?: entryTitle(tx),
                        style = ZarType.bodyM.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            if (detail.isTransfer) IncomeFormat.withCode(tx.amount) else "${if (tx.direction == Direction.IN) "+" else "−"} ${IncomeFormat.withCode(tx.amount)}",
                            detail.account?.name.takeIf { !detail.isTransfer },
                            IncomeFormat.shortDate(tx.date),
                        ).joinToString(" · "),
                        style = ZarType.bodyS.copy(lineHeight = 18.sp),
                        color = colors.textSecondary,
                    )
                }
            }
            Text(
                voidBody(detail),
                style = ZarType.bodyS.copy(lineHeight = 18.sp),
                color = colors.textSecondary,
                modifier = Modifier.testTag("void-body"),
            )
            if (!state.isOnline) {
                InfoBanner(
                    title = stringResource(R.string.activity_offline_title),
                    body = stringResource(R.string.activity_void_offline_body),
                    modifier = Modifier.testTag("void-offline"),
                )
            }
            state.voidProblem?.let { problem ->
                Text(
                    stringResource(if (problem == VoidProblem.NOT_SIGNED_IN) R.string.manual_message_not_signed_in else R.string.activity_void_failed),
                    style = ZarType.bodyCaption,
                    color = colors.danger,
                    modifier = Modifier.testTag("void-error"),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(
                    text = stringResource(R.string.activity_void_keep),
                    onClick = actions.onDismissVoid,
                    style = ZarButtonStyle.Soft,
                    enabled = !state.isVoiding,
                    modifier = Modifier.weight(1f).testTag("void-keep"),
                )
                // The confirm button is coral, as in Figma Z18, so it reads as the destructive choice.
                Box(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .alpha(if (state.isVoiding) 0.4f else 1f)
                        .clip(ZarShapes.lg)
                        .background(colors.textAttention)
                        .clickable(enabled = !state.isVoiding, role = Role.Button, onClick = actions.onConfirmVoid)
                        .testTag("void-confirm"),
                    Alignment.Center,
                ) {
                    Text(
                        stringResource(if (detail.isTransfer) R.string.activity_void_transfer else R.string.activity_void_entry),
                        style = ZarType.labelButton,
                        color = colors.textOnInverse,
                    )
                }
            }
        }
    }
}

@Composable
private fun voidBody(detail: EntryDetail): String = when {
    !detail.isTransfer -> stringResource(R.string.activity_void_body)
    detail.fee != null -> stringResource(R.string.activity_void_body_transfer_fee, IncomeFormat.withCode(detail.fee.current.amount))
    else -> stringResource(R.string.activity_void_body_transfer)
}
