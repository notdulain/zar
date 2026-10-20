package com.example.zar.core.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Simple line icons drawn on a 24-unit grid, so the app needs no icon library.

@Composable
fun TopLevelIcon(destination: TopLevelDestination, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(width = 1.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (destination) {
            TopLevelDestination.Home -> {
                line(tint, stroke, unit, 3f to 11f, 12f to 4f, 21f to 11f)
                line(tint, stroke, unit, 6f to 9.5f, 6f to 20f, 18f to 20f, 18f to 9.5f)
            }
            TopLevelDestination.Activity -> {
                line(tint, stroke, unit, 3f to 3f, 3f to 21f, 21f to 21f)
                line(tint, stroke, unit, 7f to 15f, 11f to 11f, 14f to 14f, 20f to 7f)
            }
            TopLevelDestination.Plans -> {
                line(tint, stroke, unit, 6f to 3f, 6f to 21f)
                line(tint, stroke, unit, 6f to 4f, 18f to 4f, 15f to 8f, 18f to 12f, 6f to 12f)
            }
            TopLevelDestination.More -> {
                drawCircle(tint, radius = 3f * unit, center = center, style = stroke)
                drawCircle(tint, radius = 7f * unit, center = center, style = stroke)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        tint,
                        center + Offset(dx, dy) * (7f * unit),
                        center + Offset(dx, dy) * (10f * unit),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
fun PlusIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.width / 24f
        val width = 2f * unit
        drawLine(tint, Offset(12f * unit, 4f * unit), Offset(12f * unit, 20f * unit), width, StrokeCap.Round)
        drawLine(tint, Offset(4f * unit, 12f * unit), Offset(20f * unit, 12f * unit), width, StrokeCap.Round)
    }
}

private fun DrawScope.line(color: Color, stroke: Stroke, unit: Float, vararg points: Pair<Float, Float>) {
    val path = Path().apply {
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) moveTo(x * unit, y * unit) else lineTo(x * unit, y * unit)
        }
    }
    drawPath(path, color, style = stroke)
}
