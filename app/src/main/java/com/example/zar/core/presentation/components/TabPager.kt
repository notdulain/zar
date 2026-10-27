package com.example.zar.core.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionLevel
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The four tabs as pages of one [HorizontalPager]. It owns the three things the navbar and the host need to agree on:
 * the pager itself, which tab a tap asked for while the page is still travelling there ([tapTarget]), and the arrival
 * effect of a long jump. The bottom chrome sits outside the pager and never moves during a swipe.
 *
 * Selection semantics follow [selectedIndex]: the tapped target while a tap is in flight, otherwise the settled page.
 * The capsule follows the pager one for one while the finger drags it or while a swipe settles (see [DriveCapsule]);
 * a tap springs the capsule on its own.
 */
@Stable
class TabPagerController internal constructor(
    val pagerState: PagerState,
    private val scope: CoroutineScope,
) {
    /** The tab a tap is moving to, until both the page and the capsule have arrived. Null while the finger or a swipe drives. */
    var tapTarget by mutableStateOf<Int?>(null)
        internal set

    /** The tab whose page and capsule have both arrived after the latest tap. */
    private var arrived by mutableStateOf<Int?>(null)

    /** Incremented for each long jump or reduced/instant tap, so the incoming page can play its arrival effect once. */
    var arrivalNonce by mutableIntStateOf(0)
        private set
    var arrivalTarget by mutableIntStateOf(-1)
        private set

    /** +1 when the arrival comes from the logical end (a jump toward the end of the tab order), -1 from the start. */
    var arrivalDirection by mutableIntStateOf(1)
        private set
    var arrivalFadeOnly by mutableStateOf(false)
        private set

    private var job: Job? = null

    val currentIndex: Int get() = pagerState.currentPage

    /** The destination that should read as selected: the tap's target, otherwise the page that has settled. */
    val selectedIndex: Int get() = tapTarget ?: pagerState.settledPage

    val selected: TopLevelDestination get() = TopLevelDestination.entries[selectedIndex.coerceIn(0, TopLevelDestination.entries.size - 1)]

    /**
     * Brings [destination] on screen. An adjacent tab animates with the Route spring; a jump over one or more pages
     * lands at once and the incoming page plays the short TabTravel slide and fade, so no middle page ever flashes by.
     * Reduced motion switches at once with a crossfade of at most 100 ms; with system animations off nothing plays.
     */
    fun select(destination: TopLevelDestination, policy: MotionPolicy) = select(destination.ordinal, policy)

    fun select(index: Int, policy: MotionPolicy) {
        val target = index.coerceIn(0, TopLevelDestination.entries.size - 1)
        val from = pagerState.currentPage
        if (target == from && tapTarget == null && pagerState.currentPageOffsetFraction == 0f) return
        tapTarget = target
        arrived = null
        job?.cancel()
        job = scope.launch {
            val distance = abs(target - from)
            when {
                policy.level == MotionLevel.Full && distance == 1 && pagerState.currentPageOffsetFraction == 0f ->
                    pagerState.animateScrollToPage(target, animationSpec = spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness))
                else -> {
                    pagerState.scrollToPage(target)
                    if (policy.level != MotionLevel.Instant && target != from) {
                        arrivalDirection = if (target >= from) 1 else -1
                        arrivalFadeOnly = policy.level == MotionLevel.Reduced
                        arrivalTarget = target
                        arrivalNonce += 1
                    }
                }
            }
            arrived = target
        }
    }

    internal suspend fun awaitArrival(target: Int) {
        snapshotFlow { arrived }.first { it == target }
    }

    internal fun release(target: Int) {
        if (tapTarget == target) tapTarget = null
    }
}

@Composable
fun rememberTabPagerController(initial: TopLevelDestination = TopLevelDestination.Home): TabPagerController {
    val pagerState = rememberPagerState(initialPage = initial.ordinal) { TopLevelDestination.entries.size }
    val scope = rememberCoroutineScope()
    return remember(pagerState, scope) { TabPagerController(pagerState, scope) }
}

/**
 * Keeps [capsule] in step with the pager. While a finger drags the pager, or the pager settles from a swipe, the capsule
 * is placed at `currentPage + currentPageOffsetFraction` every frame: one for one, no spring, no lag. A tap instead
 * springs the capsule to the tapped slot (or snaps it under reduced motion), and the pager release hands back to
 * one-for-one tracking once both have arrived. Grabbing the pager during a tap's flight takes over immediately.
 */
@Composable
fun TabPagerController.DriveCapsule(capsule: NavCapsuleController) {
    val policy = LocalZarMotion.current
    val pager = pagerState
    LaunchedEffect(pager, capsule) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
            if (tapTarget == null) {
                capsule.select(pager.currentPage + if (pager.currentPageOffsetFraction > 0.5f) 1 else 0)
                capsule.dragTo(position)
            }
        }
    }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) tapTarget = null }
    }
    LaunchedEffect(pager, capsule, policy) {
        snapshotFlow { tapTarget }.collectLatest { target ->
            if (target == null) return@collectLatest
            capsule.select(target)
            if (policy.allowsSpatialMotion) {
                capsule.moveTo(target, policy.spring(ZarSprings.NavSelection, SLOT_THRESHOLD))
            } else {
                capsule.snapTo(target.toFloat())
            }
            awaitArrival(target)
            release(target)
        }
    }
}

private const val SLOT_THRESHOLD = 0.0005f

/**
 * Lets pages be dragged horizontally between the four tabs. The page content decides its own vertical scrolling; the
 * pager claims a gesture only when the platform's drag axis lock says it is horizontal. [userScrollEnabled] is false
 * while a sheet or dialog is open, while the keyboard is up and while the Home chart is being scrubbed.
 * System edge gestures are never excluded, so an edge swipe always belongs to Android Back.
 */
