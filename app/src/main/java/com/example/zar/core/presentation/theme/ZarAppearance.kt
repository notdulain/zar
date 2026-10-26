package com.example.zar.core.presentation.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.example.zar.features.settings.domain.model.AppearanceMode

/**
 * Resolves the saved [AppearanceMode] once, above the navigation roots, and applies it to everything the app draws:
 * the Compose theme (so sheets, dialogs and pickers follow too), the system-bar glyphs and the activity window
 * behind the first frame. Explicit Light and Dark ignore Android; only System follows it, and it re-resolves
 * immediately because [isSystemInDarkTheme] reads the live configuration.
 *
 * Nothing here is keyed on screen or form state, so switching appearance recomposes colours in place without
 * touching navigation, scroll or unsaved input.
 */
@Composable
fun ZarAppearance(appearance: AppearanceMode, content: @Composable () -> Unit) {
    val dark = appearance.resolveDark(systemDark = isSystemInDarkTheme())
    val activity = LocalContext.current.findHostActivity()
    // Declared before the content so that a screen's own system-bar choice (for example white glyphs over a hero)
    // is applied after this baseline whenever the appearance flips.
    DisposableEffect(activity, dark) {
        activity?.applyZarAppearance(dark)
        onDispose { }
    }
    ZarTheme(darkTheme = dark, content = content)
}

/** Whether Android is currently in its dark mode, for the very first frame before Compose is running. */
fun Configuration.isSystemNightMode(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * Makes the window agree with the resolved appearance: transparent status bar and navigation glyphs that contrast
 * with the app (not with Android's mode), and a window background in the app's canvas colour so a frame drawn
 * before or between Compose content never shows the other mode.
 */
fun ComponentActivity.applyZarAppearance(dark: Boolean) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
        navigationBarStyle = SystemBarStyle.auto(NavigationScrimLight, NavigationScrimDark) { dark },
    )
    window.setBackgroundDrawable(ColorDrawable((if (dark) ZarDarkColors else ZarLightColors).canvas.toArgb()))
}

// Same scrims as the platform default, used by three-button navigation.
private val NavigationScrimLight = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavigationScrimDark = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)

private fun Context.findHostActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
