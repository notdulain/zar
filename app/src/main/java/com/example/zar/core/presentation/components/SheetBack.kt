package com.example.zar.core.presentation.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogWindowProvider
import com.example.zar.R
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionLevel
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarSprings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * The state of a sheet that is dismissed by sliding to the right (the logical end), as Back does everywhere in the app.
 * [slide] is the sheet's travel as a fraction of its width and [fade] its opacity; the backdrop never moves, and the scrim
 * fades as the sheet leaves. Reduced-motion Back resolves immediately; a downward swipe may crossfade in at most 100 ms.
 */
@Stable
class SheetBackState internal constructor(
    internal val slide: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    internal val fade: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = Animatable(1f),
    internal val entrance: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = Animatable(0f),
    entryHeightPx: Float = 0f,
    internal val swipeEnabled: Boolean = false,
    internal val swipeScope: CoroutineScope? = null,
) {
    // Window geometry is captured once per opening, so the translation distance stays stable while form data loads.
    internal var entryHeightPx by mutableFloatStateOf(entryHeightPx)
    internal var swipeOffsetPx by mutableFloatStateOf(0f)
    internal var swipeExtentPx = 1f
    internal var swipeSettling by androidx.compose.runtime.mutableStateOf(false)
    internal var swipeJob: kotlinx.coroutines.Job? = null
    internal var closing = false
    internal var onSwipeDismiss: ((Float) -> Unit)? = null

    internal suspend fun settleSwipe(target: Float, velocity: Float, policy: MotionPolicy) {
        swipeSettling = true
        try {
            Animatable(swipeOffsetPx).animateTo(target, policy.spring(ZarSprings.Settle, 0.5f), initialVelocity = velocity) {
                swipeOffsetPx = value.coerceAtLeast(0f)
            }
        } finally { swipeSettling = false }
    }

    /** Automatic IME focus waits for entrance, so keyboard resize and sheet travel do not fight each other. */
    internal suspend fun awaitEntrance() {
        snapshotFlow { entrance.value == 0f && fade.value == 1f }.first { it }
    }
    /** 0 at rest, 1 once the sheet has left. Used by the scrim, so it fades with the sheet whichever way it leaves. */
    internal fun leaving(): Float = maxOf(slide.value, 1f - fade.value).coerceIn(0f, 1f)
}

val LocalSheetBack = compositionLocalOf<SheetBackState?> { null }

/**
 * Hosts a dialog-route sheet whose Back (system button, predictive edge gesture, ✕, Cancel, saved) slides it right.
 * Navigation's `dialog` destinations are separate platform windows whose own exit animation zooms and fades, so this
 * switches that exit animation off and moves the sheet inside the window. With [slideUpOnEnter], entry is also driven
 * inside the window: the sheet rises over a stationary scrim, with no second platform zoom/fade.
 * [content] receives `dismiss`, which plays the slide and then calls [onPop].
 *
 * During a predictive Back gesture the sheet follows at half the gesture's progress; releasing past the threshold springs
 * it the rest of the way out, cancelling springs it back. Android's own exit animation for the root is not involved.
 */
