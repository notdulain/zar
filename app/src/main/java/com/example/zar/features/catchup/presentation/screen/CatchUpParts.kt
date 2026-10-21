package com.example.zar.features.catchup.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarBackButton
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarMonoLabel
import com.example.zar.core.presentation.components.ZarTitleBlock
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType
import com.example.zar.features.catchup.domain.model.UnresolvedItem
import com.example.zar.features.catchup.domain.model.UnresolvedKind
import com.example.zar.features.transactions.presentation.activity.EntryDisplay
import com.example.zar.features.transactions.presentation.activity.entrySubtitle
import com.example.zar.features.transactions.presentation.activity.entryTitle
import com.example.zar.features.transactions.presentation.activity.screen.DarkStatusBarIcons
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Words for days and times in the reminder screens: "Sunday", "7:00 pm", "Sun 26 Oct · 7:00 pm". */
internal object CatchUpFormat {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val NEXT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun time(time: LocalTime): String = time.format(TIME).lowercase(Locale.ENGLISH)

    fun next(at: ZonedDateTime): String = "${at.format(NEXT)} · ${time(at.toLocalTime())}"

    fun day(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun dayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}

/**
 * A pushed page on the canvas: back button, title, scrolling content and, when given, actions pinned to the
 * bottom. Shared by Needs review, the catch-up steps and the reminder settings.
 */
@Composable
internal fun CatchUpScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ZarTheme.colors
    DarkStatusBarIcons()
    val backTop = maxOf(4.dp, 54.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    Column(modifier.fillMaxSize().background(colors.canvas).statusBarsPadding()) {
        ZarBackButton(onBack, backLabel, Modifier.padding(start = 10.dp, top = backTop))
        ZarTitleBlock(title, subtitle, Modifier.padding(top = 6.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (actions != null) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = actions,
            )
        }
    }
}

/** A white card whose children are spaced the way Figma's catch-up cards are. */
@Composable
internal fun CatchUpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ZarCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun StepLabel(text: String, modifier: Modifier = Modifier) {
    ZarMonoLabel(text, modifier.padding(start = 12.dp), color = ZarTheme.colors.textAttention)
}

/** A label on the left and its value on the right. */
@Composable
internal fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = ZarTheme.colors.textPrimary) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
        Text(value, style = ZarType.bodyS, color = valueColor)
    }
}

/**
 * One open entry: why it is listed, what it is and how much. With [onOpen] the whole row opens the entry and
 * says so, so nothing about the entry can be changed from here by accident.
 */
@Composable
internal fun UnresolvedRow(item: UnresolvedItem, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val colors = ZarTheme.colors
    val tx = item.transaction
    Row(
        modifier.fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(vertical = 14.dp)
            .testTag("unresolved-${tx.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ZarIconChip(EntryDisplay.icon(tx))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZarMonoLabel(stringResource(item.kind.labelRes()), color = colors.textAttention)
            Text(entryTitle(tx, item.account?.type), style = ZarType.bodyM, color = colors.textPrimary)
            Text(
                "${EntryDisplay.signed(tx)} · ${entrySubtitle(tx, item.account?.name)}",
                style = ZarType.bodyS,
                color = colors.textSecondary,
            )
            if (onOpen != null) {
                Text(
                    stringResource(R.string.review_open_entry),
                    style = ZarType.labelAction,
                    color = colors.textSelected,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@StringRes
internal fun UnresolvedKind.labelRes(): Int = when (this) {
    UnresolvedKind.UNCATEGORISED -> R.string.review_kind_uncategorised
    UnresolvedKind.MISSING_RATE -> R.string.review_kind_missing_rate
    UnresolvedKind.UNEXPLAINED_ADJUSTMENT -> R.string.review_kind_adjustment
}
