package com.example.zar.features.bills.presentation.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarDivider
import com.example.zar.core.presentation.components.ZarIcon
import com.example.zar.core.presentation.components.ZarIconChip
import com.example.zar.core.presentation.components.ZarEyebrow
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType

/** Section label with an optional value or Label/Action on the right (Z22 headers). */
@Composable
internal fun BillSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = ZarTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 22.dp).heightIn(min = 17.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarEyebrow(label)
        when {
            action != null -> Text(
                action,
                style = ZarType.labelAction,
                color = colors.textSelected,
                modifier = Modifier.clickable(enabled = onAction != null, role = Role.Button) { onAction?.invoke() },
            )
            value != null -> ZarEyebrow(value)
        }
    }
}

/** White card holding rows separated by hairlines, 10dp from the screen edges. */
@Composable
internal fun BillCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ZarCard(
        modifier.padding(horizontal = 10.dp).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

/** What sits at the right of a row: an amount, or a Label/Action such as "I paid it →". */
internal sealed interface RowEnd {
    data class Amount(val text: String) : RowEnd
    data class Action(val text: String) : RowEnd
}

/**
 * The Z22/Z23 row: 36dp icon chip, Label/Button title, a caption with an optional 6dp status dot,
 * and an amount or action at the end.
 */
@Composable
internal fun BillRow(
    @DrawableRes icon: Int,
    title: String,
    meta: String,
    end: RowEnd?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    onEndClick: (() -> Unit)? = null,
) {
    val colors = ZarTheme.colors
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIconChip(icon, size = 36.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZarType.labelButton.copy(lineHeight = 19.sp), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) StatusDot(dot)
                Text(meta, style = ZarType.bodyCaption, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when (end) {
            is RowEnd.Amount -> Text(end.text, style = ZarType.bodyM, color = colors.textPrimary, maxLines = 1)
            is RowEnd.Action -> Text(
                end.text, style = ZarType.labelAction, color = colors.textSelected, maxLines = 1,
                modifier = if (onEndClick != null) Modifier.clickable(role = Role.Button, onClick = onEndClick) else Modifier,
            )
            null -> Unit
        }
    }
}

/** Rows inside one card, with hairline dividers between them. */
@Composable
internal fun <T> BillRows(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    BillCard(modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) ZarDivider()
            row(item)
        }
    }
}

/** Z23 detail row: label (110dp), value, and a caret when it opens the edit form. */
@Composable
internal fun BillDetailRow(label: String, value: String, onClick: (() -> Unit)?) {
    val colors = ZarTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(110.dp)) { ZarEyebrow(label) }
        Text(value, style = ZarType.bodyM.copy(lineHeight = 19.sp), color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (onClick != null) ZarIcon(R.drawable.ic_ph_caret_right, size = 14.dp, tint = colors.iconMuted)
    }
}

/** Body/S note in secondary text, inset like the section headers. */
@Composable
internal fun BillNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ZarType.bodyS.copy(lineHeight = 19.sp), color = ZarTheme.colors.textSecondary, modifier = modifier.padding(horizontal = 22.dp))
}
