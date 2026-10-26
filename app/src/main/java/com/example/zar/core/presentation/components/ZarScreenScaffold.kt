package com.example.zar.core.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarLightColors
import com.example.zar.core.presentation.theme.ZarTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size

/**
 * Layout shared by the four main screens.
 *
 * - [NavChrome.AddOnly] (initial Home of a fresh session): no navbar, Add button centred at the bottom.
 * - [NavChrome.Full]: pill navbar and Add button share one floating row. Every other top-level screen, and Home once
 *   another tab has been visited, uses this; [chrome] comes from the session in [LocalNavSession].
 *
 * [content] receives padding that already clears the navbar and the Add button, so lists can
 * scroll fully into view.
 *
 * The chrome itself is drawn once for the whole signed-in graph by `ZarNavHost` (see [LocalNavChromeHost]); here the
 * scaffold only reserves the same height, so the one navbar stays stationary while content travels between tabs.
 * Outside that host (previews, component tests) the scaffold draws the chrome itself.
 */
@Composable
fun ZarScreenScaffold(
    destination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    modifier: Modifier = Modifier,
    heroUnderStatusBar: Boolean = destination == TopLevelDestination.Home,
    chrome: NavChrome = LocalNavSession.current.chromeFor(destination),
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    val isLightTheme = ZarTheme.colors.canvas == ZarLightColors.canvas
    val sharedChromeHost = LocalNavChromeHost.current

    // A hero that extends under the status bar (Home, Activity) needs white system glyphs and no top inset.
    // Nothing is restored on exit: the screen opened next sets what it needs, and putting the old value back
    // after the transition left white glyphs on the light screens opened from Activity and More.
    DisposableEffect(activity, view, heroUnderStatusBar, isLightTheme) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.isAppearanceLightStatusBars = !heroUnderStatusBar && isLightTheme
        onDispose { }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (heroUnderStatusBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            WindowInsets.safeDrawing
        },
        bottomBar = {
            if (sharedChromeHost) {
                ZarBottomChromeSpace()
            } else {
                ZarTopLevelChrome(
                    destination = destination,
                    chrome = chrome,
                    onNavigate = onNavigate,
                    onAddOption = onAddOption,
                    controller = LocalNavCapsuleController.current ?: remember { NavCapsuleController() },
                )
            }
        },
    ) { inner ->
        // Preserve side insets as well, so landscape content clears display cutouts.
        content(
            PaddingValues(
                start = inner.calculateStartPadding(layoutDirection),
                end = inner.calculateEndPadding(layoutDirection),
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + ZarDimens.NavBottomGap,
            ),
        )
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun ZarAddButton(description: String, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(ZarDimens.AddButtonSize)
            .testTag("add-entry")
            .semantics { contentDescription = description },
        shape = CircleShape,
        containerColor = colors.accentPrimary,
        contentColor = colors.textOnInverse,
        elevation = FloatingActionButtonDefaults.elevation(4.dp, 4.dp, 4.dp, 4.dp),
    ) {
        PlusIcon(tint = colors.textOnInverse)
    }
}
