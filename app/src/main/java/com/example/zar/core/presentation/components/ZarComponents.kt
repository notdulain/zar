package com.example.zar.core.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.theme.ZarShapes
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarType

// Shared building blocks from the Zar-UI Figma design system (Components page).

/** White card with the card elevation (Elevation/Card) and a hairline border. */
fun Modifier.zarCard(shape: Shape = ZarShapes.lg): Modifier = composed {
    val colors = ZarTheme.colors
    this
        .shadow(6.dp, shape, ambientColor = Color(0x0A0F1A2B), spotColor = Color(0x1F0F1A2B))
        .background(colors.surface, shape)
        .border(1.dp, colors.borderDefault, shape)
        .clip(shape)
}

@Composable
fun ZarDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(ZarTheme.colors.borderHairline))
}

/** Icon at its Figma size, tinted from the theme. */
@Composable
fun ZarIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = ZarTheme.colors.iconStrong) {
    Icon(painterResource(icon), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/** 40dp icon holder used by Figma's transaction and account rows. */
@Composable
fun ZarIconChip(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 19.dp,
    background: Color = ZarTheme.colors.subtle,
    tint: Color = ZarTheme.colors.iconStrong,
) {
    Box(modifier.size(size).clip(CircleShape).background(background), Alignment.Center) {
        ZarIcon(icon, size = iconSize, tint = tint)
    }
}

/** 44dp back button with the 22dp arrow, at the top-left of a screen. */
@Composable
fun ZarBackButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        Alignment.Center,
    ) {
        Icon(
            painterResource(com.example.zar.R.drawable.ic_ph_arrow_left),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = ZarTheme.colors.iconStrong,
        )
    }
}

/** Display/Title with a Body/S line under it. */
@Composable
fun ZarTitleBlock(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 22.dp)) {
        Text(title, style = ZarType.displayTitle, color = ZarTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = ZarType.bodyS, color = ZarTheme.colors.textSecondary)
    }
}

/** Mono/Label section header with an optional trailing action (Label/Action). */
@Composable
fun ZarSectionHeader(label: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 22.dp).height(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarMonoLabel(label)
        if (action != null) {
            Text(
                action,
                style = ZarType.labelAction,
                color = ZarTheme.colors.textSelected,
                modifier = Modifier.clickable(enabled = onAction != null, role = Role.Button) { onAction?.invoke() },
            )
        }
    }
}

@Composable
fun ZarMonoLabel(text: String, modifier: Modifier = Modifier, color: Color = ZarTheme.colors.textTertiary) {
    Text(text.uppercase(), style = ZarType.monoLabel, color = color, modifier = modifier)
}

private const val ICON_MAX_FONT_SCALE = 1.2f

enum class ZarButtonStyle { Primary, Soft, Outline }
enum class ZarButtonSize { Large, Medium }

/** Figma button variants: Large (58dp) for sheets, Medium (44dp) inside cards. */
@Composable
fun ZarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ZarButtonStyle = ZarButtonStyle.Primary,
    enabled: Boolean = true,
    size: ZarButtonSize = ZarButtonSize.Large,
    @DrawableRes icon: Int? = null,
    contentColor: Color? = null,
) {
    val colors = ZarTheme.colors
    val shape = if (size == ZarButtonSize.Large) ZarShapes.lg else androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val height = if (size == ZarButtonSize.Large) 58.dp else 44.dp
    val horizontalPadding = if (size == ZarButtonSize.Large) 24.dp else 16.dp
    val container = when (style) {
        ZarButtonStyle.Primary -> colors.accentPrimary
        ZarButtonStyle.Soft -> colors.subtle
        ZarButtonStyle.Outline -> colors.surface
    }
    val content = contentColor ?: if (style == ZarButtonStyle.Primary) colors.textOnInverse else colors.textPrimary
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(container)
            .then(if (style == ZarButtonStyle.Outline) Modifier.border(BorderStroke(1.dp, colors.borderDefault), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // A decorative icon gives way to the label at large font sizes, where half-width buttons otherwise cut the
            // label to "Quick n…".
            if (icon != null && LocalDensity.current.fontScale <= ICON_MAX_FONT_SCALE) {
                ZarIcon(icon, size = if (size == ZarButtonSize.Large) 20.dp else 18.dp, tint = content)
            }
            Text(
                text,
                style = if (size == ZarButtonSize.Large) ZarType.labelButton else ZarType.labelChip,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Single-select chip. Selected is solid accent/selected with a white label. */
@Composable
fun ZarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    multiSelect: Boolean = false,
) {
    val colors = ZarTheme.colors
    val foreground = if (selected) colors.textOnInverse else colors.textPrimary
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(ZarShapes.sm)
            .background(if (selected) colors.accentSelected else colors.subtle)
            .then(if (multiSelect) Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
                else Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick))
            .padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) ZarIcon(icon, size = 16.dp, tint = foreground)
        Text(label, style = ZarType.labelChip, color = foreground, maxLines = 1)
    }
}

/** Floating white pill that adds something, at the bottom of a list screen. */
@Composable
fun ZarAddPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZarTheme.colors
    Row(
        modifier
            .height(56.dp)
            .shadow(8.dp, ZarShapes.pill, ambientColor = Color(0x0F0F1A2B), spotColor = Color(0x1F0F1A2B))
            .background(colors.surface, ZarShapes.pill)
            .border(1.dp, colors.borderDefault, ZarShapes.pill)
            .clip(ZarShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZarIcon(com.example.zar.R.drawable.ic_ph_plus)
        Text(text, style = ZarType.labelButton, color = colors.textPrimary)
    }
}

/** A label (Mono/Label, fixed width) beside its value, as in Figma's form rows. */
@Composable
fun ZarFormRow(
    label: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 96.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(labelWidth).height(16.dp), Alignment.CenterStart) { ZarMonoLabel(label) }
        content()
    }
}
