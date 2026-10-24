package com.example.zar.core.presentation.components

import com.example.zar.core.presentation.privacy.AmountMask
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType

/** The 46dp Figma track with 38dp segments and a white selected thumb. */
@Composable
fun ZarSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(labels.isNotEmpty()) { "Segmented control requires at least one label" }
    val colors = ZarTheme.colors
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.subtle)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .then(
                        if (selected) Modifier.shadow(
                            3.dp,
                            shape,
                            ambientColor = Color(0x140F1A2B),
                            spotColor = Color(0x140F1A2B),
                        ) else Modifier,
                    )
                    .clip(shape)
                    .background(if (selected) colors.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = ZarType.bodyS.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                    color = if (selected) colors.textPrimary else colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A proportional 5dp bar; unknown retains a visible segment even when its amount is zero. */
@Composable
fun ZarSplitBar(
    committed: Float,
    discretionary: Float,
    unknown: Float,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val safeCommitted = committed.coerceAtLeast(0f)
    val safeDiscretionary = discretionary.coerceAtLeast(0f)
    val safeUnknown = unknown.coerceAtLeast(0f)
    val total = (safeCommitted + safeDiscretionary + safeUnknown).coerceAtLeast(1f)
    if (AmountMask.isHidden) {
        // The proportions would reveal the split, so show one neutral bar instead.
        Box(modifier.fillMaxWidth().height(5.dp).background(colors.chartUnknown, RoundedCornerShape(3.dp)))
        return
    }
    Row(
        modifier.fillMaxWidth().height(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (safeCommitted > 0f) {
            Box(Modifier.weight(safeCommitted / total).height(5.dp).background(colors.accentSelected, RoundedCornerShape(3.dp)))
        }
        if (safeDiscretionary > 0f) {
            Box(Modifier.weight(safeDiscretionary / total).height(5.dp).background(colors.attention, RoundedCornerShape(3.dp)))
        }
        Box(
            Modifier.weight((safeUnknown / total).coerceAtLeast(0.02f))
                .height(5.dp)
                .background(colors.chartUnknown, RoundedCornerShape(3.dp)),
        )
    }
}

/** The 38×22 Figma switch geometry, exposed as a real toggle for accessibility. */
@Composable
fun ZarSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val colors = ZarTheme.colors
    // The Design System switch is 38x22; Figma's filter sheet uses a 46x28 one with a 22dp thumb.
    val width = if (large) 46.dp else 38.dp
    val height = if (large) 28.dp else 22.dp
    val inset = if (large) 3.dp else 2.dp
    val thumb = height - inset * 2
    Box(
        modifier
            .size(width = width, height = height)
            .clip(CircleShape)
            .background(if (checked) colors.accentSelected else colors.trackOff)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = if (checked) width - thumb - inset else inset)
                .size(thumb)
                .shadow(2.dp, CircleShape, ambientColor = Color(0x140F1A2B), spotColor = Color(0x140F1A2B))
                .background(Color.White, CircleShape),
        )
    }
}
