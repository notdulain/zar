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
    fun reducedMotionFadesEveryKindOfChangeIncludingSheets() {
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.enter(false, targetModal = false))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.enter(false, targetModal = true))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.exit(false, targetModal = true))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.popExit(false))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.popEnter(false, initialModal = false))
    }
}
