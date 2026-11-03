package com.example.zar.features.home.presentation.screen

import com.example.zar.core.presentation.privacy.AmountPrivacyViewModel
import com.example.zar.core.presentation.privacy.AmountMask
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import com.example.zar.core.domain.calculation.ZarTime
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.zar.core.presentation.components.CollapseLine
import com.example.zar.core.presentation.components.HomeCollapse
import com.example.zar.core.presentation.components.collapseChartFade
import com.example.zar.core.presentation.components.collapseLine
import com.example.zar.core.presentation.components.collapseShift
import com.example.zar.core.presentation.components.rememberHomeCollapse
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.zar.features.home.presentation.viewmodel.HomeGoalContent
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.core.domain.model.money.Money
import com.example.zar.R
import com.example.zar.features.home.domain.model.DonutStatus
import com.example.zar.core.domain.model.money.FiatMoney
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.presentation.components.AddEntryOption
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.components.ZarSplitBar
import com.example.zar.core.presentation.components.heroDisplacement
import com.example.zar.core.presentation.components.heroParallax
import com.example.zar.core.presentation.format.formatForRow
import com.example.zar.core.presentation.format.formatWithCode
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarHighlight
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.amountText
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.viewmodel.HomeAllowanceContent
import com.example.zar.features.home.presentation.viewmodel.HomeCommitmentsContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import com.example.zar.features.home.presentation.viewmodel.HomeViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val AxisFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val ShortMonthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val DayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenReview: () -> Unit,
    onOpenFreelance: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBill: (String) -> Unit,
    onOpenSpending: (YearMonth) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    privacyViewModel: AmountPrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onRetry = viewModel::retry,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
        onOpenReview = onOpenReview,
        onOpenFreelance = onOpenFreelance,
        onOpenBills = onOpenBills,
        onOpenBill = onOpenBill,
        onOpenSpending = onOpenSpending,
        onOpenGoal = { id ->
            viewModel.openGoal(id)
            onNavigate(TopLevelDestination.Plans)
        },
        onToggleAmounts = privacyViewModel::toggle,
    )
}

@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    onOpenReview: () -> Unit = {},
    onOpenFreelance: () -> Unit = {},
    onOpenBills: () -> Unit = {},
    onOpenBill: (String) -> Unit = {},
    onOpenSpending: (YearMonth) -> Unit = {},
    onOpenGoal: (GoalId) -> Unit = {},
    onToggleAmounts: () -> Unit = {},
) {
    ZarScreenScaffold(
        destination = TopLevelDestination.Home,
        onNavigate = onNavigate,
        onAddOption = onAddOption,
    ) { padding ->
        // The hero reads this same state for its bounded parallax; nothing else about the scroll changes.
        val scrollState = rememberScrollState()
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        Box(Modifier.fillMaxSize()) {
        Column(
            // Only the sides and top are outer padding. The bottom clearance is scroll content, so the list passes behind the
            // floating nav and its last row ends 24dp above it.
            Modifier.fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                )
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when (val content = state.content) {
                HomeContent.Loading -> HomeStateBlock(state.month, state.canGoForward, onPreviousMonth, onNextMonth) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.home_loading), style = ZarType.bodyS)
                }
                HomeContent.Error -> HomeStateBlock(state.month, state.canGoForward, onPreviousMonth, onNextMonth) {
                    ZarCard(Modifier.fillMaxWidth().testTag("home-error")) {
                        Text(stringResource(R.string.home_error), style = ZarType.bodyS)
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("home-retry")) {
                            Text(stringResource(R.string.home_retry))
                        }
                    }
                }
                is HomeContent.Ready -> OverviewBlock(
                    overview = content.overview,
                    canGoForward = state.canGoForward,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onNavigate = onNavigate,
                    onOpenReview = onOpenReview,
                    commitments = state.commitments,
                    allowance = state.allowance,
                    goal = state.goal,
                    onRetry = onRetry,
                    onOpenFreelance = onOpenFreelance,
                    onOpenBills = onOpenBills,
                    onOpenBill = onOpenBill,
                    onOpenSpending = onOpenSpending,
                    onOpenGoal = onOpenGoal,
                    onToggleAmounts = onToggleAmounts,
                    scrollState = scrollState,
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
        // The pinned band that takes over the status-bar area once the card has collapsed into it.
        val collapse = rememberHomeCollapse(scrollState, cardHeight = 507.dp, fadeMargin = 8.dp)
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .graphicsLayer { alpha = HomeCollapse.bandAlpha(collapse.p()) }
                .background(Color(0xFF05070B))
                .testTag("home-status-band"),
        )
        }
    }
}

