package com.example.zar.features.bills.presentation.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.MatchState
import com.example.zar.core.presentation.components.ZarAddPill
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillSummary
import com.example.zar.features.bills.presentation.model.BillList
import com.example.zar.features.bills.presentation.model.DueRow
import com.example.zar.features.bills.presentation.model.NeedsLook
import com.example.zar.features.bills.presentation.viewmodel.BillsViewModel
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import kotlinx.coroutines.launch

private val HeroHeight = 320.dp

/** Z22 — Bills: what's due this week, what needs a look, and what's been paid. */
@Composable
fun BillsScreen(
    onBack: () -> Unit,
    onAddBill: () -> Unit,
    onOpenBill: (String) -> Unit,
    onOpenDueDate: (billId: String, dueDate: String) -> Unit,
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZarTheme.colors
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var needsLookY by remember { mutableIntStateOf(0) }
    var paidY by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val list = uiState.list
            Hero(
                list = list,
                loadFailed = uiState.loadFailed,
                onBack = onBack,
                onNeedsLook = { scope.launch { scroll.animateScrollTo(needsLookY) } },
            )
            when {
                uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 72.dp), Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentSelected)
                }
                uiState.loadFailed || list == null -> BillNote(stringResource(R.string.bills_load_failed), Modifier.padding(top = 24.dp))
                else -> Sections(
                    list = list,
                    accountNames = uiState.accountNames,
                    onOpenBill = onOpenBill,
                    onOpenDueDate = onOpenDueDate,
                    onJumpToPaid = { scope.launch { scroll.animateScrollTo(paidY) } },
                    onNeedsLookPlaced = { needsLookY = it },
                    onPaidPlaced = { paidY = it },
                )
            }
            // Room so the last card clears the floating Add bill pill.
            Spacer(Modifier.height(124.dp))
        }
        StatusBarFade(scroll)
        ZarAddPill(
            text = stringResource(R.string.bills_add),
            onClick = onAddBill,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp).testTag("bills-add"),
        )
    }
}

/**
 * Once the dark hero has scrolled under the status bar, the glyphs turn dark over a fade of the
 * canvas colour, so the clock never sits on top of a list row.
 */
@Composable
private fun StatusBarFade(scroll: ScrollState) {
    val density = LocalDensity.current
    val colors = ZarTheme.colors
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The hero's lower edge reaches the status bar here.
    val start = with(density) { (HeroHeight - statusBar - 24.dp).toPx() }
    val fade = with(density) { 24.dp.toPx() }
    val pastHero by remember(start) { derivedStateOf { scroll.value > start + fade / 2 } }
    StatusBarGlyphs(onDarkHero = !pastHero)
    Box(
        Modifier.fillMaxWidth().height(statusBar + 28.dp)
            // Read the scroll position while drawing, so scrolling doesn't recompose the screen.
            .graphicsLayer { alpha = ((scroll.value - start) / fade).coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(0f to colors.canvas, 0.65f to colors.canvas, 1f to colors.canvas.copy(alpha = 0f))),
    )
}

@Composable
private fun Hero(list: BillList?, loadFailed: Boolean, onBack: () -> Unit, onNeedsLook: () -> Unit) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier.fillMaxWidth().height(HeroHeight)
            .clip(ZarShapes.hero)
            .background(ZarGradients.spent)
            .testTag("bills-hero"),
    ) {
        Box(
            Modifier.padding(start = 14.dp, top = maxOf(54.dp, statusBar + 4.dp)).width(44.dp).height(44.dp)
                .clip(ZarShapes.full)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.bills_back), onClick = onBack),
            Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_ph_arrow_left), stringResource(R.string.bills_back), Modifier.width(22.dp).height(22.dp), tint = Color.White)
        }
        if (list != null && !loadFailed) {
            val (lead, follow) = heroSentence(list)
            // One centred paragraph: the lead sentence in white with its key word semi-bold, then the rest in 80% white.
            Text(
                text = buildAnnotatedString {
                    append(BillFormat.emphasise(lead))
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.8f))) { append(" "); append(follow) }
                },
                style = ZarType.amountM.copy(lineHeight = 33.sp, textAlign = TextAlign.Center),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 152.dp).width(348.dp).testTag("bills-hero-text"),
            )
            StatusLink(list, onNeedsLook, Modifier.align(Alignment.TopCenter).padding(top = 272.dp))
        }
    }
}

