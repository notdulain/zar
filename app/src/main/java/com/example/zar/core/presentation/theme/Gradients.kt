package com.example.zar.core.presentation.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode

/**
 * Figma's signature paint styles. Stops beyond 100% sit outside the visible card, preserving
 * the deliberately dark lower edge. Use as a background brush at the element's own size.
 */
object ZarGradients {
    val dusk: Brush = vertical(
        2.1019f,
        0f to Color(0xFF05070B),
        0.94587f to Color(0xFF0B1524),
        1.6815f to Color(0xFF14304A),
        2.1019f to Color(0xFF26506A),
    )

    val received: Brush = vertical(
        1.2868f,
        0f to Color(0xFF05070B),
        0.57904f to Color(0xFF0C1A2B),
        1.0294f to Color(0xFF174257),
        1.2868f to Color(0xFF2E7F86),
    )

    val spent: Brush = vertical(
        1.2445f,
        0f to Color(0xFF0A070C),
        0.47511f to Color(0xFF2A1828),
        0.99559f to Color(0xFF7E3D52),
        1.2445f to Color(0xFFC9685A),
    )

    val goal: Brush = vertical(
        1.1453f,
        0.023333f to Color(0xFF05070B),
        0.093471f to Color(0xFF0B1524),
        // Final Figma handoff: the navy mid-stop moves from 49.4% to 70% of the gradient (0.70 * 1.1453) so the
        // text near the coral edge keeps its contrast; the colours are unchanged.
        0.8017f to Color(0xFF14304A),
        1.1453f to Color(0xFFF3A78F),
    )

    /**
     * Crypto hero "realized result": near-black to a deep gold. It used to end on bright Binance gold (#F0B90B), which
     * left the status line under it at about 1.6:1; ending on #85620A keeps the gold glow and the line at 4.9:1.
     */
    val crypto: Brush = vertical(
        1f,
        0f to Color(0xFF05070B),
        0.439f to Color(0xFF2A1F08),
        0.90f to Color(0xFF6E520C),
        1f to Color(0xFF85620A),
    )

    val statusBarScrim: Brush = vertical(
        1f,
        0f to Color(0xD105070B),
        0.5f to Color(0x8005070B),
        1f to Color.Transparent,
    )
}

private fun vertical(endAt: Float, vararg stops: Pair<Float, Color>): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader = LinearGradientShader(
            from = Offset.Zero,
            to = Offset(0f, size.height * endAt),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first / endAt },
            tileMode = TileMode.Clamp,
        )
    }
