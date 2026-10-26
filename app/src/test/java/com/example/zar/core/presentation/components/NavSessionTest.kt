package com.example.zar.core.presentation.components

import androidx.compose.runtime.saveable.SaverScope
import com.example.zar.core.presentation.components.TopLevelDestination.Activity
import com.example.zar.core.presentation.components.TopLevelDestination.Home
import com.example.zar.core.presentation.components.TopLevelDestination.More
import com.example.zar.core.presentation.components.TopLevelDestination.Plans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavSessionTest {

    private fun NavSession.save(): Boolean = with(NavSession.Saver) { SaverScope { true }.save(this@save)!! }
    private fun restore(saved: Boolean): NavSession = NavSession.Saver.restore(saved)!!

    @Test
    fun freshSessionHomeShowsOnlyAdd() {
        val session = NavSession()

        assertFalse(session.hasVisitedOtherTab)
        assertEquals(NavChrome.AddOnly, session.chromeFor(Home))
    }

    @Test
    fun otherTopLevelScreensAlwaysShowTheFullNavbar() {
        val session = NavSession()

        listOf(Activity, Plans, More).forEach { assertEquals(NavChrome.Full, session.chromeFor(it)) }
    }

    @Test
    fun homeStaysInitialWhileNothingIsVisited() {
        val session = NavSession()
        // Scrolling, refreshing, opening or cancelling Add, adding an entry: none of these reach the session. Arriving
        // at Home itself (first composition, or Back from a child screen) must not reveal it either.
        session.onTopLevelArrived(Home)
        session.onTopLevelArrived(Home)

        assertEquals(NavChrome.AddOnly, session.chromeFor(Home))
    }

    @Test
    fun visitingAnotherTabRevealsTheNavbarOnHomeFromThenOn() {
        val session = NavSession()

        session.onTopLevelArrived(Activity)
        assertTrue(session.hasVisitedOtherTab)
        assertEquals(NavChrome.Full, session.chromeFor(Activity))
        assertEquals(NavChrome.Full, session.chromeFor(Home))
    }

    @Test
    fun returningHomeByNavbarBackOrRepeatedTapKeepsItRevealed() {
        val session = NavSession()
        session.onTopLevelArrived(Plans)

        // Home from the navbar, Home via system Back, Home again after More, a child screen and Back.
        listOf(Home, More, Home, Home, Activity, Home).forEach {
            session.onTopLevelArrived(it)
            assertEquals("after arriving at $it", NavChrome.Full, session.chromeFor(Home))
        }
        assertTrue(session.hasVisitedOtherTab)
    }

    @Test
    fun launchingDirectlyIntoAnotherTabStartsRevealed() {
        val session = NavSession()
        session.onTopLevelArrived(More)

        assertEquals(NavChrome.Full, session.chromeFor(More))
        assertEquals(NavChrome.Full, session.chromeFor(Home))
    }

    @Test
    fun revealedStateSurvivesRecreationAndRestoration() {
        val revealed = NavSession().apply { onTopLevelArrived(Activity) }

        val restored = restore(revealed.save())

        assertTrue(restored.hasVisitedOtherTab)
        assertEquals(NavChrome.Full, restored.chromeFor(Home))
    }

    @Test
    fun anInitialSessionIsRestoredAsInitial() {
        val restored = restore(NavSession().save())

        assertFalse(restored.hasVisitedOtherTab)
        assertEquals(NavChrome.AddOnly, restored.chromeFor(Home))
    }

    @Test
    fun aNewSessionAfterTaskDismissalOrSignOutStartsInitialAgain() {
        val old = NavSession().apply { onTopLevelArrived(Activity) }
        assertEquals(NavChrome.Full, old.chromeFor(Home))

        // No saved state exists for a dismissed task or a removed graph, so the next session is simply a new one.
        val fresh = NavSession()

        assertEquals(NavChrome.AddOnly, fresh.chromeFor(Home))
    }

    @Test
    fun anOldSessionNeverGoesBackToInitial() {
        val session = NavSession().apply { onTopLevelArrived(Plans) }

        TopLevelDestination.entries.forEach { session.onTopLevelArrived(it) }

        assertTrue(session.hasVisitedOtherTab)
    }
}
