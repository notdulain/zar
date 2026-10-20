package com.example.zar.features.transactions.presentation.activity.screen

import androidx.annotation.DrawableRes
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.example.zar.core.presentation.components.ZarButton
import com.example.zar.core.presentation.components.ZarButtonSize
import com.example.zar.core.presentation.components.ZarCard
import com.example.zar.core.presentation.components.ZarIcon
import androidx.core.view.WindowCompat
import com.example.zar.core.presentation.theme.ZarLightColors
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType

/** A soft note card: an optional icon, a title and a line of detail (offline, voided, original kept). */
@Composable
internal fun InfoBanner(title: String, body: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
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
        }
    }
}

/** Small mono tag such as EDITED or VOID. */
@Composable
internal fun TagPill(text: String, modifier: Modifier = Modifier, attention: Boolean = false) {
    val colors = ZarTheme.colors
    Text(
        text.uppercase(),
        style = ZarType.monoCaption,
        color = if (attention) colors.textAttention else colors.textTertiary,
        modifier = modifier.clip(CircleShape).background(colors.subtle).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A rounded filter chip. With [onRemove] it shows a small cross and removing it clears that filter. */
@Composable
internal fun FilterPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, removeLabel: String? = null) {
    val colors = ZarTheme.colors
    val foreground = if (selected) colors.textOnInverse else colors.textSecondary
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) colors.accentPrimary else colors.surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = removeLabel, onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = if (onClick != null) 14.dp else 18.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ZarType.labelChip, color = foreground, maxLines = 1)
        if (onClick != null) ZarIcon(R.drawable.ic_ph_x, size = 12.dp, tint = foreground)
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
    ZarCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 30.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.size(56.dp).clip(CircleShape).background(colors.subtle),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { ZarIcon(icon, size = 26.dp) }
            Text(title, style = ZarType.headingSheet, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(body, style = ZarType.bodyS, color = colors.textSecondary, textAlign = TextAlign.Center)
            if (action != null && onAction != null) {
                ZarButton(action, onClick = onAction, size = ZarButtonSize.Medium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Dark status bar icons for a light screen, so the clock stays visible on the detail and form screens. */
@Composable
internal fun DarkStatusBarIcons() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    val isLightTheme = ZarTheme.colors.canvas == ZarLightColors.canvas
    DisposableEffect(activity, view, isLightTheme) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        if (isLightTheme) controller?.isAppearanceLightStatusBars = true
        onDispose { }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
