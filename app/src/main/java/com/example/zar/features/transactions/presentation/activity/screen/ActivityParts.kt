package com.example.zar.features.transactions.presentation.activity.screen

import androidx.annotation.DrawableRes
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.example.zar.core.presentation.theme.ZarPrimitives
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zar.R
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarIcon
import androidx.core.view.WindowCompat
import com.example.zar.core.presentation.theme.ZarLightColors
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType

/** A soft note card: an optional icon, a title and a line of detail (offline, voided, original kept). */
@Composable
internal fun InfoBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = ZarTheme.colors
    Row(
        modifier.fillMaxWidth().clip(ZarShapes.lg).background(colors.subtle).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) ZarIcon(icon, size = 20.dp, tint = colors.iconStrong)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZarType.labelChip, color = colors.textPrimary)
            Text(body, style = ZarType.bodyCaption.copy(lineHeight = 16.sp), color = colors.textSecondary)
            if (actions != null) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
    }
}

/** A small pill action for use inside an [InfoBanner]. */
@Composable
internal fun BannerAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val colors = ZarTheme.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(if (filled) colors.accentPrimary else colors.canvas)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = ZarType.labelChip, color = if (filled) colors.textOnInverse else colors.textPrimary)
    }
}

/** Small tag such as EDITED or VOID. */
@Composable
internal fun TagPill(text: String, modifier: Modifier = Modifier, attention: Boolean = false) {
    val colors = ZarTheme.colors
    Text(
        text.uppercase(),
        style = ZarType.caption,
        color = if (attention) colors.textAttention else colors.textTertiary,
        modifier = modifier.clip(CircleShape).background(colors.subtle).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Figma's filter pill. Selected is a solid navy pill; unselected is white with a hairline border. With [onRemove]
 * a selected pill also shows a small cross and tapping it clears that filter.
 */
@Composable
internal fun FilterPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    removeLabel: String? = null,
    removable: Boolean = false,
) {
    val colors = ZarTheme.colors
    val foreground = if (selected) colors.textOnInverse else colors.textSecondary
    // Unselected pills are 35dp (1dp border, 8dp padding), selected ones 37dp (no border, 10dp padding), as in Figma.
    val vertical = if (selected) 10.dp else 8.dp
    val start = if (selected) 18.dp else 16.dp
    val end = if (removable) 14.dp else start
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) colors.accentPrimary else colors.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Color(0xFFE6E6E9), CircleShape))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = removeLabel, onClick = onClick) else Modifier)
            .padding(start = start, end = end, top = vertical, bottom = vertical),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ZarType.labelChip.copy(lineHeight = 17.sp), color = foreground, maxLines = 1)
        if (removable) ZarIcon(R.drawable.ic_ph_x, size = 12.dp, tint = foreground)
    }
}

/** Sheet title with the 20dp cross at the right edge of the 24dp margin. */
@Composable
internal fun SheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val close = stringResource(R.string.action_close)
    Row(modifier.fillMaxWidth().height(23.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ZarType.headingSheet.copy(lineHeight = 23.sp), color = ZarTheme.colors.textPrimary)
        // A 40dp touch target around the 20dp cross, pulled out so the cross sits on the margin.
        Box(
            Modifier
                .requiredSize(40.dp)
                .offset(x = 10.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = close, onClick = onClose)
                .semantics { contentDescription = close },
            Alignment.Center,
        ) { ZarIcon(R.drawable.ic_ph_x, size = 20.dp) }
    }
}

/** Icon, title and a sentence, with one optional action. Used for "nothing here" and "nothing matches". */
@Composable
internal fun EmptyCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = ZarTheme.colors
    // Figma Z48: 32dp above the 56dp icon chip, 28dp below the button, 10dp between the lines.
    ZarCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.size(56.dp).clip(CircleShape).background(colors.subtle),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { ZarIcon(icon, size = 26.dp) }
            Text(title, style = ZarType.headingSheet.copy(lineHeight = 22.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(body, style = ZarType.bodyS.copy(lineHeight = 18.sp), color = colors.textSecondary, textAlign = TextAlign.Center)
            if (action != null && onAction != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accentPrimary)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text(action, style = ZarType.labelChip.copy(lineHeight = 17.sp), color = colors.textOnInverse)
                }
            }
        }
    }
}

/** Status bar icons that contrast with a plain screen: dark on Light, light on Dark, so the clock stays visible. */
@Composable
internal fun DarkStatusBarIcons() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    val isLightTheme = ZarTheme.colors.canvas == ZarLightColors.canvas
    DisposableEffect(activity, view, isLightTheme) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.isAppearanceLightStatusBars = isLightTheme
        onDispose { }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
