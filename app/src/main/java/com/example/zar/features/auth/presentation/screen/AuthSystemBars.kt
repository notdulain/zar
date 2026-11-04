package com.example.zar.features.auth.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.zar.core.presentation.components.findActivity
import com.example.zar.core.presentation.theme.ZarTheme

/** Every auth/restore frame uses dusk, including when the saved appearance is Light. */
@Composable
internal fun AuthSystemBars() {
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    val canvas = ZarTheme.colors.canvas
    DisposableEffect(activity, view, canvas) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            status?.let { controller?.isAppearanceLightStatusBars = it }
            navigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
}
