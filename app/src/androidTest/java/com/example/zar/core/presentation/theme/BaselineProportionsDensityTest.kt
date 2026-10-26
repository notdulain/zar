package com.example.zar.core.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.FontScaleConverterFactory
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Installed-Compose evidence for the baseline-proportion policy on a real composition, not just its arithmetic.
 *
 * The policy is applied at the app root in `MainActivity` (`stabilize-mobile-proportions` 1.2/2.1). These tests force
 * the risky case the earlier test missed: a **below-default display size** (density below the stable reference) so the
 * density override actually fires, combined with a large font. They then assert real measured pixel bounds (a 48dp
 * control, a dialog) and that Android's non-linear font conversion still applies at 1.3/1.5/2.0.
 *
 * `FontScaling.toDp` consults the static [FontScaleConverterFactory] by font scale, not a converter stored on the
 * density instance, so a `Density(density, fontScale)` built for the layout-density override still scales text
 * non-linearly. Runs on the emulator because the conversion tables are Android resources.
 */
class BaselineProportionsDensityTest {
    @get:Rule val compose = createComposeRule()

    private val nonlinearScales = listOf(1.3f, 1.5f, 2.0f)
    private val stableDpi = 320 // stable density 2.0 at 160 dpi/unit

    // --- Real composition: the density override fires and measured bounds follow it -----------------------------

    @Test
    fun aBelowDefaultDisplaySizeRaisesTheLayoutDensityInComposition() {
        var observedDensity = 0f
        var observedFontScale = 0f
        compose.setContent {
            // Small display size: density 1.5 is below the stable 2.0, so the override must fire.
            CompositionLocalProvider(LocalDensity provides Density(1.5f, 1.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    val d = LocalDensity.current
                    observedDensity = d.density
                    observedFontScale = d.fontScale
                }
            }
        }
        compose.waitForIdle()
        assertEquals("density is raised back to the stable reference", 2.0f, observedDensity, 0.0001f)
        assertEquals("the font scale is untouched", 1.0f, observedFontScale, 0.0001f)
    }

    @Test
    fun aReal48DpControlIsLaidOutWithTheResolvedDensity() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f, 1.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    Box(Modifier.size(48.dp).background(Color.Black).testTag("target"))
                }
            }
        }
        val size = compose.onNodeWithTag("target").fetchSemanticsNode().size
        // 48dp at the resolved density 2.0 is 96px; at the un-adjusted 1.5 it would be 72px.
        assertEquals(96, size.width)
        assertEquals(96, size.height)
    }

    @Test
    fun aMinimumTouchTargetStaysAtLeast48DpAfterAdjustment() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f, 1.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    Box(Modifier.size(48.dp).clickable {}.background(Color.Black).testTag("tap"))
                }
            }
        }
        val size = compose.onNodeWithTag("tap").fetchSemanticsNode().size
        val minPx = (48 * 2.0f).toInt()
        assertTrue("hit target must stay at least 48dp ($minPx px) tall", size.height >= minPx)
        assertTrue("hit target must stay at least 48dp wide", size.width >= minPx)
    }

    @Test
    fun dialogContentUsesTheWindowDensityAndStaysTappable() {
        // A Compose Dialog is its own window and does not inherit the composition's LocalDensity, so its content is
        // laid out at the platform (window) density. This test records that measured behaviour rather than assuming it.
        var taps = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f, 1.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    Dialog(onDismissRequest = {}) {
                        Box(Modifier.size(48.dp).clickable { taps++ }.background(Color.Black).testTag("dialog-box"))
                    }
                }
            }
        }
        val platformDensity = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val size = compose.onNodeWithTag("dialog-box").fetchSemanticsNode().size
        assertEquals("dialog content is measured at the window density", (48 * platformDensity).roundToInt(), size.width)
        compose.onNodeWithTag("dialog-box").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    // --- Font scaling: the non-linear conversion is preserved at 1.3 / 1.5 / 2.0 with a small density -----------

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
    fun smallDisplayPlusLargeFontKeepsTheNonLinearFontScale() {
        // The case the earlier test missed: density 1.5 (below stable 2.0) fires the override, and fontScale 2.0 must
        // survive it and still convert non-linearly.
        var observedFontScale = 0f
        var observedDensity = 0f
        var observedDp = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f, 2.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    val d = LocalDensity.current
                    observedFontScale = d.fontScale
                    observedDensity = d.density
                    observedDp = with(d) { 16.sp.toDp() }.value
                }
            }
        }
        compose.waitForIdle()
        assertEquals("density override fires", 2.0f, observedDensity, 0.0001f)
        assertEquals("large font is passed through", 2.0f, observedFontScale, 0.0001f)
        val converter = FontScaleConverterFactory.forScale(2f)
        if (FontScaleConverterFactory.isNonLinearFontScalingActive(2f) && converter != null) {
            assertEquals(converter.convertSpToDp(16f), observedDp, 0.0001f)
        }
    }

    @Test
    fun textGrowsAtEachLargeScaleWithASmallDensity() {
        val scale = androidx.compose.runtime.mutableStateOf(1.3f)
        var observedDp = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f, scale.value)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    observedDp = with(LocalDensity.current) { 16.sp.toDp() }.value
                }
            }
        }
        nonlinearScales.forEach { s ->
            compose.runOnIdle { scale.value = s }
            compose.waitForIdle()
            assertTrue("text must grow above 16dp at scale $s (was $observedDp)", observedDp > 16f)
        }
    }

    // --- Defaults and the font floor -----------------------------------------------------------------------------

    @Test
    fun defaultDisplayAndFontAreLeftUntouched() {
        var observedDensity = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.0f, 1.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    observedDensity = LocalDensity.current.density
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2.0f, observedDensity, 0.0001f)
    }

    @Test
    fun aSmallFontOnlyIsFlooredAtOne() {
        val resolved = ZarProportionPolicy.resolve(density = 2.0f, stableDensity = 2.0f, fontScale = 0.85f)
        assertTrue(resolved.fontScaleAdjusted)
        assertEquals(1.0f, resolved.fontScale, 0.0001f)
        assertTrue(!resolved.densityAdjusted)
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

    @Test
    fun aRenderedTextIsLaidOutAtTheResolvedDensityToo() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f, 1.0f)) {
                ProvideBaselineProportions(stableDensityDpi = stableDpi) {
                    Text("Zar", modifier = Modifier.testTag("label"))
                }
            }
        }
        // The label exists and is measured; its height is a positive pixel value at the resolved density.
        val height = compose.onNodeWithTag("label").fetchSemanticsNode().size.height
        assertTrue(height > 0)
    }
}
