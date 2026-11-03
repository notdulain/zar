package com.example.zar.core.presentation.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Zar has no card shadows: cards, rows, hero and settings blocks separate through surface colour, border and spacing.
 * The one deliberate exception is the floating bottom navigation, whose depth is this named, theme-aware token so
 * flattening cards can never remove it. Other floating buttons own their own reviewed values; none reads a card default.
 *
 * The live Light reference stacks two CSS shadows, (0,2,6 @6%) and (0,14,34,-4 @10%) of #0F1A2B. Android's shadow
 * modifier has one blur per layer, so a tight and a wide layer are stacked; Dark swaps to black tones that stay visible
 * on the night canvas.
 */
fun Modifier.zarFloatingNavShadow(shape: Shape, colors: ZarColors): Modifier {
    val dark = colors.canvas.luminance() < 0.5f
    val tight = if (dark) Color(0x40000000) else Color(0x0F0F1A2B)
    val wide = if (dark) Color(0x66000000) else Color(0x1A0F1A2B)
    return this
        .shadow(elevation = 3.dp, shape = shape, ambientColor = tight, spotColor = tight)
        .shadow(elevation = 14.dp, shape = shape, ambientColor = tight, spotColor = wide)
}
