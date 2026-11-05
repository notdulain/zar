package com.example.zar.core.presentation.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A card's first arrival: it fades in while rising [ZarMotion.RiseInTravel], a little after the one before it ([order]).
 * Only the first [ZarMotion.RISE_IN_MAX_ORDER] wait their turn, so a long list never keeps anything waiting. With [play]
 * false (e.g. coming back to a screen) or reduced motion, the card is simply there. Drawn in the layer phase only, so
 * layout, semantics and touch targets are never affected.
 */
@Composable
fun Modifier.riseIn(order: Int, play: Boolean): Modifier {
    val policy = LocalZarMotion.current
    if (!play || !policy.allowsSpatialMotion) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order.coerceIn(0, ZarMotion.RISE_IN_MAX_ORDER) * ZarMotion.RISE_IN_STAGGER_MILLIS)
        progress.animateTo(1f, tween(ZarMotion.RISE_IN_MILLIS, easing = FastOutSlowInEasing))
    }
    val travel = with(LocalDensity.current) { ZarMotion.RiseInTravel.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * travel
    }
}

/**
 * Whether a screen's cards should rise in: true on its first visit, false when coming back to it (the answer is saved
 * with the screen, so returning from a detail page or switching tabs shows the cards in place). [loaded] is the moment
 * the cards first appear; until then a loading state may show without spending the animation.
 */
@Composable
fun rememberRiseInOnFirstVisit(loaded: Boolean): Boolean {
    var firstVisit by rememberSaveable { mutableStateOf(true) }
    val play = remember { firstVisit }
    LaunchedEffect(loaded) { if (loaded) firstVisit = false }
    return play
}

/**
 * The same as [rememberRiseInOnFirstVisit], for lazy lists. A lazy item is composed again whenever it scrolls back into
 * view, so the rise-in is only offered while the first cards are arriving; anything composed after that window, by
 * scrolling, is simply there.
 */
@Composable
fun rememberRiseInForLazyList(loaded: Boolean): Boolean {
    val play = rememberRiseInOnFirstVisit(loaded)
    var open by remember { mutableStateOf(play) }
    LaunchedEffect(loaded) {
        if (loaded && open) {
            delay(ZarMotion.RISE_IN_MAX_ORDER * ZarMotion.RISE_IN_STAGGER_MILLIS + ZarMotion.RISE_IN_MILLIS + 100L)
            open = false
        }
    }
    return open
}
