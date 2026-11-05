package com.example.zar.core.presentation.components

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.LayoutCoordinates
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import com.example.zar.core.presentation.motion.MotionLevel
import com.example.zar.core.presentation.motion.ZarSprings
import com.example.zar.core.presentation.theme.ZarTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
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
@Stable
class NavContextActions {
    var newPlan: (() -> Unit)? by mutableStateOf(null)
    var clearance: Dp by mutableStateOf(ZarDimens.ChromeClearance)
}
val LocalNavContextActions = compositionLocalOf<NavContextActions?> { null }

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
    onNewPlan: (() -> Unit)? = LocalNavContextActions.current?.newPlan,
    enabled: Boolean = true,
    pageMoving: () -> Boolean = { false },
    onMenuOpenChange: (Boolean) -> Unit = {},
) {
    val policy = LocalZarMotion.current
    val density = LocalDensity.current
    val contextActions = LocalNavContextActions.current
    val colors = ZarTheme.colors
    val keyboardOpen = rememberKeyboardOpen()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val addDescription = stringResource(R.string.add_entry)
    val closeDescription = stringResource(R.string.add_menu_close)
    val expandedState = stringResource(R.string.add_menu_expanded)
    val collapsedState = stringResource(R.string.add_menu_collapsed)
    val newPlanLabel = stringResource(R.string.plans_new_plan)
    val onPlans = chrome != NavChrome.AddOnly && destination == TopLevelDestination.Plans
    val canCreatePlan = enabled && onNewPlan != null
    val newPlanStretch = rememberNewPlanStretch(onPlans, enabled, pageMoving)

    BoxWithConstraints(modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        // Replacing these remembered values snaps closed before drawing at any new window/IME geometry.
        var open by remember(maxWidth, maxHeight, keyboardOpen) { mutableStateOf(false) }
        val progress = remember(maxWidth, maxHeight, keyboardOpen) { Animatable(0f) }
        // Written only while the menu is open: the button is resized by the stretch every frame, which must not recompose this.
        var anchor by remember { mutableStateOf(Rect.Zero) }
        val anchorCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        val focus = remember { FocusRequester() }
        SideEffect { onMenuOpenChange(open) }
        DisposableEffect(Unit) { onDispose { onMenuOpenChange(false) } }
        LaunchedEffect(destination, enabled) { open = false; onMenuOpenChange(false) }
        LaunchedEffect(progress, policy) {
            snapshotFlow { open }.collect { expanded ->
                // Let animateTo interrupt its predecessor itself: its default initial velocity is read BEFORE cancellation.
                launch {
                    val target = if (expanded) 1f else 0f
                    if (policy.level == MotionLevel.Instant) progress.snapTo(target)
                    else progress.animateTo(
                        target,
                        if (policy.level == MotionLevel.Reduced) policy.fade(100)
                        else policy.spring(if (expanded) ZarSprings.SpeedDialOpen else ZarSprings.Settle, 0.001f),
                    )
                }
            }
        }
        fun setExpanded(expanded: Boolean) {
            if (expanded) anchorCoordinates[0]?.takeIf { it.isAttached }?.let { anchor = it.boundsInWindow() }
            open = expanded
            onMenuOpenChange(expanded)
        }
        fun close() {
            setExpanded(false)
            focus.requestFocus()
        }
        if (open) key(open) { BackHandler { close() } }
        // Observe only visibility here; spring frames update cached render layers, never the chrome composition.
        val overlayVisible by remember(progress) { derivedStateOf { open || progress.value != 0f } }
        val safeTop = WindowInsets.safeDrawing.getTop(density)
        val row = speedDialUsesRow(with(density) { (anchor.top - safeTop).toDp().value })
        val towardCenter = speedDialRowDirection(
            centered = chrome == NavChrome.AddOnly,
            rtl = rtl,
            anchorCenter = anchor.center.x,
            windowCenter = origin.x + constraints.maxWidth / 2f,
        )

        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            if (overlayVisible) {
                // No click/semantics modifier remains during closing. Consume the entire outside gesture from DOWN.
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) * colors.speedDialScrimOpacity }
                        .then(if (open) Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                close()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        } else Modifier)
                        .background(colors.scrim),
                )
                if (anchor != Rect.Zero) {
                    val halfAction = with(density) { ZarDimens.SpeedDialAction.toPx() / 2 }
                    val x = anchor.center.x - origin.x - halfAction
                    val y = anchor.center.y - origin.y - halfAction
                    for (option in listOf(AddEntryOption.QuickNote, AddEntryOption.Manual)) {
                        SpeedDialAction(
                            option = option,
                            progress = { progress.value },
                            level = policy.level,
                            row = row,
                            towardCenter = towardCenter,
                            interactive = open,
                            onClick = {
                                if (open) {
                                    close()
                                    onAddOption(option)
                                }
                            },
                            modifier = Modifier.absoluteOffset {
                                IntOffset(x.roundToInt(), y.roundToInt())
                            },
                        )
                    }
                }
            }

            if (!keyboardOpen) Box(
                Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { size -> contextActions?.clearance = with(density) { size.height.toDp() } + ZarDimens.NavContentGap }
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(start = ZarDimens.ScreenGutter, end = ZarDimens.ScreenGutter, top = ZarDimens.SpaceMd, bottom = ZarDimens.NavBottomGap),
                contentAlignment = Alignment.Center,
            ) {
                val anchorModifier = Modifier
                    .focusRequester(focus)
                    .onGloballyPositioned {
                        anchorCoordinates[0] = it
                        if (open) anchor = it.boundsInWindow()
                    }
                    .graphicsLayer {
                        // Jelly: the button flattens while it stretches fast and recovers as it settles.
                        scaleY = 1f - (abs(newPlanStretch.velocity) * ZarMotion.ADD_BUTTON_SQUASH_PER_SPEED).coerceAtMost(ZarMotion.ADD_BUTTON_MAX_SQUASH)
                        // Unavailable on Plans reads dim only as the pill, so a tap's arrival never flashes the plus.
                        alpha = if (onPlans && !canCreatePlan) 1f - 0.5f * newPlanStretch.value.coerceIn(0f, 1f) else 1f
                    }
                    .semantics {
                        traversalIndex = 0f
                        if (!onPlans) {
                            stateDescription = if (open) expandedState else collapsedState
                            if (open) collapse { close(); true } else expand { if (enabled) setExpanded(true); enabled }
                        }
                    }
                val addButton: @Composable () -> Unit = {
                    ZarAddButton(
                        description = if (onPlans) newPlanLabel else if (open) closeDescription else addDescription,
                        label = newPlanLabel,
                        modifier = anchorModifier,
                        tag = if (onPlans) "plans-new-plan" else "add-entry",
                        rotation = { speedDialRotation(progress.value, policy.level, open) },
                        stretch = { newPlanStretch.value },
                        onClick = {
                            when {
                                onPlans -> if (canCreatePlan) onNewPlan?.invoke()
                                open -> close()
                                enabled -> setExpanded(true)
                            }
                        },
                    )
                }
                if (chrome == NavChrome.AddOnly) addButton()
                else {
                    val animateReveal = !controller.revealed
                    val reveal = remember { MutableTransitionState(!animateReveal) }
                    LaunchedEffect(Unit) { reveal.targetState = true }
                    val rise = with(density) { policy.travel(ZarMotion.NavRevealRise).roundToPx() }
                    AnimatedVisibility(
                        visibleState = reveal,
                        enter = fadeIn(policy.fade<Float>(ZarMotion.NAV_REVEAL_MILLIS)) +
                            slideInVertically(policy.fade<IntOffset>(ZarMotion.NAV_REVEAL_MILLIS)) { rise },
                    ) {
                        // Figma "Floating Nav": the tab bar and the separate action button share one row, 8dp apart. The bar
                        // keeps whatever width the action leaves, up to the 293dp of the Home variant. On Plans the action
                        // stretches into the "New plan" button, so the bar gives way as it does.
                        Row(horizontalArrangement = Arrangement.spacedBy(ZarDimens.SpaceXs), verticalAlignment = Alignment.CenterVertically) {
                            ZarBottomNav(
                                selected = destination,
                                onNavigate = { setExpanded(false); onNavigate(it) },
                                controller = controller,
                                driveCapsule = driveCapsule,
                                modifier = Modifier.weight(1f, fill = false).widthIn(max = 293.dp)
                                    .semantics { traversalIndex = 3f; isTraversalGroup = true },
                            )
                            addButton()
                        }
                    }
                }
            }
        }
    }
}

