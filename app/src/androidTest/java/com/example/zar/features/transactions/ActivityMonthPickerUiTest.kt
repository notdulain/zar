package com.example.zar.features.transactions

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.model.*
import com.example.zar.features.transactions.presentation.activity.viewmodel.*
import com.example.zar.features.transactions.presentation.screen.ActivityActions
import com.example.zar.features.transactions.presentation.screen.ActivityBody
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivityMonthPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 5)
    private var filter by mutableStateOf(ActivityFilter(accountId = AccountId("bank"), query = "coffee", showVoided = true))
    private val snapshot = ActivitySnapshot(emptyList(), emptyList())

    @Test fun monthPickerChangesDatesWithoutOpeningFiltersAndPreservesOtherSelections() {
        compose.setContent {
            ZarTheme {
                ActivityBody(
                    ActivityUiState(filter, ActivityContent.Ready(ActivityQuery.apply(snapshot, filter, today)), today = today),
                    ActivityActions({}, {}, { filter = it }, {}, {}, {}, { 0 }),
                )
            }
        }
        val hero = compose.onNodeWithTag("activity-hero").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("activity-range").performClick()
        compose.onNodeWithTag("month-pager").assertIsDisplayed()
        compose.onNodeWithTag("activity-filter-sheet").assertDoesNotExist()
        compose.onNodeWithTag("month-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(DatePreset.LAST_MONTH, filter.preset)
        compose.onNodeWithTag("month-previous").performClick()
        compose.waitForIdle()
        assertEquals(DatePreset.CUSTOM, filter.preset)
        assertEquals(LocalDate.of(2026, 9, 1), filter.customFrom)
        assertEquals(LocalDate.of(2026, 9, 30), filter.customTo)
        assertEquals(AccountId("bank"), filter.accountId)
        assertEquals("coffee", filter.query)
        assertTrue(filter.showVoided)
        screenshot("activity-month-open.png")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("activity-range").assertTextContains("SEP")
        assertEquals(hero, compose.onNodeWithTag("activity-hero").getUnclippedBoundsInRoot())
        screenshot("activity-after-month.png")
        compose.onNodeWithTag("activity-filter-button").performClick()
        compose.onNodeWithTag("activity-filter-sheet").assertIsDisplayed()
        compose.onNodeWithTag("month-pager").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
