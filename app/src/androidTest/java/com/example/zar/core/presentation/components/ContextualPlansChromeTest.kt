package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContextualPlansChromeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun plansShowsNewPlanBesideTheTabBarOnOneRow() {
        var destination by mutableStateOf(TopLevelDestination.Activity)
        var creates = 0
        val controller = NavCapsuleController()
        compose.setContent { ZarTheme { Box(Modifier.fillMaxSize()) {
            ZarTopLevelChrome(destination, NavChrome.Full, { destination = it }, {}, controller,
                modifier = Modifier.align(Alignment.BottomCenter), onNewPlan = { creates++ })
        } } }
        compose.onNodeWithTag("add-entry").assertIsDisplayed()
        compose.onNodeWithTag("plans-new-plan").assertDoesNotExist()
        compose.onNodeWithTag("nav-plans").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("add-entry").assertDoesNotExist()
        val action = compose.onNodeWithTag("plans-new-plan").assertIsDisplayed().getUnclippedBoundsInRoot()
        val tabs = listOf("nav-home", "nav-activity", "nav-plans", "nav-more").map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        // Figma "Floating Nav · Plans": one row, the action 68dp high and to the right of the last tab, never above the bar.
        assertEquals(68f, (action.bottom - action.top).value, 0.5f)
        assertTrue(action.left >= tabs.last().right)
        assertEquals((tabs.first().top + tabs.first().bottom).value / 2, (action.top + action.bottom).value / 2, 1f)
        compose.onNodeWithTag("plans-new-plan").performClick()
        assertEquals(1, creates)
    }
}
