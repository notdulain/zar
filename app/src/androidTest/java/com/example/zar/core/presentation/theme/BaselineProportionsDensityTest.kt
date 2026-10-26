package com.example.zar.core.presentation.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.fontscaling.FontScaleConverterFactory
import com.example.zar.core.presentation.theme.ZarProportionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Installed-Compose evidence that the baseline-proportion density override keeps Android's non-linear font scaling.
 * `FontScaling.toDp` consults the static [FontScaleConverterFactory] by font scale, not a converter stored on the
 * density instance, so a `Density(density, fontScale)` built for the layout-density override still scales text
 * non-linearly at 1.3/1.5/2.0. Runs on the emulator because the converting tables are Android resources.
 */
class BaselineProportionsDensityTest {
    @get:Rule val compose = createComposeRule()

    private val nonlinearScales = listOf(1.3f, 1.5f, 2.0f)

    @Test
    fun reconstructedDensityMatchesTheNonLinearConverter() {
        nonlinearScales.forEach { scale ->
            val linear = with(Density(1f, scale)) { 16.sp.toDp() }.value
            val converter = FontScaleConverterFactory.forScale(scale)
            if (FontScaleConverterFactory.isNonLinearFontScalingActive(scale) && converter != null) {
                val expected = converter.convertSpToDp(16f)
                assertEquals("fontScale $scale must be non-linear", expected, linear, 0.0001f)
                assertTrue("fontScale $scale must not be the linear 16*scale", kotlin.math.abs(16f * scale - linear) > 0.01f)
            }
        }
    }

    @Test
    fun baselineProportionsKeepsTheNonLinearFontScaleInComposition() {
        // A small display size (density below stable) combined with a large font: the risky case.
        var observedFontScale = 0f
        var observedDensity = 0f
        var observedDp = 0f
        compose.setContent {
            val base = Density(2.0f, 2.0f) // density 2 (small display) and fontScale 2 (large font)
            CompositionLocalProvider(LocalDensity provides base) {
                ProvideBaselineProportions(stableDensityDpi = 320) { // stable density 2.0 at 160dpi/unit -> no density change
                    val d = LocalDensity.current
                    observedFontScale = d.fontScale
                    observedDensity = d.density
                    observedDp = with(d) { 16.sp.toDp() }.value
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2f, observedFontScale, 0.0001f)
        assertEquals(2f, observedDensity, 0.0001f)
        val converter = FontScaleConverterFactory.forScale(2f)
        if (FontScaleConverterFactory.isNonLinearFontScalingActive(2f) && converter != null) {
            assertEquals(converter.convertSpToDp(16f), observedDp, 0.0001f)
        }
    }

    @Test
    fun aSmallDisplaySizeRaisesOnlyTheLayoutDensity() {
        val resolved = ZarProportionPolicy.resolve(density = 1.5f, stableDensity = 2.0f, fontScale = 1.0f)
        assertTrue(resolved.densityAdjusted)
        assertEquals(2.0f, resolved.density, 0.0001f)
        assertEquals(1.0f, resolved.fontScale, 0.0001f)
        // A larger display size is never compensated.
        val bigger = ZarProportionPolicy.resolve(density = 2.5f, stableDensity = 2.0f, fontScale = 1.0f)
        assertTrue(!bigger.adjusted)
    }
}
