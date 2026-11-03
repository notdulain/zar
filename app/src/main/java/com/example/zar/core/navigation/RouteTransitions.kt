package com.example.zar.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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
    return remember(policy) { buildRouteTransitions(policy) }
}

private fun buildRouteTransitions(policy: MotionPolicy): ZarRouteTransitions {
    val spatial = policy.allowsSpatialMotion
    val spec = policy.spring<IntOffset>(ZarSprings.Route)
    // Back must be seekable for the predictive gesture (a spring has no fixed length to scrub), so pops use a tween.
    val popSpec = tween<IntOffset>(ZarMotion.POP_MILLIS, easing = FastOutSlowInEasing)
    val fade = policy.fade<Float>(MotionPolicy.REDUCED_FADE_MAX_MILLIS)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.into(move: NavRouteMotion.Move): EnterTransition = when (move) {
        NavRouteMotion.Move.Fade -> fadeIn(fade)
        NavRouteMotion.Move.PushIn -> slideIntoContainer(SlideDirection.Left, spec)
        // The parent returns from a little behind the left edge while the child leaves toward the right: no scale, no fade. These are physical directions, so Back goes right in RTL too.
        NavRouteMotion.Move.PopIn -> slideIntoContainer(SlideDirection.Right, popSpec) { (it * ZarMotion.ROUTE_PARENT_RECEDE).toInt() }
        NavRouteMotion.Move.ModalUp -> slideIntoContainer(SlideDirection.Up, spec)
        else -> EnterTransition.None
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.out(move: NavRouteMotion.Move): ExitTransition = when (move) {
        NavRouteMotion.Move.Fade -> fadeOut(fade)
        // The parent recedes only a little; the child covers the rest.
        NavRouteMotion.Move.Recede -> slideOutOfContainer(SlideDirection.Left, spec) { (it * ZarMotion.ROUTE_PARENT_RECEDE).toInt() }
        NavRouteMotion.Move.PopOut -> slideOutOfContainer(SlideDirection.Right, popSpec)
        else -> ExitTransition.None
    }

    return ZarRouteTransitions(
        enter = { into(NavRouteMotion.enter(spatial, targetState.destination.isModalRoute())) },
        exit = { out(NavRouteMotion.exit(spatial, targetState.destination.isModalRoute())) },
        popEnter = { into(NavRouteMotion.popEnter(spatial, initialState.destination.isModalRoute())) },
        popExit = { out(NavRouteMotion.popExit(spatial)) },
    )
}

/**
 * Compose routes that present the Add-transaction sheet shell (grabber, close): they rise from below. Dialog routes
 * (Add transaction, Quick note) are platform windows driven by `SheetBackHost`. Any Back, a sheet's included, slides physically right.
 */
internal fun NavDestination.isModalRoute(): Boolean = hasRoute<IncomeFormRoute>()
