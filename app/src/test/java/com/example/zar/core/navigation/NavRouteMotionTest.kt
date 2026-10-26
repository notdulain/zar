package com.example.zar.core.navigation

import com.example.zar.core.presentation.components.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRouteMotionTest {

    @Test
    fun aChangeBetweenTwoTabsIsAPeerSwitch() {
        assertTrue(NavRouteMotion.isPeerTab(TopLevelDestination.Home, TopLevelDestination.Plans))
        assertTrue(NavRouteMotion.isPeerTab(TopLevelDestination.More, TopLevelDestination.Home))
    }

    @Test
    fun aChildOrUnknownDestinationIsNotAPeerSwitch() {
        assertFalse(NavRouteMotion.isPeerTab(null, TopLevelDestination.Home))
        assertFalse(NavRouteMotion.isPeerTab(TopLevelDestination.Home, null))
        assertFalse(NavRouteMotion.isPeerTab(null, null))
    }

    @Test
    fun travelDirectionFollowsHomeActivityPlansMoreOrder() {
        assertEquals(1, NavRouteMotion.tabDirection(TopLevelDestination.Home, TopLevelDestination.More))
        assertEquals(1, NavRouteMotion.tabDirection(TopLevelDestination.Activity, TopLevelDestination.Plans))
        assertEquals(-1, NavRouteMotion.tabDirection(TopLevelDestination.More, TopLevelDestination.Home))
        assertEquals(-1, NavRouteMotion.tabDirection(TopLevelDestination.Plans, TopLevelDestination.Activity))
    }

    @Test
    fun stayingOnTheSameTabHasNoBackwardTravel() {
        assertEquals(1, NavRouteMotion.tabDirection(TopLevelDestination.Plans, TopLevelDestination.Plans))
    }

    private val home = TopLevelDestination.Home
    private val plans = TopLevelDestination.Plans

    @Test
    fun aHierarchicalPushEntersFromTheEndAndTheParentRecedes() {
        assertEquals(NavRouteMotion.Move.PushIn, NavRouteMotion.enter(true, home, null, targetModal = false))
        assertEquals(NavRouteMotion.Move.Recede, NavRouteMotion.exit(true, home, null, targetModal = false))
    }

    @Test
    fun backReversesTheSamePath() {
        assertEquals(NavRouteMotion.Move.PopOut, NavRouteMotion.popExit(true, null, home, initialModal = false))
        assertEquals(NavRouteMotion.Move.PopIn, NavRouteMotion.popEnter(true, null, home, initialModal = false))
    }

    @Test
    fun aSheetStyleRouteEntersFromBelowAndLeavesDownwardWhileTheParentHolds() {
        assertEquals(NavRouteMotion.Move.ModalUp, NavRouteMotion.enter(true, null, null, targetModal = true))
        assertEquals(NavRouteMotion.Move.Hold, NavRouteMotion.exit(true, null, null, targetModal = true))
        assertEquals(NavRouteMotion.Move.ModalDown, NavRouteMotion.popExit(true, null, null, initialModal = true))
        assertEquals(NavRouteMotion.Move.Hold, NavRouteMotion.popEnter(true, null, null, initialModal = true))
    }

    @Test
    fun peerTabsTravelTowardTheirOrderAndNeverBehaveLikeAPush() {
        assertEquals(NavRouteMotion.Move.PeerTowardEnd, NavRouteMotion.enter(true, home, plans, targetModal = false))
        assertEquals(NavRouteMotion.Move.PeerTowardEnd, NavRouteMotion.exit(true, home, plans, targetModal = false))
        assertEquals(NavRouteMotion.Move.PeerTowardStart, NavRouteMotion.enter(true, plans, home, targetModal = false))
        assertEquals(NavRouteMotion.Move.PeerTowardStart, NavRouteMotion.popEnter(true, plans, home, initialModal = false))
    }

    @Test
    fun reducedMotionFadesEveryKindOfChangeIncludingSheets() {
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.enter(false, home, plans, targetModal = false))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.enter(false, null, null, targetModal = true))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.exit(false, null, null, targetModal = true))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.popExit(false, null, null, initialModal = true))
        assertEquals(NavRouteMotion.Move.Fade, NavRouteMotion.popEnter(false, null, home, initialModal = false))
    }
}
