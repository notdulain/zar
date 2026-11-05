package com.example.zar.core.presentation.components

import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionLevel
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.motion.ZarSprings
import kotlinx.coroutines.CancellationException
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
    internal var arrived by mutableStateOf<Int?>(null)

    private var job: Job? = null

    /**
     * Back to Home as a slide: the departing page's travel as a fraction of the width, and which page is departing (-1
     * when no Back is in progress). Home is drawn under it, coming in from 0.2 of the width behind. Read in the layer phase.
     */
    internal val backSlide = Animatable(0f)
    var backFrom by mutableIntStateOf(-1)
        internal set

    internal var tabFrom by mutableIntStateOf(-1)
    internal var tabTo by mutableIntStateOf(-1)
    internal val tabSlide = Animatable(0f)

    /** How many pages beside the current one the host has composed. A Back slide needs Home composed, so it checks this. */
    internal var backWarmReach by mutableIntStateOf(0)
    internal var warmedReach by mutableIntStateOf(0)

    val currentIndex: Int get() = pagerState.currentPage

    private suspend fun prepareBack(from: Int) {
        // Finish any pager travel before applying our independent Back translation.
        // An old arrival fade/offset must never be added to a Back slide.
        tabFrom = -1
        tabTo = -1
        pagerState.scrollToPage(from)
        backSlide.snapTo(0f)
        backWarmReach = maxOf(backWarmReach, from)
        snapshotFlow { warmedReach }.first { it >= from }
        backFrom = from
    }

    internal suspend fun beginBack() {
        val from = selectedIndex
        job?.cancel()
        prepareBack(from)
    }

    internal fun cancelBack() {
        scope.launch {
            backSlide.animateTo(0f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness))
            backFrom = -1
        }
    }

    /**
     * Completes Back to Home. With Home composed and full motion, the current page slides right over Home (which comes in
     * from 0.2 of the width behind) and the pager then switches pages unseen; the capsule springs home meanwhile. Reduced
     * motion switches at once. Home is composed on demand before any departure, so Back never falls back to a tab arrival.
     */
    internal fun commitBack(policy: MotionPolicy) {
        val from = backFrom.takeIf { it >= 0 } ?: selectedIndex
        if (from == 0) return
        job?.cancel()
        arrived = null
        job = scope.launch {
            if (policy.allowsSpatialMotion) {
                if (backFrom != from) prepareBack(from)
                tapTarget = 0
                backSlide.animateTo(1f, spring(ZarSprings.Route.dampingRatio, ZarSprings.Route.stiffness))
                pagerState.scrollToPage(0)
                backFrom = -1
            } else {
                backFrom = -1
                tapTarget = 0
                pagerState.scrollToPage(0)
            }
            arrived = 0
        }
    }

    /** The destination that should read as selected: the tap's target, otherwise the page that has settled. */
    val selectedIndex: Int get() = tapTarget ?: pagerState.settledPage

    val selected: TopLevelDestination get() = TopLevelDestination.entries[selectedIndex.coerceIn(0, TopLevelDestination.entries.size - 1)]

    /** True while any page is travelling: a finger or fling, a tap's slide (which is not a pager scroll), or a Back slide. */
    val isMoving: Boolean get() = tapTarget != null || backFrom >= 0 || pagerState.isScrollInProgress

    /**
     * Brings [destination] on screen with the same push/pop for every tab distance.
     * Only the source and destination are drawn during travel. Reduced motion resolves instantly.
     */
    fun select(destination: TopLevelDestination, policy: MotionPolicy) = select(destination.ordinal, policy)

    fun select(index: Int, policy: MotionPolicy) {
        val target = index.coerceIn(0, TopLevelDestination.entries.size - 1)
        val from = pagerState.currentPage
        if (target == from && tapTarget == null && pagerState.currentPageOffsetFraction == 0f) return
        tapTarget = target
        arrived = null
        job?.cancel()
        backFrom = -1
        tabFrom = -1
        job = scope.launch {
            pagerState.scrollToPage(from)
            if (policy.allowsSpatialMotion && target != from) {
                // Keep exactly the source and destination visible, regardless of tab distance.
                // Compose the destination before the first animated frame; intermediate tabs
                // may be warmed but never appear during this transition.
                backWarmReach = maxOf(backWarmReach, abs(target - from))
                snapshotFlow { warmedReach }.first { it >= abs(target - from) }
                tabSlide.snapTo(0f)
                tabTo = target
                tabFrom = from
                tabSlide.animateTo(1f, tween(ZarMotion.POP_MILLIS))
                pagerState.scrollToPage(target)
                tabFrom = -1
                tabTo = -1
            } else {
                pagerState.scrollToPage(target)
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
    val composedReach = maxOf(warm.pageCount, controller.backWarmReach)
    SideEffect { controller.warmedReach = composedReach }
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
        beyondViewportPageCount = composedReach,
        userScrollEnabled = userScrollEnabled && controller.backFrom < 0 && controller.tabFrom < 0,
        key = { it },
        flingBehavior = fling,
    ) { index ->
        val pageIndex = index
        Box(
            Modifier.fillMaxSize()
                .zIndex(if (controller.tabFrom >= 0 && index == maxOf(controller.tabFrom, controller.tabTo)) 1f else 0f)
                .graphicsLayer {
                val tabSource = controller.tabFrom
                if (tabSource >= 0) {
                    val target = controller.tabTo
                    val f = controller.tabSlide.value
                    val w = size.width
                    val laidOutAt = (index - controller.pagerState.currentPage) * w * if (rtl) -1f else 1f
                    val forward = target > tabSource
                    val wantedAt = when (index) {
                        tabSource -> if (forward) -ZarMotion.ROUTE_PARENT_RECEDE * f * w else f * w
                        target -> if (forward) (1f - f) * w else -ZarMotion.ROUTE_PARENT_RECEDE * (1f - f) * w
                        else -> laidOutAt
                    }
                    translationX = wantedAt - laidOutAt
                    alpha = if (index == tabSource || index == target) 1f else 0f
                }
                val departing = controller.backFrom
                if (departing >= 0) {
                    // Back to Home: the departing page slides right over Home, which comes in from 0.2 of the width behind.
                    // Positions are stated as where the page should be, minus where the pager laid it out, so the same
                    // numbers hold before, during and after the pager switches its page under the slide.
                    val f = controller.backSlide.value
                    val w = size.width
                    val dir = if (rtl) -1f else 1f
                    val laidOutAt = (pageIndex - controller.pagerState.currentPage) * w * dir
                    val wantedAt = when (pageIndex) {
                        departing -> f * w
                        0 -> -ZarMotion.ROUTE_PARENT_RECEDE * (1f - f) * w
                        else -> laidOutAt
                    }
                    translationX = wantedAt - laidOutAt
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
 * Back from Activity, Plans or More goes to Home the way every other Back goes: the page leaves to the right (a predictive
 * gesture drags it at half its progress, a button or a committed gesture springs it out) while Home comes in from 0.2 of the
 * width behind. At Home Back is not handled here, so Android's own exit animation is preserved.
 */
@Composable
fun TabPagerBackHandler(controller: TabPagerController, enabled: Boolean) {
    val policy = LocalZarMotion.current
    PredictiveBackHandler(enabled = enabled && controller.selectedIndex != TopLevelDestination.Home.ordinal) { events ->
        val slide = policy.allowsSpatialMotion && controller.selectedIndex > 0
        try {
            if (slide) {
                controller.beginBack()
                events.collect { controller.backSlide.snapTo(PREVIEW_FACTOR * it.progress) }
            } else {
                events.collect { }
            }
            controller.commitBack(policy)
        } catch (cancelled: CancellationException) {
            if (slide) controller.cancelBack()
            throw cancelled
        }
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