@Composable
fun SheetBackHost(onPop: () -> Unit, slideUpOnEnter: Boolean = false, content: @Composable (dismiss: () -> Unit) -> Unit) {
    val policy = LocalZarMotion.current
    val scope = rememberCoroutineScope()
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val state = remember {
        SheetBackState(
            slide = Animatable(0f),
            fade = Animatable(if (slideUpOnEnter && policy.level == MotionLevel.Reduced) 0f else 1f),
            entrance = Animatable(if (slideUpOnEnter && policy.allowsSpatialMotion) 1f else 0f),
            entryHeightPx = windowHeight,
            swipeEnabled = slideUpOnEnter,
            swipeScope = scope,
        )
    }
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.apply {
            setWindowAnimations(if (slideUpOnEnter) R.style.ZarSheetContentAnimation else R.style.ZarSheetWindowAnimation)
            // The scrim is drawn by the sheet's screen, so it can fade with the slide; the window must not dim again.
            setDimAmount(0f)
        }
        if (state.entryHeightPx == 0f && windowHeight > 0f) state.entryHeightPx = windowHeight
    }
    LaunchedEffect(state, slideUpOnEnter, policy) {
        if (slideUpOnEnter) when (policy.level) {
            MotionLevel.Full -> state.entrance.animateTo(0f, policy.spring(ZarSprings.Route, 0.001f))
            MotionLevel.Reduced -> {
                state.entrance.snapTo(0f)
                state.fade.animateTo(1f, policy.fade(MotionPolicy.REDUCED_FADE_MAX_MILLIS))
            }
            MotionLevel.Instant -> { state.entrance.snapTo(0f); state.fade.snapTo(1f) }
        }
    }

    fun dismiss() {
        if (state.closing) return
        state.closing = true
        state.swipeJob?.cancel()
        scope.leave(state, policy, onPop)
    }

    SideEffect {
        state.onSwipeDismiss = { velocity ->
            if (!state.closing) {
                state.closing = true
                state.swipeJob?.cancel()
                state.swipeJob = scope.launch {
                    when (policy.level) {
                        MotionLevel.Full -> state.settleSwipe(state.swipeExtentPx, velocity, policy)
                        MotionLevel.Reduced -> state.fade.animateTo(0f, policy.fade(MotionPolicy.REDUCED_FADE_MAX_MILLIS))
                        MotionLevel.Instant -> Unit
                    }
                    onPop()
                }
            }
        }
    }

    PredictiveBackHandler(enabled = !state.closing) { events ->
        try {
            state.entrance.stop()
            events.collect { event -> if (policy.allowsSpatialMotion) state.slide.snapTo(PREVIEW_FACTOR * event.progress) }
            dismiss()
        } catch (cancelled: CancellationException) {
            scope.launch { state.slide.animateTo(0f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness)) }
            scope.launch { state.entrance.animateTo(0f, policy.spring(ZarSprings.Route)) }
            throw cancelled
        }
    }

    CompositionLocalProvider(LocalSheetBack provides state) { content(::dismiss) }
}

/** Commits Back rightward, or pops immediately when motion is reduced/off. Shared with tests. */
internal fun CoroutineScope.leave(state: SheetBackState, policy: MotionPolicy, onPop: () -> Unit) {
    launch {
        state.entrance.stop()
        when (policy.level) {
            MotionLevel.Full -> state.slide.animateTo(1f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness))
            MotionLevel.Reduced, MotionLevel.Instant -> Unit
        }
        onPop()
    }
}

/** The sheet follows the predictive gesture at this fraction of its progress, so the user sees where it is going. */
const val PREVIEW_FACTOR = 0.5f

/** Moves the sheet with Back. A no-op outside [SheetBackHost]. Read in the layer phase only. */
fun Modifier.sheetBackSlide(swipeEnabled: Boolean = true): Modifier = composed {
    val state = LocalSheetBack.current ?: return@composed this
    val policy = LocalZarMotion.current
    sheetSwipeToDismiss(state, swipeEnabled).graphicsLayer {
        translationX = state.slide.value * size.width
        translationY = state.entrance.value * (if (state.entryHeightPx > 0f) state.entryHeightPx else size.height) +
            if (policy.allowsSpatialMotion) state.swipeOffsetPx else 0f
        alpha = state.fade.value
    }
}

/** Fades the dialog's scrim as its sheet leaves. A no-op outside [SheetBackHost]. */
fun Modifier.sheetBackScrim(): Modifier = composed {
    val state = LocalSheetBack.current ?: return@composed this
    val policy = LocalZarMotion.current
    graphicsLayer {
        val swipe = if (policy.allowsSpatialMotion) (state.swipeOffsetPx / state.swipeExtentPx).coerceIn(0f, 1f) else 0f
        alpha = (1f - state.leaving()) * (1f - state.entrance.value.coerceIn(0f, 1f)) * (1f - swipe)
    }
}

/**
 * A sub-surface of a Back-hosted sheet (a date or time picker, the new-category sheet) at the bottom of the enclosing Box.
 * Opening shows it at once; Back or Cancel clears [visible] and it leaves toward the physical right with no fade or scale.
 * Keep whatever it displays alive until the exit ends, and compose it after (above) the surface it covers. With motion
 * reduced or off it disappears immediately.
 */
@Composable
fun BoxScope.SheetSubSurface(visible: Boolean, content: @Composable () -> Unit) {
    val policy = LocalZarMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = EnterTransition.None,
        exit = if (policy.allowsSpatialMotion) {
            slideOutHorizontally(spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness)) { it }
        } else {
            ExitTransition.None
        },
    ) { content() }
}
