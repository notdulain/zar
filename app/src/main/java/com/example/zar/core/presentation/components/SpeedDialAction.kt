package com.example.zar.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.motion.MotionLevel
import com.example.zar.core.presentation.motion.pressScale
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarTheme

@Composable
internal fun SpeedDialAction(
    option: AddEntryOption,
    progress: () -> Float,
    level: MotionLevel,
    row: Boolean,
    towardCenter: Float,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZarTheme.colors
    val quick = option == AddEntryOption.QuickNote
    val label = stringResource(if (quick) R.string.add_quick_note else R.string.add_manual)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val surfaceModifier = modifier
        .graphicsLayer {
            // Float transforms preserve subpixel motion and reuse the recorded icon, border and shadow drawing.
            val p = progress()
            val full = level == MotionLevel.Full
            val phase = if (full) speedDialPhase(p, quick) else 1f
            translationX = if (row && quick) towardCenter * (ZarDimens.SpeedDialAction + ZarDimens.SpaceSm).toPx() * phase else 0f
            translationY = -(if (row || !quick) ZarDimens.SpeedDialManualOffset else ZarDimens.SpeedDialQuickOffset).toPx() * phase
            scaleX = if (full) speedDialScale(phase) else 1f
            scaleY = scaleX
            alpha = if (full) speedDialAlpha(phase) else p.coerceIn(0f, 1f)
        }
        .pressScale(interaction)
        .size(ZarDimens.SpeedDialAction)
        .drawBehind {
            if (interactive && focused) drawCircle(colors.borderSelected, radius = size.minDimension / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        .shadow(14.dp, CircleShape, ambientColor = Color(0x0F0F1A2B), spotColor = Color(0x1A0F1A2B))
    // Keep the same drawing subtree throughout reversal; only input/semantics are detached while closing.
    Box(
        surfaceModifier.clip(CircleShape)
            .background(if (pressed) colors.subtle else colors.speedDialActionFill)
            .border(ZarDimens.Hairline, colors.speedDialActionBorder, CircleShape)
            .then(if (interactive) Modifier
                .testTag(if (quick) "add-quick-note" else "add-manual")
                .semantics {
                    contentDescription = label
                    traversalIndex = if (quick) 1f else 2f
                }
                .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            else Modifier),
    ) { ActionContent(option) }
}

@Composable
private fun ActionContent(option: AddEntryOption) {
    Box(Modifier.size(ZarDimens.SpeedDialAction), contentAlignment = Alignment.Center) {
        AddActionIcon(option, ZarTheme.colors.iconStrong)
    }
}
