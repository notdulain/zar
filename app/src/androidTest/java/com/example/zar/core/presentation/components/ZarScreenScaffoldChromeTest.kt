package com.example.zar.core.presentation.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Rule
import org.junit.Test

/** Home shows only + until another tab is visited, then the full navbar with Home selected. */
class ZarScreenScaffoldChromeTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = NavSession()

    private fun showHome() = compose.setContent {
        ZarTheme {
            CompositionLocalProvider(LocalNavSession provides session) {
                ZarScreenScaffold(Home, onNavigate = {}, onAddOption = {}) { Text("home") }
            }
        }
    }

    private val Home = TopLevelDestination.Home

    @Test
    fun freshHomeShowsOnlyAddThenTheFullNavbarAfterAnotherTab() {
        showHome()
        compose.onNodeWithTag("add-entry").assertIsDisplayed()
        compose.onNodeWithTag("nav-activity").assertDoesNotExist()

        session.onTopLevelArrived(TopLevelDestination.Activity)
        compose.waitForIdle()

        compose.onNodeWithTag("nav-home").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("nav-activity").assertIsDisplayed()
        compose.onNodeWithTag("add-entry").assertIsDisplayed()
    }
}
