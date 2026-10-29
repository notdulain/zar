package com.example.zar.core.presentation.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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

/**
 * The state of a sheet that is dismissed by sliding to the right (the logical end), as Back does everywhere in the app.
 * [slide] is the sheet's travel as a fraction of its width and [fade] its opacity; the backdrop never moves, and the scrim
 * fades as the sheet leaves. Reduced motion never translates: the sheet crossfades out in at most 100 ms.
 */
@Stable
class SheetBackState internal constructor(
    internal val slide: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    internal val fade: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) {
    /** 0 at rest, 1 once the sheet has left. Used by the scrim, so it fades with the sheet whichever way it leaves. */
    internal fun leaving(): Float = maxOf(slide.value, 1f - fade.value).coerceIn(0f, 1f)
}

val LocalSheetBack = compositionLocalOf<SheetBackState?> { null }

/**
 * Hosts a dialog-route sheet whose Back (system button, predictive edge gesture, ✕, Cancel, saved) slides it right.
 * Navigation's `dialog` destinations are separate platform windows whose own exit animation zooms and fades, so this
 * switches that exit animation off, keeps the platform's enter animation as it was, and moves the sheet inside the window
 * instead. [content] receives `dismiss`, which plays the slide and then calls [onPop].
 *
 * During a predictive Back gesture the sheet follows at half the gesture's progress; releasing past the threshold springs
 * it the rest of the way out, cancelling springs it back. Android's own exit animation for the root is not involved.
 */
@Composable
fun SheetBackHost(onPop: () -> Unit, content: @Composable (dismiss: () -> Unit) -> Unit) {
    val policy = LocalZarMotion.current
    val scope = rememberCoroutineScope()
    val state = remember { SheetBackState(Animatable(0f), Animatable(1f)) }
    val dismissing = remember { booleanArrayOf(false) }
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.apply {
            // Enter animation as before; no exit animation, so no zoom or fade happens when Back dismisses the sheet.
            setWindowAnimations(R.style.ZarSheetWindowAnimation)
            // The scrim is drawn by the sheet's screen, so it can fade with the slide; the window must not dim again.
            setDimAmount(0f)
        }
    }

    fun dismiss() {
        if (dismissing[0]) return
        dismissing[0] = true
        scope.leave(state, policy, onPop)
    }

    PredictiveBackHandler(enabled = !dismissing[0]) { events ->
        try {
            events.collect { event -> if (policy.allowsSpatialMotion) state.slide.snapTo(PREVIEW_FACTOR * event.progress) }
            dismiss()
        } catch (cancelled: CancellationException) {
            scope.launch { state.slide.animateTo(0f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness)) }
            throw cancelled
        }
    }

    CompositionLocalProvider(LocalSheetBack provides state) { content(::dismiss) }
}

/** Commits the slide (or the reduced-motion crossfade) and then pops. Shared with tests. */
internal fun CoroutineScope.leave(state: SheetBackState, policy: MotionPolicy, onPop: () -> Unit) {
    launch {
        when (policy.level) {
            MotionLevel.Full -> state.slide.animateTo(1f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness))
            MotionLevel.Reduced -> state.fade.animateTo(0f, tween(MotionPolicy.REDUCED_FADE_MAX_MILLIS))
            MotionLevel.Instant -> Unit
        }
        onPop()
    }
}

/** The sheet follows the predictive gesture at this fraction of its progress, so the user sees where it is going. */
const val PREVIEW_FACTOR = 0.5f

/** Moves the sheet with Back. A no-op outside [SheetBackHost]. Read in the layer phase only. */
fun Modifier.sheetBackSlide(): Modifier = composed {
    val state = LocalSheetBack.current ?: return@composed this
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    graphicsLayer {
        translationX = state.slide.value * size.width * if (rtl) -1f else 1f
        alpha = state.fade.value
    }
}

/** Fades the dialog's scrim as its sheet leaves. A no-op outside [SheetBackHost]. */
fun Modifier.sheetBackScrim(): Modifier = composed {
    val state = LocalSheetBack.current ?: return@composed this
    graphicsLayer { alpha = 1f - state.leaving() }
}
