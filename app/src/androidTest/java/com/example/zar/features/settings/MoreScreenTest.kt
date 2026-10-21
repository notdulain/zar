package com.example.zar.features.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.settings.presentation.screen.MoreScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The More tab (Figma Z86): five rows that open their destinations, and no rows for features not built yet. */
class MoreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show() = compose.setContent {
        ZarTheme {
            MoreScreen(
                onNavigate = {},
                onAddOption = {},
                onOpenSettings = { opened += "settings" },
                onOpenAccounts = { opened += "accounts" },
                onOpenIncome = { opened += "income" },
                onOpenFreelance = { opened += "freelance" },
                onOpenReminder = { opened += "reminder" },
            )
        }
    }

    @Test
    fun showsTheFiveConnectedRowsOnly() {
        show()

        listOf("open-accounts", "open-income", "open-freelance", "open-reminder", "open-settings").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        listOf("Privacy", "Export data", "Ways to add transactions", "Capture sources").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
    }

    @Test
    fun tappingRowsOpensDestinations() {
        show()

        listOf("open-accounts", "open-income", "open-freelance", "open-reminder", "open-settings").forEach {
            compose.onNodeWithTag(it).performScrollTo().performClick()
        }

        assertEquals(listOf("accounts", "income", "freelance", "reminder", "settings"), opened)
    }
}
