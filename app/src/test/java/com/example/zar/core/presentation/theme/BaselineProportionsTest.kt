package com.example.zar.core.presentation.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arithmetic of the prototype policy. The density numbers model the Pixel 6a AVD profile (420 dpi, 1080 x 2400 px);
 * no physical-device measurement has been taken, and the "small" values are illustrative steps, not measured settings.
 */
class BaselineProportionsTest {

    private val stableDpi = 420
    private val stable = stableDpi / 160f // 2.625
    private val screenWidthPx = 1080
    private val smallDisplay = 357 / 160f // illustrative: about 85% of default
    private val smallFont = 0.85f

    private fun resolve(density: Float, fontScale: Float) = ZarProportionPolicy.resolve(density, stable, fontScale)

    /** Physical size in pixels of a dp or sp value under the resolved layout density. */
    private fun dpPx(value: Float, r: ZarProportionPolicy.Resolved) = value * r.density
    private fun spPx(value: Float, r: ZarProportionPolicy.Resolved) = value * r.density * r.fontScale

    private val baseline = resolve(stable, 1f)

    @Test
    fun defaultSettingsAreLeftExactlyAsAndroidReportsThem() {
        assertFalse(baseline.adjusted)
        assertEquals(stable, baseline.density, 0f)
        assertEquals(1f, baseline.fontScale, 0f)
    }

    @Test
    fun displayOnlySmallKeepsDpAndTextAtTheirBaselinePixelSizes() {
        val r = resolve(smallDisplay, 1f)

        assertTrue(r.densityAdjusted)
        assertFalse(r.fontScaleAdjusted)
        assertEquals(dpPx(68f, baseline), dpPx(68f, r), 0.001f) // navbar and Add
        assertEquals(spPx(14f, baseline), spPx(14f, r), 0.001f)
        assertEquals(screenWidthPx / baseline.density, screenWidthPx / r.density, 0.001f) // same logical width
    }

    @Test
    fun fontOnlySmallIsFlooredAndLeavesGeometryAlone() {
        val r = resolve(stable, smallFont)

        assertFalse(r.densityAdjusted)
        assertTrue(r.fontScaleAdjusted)
        assertEquals(1f, r.fontScale, 0f)
        assertEquals(spPx(52f, baseline), spPx(52f, r), 0.001f)
        assertEquals(dpPx(68f, baseline), dpPx(68f, r), 0.001f)
    }

    @Test
    fun bothSmallAreHandledIndependentlyAndMatchTheBaselineWithinFivePercent() {
        val r = resolve(smallDisplay, smallFont)

        assertTrue(r.densityAdjusted && r.fontScaleAdjusted)
        listOf(68f, 412f, 9f, 215f).forEach { dp ->
            assertEquals("$dp dp", dpPx(dp, baseline), dpPx(dp, r), dpPx(dp, baseline) * 0.05f)
        }
        listOf(11f, 14f, 18f, 34f, 52f).forEach { sp ->
            assertEquals("$sp sp", spPx(sp, baseline), spPx(sp, r), spPx(sp, baseline) * 0.05f)
        }
    }

    @Test
    fun enlargedFontsPassThroughSoNativeScalingStillApplies() {
        listOf(1.15f, 1.3f, 1.5f, 2f).forEach { scale ->
            val r = resolve(stable, scale)
            assertFalse("scale $scale", r.adjusted)
            assertEquals(scale, r.fontScale, 0f)
            assertEquals(stable, r.density, 0f)
        }
    }

    @Test
    fun enlargedFontOnASmallDisplayStillGrowsFromTheBaselineSize() {
        val r = resolve(smallDisplay, 1.5f)

        assertTrue(r.densityAdjusted)
        assertFalse(r.fontScaleAdjusted)
        assertEquals(spPx(14f, baseline) * 1.5f, spPx(14f, r), 0.001f)
    }

    @Test
    fun aLargerDisplaySizeIsNotCompensated() {
        val r = resolve(stable * 1.1f, 1f)

        assertFalse(r.adjusted)
        assertEquals(stable * 1.1f, r.density, 0f)
    }

    @Test
    fun anUnknownStableDensityOnlyAppliesTheFontFloor() {
        val r = ZarProportionPolicy.resolve(density = smallDisplay, stableDensity = 0f, fontScale = smallFont)

        assertFalse(r.densityAdjusted)
        assertEquals(smallDisplay, r.density, 0f)
        assertEquals(1f, r.fontScale, 0f)
    }

    @Test
    fun invalidFontScalesAreTreatedAsDefault() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals("scale $it", 1f, resolve(stable, it).fontScale, 0f)
        }
    }

    @Test
    fun resolvingTwiceChangesNothingFurther() {
        val first = resolve(smallDisplay, smallFont)
        val second = resolve(first.density, first.fontScale)

        assertFalse(second.adjusted)
        assertEquals(first.density, second.density, 0f)
        assertEquals(first.fontScale, second.fontScale, 0f)
    }

    @Test
    fun fixedPixelInsetsKeepTheirPhysicalOffsetUnderTheOverride() {
        // Android reports window insets in pixels; Compose converts them through LocalDensity. Providing the raised
        // density therefore leaves status-bar/IME/gesture offsets where the platform put them in physical pixels.
        val resolved = resolve(smallDisplay, 1f)
        val provided = Density(resolved.density, resolved.fontScale)
        val insetPx = 24f * smallDisplay
        with(provided) { assertEquals(insetPx, insetPx.toDp().toPx(), 0.001f) }
    }

    @Test
    fun touchTargetsNeverShrinkBelowTheDefaultPixelSize() {
        val resolved = resolve(smallDisplay, 1f)
        val provided = Density(resolved.density, resolved.fontScale)
        val androidSmall = Density(smallDisplay, 1f)
        val atDefault = Density(stable, 1f)
        with(provided) {
            assertTrue(48.dp.toPx() >= with(androidSmall) { 48.dp.toPx() })
            assertEquals(with(atDefault) { 48.dp.toPx() }, 48.dp.toPx(), 0.001f)
        }
    }

    @Test
    fun theComposeDensityOverloadReadsDensityAndFontScaleFromTheCurrentDensity() {
        val small = Density(smallDisplay, smallFont)
        val r = ZarProportionPolicy.resolve(small, stableDpi)
        val adjusted = Density(r.density, r.fontScale)

        with(adjusted) {
            assertEquals(68.dp.toPx(), with(Density(stable, 1f)) { 68.dp.toPx() }, 0.001f)
            assertEquals(14.sp.toPx(), with(Density(stable, 1f)) { 14.sp.toPx() }, 0.001f)
        }
        val atDefault = Density(stable, 1f)
        assertFalse(ZarProportionPolicy.resolve(atDefault, stableDpi).adjusted)
    }
}
