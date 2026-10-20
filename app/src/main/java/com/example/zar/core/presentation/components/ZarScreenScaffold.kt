package com.example.zar.core.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.zar.R
import com.example.zar.core.presentation.theme.ZarDimens
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.presentation.theme.ZarLightColors

/**
 * Layout shared by the four main screens.
 *
 * - [TopLevelDestination.Home]: no navbar, Add button centred at the bottom.
 * - Everything else: pill navbar and Add button share one floating row.
 *
 * [content] receives padding that already clears the navbar and the Add button, so lists can
 * scroll fully into view.
 */
@Composable
fun ZarScreenScaffold(
    destination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val isHome = destination == TopLevelDestination.Home
    val addDescription = stringResource(R.string.add_entry)
    val layoutDirection = LocalLayoutDirection.current
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    val isLightTheme = ZarTheme.colors.canvas == ZarLightColors.canvas

    // The Home hero extends under the status bar, so its system glyphs must be white.
    DisposableEffect(activity, view, isHome, isLightTheme) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !isHome && isLightTheme
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (isHome) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            WindowInsets.safeDrawing
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = ZarDimens.ScreenGutter, vertical = ZarDimens.SpaceMd),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isHome) {
                        ZarAddButton(addDescription) { showAddSheet = true }
                    } else {
                        // Figma's floating group is 293 + 8 + 68 = 369dp. Keep it
                        // centered and let the tab bar contract on narrow screens.
                        val navWidth = (maxWidth - ZarDimens.AddButtonSize - ZarDimens.SpaceXs)
                            .coerceAtMost(293.dp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ZarBottomNav(
                                selected = destination,
                                onNavigate = onNavigate,
                                modifier = Modifier.width(navWidth),
                            )
                            ZarAddButton(addDescription) { showAddSheet = true }
                        }
                    }
                }
            }
        },
    ) { inner ->
        // Preserve side insets as well, so landscape content clears display cutouts.
        content(
            PaddingValues(
                start = inner.calculateStartPadding(layoutDirection),
                end = inner.calculateEndPadding(layoutDirection),
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + ZarDimens.SpaceMd,
            ),
        )
    }

    if (showAddSheet) {
        AddEntrySheet(
            onDismiss = { showAddSheet = false },
            onOption = { option ->
                showAddSheet = false
                onAddOption(option)
            },
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ZarAddButton(description: String, onClick: () -> Unit) {
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
