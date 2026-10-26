package com.example.zar.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** What the bottom of a top-level screen shows. */
enum class NavChrome {
    /** The centred + only. Initial Home of a fresh navigation session. */
    AddOnly,

    /** The Home / Activity / Plans / More pill with the + beside it. */
    Full,
}

/**
 * Owns, for one navigation session, whether the full navbar has been revealed. It lives with the signed-in graph
 * (see `ZarNavHost`), not with any screen or ViewModel, so a screen being created again can never reset it.
 *
 * The session is "the same" while Android restores the same task state: backgrounding, rotation, theme and font
 * changes and Activity recreation all restore it through [Saver]. A new task after force-stop or task dismissal,
 * and a sign-out (which removes the whole graph), start a new one.
 */
@Stable
class NavSession(hasVisitedOtherTab: Boolean = false) {

    /** True once the user has reached Activity, Plans or More in this session. It never goes back to false. */
    var hasVisitedOtherTab by mutableStateOf(hasVisitedOtherTab)
        private set

    /** Call whenever a top-level destination becomes current, whether by a tap, Back or restoration. */
    fun onTopLevelArrived(destination: TopLevelDestination) {
        if (destination != TopLevelDestination.Home) hasVisitedOtherTab = true
    }

    /**
     * Only the initial Home of a session is [NavChrome.AddOnly]; every other top-level screen, and Home once
     * another tab has been visited, shows the full navbar. Scrolling, Add, and modals never change this.
     */
    fun chromeFor(destination: TopLevelDestination): NavChrome =
        if (destination == TopLevelDestination.Home && !hasVisitedOtherTab) NavChrome.AddOnly else NavChrome.Full

    companion object {
        val Saver: Saver<NavSession, Boolean> = Saver(
            save = { it.hasVisitedOtherTab },
            restore = { NavSession(hasVisitedOtherTab = it) },
        )
    }
}

@Composable
fun rememberNavSession(): NavSession = rememberSaveable(saver = NavSession.Saver) { NavSession() }

/**
 * The session the current screen belongs to. Outside `ZarNavHost` (previews, component tests) this is a fresh
 * session, which reproduces the original initial-Home behaviour.
 */
val LocalNavSession = compositionLocalOf { NavSession() }
