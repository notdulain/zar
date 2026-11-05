package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** A month strip with direct manipulation; only a settled page changes the dashboard query. */
@Composable
internal fun ZarMonthPicker(
    month: YearMonth,
    latestMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onDark: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val stripHeight = 56.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val color = if (onDark) Color.White else ZarTheme.colors.textPrimary
    // Anchor the pages for this picker session, even while the dashboard loads another month.
    val origin = remember { month.minusMonths(1200) }
    val latest = remember { latestMonth }
    val lastPage = ChronoUnit.MONTHS.between(origin, latest).toInt().coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = 1200.coerceAtMost(lastPage), pageCount = { lastPage + 1 })
    val scope = rememberCoroutineScope()
    val motion = LocalZarMotion.current
    val selectMonth by rememberUpdatedState(onMonthSelected)
    LaunchedEffect(pager) {
        var committedPage = pager.settledPage
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val distance = page - committedPage
            // Track the committed page locally: a second swipe may finish before the first query returns.
            committedPage = page
            if (distance != 0) selectMonth(origin.plusMonths(page.toLong()))
        }
    }
    fun move(delta: Int) {
        scope.launch {
            val target = (pager.currentPage + delta).coerceIn(0, lastPage)
            if (motion.allowsSpatialMotion) pager.animateScrollToPage(target)
            else pager.scrollToPage(target)
        }
    }
    Row(Modifier.fillMaxWidth().height(stripHeight), verticalAlignment = Alignment.CenterVertically) {
        val previous = stringResource(R.string.home_previous_month)
        val next = stringResource(R.string.home_next_month)
        IconButton(onClick = { move(-1) }, enabled = pager.currentPage > 0,
            modifier = Modifier.size(48.dp).testTag("month-previous").semantics { contentDescription = previous }) {
            Icon(painterResource(R.drawable.ic_ph_caret_right), null, Modifier.size(18.dp).rotate(180f), tint = color)
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f).width(184.dp).height(stripHeight).testTag("month-pager"), beyondViewportPageCount = 1) { page ->
            Text(
                origin.plusMonths(page.toLong()).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("month-label"),
                textAlign = TextAlign.Center, style = ZarType.labelAction, color = color,
            )
        }
        val canAdvance = pager.currentPage < lastPage
        IconButton(onClick = { move(1) }, enabled = canAdvance,
            modifier = Modifier.size(48.dp).testTag("month-next").semantics { contentDescription = next }) {
            Icon(painterResource(R.drawable.ic_ph_caret_right), null, Modifier.size(18.dp), tint = color.copy(alpha = if (canAdvance) 1f else 0.35f))
        }
    }
}