@Composable
private fun HomeStateBlock(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 50.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MonthPicker(month, canGoForward, onPrevious, onNext, onDark = false)
        content()
    }
}

@Composable
private fun OverviewBlock(
    overview: HomeOverview,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNavigate: (TopLevelDestination) -> Unit,
    onOpenReview: () -> Unit,
    commitments: HomeCommitmentsContent,
    allowance: HomeAllowanceContent,
    goal: HomeGoalContent,
    onRetry: () -> Unit,
    onOpenFreelance: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBill: (String) -> Unit,
    onOpenSpending: (YearMonth) -> Unit,
    onOpenGoal: (GoalId) -> Unit,
    onToggleAmounts: () -> Unit,
    scrollState: ScrollState,
) {
    ReceivedHero(overview, canGoForward, onPreviousMonth, onNextMonth, onToggleAmounts, scrollState)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 9.dp).padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickNavigation(onNavigate)
        if (overview.hasIncompleteData) {
            ReviewPrompt(overview, onClick = onOpenReview)
        }
        SpendingCard(overview, onClick = { onOpenSpending(overview.month) })
        GoalCard(goal, onOpenGoal = onOpenGoal, onOpenPlans = { onNavigate(TopLevelDestination.Plans) }, onRetry = onRetry)
        RecentActivity(overview.recent)
        ClientsOweRow(commitments, onClick = onOpenFreelance, onRetry = onRetry)
        // Both cards measure to the taller one, so long or empty content in either never leaves them mismatched.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NextBillCard(
                commitments = commitments,
                onOpenBill = onOpenBill,
                onOpenBills = onOpenBills,
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            WeeklyLimitCard(
                allowance = allowance,
                onOpenPlans = { onNavigate(TopLevelDestination.Plans) },
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ReceivedHero(
    overview: HomeOverview,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleAmounts: () -> Unit,
    scrollState: ScrollState,
) {
    val colors = ZarTheme.colors
    var chartMode by rememberSaveable { mutableIntStateOf(0) }
    // The whole card collapses into the status bar as the page scrolls (see HomeCollapse): the gradient drifts at 0.35 of
    // the scroll, a dark overlay builds to 70%, the text group follows at 0.30 and fades line by line before it reaches the
    // status-bar glyphs, the chart group drifts at 0.18 and fades over the middle, the bottom corners flatten and a pinned
    // band takes over. Everything is read in the layer phase, so scrolling never recomposes the card.
    val spec = rememberHomeCollapse(scrollState, cardHeight = 507.dp, fadeMargin = 8.dp)
    val density = LocalDensity.current
    val fullRadiusPx = with(density) { 32.dp.toPx() }
    val tabsLine = remember { CollapseLine() }
    val monthLine = remember { CollapseLine() }
    val labelLine = remember { CollapseLine() }
    val amountLine = remember { CollapseLine() }
    val statusLine = remember { CollapseLine() }
    Box(
        Modifier.fillMaxWidth().height(507.dp)
            .graphicsLayer {
                clip = true
                val r = HomeCollapse.cornerRadius(spec.p(), fullRadiusPx)
                shape = RoundedCornerShape(topStart = 0f, topEnd = 0f, bottomEnd = r, bottomStart = r)
            }
            .background(Color(0xFF05070B))
            .testTag("received-card"),
    ) {
        Box(
            Modifier.fillMaxSize()
                .collapseShift(spec, HomeCollapse.BACKGROUND_RATE)
                .background(ZarGradients.dusk),
        )
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = HomeCollapse.overlayAlpha(spec.p()) }
                .background(Color(0xFF05070B)),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp)) {
            Column(Modifier.collapseShift(spec, HomeCollapse.TEXT_RATE)) {
                Spacer(Modifier.height(54.dp))
                Box(Modifier.collapseLine(spec, tabsLine)) { ChartModeTabs(selected = chartMode, onSelect = { chartMode = it }) }
                Spacer(Modifier.height(38.dp))
                Box(Modifier.collapseLine(spec, labelLine)) {
                    Text(
                        stringResource(R.string.home_received_this_month).uppercase(),
                        style = ZarType.label,
                        color = colors.textOnInverse,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(Modifier.collapseLine(spec, amountLine)) {
                    Text(
                        overview.summary.received.amount.formatWithCode(),
                        style = ZarType.amountL,
                        color = colors.textOnInverse,
                        modifier = Modifier.testTag("received-amount"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.collapseLine(spec, statusLine)) { HeroStatus(overview) }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.collapseShift(spec, HomeCollapse.CHART_RATE).collapseChartFade(spec)) {
                val chartModifier = Modifier.fillMaxWidth().height(165.dp).padding(horizontal = 18.dp)
                when {
                    AmountMask.isHidden -> HiddenChart(chartModifier)
                    chartMode == 0 -> ReceivedBarChart(overview, chartModifier)
                    chartMode == 1 -> SpendingDonutChart(overview, chartModifier)
                    else -> ReceivedLineChart(overview, chartModifier)
                }
                Spacer(Modifier.height(7.dp))
                if (chartMode == 2 && overview.receivedByMonth.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        val first = overview.month.minusMonths((overview.receivedByMonth.size - 1).toLong())
                        overview.receivedByMonth.indices.forEach { i ->
                            Text(
                                first.plusMonths(i.toLong()).format(ShortMonthFormat).uppercase(),
                                style = ZarType.label,
                                color = colors.textOnInverse,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val today = remember { ZarTime.today() }
                    val end = if (today in overview.month.atDay(1)..overview.month.atEndOfMonth()) today else overview.month.atEndOfMonth()
                    Text(
                        if (chartMode == 1) stringResource(R.string.home_donut_axis_range, overview.month.atDay(1).format(AxisFormat), end.format(AxisFormat)).uppercase()
                        else overview.month.atDay(1).format(AxisFormat).uppercase(),
                        style = ZarType.label, color = colors.textOnInverse,
                    )
                    Text(
                        if (chartMode == 1) stringResource(R.string.home_donut_axis_share).uppercase()
                        else overview.month.atEndOfMonth().format(AxisFormat).uppercase(),
                        style = ZarType.label, color = colors.textOnInverse,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 22.dp)
                .collapseShift(spec, HomeCollapse.TEXT_RATE)
                .collapseLine(spec, monthLine),
        ) {
            var expanded by remember { mutableStateOf(false) }
            val changeRange = stringResource(R.string.activity_change_range)
            Row(
                Modifier.clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = changeRange) { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("home-month-selector"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val format = if (overview.month.year == ZarTime.today().year) ShortMonthFormat
                    else DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
                Text(overview.month.format(format).uppercase(), style = ZarType.label.copy(lineHeight = 14.sp), color = Color.White)
                Icon(painterResource(R.drawable.ic_ph_caret_down), contentDescription = null,
                    modifier = Modifier.size(12.dp), tint = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Box(Modifier.width(280.dp)) {
                    MonthPicker(overview.month, canGoForward, onPreviousMonth, onNextMonth, onDark = false)
                }
            }
        }
    }
}

/** Stands in for the charts, whose bars and slices would otherwise reveal the amounts. */
@Composable
private fun HiddenChart(modifier: Modifier = Modifier) {
    Box(modifier.testTag("received-chart-hidden"), Alignment.Center) {
        Text(
            stringResource(R.string.home_chart_hidden),
            style = ZarType.bodyCaption,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChartModeTabs(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(R.drawable.ic_ph_chartbar, R.drawable.ic_ph_chartdonut, R.drawable.ic_ph_chartlineup)
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center) {
        icons.forEachIndexed { index, icon ->
            Column(
                Modifier.width(48.dp).height(40.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(icon), contentDescription = when (index) {
                    0 -> "Bar chart"
                    1 -> "Spending donut"
                    else -> "Received trend"
                }, modifier = Modifier.size(20.dp), tint = Color.Unspecified)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(22.dp).height(2.dp).background(if (selected == index) Color.White else Color.Transparent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
private fun SpendingDonutChart(overview: HomeOverview, modifier: Modifier = Modifier) {
    val donut = overview.donut
    val colors = ZarTheme.colors
    val spentColor = Color.White
    val planColor = ZarHighlight
    val freeColor = Color.White.copy(alpha = 0.28f)
    val spentText = donut.spent.amount.formatWithCode()
    val plansText = donut.forPlans?.amount?.formatWithCode() ?: stringResource(R.string.home_donut_unavailable)
    val freeText = FiatMoney(donut.stillFreeMinor, donut.received.amount.currency).formatWithCode()
    val status = when (donut.status) {
        DonutStatus.COMPLETE -> null
        DonutStatus.OVERSPENT -> R.string.home_donut_overspent
        DonutStatus.NO_INCOME -> R.string.home_donut_no_income
        DonutStatus.UNRESOLVED_RATES -> R.string.home_donut_unresolved
        DonutStatus.PLANS_UNAVAILABLE -> null
    }?.let { stringResource(it) }
    val percent = donut.stillFreePercent
    val description = stringResource(R.string.home_donut_description, spentText, plansText, freeText) +
        (percent?.let { " " + stringResource(R.string.home_donut_percent_description, it) } ?: "") +
        (status?.let { " $it" } ?: "")
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description }.testTag("donut-chart"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                val arcs = donut.arcFractions
                if (arcs == null) {
                    drawArc(freeColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                } else {
                    val shown = arcs.filter { it > 0f }
                    val gap = if (shown.size > 1) 3f else 0f
                    var start = -90f
                    listOf(spentColor, planColor, freeColor).forEachIndexed { index, color ->
                        val sweep = arcs[index] * 360f
                        if (sweep > 0f) {
                            drawArc(color, start + gap / 2f, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke))
                            start += sweep
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(percent?.let { "$it%" } ?: "—", style = ZarType.amountM, color = Color.White, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (donut.status == DonutStatus.OVERSPENT) R.string.home_donut_over else R.string.home_donut_free).uppercase(),
                    style = ZarType.label,
                    color = colors.textOnInverse.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DonutLegendRow(spentColor, stringResource(R.string.home_donut_spent), spentText)
            DonutLegendRow(planColor, stringResource(R.string.home_donut_plans), plansText)
            DonutLegendRow(freeColor, stringResource(R.string.home_donut_free), freeText)
            if (status != null) {
                Text(
                    status,
                    style = ZarType.bodyCaption,
                    color = colors.textOnInverse.copy(alpha = 0.75f),
                    modifier = Modifier.testTag("donut-status"),
                )
            }
        }
    }
}

@Composable
private fun DonutLegendRow(dot: Color, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Text(label.uppercase(), style = ZarType.label, color = ZarTheme.colors.textOnInverse.copy(alpha = 0.7f), maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, style = ZarType.bodyL, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReceivedLineChart(overview: HomeOverview, modifier: Modifier = Modifier) {
    val today = remember { ZarTime.today() }
    val trend = remember(overview.month, overview.receivedByMonth, today) { overview.monthlyTrend(today) }
    val points = trend.points
    val colors = ZarTheme.colors
    val currency = overview.summary.received.amount.currency
    val average = trend.averageMinor
    val delta = trend.deltaMinor
    val averageText = average?.let { FiatMoney(it, currency).formatWithCode() }
    val deltaText = delta?.let { (if (it < 0) "−" else "+") + FiatMoney(Math.abs(it), currency).formatWithCode() }
    val previousLabel = trend.previous?.month?.format(ShortMonthFormat)
    val notes = listOfNotNull(
        if (trend.hasPartialMonth) stringResource(R.string.home_trend_partial) else null,
        if (trend.hasUnresolvedMonth) stringResource(R.string.home_trend_unresolved) else null,
    ).joinToString(" · ")
    val description = stringResource(
        R.string.home_trend_description,
        points.firstOrNull()?.month?.format(ShortMonthFormat) ?: "",
        points.lastOrNull()?.month?.format(ShortMonthFormat) ?: "",
    ) + " " + points.joinToString("; ") { "${it.month.format(ShortMonthFormat)}: ${it.received.amount.formatWithCode()}" } +
        (averageText?.let { ". " + stringResource(R.string.home_trend_average, it) } ?: "") +
        (if (deltaText != null && previousLabel != null) ". $deltaText " + stringResource(R.string.home_trend_vs, previousLabel) else "") +
        (if (notes.isNotEmpty()) ". $notes" else "")
    BoxWithConstraints(modifier.semantics { contentDescription = description }.testTag("trend-chart")) {
        val plotTop = 30.dp
        val plotHeight = maxHeight - plotTop - 6.dp
        fun yOf(minor: Long): Dp = plotTop + plotHeight * (1f - minor.toFloat() / trend.maximumMinor)
        Canvas(Modifier.fillMaxSize()) {
            if (points.isEmpty()) return@Canvas
            val slot = size.width / points.size
            val xs = points.indices.map { slot * (it + 0.5f) }
            val ys = points.map { yOf(it.received.amount.minorUnits).toPx() }
            if (average != null) {
                val y = yOf(average).toPx()
                drawLine(
                    Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            }
            if (points.size > 1) {
                val line = Path()
                xs.forEachIndexed { i, x -> if (i == 0) line.moveTo(x, ys[i]) else line.lineTo(x, ys[i]) }
                drawPath(line, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            xs.forEachIndexed { i, x ->
                val last = i == points.lastIndex
                drawCircle(if (last) ZarHighlight else Color.White, if (last) 5.dp.toPx() else 3.5.dp.toPx(), Offset(x, ys[i]))
            }
        }
        if (average != null && averageText != null) {
            Text(
                stringResource(R.string.home_trend_average, averageText).uppercase(),
                style = ZarType.axis,
                color = colors.textOnInverse.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).offset(y = yOf(average) - 16.dp).testTag("trend-average"),
            )
        }
        if (deltaText != null && previousLabel != null && points.isNotEmpty()) {
            Text(
                deltaText.uppercase() + " " + stringResource(R.string.home_trend_vs, previousLabel).uppercase(),
                style = ZarType.axis,
                color = colors.textOnInverse,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = (yOf(points.last().received.amount.minorUnits) - 24.dp).coerceAtLeast(0.dp)).testTag("trend-delta"),
            )
        }
        if (notes.isNotEmpty()) {
            Text(notes, style = ZarType.bodyCaption, color = colors.textOnInverse.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.align(Alignment.TopStart).testTag("trend-note"))
        }
    }
}

@Composable
private fun MonthPicker(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDark: Boolean,
) {
    val color = if (onDark) Color.White else ZarTheme.colors.textPrimary
    val previous = stringResource(R.string.home_previous_month)
    val next = stringResource(R.string.home_next_month)
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp).testTag("month-previous").semantics { contentDescription = previous }) {
            Text("‹", style = ZarType.headingSheet, color = color)
        }
        Text(
            month.format(MonthFormat),
            modifier = Modifier.width(130.dp).testTag("month-label"),
            textAlign = TextAlign.Center,
            style = ZarType.labelAction,
            color = color,
            maxLines = 1,
        )
        IconButton(
            onClick = onNext,
            enabled = canGoForward,
            modifier = Modifier.size(48.dp).testTag("month-next").semantics { contentDescription = next },
        ) {
            Text("›", style = ZarType.headingSheet, color = color.copy(alpha = if (canGoForward) 1f else 0.35f))
        }
    }
}

@Composable
private fun HeroStatus(overview: HomeOverview) {
    val colors = ZarTheme.colors
    val hasMissingRate = overview.missingRateCount > 0
    val note = when {
        hasMissingRate -> stringResource(R.string.home_warning_prefix) + " " +
            stringResource(R.string.home_coverage_missing_rate, overview.missingRateCount)
        overview.adjustmentCount > 0 -> stringResource(R.string.home_coverage_adjustments, overview.adjustmentCount)
        else -> stringResource(R.string.home_received_note_short)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(5.dp).background(colors.fresh, CircleShape))
        Text(
            note,
            style = ZarType.bodyCaption,
            color = colors.textOnInverse.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(if (hasMissingRate) "received-warning" else "received-status"),
        )
    }
}

@Composable
private fun ReceivedBarChart(overview: HomeOverview, modifier: Modifier = Modifier) {
    val today = remember { ZarTime.today() }
    val series = remember(overview.month, overview.receivedByDay, today) { overview.cumulativeIncome(today) }
    val slots = series.slots
    var selected by remember(overview.month) { mutableIntStateOf(-1) }
    val monthLabel = overview.month.format(MonthFormat)
    val description = stringResource(R.string.home_received_chart, monthLabel) + " " +
        if (series.isAllZero) stringResource(R.string.home_received_chart_empty) else {
            val last = slots.lastOrNull { !it.isFuture } ?: slots.first()
            stringResource(
                R.string.home_received_chart_total,
                last.date.format(DayFormat),
                last.cumulative.amount.formatWithCode(),
            ) + if (series.hasUnresolved) " " + stringResource(R.string.home_received_chart_unresolved) else ""
        }
    BoxWithConstraints(
        modifier
            .semantics { contentDescription = description }
            .testTag("received-chart")
            .pointerInput(slots) {
                detectTapGestures { tap ->
                    val index = (tap.x / (size.width.toFloat() / slots.size)).toInt().coerceIn(0, slots.lastIndex)
                    selected = if (slots[index].isFuture || index == selected) -1 else index
                }
            },
    ) {
        val tooltipSpace = 44.dp
        Canvas(Modifier.fillMaxSize().padding(top = tooltipSpace)) {
            val slotWidth = size.width / slots.size
            val barWidth = 5.dp.toPx().coerceAtMost(slotWidth * 0.7f)
            val markerHeight = barWidth
            slots.forEachIndexed { index, slot ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val progress = index.toFloat() / slots.lastIndex.coerceAtLeast(1)
                val opacity = 0.18f + 0.82f * progress * progress * progress
                val isToday = !slot.isFuture && (index == slots.lastIndex || slots[index + 1].isFuture)
                val color = when {
                    slot.isFuture -> Color.White.copy(alpha = 0.12f)
                    index == selected || isToday -> ZarHighlight
                    else -> Color.White.copy(alpha = opacity.coerceAtLeast(0.3f))
                }
                val height = if (slot.isFuture || slot.isZeroMarker) markerHeight
                else (size.height * series.fractionOf(slot)).coerceAtLeast(markerHeight)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }
        // Keep the last anchor during fade-out; selection changes retarget the spring in flight.
        var lastSelected by remember(overview.month) { mutableIntStateOf(0) }
        if (selected >= 0) lastSelected = selected
        val picked = slots[lastSelected]
        var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val reservedPx = with(density) { tooltipSpace.toPx() }
        val barHeight = ((heightPx - reservedPx) * series.fractionOf(picked)).coerceAtLeast(with(density) { 5.dp.toPx() })
        val anchorX = widthPx * (lastSelected + 0.5f) / slots.size
        val target = IntOffset(
            (anchorX - tooltipSize.width / 2f).toInt().coerceIn(0, (widthPx - tooltipSize.width).toInt().coerceAtLeast(0)),
            (heightPx - barHeight - tooltipSize.height - with(density) { 8.dp.toPx() }).toInt().coerceAtLeast(0),
        )
        val tooltipOffset by animateIntOffsetAsState(target, spring(dampingRatio = 1f, stiffness = 350f), label = "income tooltip position")
        val tooltipAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f, tween(180), label = "income tooltip visibility")
        Text(
            picked.date.format(DayFormat) + ": " + picked.cumulative.amount.formatWithCode() +
                if (picked.cumulative.unresolvedCount > 0) " + " + stringResource(R.string.home_received_chart_unresolved) else "",
            color = Color.White,
            style = ZarType.bodyCaption,
            maxLines = 2,
            modifier = Modifier.offset { tooltipOffset }
                .onSizeChanged { tooltipSize = it }
                .graphicsLayer { alpha = tooltipAlpha }
                .background(Color(0xFF202229), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("received-chart-caption"),
        )
        if (series.isAllZero && selected < 0) {
            Text(stringResource(R.string.home_received_chart_empty), color = Color.White.copy(alpha = 0.7f),
                style = ZarType.bodyCaption, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun QuickNavigation(onNavigate: (TopLevelDestination) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        QuickCard(stringResource(R.string.nav_activity), stringResource(R.string.home_activity_summary), R.drawable.ic_ph_chartline_home, Modifier.weight(1f)) {
            onNavigate(TopLevelDestination.Activity)
        }
        QuickCard(stringResource(R.string.nav_plans), stringResource(R.string.home_plans_summary), R.drawable.ic_ph_flag_home, Modifier.weight(1f)) {
            onNavigate(TopLevelDestination.Plans)
        }
        QuickCard(stringResource(R.string.nav_more), stringResource(R.string.home_accounts_summary), R.drawable.ic_ph_gearsix_home, Modifier.weight(1f).testTag("nav-more")) {
            onNavigate(TopLevelDestination.More)
        }
    }
}

@Composable
private fun QuickCard(title: String, detail: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    ZarCard(modifier.height(114.dp), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(colors.subtle, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
            }
            Icon(painterResource(R.drawable.ic_ph_arrowupright), contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = ZarType.bodyM, color = colors.textPrimary)
        Text(detail, style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReviewPrompt(overview: HomeOverview, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val count = overview.missingRateCount + overview.adjustmentCount + if (overview.hasUnknownSpending) 1 else 0
    ZarCard(Modifier.fillMaxWidth().height(60.dp).testTag("home-review"), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp).background(colors.attention, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_needs_review, count), style = ZarType.bodyM, color = colors.textPrimary)
                Text(stringResource(R.string.home_review_detail), style = ZarType.bodyCaption, color = colors.textSecondary)
            }
            Text(stringResource(R.string.home_review_action), style = ZarType.labelAction, color = colors.textSelected)
        }
    }
}

@Composable
private fun SpendingCard(overview: HomeOverview, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val summary = overview.summary.spent
    ZarCard(Modifier.fillMaxWidth().testTag("spent-card"), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(stringResource(R.string.home_spent).uppercase(), style = ZarType.label, color = colors.textPrimary)
                Text(stringResource(R.string.home_this_month).uppercase(), style = ZarType.label, color = colors.textTertiary)
            }
            Text(summary.total.amount.formatWithCode(), style = ZarType.amountM, color = colors.textPrimary, modifier = Modifier.testTag("spent-amount"))
        }
        Spacer(Modifier.height(8.dp))
        SplitLine(R.string.home_spent_committed, summary.committed, "spent-committed", colors.accentSelected)
        SplitLine(R.string.home_spent_discretionary, summary.discretionary, "spent-discretionary", colors.attention)
        SplitLine(R.string.home_spent_unknown, summary.unknown, "spent-unknown", Color(0xFFE6E6EA))
        Spacer(Modifier.height(10.dp))
        ZarSplitBar(
            committed = summary.committed.amount.minorUnits.toFloat(),
            discretionary = summary.discretionary.amount.minorUnits.toFloat(),
            unknown = summary.unknown.amount.minorUnits.toFloat(),
        )
        if (overview.hasIncompleteData) {
            Text(
                stringResource(R.string.home_coverage_unknown),
                style = ZarType.bodyCaption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("unknown-warning"),
            )
        }
        Text(
            stringResource(R.string.home_see_breakdown),
            style = ZarType.labelAction,
            color = colors.textSelected,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun SplitLine(@StringRes label: Int, total: ReportingTotal, tag: String, dotColor: Color) {
    val colors = ZarTheme.colors
    Row(Modifier.fillMaxWidth().height(17.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label).uppercase(), style = ZarType.label, color = colors.textTertiary)
        Spacer(Modifier.width(8.dp))
        Text(total.amount.formatWithCode(), style = ZarType.label, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun GoalCard(
    goal: HomeGoalContent,
    onOpenGoal: (GoalId) -> Unit,
    onOpenPlans: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = ZarTheme.colors
    val onClick: (() -> Unit)? = when (goal) {
        is HomeGoalContent.Ready -> { { onOpenGoal(goal.goal.id) } }
        HomeGoalContent.Empty -> onOpenPlans
        else -> null
    }
    ZarCard(
        Modifier.fillMaxWidth().testTag("home-goal"),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(19.dp),
    ) {
        Text(stringResource(R.string.home_goal).uppercase(), style = ZarType.label, color = colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        when (goal) {
            HomeGoalContent.Loading -> {
                Text(stringResource(R.string.home_goal_loading), style = ZarType.bodyM, color = colors.textSecondary, modifier = Modifier.testTag("home-goal-loading"))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).background(colors.borderHairline, RoundedCornerShape(3.dp)))
            }
            HomeGoalContent.Error -> {
                Text(stringResource(R.string.home_goal_error), style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.testTag("home-goal-error"))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("home-goal-retry")) { Text(stringResource(R.string.home_retry)) }
            }
            HomeGoalContent.Empty -> {
                Text(stringResource(R.string.home_goal_empty), style = ZarType.bodyM, color = colors.textPrimary, modifier = Modifier.testTag("home-goal-empty"))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_goal_create), style = ZarType.labelAction, color = colors.textSelected, modifier = Modifier.align(Alignment.End))
            }
            is HomeGoalContent.Ready -> {
                val target = goal.goal.target
                val progress = goal.progress
                val hidden = AmountMask.isHidden
                val backed = progress?.backed ?: Money.zero(target.currency)
                val fraction = if (hidden || progress == null) 0f else progress.backedFraction(target)
                Text(goal.goal.name, style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("home-goal-name"))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_goal_backed_of, backed.formatWithCode(), target.formatWithCode()),
                    style = ZarType.bodyS,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("home-goal-amounts"),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).background(colors.borderHairline, RoundedCornerShape(3.dp))) {
                    if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(colors.textSelected, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(8.dp))
                val detail = listOfNotNull(
                    when {
                        hidden -> stringResource(R.string.home_goal_hidden)
                        progress == null || progress.hasUnknownBalance -> stringResource(R.string.home_goal_backing_unknown)
                        else -> stringResource(R.string.home_goal_percent, (fraction * 100).toInt())
                    },
                    goal.goal.targetDate?.let { stringResource(R.string.home_goal_by, it.format(AxisFormat)) },
                ).joinToString(" · ")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(detail, style = ZarType.bodyCaption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("home-goal-detail"))
                    Text(stringResource(R.string.home_view_goal_one), style = ZarType.labelAction, color = colors.textSelected)
                }
            }
        }
    }
}

@Composable
private fun RecentActivity(recent: List<LedgerEntry>) {
    val colors = ZarTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp).padding(top = 2.dp)) {
        Text(stringResource(R.string.home_recent_activity).uppercase(), style = ZarType.label, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        if (recent.isEmpty()) {
            Text(stringResource(R.string.home_recent_empty), style = ZarType.bodyCaption, color = colors.textSecondary)
        } else {
            recent.forEach { entry ->
                RecentRow(entry)
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderHairline))
            }
        }
    }
}

@Composable
private fun RecentRow(entry: LedgerEntry) {
    val tx = entry.current
    val typeLabel = stringResource(typeLabelRes(tx.type))
    val sign = if (tx.direction == Direction.IN) "+" else "−"
    Row(Modifier.fillMaxWidth().height(39.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tx.merchant?.takeIf { it.isNotBlank() } ?: typeLabel,
            modifier = Modifier.weight(1f),
            style = ZarType.bodyS,
            color = ZarTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$sign ${tx.amount.formatForRow()}",
            style = ZarType.bodyS,
            color = ZarTheme.colors.amountText(tx.type),
        )
    }
}

@StringRes
private fun typeLabelRes(type: TransactionType): Int = when (type) {
    TransactionType.INCOME -> R.string.home_activity_income
    TransactionType.EXPENSE -> R.string.home_activity_expense
    TransactionType.TRANSFER_IN -> R.string.home_activity_transfer_in
    TransactionType.TRANSFER_OUT -> R.string.home_activity_transfer_out
    TransactionType.REFUND -> R.string.home_activity_refund
    TransactionType.ADJUSTMENT_IN -> R.string.home_activity_adjustment_in
    TransactionType.ADJUSTMENT_OUT -> R.string.home_activity_adjustment_out
}

@Preview(showBackground = true)
@Composable
private fun HomeIncompletePreview() {
    val zero = ReportingTotal.zero()
    val overview = HomeOverview(
        month = YearMonth.of(2026, 10),
        summary = PeriodSummary(zero, SpendingBreakdown(zero, zero, zero), unclassifiedAdjustmentCount = 1),
        recent = emptyList(),
        hasRecordsInMonth = true,
    )
    ZarTheme {
        HomeScreenContent(
            state = HomeUiState(YearMonth.of(2026, 10), canGoForward = false, content = HomeContent.Ready(overview)),
            onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
        )
    }
}
