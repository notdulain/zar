package com.example.zar.features.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.settings.presentation.screen.MoreScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The More tab (Figma Z86): connected rows open their destinations, and no rows for features not built yet. */
class MoreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val rowTags = listOf(
        "open-accounts", "open-income", "open-freelance", "open-crypto",
        "open-capture", "open-bills", "open-rules", "open-reminder", "open-settings",
    )

    private fun show() = compose.setContent {
        ZarTheme {
            MoreScreen(
                onNavigate = {},
                onAddOption = {},
                onOpenSettings = { opened += "settings" },
                onOpenAccounts = { opened += "accounts" },
                onOpenIncome = { opened += "income" },
                onOpenFreelance = { opened += "freelance" },
                onOpenCrypto = { opened += "crypto" },
                onOpenCapture = { opened += "capture" },
                onOpenBills = { opened += "bills" },
                onOpenReminder = { opened += "reminder" },
                onOpenRules = { opened += "rules" },
            )
        }
    }

    @Test
    fun showsConnectedRowsOnly() {
        show()

        rowTags.forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        listOf("Privacy", "Export data").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
    }

    @Test
    fun tappingRowsOpensDestinations() {
        show()

        // A click action rather than a touch: the last row can sit under the floating navigation bar
        // until it is scrolled clear of it.
        rowTags.forEach {
            compose.onNodeWithTag(it).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        }

        assertEquals(
            listOf("accounts", "income", "freelance", "crypto", "capture", "bills", "rules", "reminder", "settings"),
            opened,
        )
    }
}
