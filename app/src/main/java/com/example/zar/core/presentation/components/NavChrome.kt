package com.example.zar.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.zar.R
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.theme.ZarDimens

/**
 * True while a shared chrome host (see `ZarNavHost`) is drawing the bottom navbar outside the content NavHost and its
 * pager. The chrome floats over the page content: a screen's [ZarScreenScaffold] reserves nothing for it and only pads the
 * end of its content so the last item can scroll clear. Outside the host (previews, component tests) the scaffold overlays
 * the chrome itself.
 */
val LocalNavChromeHost = compositionLocalOf { false }

/**
 * The retained selected-capsule mover for the signed-in session. It is provided once beside [LocalNavSession] so it
 * survives every top-level route composition; when absent (previews, component tests) the caller remembers its own.
 */
val LocalNavCapsuleController = compositionLocalOf<NavCapsuleController?> { null }

/**
 * The one bottom chrome for the signed-in graph, drawn by `ZarNavHost` above the content NavHost. [chrome] is the
 * session's [NavChrome] for the current top-level [destination].
 */
@Composable
fun ZarTopLevelChrome(
    destination: TopLevelDestination,
    chrome: NavChrome,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddOption: (AddEntryOption) -> Unit,
    controller: NavCapsuleController,
    modifier: Modifier = Modifier,
    driveCapsule: Boolean = true,
) {
    val policy = LocalZarMotion.current
    val density = LocalDensity.current
    val addDescription = stringResource(R.string.add_entry)
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    // Figma Z190: with the keyboard open the navbar and Add button step out of the way, so the field being typed in and
    // the results above it keep the room. The capsule controller outlives this, so the navbar returns where it was.
    val keyboardOpen = rememberKeyboardOpen()

    if (!keyboardOpen) Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(
                start = ZarDimens.ScreenGutter,
                end = ZarDimens.ScreenGutter,
                top = ZarDimens.SpaceMd,
                bottom = ZarDimens.NavBottomGap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (chrome == NavChrome.AddOnly) {
                ZarAddButton(addDescription) { showAddSheet = true }
            } else {
                // The full pill is revealed only once per session: the first time it mounts with an unmeasured capsule.
                val animateReveal = !controller.revealed
                val reveal = remember { MutableTransitionState(!animateReveal) }
                LaunchedEffect(Unit) { reveal.targetState = true }
                val rise = with(density) { policy.travel(ZarMotion.NavRevealRise).roundToPx() }
                AnimatedVisibility(
                    visibleState = reveal,
                    enter = fadeIn(policy.fade<Float>(ZarMotion.NAV_REVEAL_MILLIS)) +
                        slideInVertically(policy.fade<IntOffset>(ZarMotion.NAV_REVEAL_MILLIS)) { rise },
                ) {
                    // Figma's floating group is 293 + 8 + 68 = 369dp. Keep it centred and let the tab bar contract.
                    val navWidth = (maxWidth - ZarDimens.AddButtonSize - ZarDimens.SpaceXs).coerceAtMost(293.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZarBottomNav(
                            selected = destination,
                            onNavigate = onNavigate,
                            controller = controller,
                            driveCapsule = driveCapsule,
                            modifier = Modifier.width(navWidth),
                        )
                        ZarAddButton(addDescription) { showAddSheet = true }
                    }
                }
            }
        }
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

/** True while the soft keyboard is showing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberKeyboardOpen(): Boolean = WindowInsets.isImeVisible
