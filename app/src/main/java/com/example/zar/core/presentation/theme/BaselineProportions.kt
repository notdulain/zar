package com.example.zar.core.presentation.theme

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Keeps the accepted default-size composition when Android's Display size or Font size is set below default.
 * PROTOTYPE: the arithmetic is covered by unit tests, but the stable-density reference has not been confirmed against
 * the physical Pixel 6a, and [ProvideBaselineProportions] is deliberately not applied at the app root yet
 * (`stabilize-mobile-proportions` tasks 1.1 and 1.2). See docs/design-system.md "Baseline proportions".
 *
 * Font size and Display size are separate inputs and are treated separately:
 *
 * - **Display size** lowers `densityDpi`, so every dp (and every sp) gets physically smaller while more dp fit on screen.
 *   When the current density is below the device's stable density, the density the UI is laid out with is raised back to
 *   the stable one. One dp is then the same number of pixels as at default, and the window has the same logical width,
 *   so the baseline composition is reproduced without scaling individual tokens.
 * - **Font size** below 1.0 is floored at 1.0. Above 1.0 the platform's own scale (non-linear on Android 14+) is passed
 *   through untouched, so enlarged text still grows and reflows rather than being frozen.
 * - A larger display size (current above stable) is never compensated: content may grow and reflow.
 *
 * Only Compose's layout density is adjusted. Nothing here changes Android's settings or configuration, scales a bitmap,
 * or derives anything from xdpi/ydpi; pointer coordinates, insets and the IME stay in the window's native pixels and are
 * converted through the same density, so hit areas follow what is drawn. Platform surfaces that do not read
 * [LocalDensity] (system pickers, permission dialogs) keep Android's native sizing.
 */
object ZarProportionPolicy {

    /** What the UI should be laid out with. [densityAdjusted]/[fontScaleAdjusted] say whether it differs from Android's. */
    data class Resolved(
        val density: Float,
        val fontScale: Float,
        val densityAdjusted: Boolean,
        val fontScaleAdjusted: Boolean,
    ) {
        val adjusted: Boolean get() = densityAdjusted || fontScaleAdjusted
    }

    /**
     * @param density Compose density (`densityDpi / 160`) Android currently reports.
     * @param stableDensity the device's default density (`DENSITY_DEVICE_STABLE / 160`); zero or negative when unknown, in
     * which case display size cannot be compensated and only the font floor applies.
     * @param fontScale Android's current font scale.
     */
    fun resolve(density: Float, stableDensity: Float, fontScale: Float): Resolved {
        val safeFont = if (fontScale.isFinite() && fontScale > 0f) fontScale else 1f
        val lowDensity = stableDensity > 0f && density > 0f && density < stableDensity
        val lowFont = safeFont < 1f
        return Resolved(
            density = if (lowDensity) stableDensity else density,
            fontScale = if (lowFont) 1f else safeFont,
            densityAdjusted = lowDensity,
            fontScaleAdjusted = lowFont,
        )
    }

    fun resolve(current: Density, stableDensityDpi: Int): Resolved =
        resolve(current.density, stableDensityDpi / DENSITY_DPI_PER_UNIT, current.fontScale)

    /** Android's default density is 160 dpi per unit of [Density.density]. */
    const val DENSITY_DPI_PER_UNIT = 160f
}

/**
 * Lays out [content] with the baseline-size policy. A no-op (the same [Density] object) at or above default settings.
 * Not applied at the app root until the policy is verified on the device; see [ZarProportionPolicy].
 */
@Composable
fun ProvideBaselineProportions(
    stableDensityDpi: Int = DisplayMetrics.DENSITY_DEVICE_STABLE,
    content: @Composable () -> Unit,
) {
    val current = LocalDensity.current
    val density = remember(current, stableDensityDpi) {
        val resolved = ZarProportionPolicy.resolve(current, stableDensityDpi)
        if (resolved.adjusted) Density(resolved.density, resolved.fontScale) else current
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
