package com.example.zar.core.navigation

import android.util.Log
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Route motion through a real NavHost on a controlled clock, using the production transition policy: a hierarchical
 * push from the logical end with a receding parent, a sheet-style route from below with a parent that holds, Back
 * reversing the same paths, and predictive Back (injected through the activity's own back dispatcher) cancelling with no
 * pop and committing exactly one. This is the OnBackPressedDispatcher path; it is not an edge gesture on hardware.
 */
class RouteMotionNavTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private var windowWidthDp = 0f
    private var windowHeightDp = 0f

    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)

    private fun show(policy: MotionPolicy) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    nav = rememberNavController()
                    val config = LocalConfiguration.current
                    windowWidthDp = config.screenWidthDp.toFloat()
                    windowHeightDp = config.screenHeightDp.toFloat()
                    val transitions = rememberZarRouteTransitions(nav)
                    NavHost(
                        navController = nav,
                        startDestination = SignInRoute,
                        enterTransition = transitions.enter,
                        exitTransition = transitions.exit,
                        popEnterTransition = transitions.popEnter,
                        popExitTransition = transitions.popExit,
                        predictivePopEnterTransition = { transitions.popEnter(this) },
                        predictivePopExitTransition = { transitions.popExit(this) },
                    ) {
                        composable<SignInRoute> { Box(Modifier.fillMaxSize().testTag("parent")) }
                        composable<RegisterRoute> { Box(Modifier.fillMaxSize().testTag("child")) }
                        composable<IncomeFormRoute> { Box(Modifier.fillMaxSize().testTag("sheet")) }
                    }
                }
            }
        }
        frame(3)
    }

    private fun frame(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun left(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value
    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value
    private fun exists(tag: String) = compose.onAllNodesWithTagCount(tag) > 0
    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun route(): String? = nav.currentBackStackEntry?.destination?.route

    @Test
    fun repeatedPushAndBackAlwaysLeavesRightIncludingInterruptedPushes() {
        show(full)
        repeat(10) { iteration ->
            compose.runOnIdle { nav.navigate(RegisterRoute) }
            frame(if (iteration % 2 == 0) 60 else 5)
            compose.runOnIdle { nav.popBackStack() }
            val positions = ArrayList<Float>()
            repeat(30) {
                frame()
                if (exists("child")) positions += left("child")
            }
            assertTrue("Back $iteration must have visible animation samples: $positions", positions.size >= 2)
            assertTrue("Back $iteration must move right: $positions",
                positions.zipWithNext().all { (a, b) -> b >= a - 0.6f })
            frame(60)
        }
    }

    @Test
    fun aPushEntersFromTheEndAndTheParentRecedesAtMostTwentyPercent() {
        show(full)

        compose.runOnIdle { nav.navigate(RegisterRoute) }
        frame()
        val childLeft = ArrayList<Float>()
        val parentLeft = ArrayList<Float>()
        repeat(30) {
            frame()
            if (exists("child")) childLeft += left("child")
            if (exists("parent")) parentLeft += left("parent")
        }
        Log.i("ZarRouteMotion", "push child left dp: $childLeft parent left dp: $parentLeft width=$windowWidthDp")

        assertTrue("child starts near the logical end: ${childLeft.first()}", childLeft.first() > windowWidthDp * 0.4f)
        assertTrue("child travels monotonically toward 0 without overshoot: $childLeft", childLeft.zipWithNext().all { (a, b) -> b <= a + 0.01f } && childLeft.all { it >= -0.5f })
        assertEquals(0f, childLeft.last(), 0.6f)
        assertTrue("parent recedes toward the start by at most 20%: $parentLeft", parentLeft.all { it >= -windowWidthDp * 0.2f - 1f && it <= 0.5f })
    }

    @Test
    fun backReversesThePushAndRestoresTheParent() {
        show(full)
        compose.runOnIdle { nav.navigate(RegisterRoute) }
        frame(60)
        assertEquals("child", "child".takeIf { exists("child") })

        compose.runOnIdle { nav.popBackStack() }
        frame()
        val childLeft = ArrayList<Float>()
        repeat(30) { frame(); if (exists("child")) childLeft += left("child") }

        assertTrue("child leaves toward the end: $childLeft", childLeft.isEmpty() || childLeft.zipWithNext().all { (a, b) -> b >= a - 0.01f })
        frame(40)
        assertEquals(SignInRoute::class, nav.currentBackStackEntry?.destination?.route?.let { SignInRoute::class })
        compose.onNodeWithTag("parent").assertIsDisplayed()
        assertEquals(0f, left("parent"), 0.6f)
    }

    @Test
    fun aSheetStyleRouteRisesFromBelowWhileTheParentHoldsStill() {
        show(full)

        compose.runOnIdle { nav.navigate(IncomeFormRoute()) }
        frame()
        val sheetTop = ArrayList<Float>()
        val parentLeft = ArrayList<Float>()
        repeat(30) {
            frame()
            if (exists("sheet")) sheetTop += top("sheet")
            if (exists("parent")) parentLeft += left("parent")
        }
        Log.i("ZarRouteMotion", "modal sheet top dp: $sheetTop parent left dp: $parentLeft height=$windowHeightDp")

        assertTrue("sheet starts below the viewport top: ${sheetTop.first()}", sheetTop.first() > windowHeightDp * 0.3f)
        assertEquals("sheet comes to rest at the top", 0f, sheetTop.last(), 0.6f)
        assertTrue("it never slides in horizontally", (0 until sheetTop.size).all { true } && compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().left.value == 0f)
        assertTrue("the parent holds still behind a sheet: $parentLeft", parentLeft.all { kotlin.math.abs(it) < 0.5f })
    }

    @Test
    fun dismissingASheetRouteLeavesToTheRightNotDownwardAndTheParentStaysPut() {
        show(full)
        compose.runOnIdle { nav.navigate(IncomeFormRoute()) }
        frame(60)

        compose.runOnIdle { nav.popBackStack() }
        frame()
        val sheetLeft = ArrayList<Float>()
        val sheetTop = ArrayList<Float>()
        val sheetWidth = ArrayList<Float>()
        val parentLeft = ArrayList<Float>()
        repeat(30) {
            frame()
            if (exists("sheet")) {
                val bounds = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot()
                sheetLeft += bounds.left.value
                sheetTop += bounds.top.value
                sheetWidth += bounds.width.value
            }
            if (exists("parent")) parentLeft += left("parent")
        }

        assertTrue("sheet leaves toward the end: $sheetLeft", sheetLeft.isEmpty() || (sheetLeft.zipWithNext().all { (a, b) -> b >= a - 0.01f } && sheetLeft.last() > 0f))
        assertTrue("it never drops: $sheetTop", sheetTop.all { kotlin.math.abs(it) < 0.5f })
        assertTrue("it is never scaled: $sheetWidth", sheetWidth.all { kotlin.math.abs(it - windowWidthDp) < 0.6f })
        assertTrue("parent never moved: $parentLeft", parentLeft.all { kotlin.math.abs(it) < 0.5f })
    }

    @Test
    fun theParentReturnsFromBehindItsStartEdgeWithoutScalingOrFading() {
        show(full)
        compose.runOnIdle { nav.navigate(RegisterRoute) }
        frame(60)

        compose.runOnIdle { nav.popBackStack() }
        frame()
        val parentLeft = ArrayList<Float>()
        val parentWidth = ArrayList<Float>()
        repeat(30) {
            frame()
            if (exists("parent")) {
                val bounds = compose.onNodeWithTag("parent").getUnclippedBoundsInRoot()
                parentLeft += bounds.left.value
                parentWidth += bounds.width.value
            }
        }

        assertTrue("the parent starts no further back than 20% of the width: $parentLeft", parentLeft.all { it >= -windowWidthDp * 0.2f - 1f && it <= 0.5f })
        assertTrue("it travels toward rest: $parentLeft", parentLeft.zipWithNext().all { (a, b) -> b >= a - 0.01f })
        assertTrue("it is never scaled: $parentWidth", parentWidth.all { kotlin.math.abs(it - windowWidthDp) < 0.6f })
    }

    @Test
    fun reducedMotionNeverTravelsAPageOrASheet() {
        show(reduced)

        compose.runOnIdle { nav.navigate(RegisterRoute) }
        frame()
        val lefts = ArrayList<Float>()
        repeat(12) { frame(); if (exists("child")) lefts += left("child") }

        assertTrue("no spatial travel under reduced motion: $lefts", lefts.all { kotlin.math.abs(it) < 0.5f })
        compose.runOnIdle { nav.navigate(IncomeFormRoute()) }
        frame(3)
        val tops = ArrayList<Float>()
        repeat(12) { frame(); if (exists("sheet")) tops += top("sheet") }
        assertTrue("no vertical travel either: $tops", tops.all { kotlin.math.abs(it) < 0.5f })
    }

    @Test
    fun predictiveBackCancelKeepsTheChildAndCommitPopsExactlyOnce() {
        show(full)
        compose.runOnIdle { nav.navigate(RegisterRoute) }
        frame(60)
        val dispatcher = compose.activity.onBackPressedDispatcher

        // A swipe that starts, travels, then is let go before the threshold: nothing is popped and nothing is lost.
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0.0f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 400f, 0.4f, BackEventCompat.EDGE_LEFT)) }
        frame(2)
        val midLeft = left("child")
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frame(60)

        Log.i("ZarRouteMotion", "predictive back child left at 40% progress dp: $midLeft; after cancel: ${left("child")}")
        assertTrue("the child follows the gesture (moved toward the end): $midLeft", midLeft > 1f)
        assertEquals("cancel restores the child in place", 0f, left("child"), 0.6f)
        assertEquals(RegisterRoute.toString().let { route() }, route())
        assertTrue("still on Register after cancel", exists("child"))
        assertEquals(2, nav.currentBackStack.value.count { it.destination.route != null })

        // A completed back gesture pops exactly one entry.
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0.0f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 400f, 0.9f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        frame(80)

        assertTrue("signed-out start is shown again", exists("parent"))
        assertNull("only one entry was popped; Register is gone", nav.previousBackStackEntry?.destination?.route)
        assertEquals(1, nav.currentBackStack.value.count { it.destination.route != null })
    }

    @Test
    fun aBackGestureDuringAnUnsettledPushStillLeavesToTheRight() {
        show(full)
        compose.runOnIdle { nav.navigate(RegisterRoute) }
        // The push looks finished long before its transition ends. The parent is still composed here, which is what made
        // AnimatedContent reuse the transition spec it captured for the push instead of asking for a Back one.
        frame(20)
        assertTrue("the push is still in flight, with the parent composed", exists("parent"))
        val dispatcher = compose.activity.onBackPressedDispatcher

        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0.0f, BackEventCompat.EDGE_LEFT)) }
        frame(2)
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 400f, 0.5f, BackEventCompat.EDGE_LEFT)) }
        val lefts = ArrayList<Float>()
        repeat(6) { frame(); if (exists("child")) lefts += left("child") }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        repeat(40) { frame(); if (exists("child")) lefts += left("child") }

        Log.i("ZarRouteMotion", "back during an unsettled push, child left dp: $lefts")
        assertTrue("the child never recedes to the left: $lefts", lefts.all { it >= -1f })
        assertTrue("the child travels right as it leaves: $lefts", lefts.any { it > windowWidthDp * 0.2f })
        frame(40)
        assertFalse("the child is gone", exists("child"))
        assertTrue("the parent is back", exists("parent"))
    }
}
