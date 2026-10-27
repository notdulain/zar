package com.example.zar.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarMotion
import com.example.zar.core.presentation.motion.ZarSprings

/**
 * The four route transitions both navigation graphs use, so signed-in and signed-out screens move by one policy.
 * [NavRouteMotion] decides what each change is; this only maps that to Compose transitions with the shared `route`
 * spring. Predictive Back drives the pop transitions directly (progress follows the gesture, cancel restores, commit
 * settles), so Back, the gesture and Up all reverse the same path.
 */
internal class ZarRouteTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)

@Composable
internal fun rememberZarRouteTransitions(): ZarRouteTransitions {
    val policy = LocalZarMotion.current
    val tabTravelPx = with(LocalDensity.current) { ZarMotion.TabTravel.roundToPx() }
    return remember(policy, tabTravelPx) { buildRouteTransitions(policy, tabTravelPx) }
}

private fun buildRouteTransitions(policy: MotionPolicy, tabTravelPx: Int): ZarRouteTransitions {
    val spatial = policy.allowsSpatialMotion
    val spec = policy.spring<IntOffset>(ZarSprings.Route)
    val fade = policy.fade<Float>(MotionPolicy.REDUCED_FADE_MAX_MILLIS)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.into(move: NavRouteMotion.Move): EnterTransition = when (move) {
        NavRouteMotion.Move.Fade -> fadeIn(fade)
        NavRouteMotion.Move.PeerTowardEnd -> slideIntoContainer(SlideDirection.Start, spec) { tabTravelPx }
        NavRouteMotion.Move.PeerTowardStart -> slideIntoContainer(SlideDirection.End, spec) { tabTravelPx }
        NavRouteMotion.Move.PushIn -> slideIntoContainer(SlideDirection.Start, spec)
        NavRouteMotion.Move.PopIn -> slideIntoContainer(SlideDirection.End, spec)
        NavRouteMotion.Move.ModalUp -> slideIntoContainer(SlideDirection.Up, spec)
        else -> EnterTransition.None
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.out(move: NavRouteMotion.Move): ExitTransition = when (move) {
        NavRouteMotion.Move.Fade -> fadeOut(fade)
        NavRouteMotion.Move.PeerTowardEnd -> slideOutOfContainer(SlideDirection.Start, spec) { tabTravelPx }
        NavRouteMotion.Move.PeerTowardStart -> slideOutOfContainer(SlideDirection.End, spec) { tabTravelPx }
        // The parent recedes only a little; the child covers the rest.
        NavRouteMotion.Move.Recede -> slideOutOfContainer(SlideDirection.Start, spec) { (it * ZarMotion.ROUTE_PARENT_RECEDE).toInt() }
        NavRouteMotion.Move.PopOut -> slideOutOfContainer(SlideDirection.End, spec)
        NavRouteMotion.Move.ModalDown -> slideOutOfContainer(SlideDirection.Down, spec)
        else -> ExitTransition.None
    }

    return ZarRouteTransitions(
        enter = { into(NavRouteMotion.enter(spatial, initialState.topLevelDestination(), targetState.topLevelDestination(), targetState.destination.isModalRoute())) },
        exit = { out(NavRouteMotion.exit(spatial, initialState.topLevelDestination(), targetState.topLevelDestination(), targetState.destination.isModalRoute())) },
        popEnter = { into(NavRouteMotion.popEnter(spatial, initialState.topLevelDestination(), targetState.topLevelDestination(), initialState.destination.isModalRoute())) },
        popExit = { out(NavRouteMotion.popExit(spatial, initialState.topLevelDestination(), targetState.topLevelDestination(), initialState.destination.isModalRoute())) },
    )
}

internal fun NavDestination.topLevelDestination(): TopLevelDestination? = when {
    hasRoute<HomeRoute>() -> TopLevelDestination.Home
    else -> null
}

internal fun NavBackStackEntry.topLevelDestination(): TopLevelDestination? = destination.topLevelDestination()

/**
 * Compose routes that present the Add-transaction sheet shell (grabber, close): they rise from below and leave downward.
 * Dialog routes (Add transaction, Quick note) are platform windows and keep the platform's own presentation.
 */
internal fun NavDestination.isModalRoute(): Boolean = hasRoute<IncomeFormRoute>()
