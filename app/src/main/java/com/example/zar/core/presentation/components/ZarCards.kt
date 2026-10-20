package com.example.zar.core.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarGradients
import com.example.zar.core.presentation.theme.ZarTheme

/** White, softly elevated card used for summaries, goals, bills and list rows. */
@Composable
fun ZarCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = ZarDimens.CardRadius,
    contentPadding: PaddingValues = PaddingValues(ZarDimens.CardPadding),
    shadowElevation: Dp = 6.dp,
    soft: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    // Figma's card lift is a faint navy shadow, softer than Material's black elevation.
    val lift = if (soft) {
        modifier.shadow(3.dp, shape, ambientColor = Color(0x0A0F1A2B), spotColor = Color(0x140F1A2B))
    } else {
        modifier
    }
    val colors = ZarTheme.colors
    val border = BorderStroke(ZarDimens.Hairline, colors.borderDefault)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = lift,
            shape = shape,
            color = colors.surface,
            border = border,
            shadowElevation = if (soft) 0.dp else shadowElevation,
        ) { inner() }
    } else {
        Surface(
            modifier = lift,
            shape = shape,
            color = colors.surface,
            border = border,
            shadowElevation = if (soft) 0.dp else shadowElevation,
        ) { inner() }
    }
}

/** Dark rounded block at the top of Activity, Plans and More. */
@Composable
fun ZarHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ZarDimens.HeroRadius))
            .background(ZarGradients.dusk),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier.padding(ZarDimens.SpaceXl),
                verticalArrangement = Arrangement.spacedBy(ZarDimens.SpaceSm),
                content = content,
            )
        }
    }
}

/** Small uppercase caption, e.g. "RECENT ACTIVITY" or a card title. */
@Composable
fun ZarLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

/** One line of money activity: title and detail on the left, signed amount on the right. */
@Composable
fun ZarActivityRow(
    title: String,
    detail: String,
    amount: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ZarCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        radius = ZarDimens.RowCardRadius,
        contentPadding = PaddingValues(horizontal = ZarDimens.SpaceLg, vertical = ZarDimens.SpaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceMd),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(amount, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
        }
    }
}
