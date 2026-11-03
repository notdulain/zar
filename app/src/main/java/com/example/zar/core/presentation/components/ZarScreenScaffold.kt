package com.example.zar.core.presentation.components

import com.example.zar.core.presentation.motion.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * The chrome floats over the content: nothing is reserved or painted behind it. [content] receives bottom padding that
 * is only an end clearance (system inset + gap + chrome height + 24dp), so the last item can scroll fully into view above
 * the pill while earlier items scroll behind it. The padding belongs inside the scrolling content, never outside it.
 *
 * The chrome itself is drawn once for the whole signed-in graph by `ZarNavHost` (see [LocalNavChromeHost]), outside the
 * tab pager. Outside that host (previews, component tests) the scaffold overlays the chrome itself.
 */
@Composable
fun ZarScreenScaffold(
    destination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    modifier: Modifier = Modifier,
    heroUnderStatusBar: Boolean = destination == TopLevelDestination.Home,
    chrome: NavChrome = LocalNavSession.current.chromeFor(destination),
    onNewPlan: (() -> Unit)? = null,
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
    // Inside the tab pager the host sets this once for all four tabs, because two pages are on screen mid-swipe.
    DisposableEffect(activity, view, heroUnderStatusBar, isLightTheme, sharedChromeHost) {
        if (!sharedChromeHost) {
            val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
            controller?.isAppearanceLightStatusBars = !heroUnderStatusBar && isLightTheme
        }
        onDispose { }
    }

    val keyboardOpen = rememberKeyboardOpen()
    var addMenuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize().then(if (addMenuOpen) Modifier.clearAndSetSemantics { } else Modifier),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (heroUnderStatusBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            WindowInsets.safeDrawing
        },
    ) { inner ->
        // Preserve side insets as well, so landscape content clears display cutouts. The chrome floats over the content, so
        // the only bottom padding is the end clearance that lets the last item scroll 24dp above the pill (nothing is
        // painted or reserved there); with the keyboard open the chrome is hidden and the clearance is the usual gap.
        content(
            PaddingValues(
                start = inner.calculateStartPadding(layoutDirection),
                end = inner.calculateEndPadding(layoutDirection),
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + if (keyboardOpen) ZarDimens.NavBottomGap else (LocalNavContextActions.current?.clearance ?: 208.dp),
            ),
        )
    }
    if (!sharedChromeHost) {
        ZarTopLevelChrome(
            destination = destination,
            chrome = chrome,
            onNewPlan = onNewPlan,
            onNavigate = onNavigate,
            onAddOption = onAddOption,
            controller = LocalNavCapsuleController.current ?: remember { NavCapsuleController() },
            modifier = Modifier.align(Alignment.BottomCenter),
            onMenuOpenChange = { addMenuOpen = it },
        )
    }
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun ZarAddButton(description: String, modifier: Modifier = Modifier, rotation: () -> Float = { 0f }, onClick: () -> Unit) {
    val colors = ZarTheme.colors
    val press = remember { MutableInteractionSource() }
    val focused by press.collectIsFocusedAsState()
    FloatingActionButton(
        onClick = onClick,
        interactionSource = press,
        modifier = modifier
            .drawBehind {
                if (focused) drawCircle(colors.borderSelected, radius = size.minDimension / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            .pressScale(press)
            .size(ZarDimens.AddButtonSize)
            .testTag("add-entry")
            .semantics { contentDescription = description },
        shape = CircleShape,
        containerColor = colors.accentPrimary,
        contentColor = colors.textOnInverse,
        elevation = FloatingActionButtonDefaults.elevation(4.dp, 4.dp, 4.dp, 4.dp),
    ) {
        PlusIcon(tint = colors.textOnInverse, modifier = Modifier.graphicsLayer { rotationZ = rotation() })
    }
}
