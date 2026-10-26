package com.example.zar.core.presentation.motion

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * Lets a section grow or shrink to its new content with the shared `settle` spring (critically damped, no bounce), for
 * a state change the user caused such as expanding "All categories". Put it on the section that changes, never on a
 * scrolling list or a keyboard-inset root. It animates the size only: the content, focus and any draft are untouched, and
 * a changed target retargets from the current size. Reduced motion and disabled animations change the size at once.
 */
@Composable
fun Modifier.settleSize(): Modifier {
    val policy = LocalZarMotion.current
    return if (policy.allowsSpatialMotion) {
        this.animateContentSize(policy.spring(ZarSprings.Settle, IntSize(1, 1)))
    } else {
        this
    }
}

/**
 * Immediate press feedback for a non-text action: it shrinks to [MotionPolicy.PRESSED_SCALE] while pressed and springs
 * back on release or when the touch is dragged away (the press ends, so the click is cancelled with it). No bounce and no
 * haptic. Reduced motion leaves the control still. Hit testing follows the drawn size, which is 2% smaller at most.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val policy = LocalZarMotion.current
    if (!policy.allowsSpatialMotion) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) policy.pressScale else 1f,
        animationSpec = policy.spring(ZarSprings.Settle),
        label = "press scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
