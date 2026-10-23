package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonStyle
import com.example.zar.core.presentation.components.ZarChip
import com.example.zar.core.presentation.components.ZarFormRow
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillOccurrence
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.model.BillSummary
import com.example.zar.features.bills.presentation.viewmodel.BillUiState
import com.example.zar.features.bills.presentation.viewmodel.BillMessage
import com.example.zar.features.bills.presentation.viewmodel.BillViewModel
import com.example.zar.features.transactions.presentation.activity.screen.InfoBanner
import com.example.zar.features.transactions.presentation.activity.screen.SheetGrabber
import com.example.zar.features.transactions.presentation.activity.screen.SheetHeader
import com.example.zar.features.transactions.presentation.activity.screen.SheetScrim
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Z23 — Bill detail: what the bill expects, its due dates and the payments matched to them. */
@Composable
fun BillDetailScreen(
    onBack: () -> Unit,
    onEditBill: (String) -> Unit,
    onOpenDueDate: (billId: String, dueDate: String) -> Unit,
    viewModel: BillViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    BillDetailContent(uiState, message, isSaving, onBack, onEditBill, onOpenDueDate,
        viewModel::resume, viewModel::intendToCancel, viewModel::confirmCancelled,
        { viewModel.setStopped(true) }, viewModel::keep)
}

@Composable
internal fun BillDetailContent(
    uiState: BillUiState,
    message: BillMessage?,
    isSaving: Boolean,
    onBack: () -> Unit,
    onEditBill: (String) -> Unit,
    onOpenDueDate: (String, String) -> Unit,
    onResume: () -> Unit,
    onIntend: () -> Unit,
    onCancelled: (LocalDate) -> Unit,
    onStop: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = ZarTheme.colors
    val summary = uiState.summary
    var showEnd by rememberSaveable { mutableStateOf(false) }
    StatusBarGlyphs(onDarkHero = false)
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 22.dp, top = backTop), verticalAlignment = Alignment.CenterVertically) {
                ZarBackButton(onBack, stringResource(R.string.bills_back))
                Spacer(Modifier.weight(1f))
                if (summary != null) EndOrResume(summary.bill, enabled = !isSaving, onEnd = { showEnd = true }, onResume = onResume)
            }
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                uiState.loadFailed -> BillNote(stringResource(R.string.bills_load_failed), Modifier.padding(top = 24.dp))
                summary == null -> BillNote(stringResource(R.string.bill_not_found), Modifier.padding(top = 24.dp))
                else -> Detail(summary, uiState.accountNames, message, onEdit = { onEditBill(summary.bill.id.value) }, onOpenDueDate)
            }
            // Room so the last card clears the floating actions.
            Spacer(Modifier.height(112.dp))
        }
        if (summary != null) {
            val target = linkTarget(summary)
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(start = ZarDimens.ScreenGutter, end = ZarDimens.ScreenGutter, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ZarButton(
                    stringResource(R.string.bill_edit),
                    onClick = { onEditBill(summary.bill.id.value) },
                    modifier = Modifier.weight(1f).testTag("bill-edit"),
                    style = ZarButtonStyle.Outline,
                )
                if (target != null) {
                    ZarButton(
                        stringResource(R.string.bill_link_payment),
                        onClick = { onOpenDueDate(summary.bill.id.value, target.dueDate.toString()) },
                        modifier = Modifier.weight(1f).testTag("bill-link"),
                    )
                }
            }
        }
    }
    if (showEnd && summary != null) {
        EndBillSheet(
            bill = summary.bill,
            onDismiss = { showEnd = false },
            onIntend = { onIntend(); showEnd = false },
            onCancelled = { from -> onCancelled(from); showEnd = false },
            onStop = { onStop(); showEnd = false },
            onKeep = { onKeep(); showEnd = false },
        )
    }
}

/** The due date "Link a payment" opens: the latest one missed, otherwise the next one. */
private fun linkTarget(summary: BillSummary): BillOccurrence? {
    val today = ZarTime.today()
    val open = summary.occurrences.filter {
        it.progress.state == MatchState.NOT_MATCHED || it.progress.state == MatchState.PARTIALLY_MATCHED
    }
    return open.filter { it.dueDate < today }.maxByOrNull { it.dueDate } ?: open.filter { it.dueDate >= today }.minByOrNull { it.dueDate }
}