@Composable
private fun heroSentence(list: BillList): Pair<String, String> {
    val today = ZarTime.today()
    val due = list.thisWeek
    val next = list.later.firstOrNull()
    return when {
        due.isNotEmpty() -> {
            val count = due.map { it.summary.bill.id }.distinct().size
            pluralStringResource(R.plurals.bills_hero_due_week, count, BillFormat.countWord(count)) to
                if (list.weekEnd == today) {
                    stringResource(R.string.bills_hero_to_pay_today, BillFormat.totals(list.thisWeekTotal))
                } else {
                    stringResource(R.string.bills_hero_to_pay, BillFormat.totals(list.thisWeekTotal), BillFormat.weekday(list.weekEnd))
                }
        }
        next != null -> stringResource(R.string.bills_hero_none_week) to
            stringResource(R.string.bills_hero_next, next.summary.bill.name, BillFormat.dayMonth(next.occurrence.dueDate))
        list.hasBills -> stringResource(R.string.bills_hero_nothing_running) to stringResource(R.string.bills_hero_nothing_running_detail)
        else -> stringResource(R.string.bills_hero_empty) to stringResource(R.string.bills_hero_empty_detail)
    }
}

@Composable
private fun StatusLink(list: BillList, onNeedsLook: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    if (!list.hasBills) return
    val count = list.needsLook.size
    Row(
        modifier
            .clip(ZarShapes.full)
            .clickable(enabled = count > 0, role = Role.Button, onClick = onNeedsLook)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (count > 0) colors.attention else colors.fresh)
        Text(
            if (count > 0) pluralStringResource(R.plurals.bills_need_a_look, count, count) else stringResource(R.string.bills_all_matched),
            style = ZarType.bodyS.copy(lineHeight = 17.sp),
            color = colors.textOnInverse,
        )
    }
}

@Composable
private fun Sections(
    list: BillList,
    accountNames: Map<AccountId, String>,
    onOpenBill: (String) -> Unit,
    onOpenDueDate: (String, String) -> Unit,
    onJumpToPaid: () -> Unit,
    onNeedsLookPlaced: (Int) -> Unit,
    onPaidPlaced: (Int) -> Unit,
) {
    val colors = ZarTheme.colors
    Column(Modifier.padding(top = 18.dp)) {
        if (list.needsLook.isNotEmpty()) {
            BillSectionHeader(
                stringResource(R.string.bills_section_needs_look),
                Modifier.onGloballyPositioned { onNeedsLookPlaced(it.positionInParent().y.toInt()) },
                action = if (list.paidThisMonth.isNotEmpty()) stringResource(R.string.bills_jump_paid) else null,
                onAction = onJumpToPaid,
            )
            BillRows(list.needsLook, Modifier.padding(top = 9.dp).testTag("bills-needs-look")) { item -> NeedsLookRow(item, onOpenBill, onOpenDueDate) }
            Gap()
        }
        SubscriptionSuggestionsSection(trailingGap = 22.dp)
        if (list.thisWeek.isNotEmpty()) {
            BillSectionHeader(stringResource(R.string.bills_section_this_week), value = BillFormat.totals(list.thisWeekTotal))
            BillRows(list.thisWeek, Modifier.padding(top = 9.dp).testTag("bills-this-week")) { DueBillRow(it, accountNames, onOpenBill) }
            Gap()
        }
        if (list.later.isNotEmpty()) {
            BillSectionHeader(stringResource(R.string.bills_section_later))
            BillRows(list.later, Modifier.padding(top = 9.dp).testTag("bills-later")) { DueBillRow(it, accountNames, onOpenBill) }
            Gap()
        }
        if (list.paidThisMonth.isNotEmpty()) {
            BillSectionHeader(
                stringResource(R.string.bills_section_paid),
                Modifier.onGloballyPositioned { onPaidPlaced(it.positionInParent().y.toInt()) },
            )
            BillRows(list.paidThisMonth, Modifier.padding(top = 9.dp).testTag("bills-paid")) { row ->
                val bill = row.summary.bill
                BillRow(
                    icon = BillFormat.icon(bill.name),
                    title = bill.name,
                    meta = stringResource(R.string.bills_paid_meta, BillFormat.dayMonth(row.paidOn)),
                    dot = colors.fresh,
                    end = RowEnd.Amount(IncomeFormat.withCode(row.occurrence.progress.matched)),
                    onClick = { onOpenBill(bill.id.value) },
                )
            }
            Gap()
        }
        if (list.ended.isNotEmpty()) {
            BillSectionHeader(stringResource(R.string.bills_section_ended))
            BillRows(list.ended, Modifier.padding(top = 9.dp).testTag("bills-ended")) { EndedRow(it, onOpenBill) }
            Gap()
        }
        if (list.annual.isNotEmpty()) BillNote(stringResource(R.string.bills_annual_note, BillFormat.totals(list.annual)))
    }
}

