package com.example.zar.core.presentation.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Zar has no card shadows: cards, rows, hero and settings blocks separate through surface colour, border and spacing.
 * The one deliberate exception is the floating bottom navigation, whose depth is this named, theme-aware token so
 * flattening cards can never remove it. Other floating buttons own their own reviewed values; none reads a card default.
 *
 * The pill is white, and often floats over white rows, so its edge must still read there. Android's elevation shadow is
 * a fixed-light shadow that barely shows on white, so from API 28 the shadow is drawn directly: a tight contact layer
 * and a wide soft one, both navy, offset downward and blurred past every edge. Older releases keep the elevation shadow.
 * Dark swaps to black tones that stay visible on the night canvas.
 */
fun Modifier.zarFloatingNavShadow(shape: Shape, colors: ZarColors): Modifier {
    val dark = colors.canvas.luminance() < 0.5f
    val tight = if (dark) Color(0x40000000) else Color(0x290F1A2B)
    val wide = if (dark) Color(0x66000000) else Color(0x3D0F1A2B)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return this
            .shadow(elevation = 3.dp, shape = shape, ambientColor = tight, spotColor = tight)
            .shadow(elevation = 14.dp, shape = shape, ambientColor = tight, spotColor = wide)
    }
    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawIntoCanvas { canvas ->
            fun layer(color: Color, blur: Float, dy: Float) {
                val paint = Paint().apply { this.color = colors.surface }
                paint.asFrameworkPaint().setShadowLayer(blur, 0f, dy, color.toArgb())
                canvas.drawOutline(outline, paint)
            }
            layer(wide, blur = 20.dp.toPx(), dy = 6.dp.toPx())
            layer(tight, blur = 5.dp.toPx(), dy = 1.5.dp.toPx())
        }
    }
}