/**
 * How far the Add button has stretched into "New plan". It stays the plus until Plans has been still for
 * [ZarMotion.NEW_PLAN_DWELL_MILLIS]: any page movement or covering screen restarts the wait, so swiping or tapping through
 * Plans never reshapes the row mid-transition. Leaving Plans pulls it back as soon as the page has landed, for the same
 * reason. Reduced and Instant change directly.
 */
@Composable
private fun rememberNewPlanStretch(
    onPlans: Boolean,
    enabled: Boolean,
    pageMoving: () -> Boolean,
): Animatable<Float, AnimationVector1D> {
    val policy = LocalZarMotion.current
    val stretch = remember { Animatable(0f) }
    var stretched by remember { mutableStateOf(false) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentPageMoving by rememberUpdatedState(pageMoving)
    LaunchedEffect(onPlans) {
        if (!onPlans) {
            snapshotFlow { currentPageMoving() }.first { moving -> !moving }
            stretched = false
            return@LaunchedEffect
        }
        snapshotFlow { currentEnabled && !currentPageMoving() }.collectLatest { still ->
            if (still) {
                delay(ZarMotion.NEW_PLAN_DWELL_MILLIS)
                stretched = true
            }
        }
    }
    LaunchedEffect(stretched, policy.level) {
        val target = if (stretched) 1f else 0f
        if (policy.allowsSpatialMotion) stretch.animateTo(target, policy.spring(ZarSprings.AddButtonStretch, visibilityThreshold = 0.001f))
        else stretch.snapTo(target)
    }
    return stretch
}

/** True while the soft keyboard is showing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberKeyboardOpen(): Boolean = WindowInsets.isImeVisible