@Composable
fun TabPagerHost(
    controller: TabPagerController,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    warmUpPages: Boolean = true,
    page: @Composable (TopLevelDestination) -> Unit,
) {
    val policy = LocalZarMotion.current
    val warm = rememberPageWarmUp(enabled = warmUpPages) { userScrollEnabled && !controller.pagerState.isScrollInProgress }
    val tabTravelPx = with(LocalDensity.current) { ZarMotion.TabTravel.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val snapSpec = if (policy.allowsSpatialMotion) {
        spring<Float>(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness)
    } else {
        // Reduced motion: a swipe still tracks the finger, but its release settles with a short fade-time tween, never a spring.
        tween(MotionPolicy.REDUCED_FADE_MAX_MILLIS)
    }
    val fling = PagerDefaults.flingBehavior(
        state = controller.pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = snapSpec,
    )
    HorizontalPager(
        state = controller.pagerState,
        modifier = modifier.fillMaxSize().then(warm.touchObserver),
        beyondViewportPageCount = warm.pageCount,
        userScrollEnabled = userScrollEnabled,
        key = { it },
        flingBehavior = fling,
    ) { index ->
        val arrival = remember { Animatable(1f) }
        LaunchedEffect(controller.arrivalNonce) {
            if (controller.arrivalNonce > 0 && controller.arrivalTarget == index) {
                arrival.snapTo(0f)
                arrival.animateTo(1f, tween(if (controller.arrivalFadeOnly) MotionPolicy.REDUCED_FADE_MAX_MILLIS else ZarMotion.TAB_ARRIVAL_MILLIS))
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val remaining = 1f - arrival.value
                if (remaining > 0f) {
                    alpha = arrival.value
                    if (!controller.arrivalFadeOnly) {
                        // The page arrives from the logical end of the tab order for a jump toward the end (mirrored in RTL).
                        translationX = remaining * tabTravelPx * controller.arrivalDirection * if (rtl) -1f else 1f
                    }
                }
            },
        ) {
            page(TopLevelDestination.entries[index])
        }
    }
}

/**
 * Idle-time warm-up of the pages beside the current one. A pager composes the incoming page inside the frame in which a
 * drag first moves it (measured on the Pixel 6a: one 55-130 ms frame at the start of every swipe, even in a release-like
 * build, from composing and laying out one page of text). Composing the neighbours before the finger arrives moves that
 * work to a moment nobody is interacting. [pageCount] grows by one at a time, only while no finger is down, nothing is
 * scrolling and no sheet or keyboard has the screen, until every tab is composed; each step is one idle-time composition,
 * not a hitch under a gesture. Pages keep their own scroll state either way.
 */
@Stable
internal class PageWarmUp(val touchObserver: Modifier) {
    var pageCount by mutableIntStateOf(0)
        internal set
}

private class TouchClock {
    var pressed by mutableStateOf(false)

    /** Counts pointer events, so a tap that comes and goes between two looks still counts as a disturbance. */
    var events by mutableIntStateOf(0)
}

@Composable
internal fun rememberPageWarmUp(enabled: Boolean, idleGate: () -> Boolean): PageWarmUp {
    val clock = remember { TouchClock() }
    val gate = rememberUpdatedState(idleGate)
    val observer = remember(clock) {
        Modifier.pointerInput(clock) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    clock.pressed = event.changes.any { it.pressed }
                    clock.events += 1
                }
            }
        }
    }
    val warm = remember(observer) { PageWarmUp(observer) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val calm = { gate.value() && !clock.pressed }
        while (warm.pageCount < TopLevelDestination.entries.size - 1) {
            // Wait (without asking for frames) until nothing has the screen, then for a quiet stretch with no touch.
            snapshotFlow { calm() }.first { it }
            val seen = clock.events
            val disturbed = withTimeoutOrNull(WARM_IDLE_MILLIS) {
                snapshotFlow { calm() to clock.events }.first { (isCalm, count) -> !isCalm || count != seen }
            }
            if (disturbed == null) warm.pageCount += 1
        }
    }
    return warm
}

private const val WARM_IDLE_MILLIS = 600L

/**
 * Back from Activity, Plans or More goes to Home with a pager scroll, so the current page leaves toward the end and Home
 * returns. At Home Back is not handled here, so Android's own exit animation is preserved.
 */
@Composable
fun TabPagerBackHandler(controller: TabPagerController, enabled: Boolean) {
    val policy = LocalZarMotion.current
    BackHandler(enabled = enabled && controller.selectedIndex != TopLevelDestination.Home.ordinal) {
        controller.select(TopLevelDestination.Home, policy)
    }
}

/** True while something inside a page is using horizontal drags itself (the Home chart's scrub), so the pager must not page. */
@Stable
class PagerGestureLock {
    private var count by mutableIntStateOf(0)
    val locked: Boolean get() = count > 0
    fun acquire() { count += 1 }
    fun release() { count = (count - 1).coerceAtLeast(0) }
}

val LocalPagerGestureLock = androidx.compose.runtime.staticCompositionLocalOf { PagerGestureLock() }

/**
 * Marks a horizontal control (chip row, month picker, chart switcher, any LazyRow) as the owner of horizontal drags that
 * start in it. When the control reaches either end, the leftover drag and fling would otherwise reach the tab pager in
 * the same gesture and page the tab; this consumes them. Place it before the control's own scroll modifier.
 */
fun Modifier.ownHorizontalDrags(): Modifier = nestedScroll(OwnHorizontalDrags)

private object OwnHorizontalDrags : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)
}
