package com.example.zar.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRouteMotionTest {

    @Test
    fun aHierarchicalPushEntersFromTheEndAndTheParentRecedes() {
        assertEquals(NavRouteMotion.Move.PushIn, NavRouteMotion.enter(true, targetModal = false))
        assertEquals(NavRouteMotion.Move.Recede, NavRouteMotion.exit(true, targetModal = false))
    }

    @Test
    fun backReversesTheSamePathToTheRight() {
        assertEquals(NavRouteMotion.Move.PopOut, NavRouteMotion.popExit(true))
        assertEquals(NavRouteMotion.Move.PopIn, NavRouteMotion.popEnter(true, initialModal = false))
    }

    @Test
    fun aSheetStyleRouteRisesFromBelowWhileTheParentHolds() {
        assertEquals(NavRouteMotion.Move.ModalUp, NavRouteMotion.enter(true, targetModal = true))
        assertEquals(NavRouteMotion.Move.Hold, NavRouteMotion.exit(true, targetModal = true))
        assertEquals(NavRouteMotion.Move.Hold, NavRouteMotion.popEnter(true, initialModal = true))
    }

    @Test
    fun everyBackLeavesToTheRightIncludingASheet() {
        // There is one pop path: the departing surface goes toward the logical end whatever it is. No downward exit exists.
        assertEquals(NavRouteMotion.Move.PopOut, NavRouteMotion.popExit(true))
        assertEquals(false, NavRouteMotion.Move.entries.any { it.name == "ModalDown" })
    }

    @Test
    fun reducedMotionFadesForwardChangesIncludingSheets() {
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.enter(false, targetModal = false))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.enter(false, targetModal = true))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.exit(false, targetModal = true))
    }

    @Test
    fun backWithMotionReducedOrOffLandsAtOnceWithNoFade() {
        assertEquals(NavRouteMotion.Move.Instant, NavRouteMotion.popExit(false))
        assertEquals(NavRouteMotion.Move.Instant, NavRouteMotion.popEnter(false, initialModal = false))
        assertEquals(NavRouteMotion.Move.Instant, NavRouteMotion.popEnter(false, initialModal = true))
        val backMoves = listOf(
            NavRouteMotion.popExit(false),
            NavRouteMotion.popEnter(false, initialModal = false),
            NavRouteMotion.popExit(true),
        )
        assertEquals(false, backMoves.contains(NavRouteMotion.Move.Fade))
    }

    @Test
    fun aChangeFromTheTopEntryToTheOneBeneathItIsABackStillInProgress() {
        assertEquals(true, NavRouteMotion.isBackInProgress(initialId = "child", targetId = "parent", topId = "child", belowTopId = "parent"))
    }

    @Test
    fun aPushIsNeverMistakenForABack() {
        // A real push targets the new top, even with the parent directly beneath it.
        assertEquals(false, NavRouteMotion.isBackInProgress(initialId = "parent", targetId = "child", topId = "child", belowTopId = "parent"))
        assertEquals(false, NavRouteMotion.isBackInProgress(initialId = "grand", targetId = "child", topId = "child", belowTopId = "parent"))
    }

    @Test
    fun aCompletedPopIsLeftToThePopCallbacks() {
        // The child has left the stack, so it is no longer the top; NavHost reads its live pop flag for this case.
        assertEquals(false, NavRouteMotion.isBackInProgress(initialId = "child", targetId = "parent", topId = "parent", belowTopId = null))
    }

    @Test
    fun theFirstCompositionWithOneEntryIsNotABack() {
        assertEquals(false, NavRouteMotion.isBackInProgress(initialId = "only", targetId = "only", topId = "only", belowTopId = null))
    }
}
