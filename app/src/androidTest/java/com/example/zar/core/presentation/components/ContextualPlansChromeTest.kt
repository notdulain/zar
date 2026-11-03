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
    @Test fun changingContextKeepsEveryTabAtTheSameBounds() {
        var destination by mutableStateOf(TopLevelDestination.Activity)
        var creates = 0
        val controller = NavCapsuleController()
        compose.setContent { ZarTheme { Box(Modifier.fillMaxSize()) {
            ZarTopLevelChrome(destination, NavChrome.Full, { destination = it }, {}, controller,
                modifier = Modifier.align(Alignment.BottomCenter), onNewPlan = { creates++ })
        } } }
        val tags = listOf("nav-home", "nav-activity", "nav-plans", "nav-more")
        val bounds = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        compose.onNodeWithTag("nav-plans").performClick()
        compose.waitForIdle()
        assertEquals(bounds, tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() })
        compose.onNodeWithTag("plans-new-plan").assertIsDisplayed().performClick()
        assertEquals(1, creates)
    }
}