@Composable
private fun Gap() = Spacer(Modifier.height(22.dp))

@Composable
private fun NeedsLookRow(item: NeedsLook, onOpenBill: (String) -> Unit, onOpenDueDate: (String, String) -> Unit) {
    val colors = ZarTheme.colors
    val bill = item.summary.bill
    when (item) {
        is NeedsLook.Missed -> {
            val latest = item.latest
            val date = BillFormat.dayMonth(latest.dueDate)
            BillRow(
                icon = BillFormat.icon(bill.name),
                title = bill.name,
                meta = when {
                    item.count > 1 -> pluralStringResource(R.plurals.bills_missed_many, item.count, item.count, date)
                    latest.progress.state == MatchState.PARTIALLY_MATCHED ->
                        stringResource(R.string.bills_partly_missed, date, IncomeFormat.withCode(latest.progress.remaining))
                    else -> stringResource(R.string.bills_missed_one, date)
                },
                dot = colors.attention,
                end = RowEnd.Action(stringResource(R.string.bills_action_paid_it)),
                onClick = { onOpenDueDate(bill.id.value, latest.dueDate.toString()) },
            )
        }
        is NeedsLook.ChargedAfterCancelling -> BillRow(
            icon = BillFormat.icon(bill.name),
            title = bill.name,
            meta = pluralStringResource(R.plurals.bills_charged_after, item.count, item.count, BillFormat.dayMonth(item.latest.current.date)),
            dot = colors.attention,
            end = RowEnd.Action(stringResource(R.string.bills_action_review)),
            onClick = { onOpenBill(bill.id.value) },
        )
    }
}

@Composable
private fun DueBillRow(row: DueRow, accountNames: Map<AccountId, String>, onOpenBill: (String) -> Unit) {
    val bill = row.summary.bill
    val date = BillFormat.weekdayDayMonth(row.occurrence.dueDate)
    val account = accountNames[bill.accountId] ?: row.summary.account?.name
    BillRow(
        icon = BillFormat.icon(bill.name),
        title = bill.name,
        meta = if (account != null) stringResource(R.string.bills_due_meta, date, account) else stringResource(R.string.bills_due_meta_no_account, date),
        end = RowEnd.Amount(IncomeFormat.withCode(row.occurrence.progress.remaining)),
        onClick = { onOpenBill(bill.id.value) },
    )
}

@Composable
private fun EndedRow(summary: BillSummary, onOpenBill: (String) -> Unit) {
    val bill = summary.bill
    val cancellation = bill.cancellation
    BillRow(
        icon = BillFormat.icon(bill.name),
        title = bill.name,
        meta = if (cancellation is BillCancellation.Confirmed) {
            stringResource(R.string.bills_ended_cancelled, BillFormat.dayMonth(cancellation.effectiveFrom))
        } else {
            stringResource(R.string.bills_ended_stopped)
        },
        end = RowEnd.Amount(IncomeFormat.withCode(bill.amount)),
        onClick = { onOpenBill(bill.id.value) },
    )
}