private val Bill.isEnded: Boolean get() = status == BillStatus.STOPPED || cancellation is BillCancellation.Confirmed

@Composable
private fun EndOrResume(bill: Bill, enabled: Boolean, onEnd: () -> Unit, onResume: () -> Unit) {
    val colors = ZarTheme.colors
    val ended = bill.isEnded
    Text(
        stringResource(if (ended) R.string.bill_resume else R.string.bill_end),
        style = ZarType.labelAction,
        color = if (ended) colors.textSelected else colors.textAttention,
        modifier = Modifier
            .clip(ZarShapes.xs)
            .clickable(enabled = enabled, role = Role.Button, onClick = if (ended) onResume else onEnd)
            .padding(vertical = 8.dp)
            .testTag(if (ended) "bill-resume" else "bill-end"),
    )
}

@Composable
private fun Detail(
    summary: BillSummary,
    accountNames: Map<AccountId, String>,
    message: BillMessage?,
    onEdit: () -> Unit,
    onOpenDueDate: (String, String) -> Unit,
) {
    val colors = ZarTheme.colors
    val bill = summary.bill
    val today = ZarTime.today()
    Column {
        ZarIconChip(BillFormat.icon(bill.name), Modifier.padding(start = 22.dp, top = 10.dp), size = 56.dp, iconSize = 26.dp)
        Text(bill.name, style = ZarType.headingSheet.copy(lineHeight = 23.sp), color = colors.textPrimary, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.textTertiary)) { append(bill.amount.currency.code + " ") }
                append(IncomeFormat.amount(bill.amount))
            },
            style = ZarType.amountM.copy(lineHeight = 34.sp),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 22.dp, top = 3.dp),
        )
        Text(subtitle(summary, today), style = ZarType.bodyS.copy(lineHeight = 18.sp), color = colors.textSecondary, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp))

        BillCard(Modifier.padding(top = 19.dp).testTag("bill-details")) {
            BillDetailRow(stringResource(R.string.bill_row_amount), stringResource(R.string.bill_amount_expected, IncomeFormat.withCode(bill.amount)), onEdit)
            ZarDivider()
            BillDetailRow(stringResource(R.string.bill_row_repeats), repeats(bill), onEdit)
            ZarDivider()
            BillDetailRow(
                stringResource(R.string.bill_row_pay_from),
                accountNames[bill.accountId] ?: summary.account?.name ?: stringResource(R.string.bill_no_account),
                onEdit,
            )
            ZarDivider()
            BillDetailRow(stringResource(R.string.bill_row_remind), stringResource(BillFormat.reminderRes(bill.reminder)), onEdit)
            ZarDivider()
            BillDetailRow(stringResource(R.string.bill_row_yearly), stringResource(R.string.bill_yearly_estimate, IncomeFormat.withCode(summary.annualCost)), null)
        }

        StatusBanner(summary)
        message?.let {
            Text(stringResource(it.textRes()), style = ZarType.bodyCaption, color = colors.danger, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp))
        }

        if (summary.chargesAfterCancellation.isNotEmpty()) {
            BillSectionHeader(stringResource(R.string.bill_section_charged_after), Modifier.padding(top = 22.dp))
            BillRows(summary.chargesAfterCancellation, Modifier.padding(top = 9.dp)) { entry ->
                BillRow(
                    icon = BillFormat.icon(bill.name),
                    title = BillFormat.dayMonth(entry.current.date),
                    meta = entry.current.merchant ?: bill.name,
                    dot = colors.attention,
                    end = RowEnd.Amount(IncomeFormat.withCode(entry.current.amount)),
                    onClick = null,
                )
            }
            BillNote(stringResource(R.string.bill_charged_after_note), Modifier.padding(top = 10.dp))
        }

        BillSectionHeader(stringResource(R.string.bill_section_payments), Modifier.padding(top = 22.dp))
        if (summary.occurrences.isEmpty()) {
            BillNote(stringResource(R.string.bill_no_due_dates), Modifier.padding(top = 10.dp))
        } else {
            BillRows(summary.occurrences, Modifier.padding(top = 9.dp).testTag("bill-payments")) { occurrence ->
                PaymentRow(occurrence, accountNames, today, onClick = { onOpenDueDate(bill.id.value, occurrence.dueDate.toString()) })
            }
        }
        BillNote(stringResource(R.string.bills_never_posts), Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun subtitle(summary: BillSummary, today: LocalDate): String {
    val bill = summary.bill
    val parts = mutableListOf(stringResource(BillFormat.everyRes(bill.frequency)))
    summary.next?.let { parts += stringResource(R.string.bill_next_due, BillFormat.longDate(it.dueDate)) }
    when (val cancellation = bill.cancellation) {
        BillCancellation.Intended -> parts += stringResource(R.string.bill_planning_cancel)
        is BillCancellation.Confirmed -> parts += stringResource(R.string.bill_cancelled_from, BillFormat.dayMonth(cancellation.effectiveFrom))
        null -> if (bill.status == BillStatus.STOPPED) parts += stringResource(R.string.bill_stopped)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun repeats(bill: Bill): String = when (bill.frequency) {
    BillFrequency.WEEKLY -> stringResource(R.string.bill_repeats_weekly, BillFormat.weekday(bill.firstDue))
    BillFrequency.MONTHLY -> stringResource(R.string.bill_repeats_monthly, BillFormat.ordinal(bill.firstDue.dayOfMonth))
    BillFrequency.YEARLY -> stringResource(R.string.bill_repeats_yearly, BillFormat.dayMonth(bill.firstDue))
}

/** A soft note when the bill is being cancelled, is cancelled, or is stopped. */
@Composable
private fun StatusBanner(summary: BillSummary) {
    val bill = summary.bill
    val (title, body) = when (val cancellation = bill.cancellation) {
        BillCancellation.Intended -> stringResource(R.string.bill_planning_title) to stringResource(R.string.bill_planning_body)
        is BillCancellation.Confirmed -> stringResource(R.string.bill_cancelled_title, BillFormat.dayMonth(cancellation.effectiveFrom)) to
            stringResource(R.string.bill_cancelled_body, IncomeFormat.withCode(summary.annualCost))
        null -> if (bill.status == BillStatus.STOPPED) {
            stringResource(R.string.bill_stopped_title) to stringResource(R.string.bill_stopped_body)
        } else {
            return
        }
    }
    InfoBanner(title, body, Modifier.padding(horizontal = 10.dp).padding(top = 12.dp).testTag("bill-status"), icon = R.drawable.ic_ph_info)
}

@Composable
private fun PaymentRow(occurrence: BillOccurrence, accountNames: Map<AccountId, String>, today: LocalDate, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val progress = occurrence.progress
    val title = BillFormat.dayMonth(occurrence.dueDate)
    when {
        progress.state == MatchState.MATCHED -> {
            val account = occurrence.payments.maxByOrNull { it.entry.current.date }?.entry?.current?.accountId?.let(accountNames::get)
            BillRow(
                icon = R.drawable.ic_ph_check_circle,
                title = title,
                meta = if (account != null) stringResource(R.string.bill_payment_matched, account) else stringResource(R.string.bill_payment_matched_no_account),
                dot = colors.fresh,
                end = RowEnd.Amount(IncomeFormat.withCode(progress.matched)),
                onClick = onClick,
            )
        }
        progress.state == MatchState.CANCELLED -> BillRow(
            icon = R.drawable.ic_ph_circle,
            title = title,
            meta = stringResource(R.string.bill_payment_cancelled),
            end = RowEnd.Amount(IncomeFormat.withCode(progress.expected.amount)),
            onClick = onClick,
        )
        progress.state == MatchState.PARTIALLY_MATCHED -> BillRow(
            icon = R.drawable.ic_ph_question,
            title = title,
            meta = stringResource(R.string.bill_payment_partly, IncomeFormat.withCode(progress.remaining)),
            dot = colors.attention,
            end = RowEnd.Action(stringResource(R.string.bill_action_link)),
            onClick = onClick,
        )
        occurrence.dueDate < today -> BillRow(
            icon = R.drawable.ic_ph_question,
            title = title,
            meta = stringResource(R.string.bill_payment_none),
            dot = colors.attention,
            end = RowEnd.Action(stringResource(R.string.bill_action_link)),
            onClick = onClick,
        )
        else -> BillRow(
            icon = R.drawable.ic_ph_circle,
            title = title,
            meta = stringResource(R.string.bill_payment_upcoming),
            end = RowEnd.Amount(IncomeFormat.withCode(progress.expected.amount)),
            onClick = onClick,
        )
    }
}

internal fun BillMessage.textRes(): Int = when (this) {
    BillMessage.NOT_SIGNED_IN -> R.string.bill_message_not_signed_in
    BillMessage.CURRENCY -> R.string.bill_message_currency
    BillMessage.FAILED -> R.string.bill_message_failed
}

private enum class EndChoice { INTEND, CANCELLED, STOP, KEEP }

/** "End bill": plan to cancel, confirm the cancellation from a date, or stop tracking it. Laid out like Z28's sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EndBillSheet(
    bill: Bill,
    onDismiss: () -> Unit,
    onIntend: () -> Unit,
    onCancelled: (LocalDate) -> Unit,
    onStop: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = ZarTheme.colors
    val initial = if (bill.cancellation == BillCancellation.Intended) EndChoice.CANCELLED else EndChoice.INTEND
    var choice by rememberSaveable { mutableStateOf(initial) }
    var fromEpochDay by rememberSaveable { mutableLongStateOf(ZarTime.today().toEpochDay()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val from = LocalDate.ofEpochDay(fromEpochDay)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZarShapes.sheet,
        containerColor = colors.surface,
        scrimColor = SheetScrim,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = ZarDimens.SheetGutter).padding(bottom = 14.dp).testTag("bill-end-sheet"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SheetHeader(stringResource(R.string.bill_end_title, bill.name), onClose = onDismiss)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bill.cancellation == null) {
                    ZarChip(stringResource(R.string.bill_end_intend), choice == EndChoice.INTEND, onClick = { choice = EndChoice.INTEND })
                }
                if (bill.cancellation == BillCancellation.Intended) {
                    ZarChip(stringResource(R.string.bill_end_keep), choice == EndChoice.KEEP, onClick = { choice = EndChoice.KEEP })
                }
                ZarChip(stringResource(R.string.bill_end_cancelled), choice == EndChoice.CANCELLED, onClick = { choice = EndChoice.CANCELLED })
                ZarChip(stringResource(R.string.bill_end_stop), choice == EndChoice.STOP, onClick = { choice = EndChoice.STOP })
            }
            Text(
                stringResource(
                    when (choice) {
                        EndChoice.KEEP -> R.string.bill_end_keep_note
                        EndChoice.INTEND -> R.string.bill_end_intend_note
                        EndChoice.CANCELLED -> R.string.bill_end_cancelled_note
                        EndChoice.STOP -> R.string.bill_end_stop_note
                    },
                ),
                style = ZarType.bodyS.copy(lineHeight = 19.sp),
                color = colors.textSecondary,
            )
            if (choice == EndChoice.CANCELLED) {
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }.testTag("bill-end-from"), verticalAlignment = Alignment.CenterVertically) {
                    ZarFormRow(stringResource(R.string.bill_end_from), Modifier.weight(1f)) {
                        Row(Modifier.weight(1f).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(IncomeFormat.fullDate(from), style = ZarType.bodyM, color = colors.textPrimary)
                            ZarIcon(R.drawable.ic_ph_caret_down, size = 14.dp, tint = colors.iconMuted)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZarButton(stringResource(R.string.bill_cancel), onDismiss, Modifier.weight(1f), ZarButtonStyle.Soft)
                ZarButton(
                    stringResource(R.string.bill_end_confirm),
                    onClick = {
                        when (choice) {
                            EndChoice.KEEP -> onKeep()
                            EndChoice.INTEND -> onIntend()
                            EndChoice.CANCELLED -> onCancelled(from)
                            EndChoice.STOP -> onStop()
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("bill-end-confirm"),
                )
            }
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { fromEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}
