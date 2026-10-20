package com.example.zar.features.home

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.zar.core.domain.calculation.PeriodSummary
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.calculation.SpendingBreakdown
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.screen.HomeScreenContent
import com.example.zar.features.home.presentation.viewmodel.HomeContent
import com.example.zar.features.home.presentation.viewmodel.HomeUiState
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 9)
    private val zero = ReportingTotal.zero()

    @Test
    fun narrowScreenAndLargeTextKeepWarningsAndNavigationReachable() {
        var destination: TopLevelDestination? = null
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(zero.copy(unresolvedCount = 1), SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { zero },
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ZarTheme {
                    Box(Modifier.width(320.dp)) {
                        HomeScreenContent(
                            state = HomeUiState(month, canGoForward = false, HomeContent.Ready(overview)),
                            onPreviousMonth = {}, onNextMonth = {}, onRetry = {},
                            onNavigate = { destination = it }, onAddOption = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("received-warning").performScrollTo().assertIsDisplayed().assertTextContains("Check:", substring = true)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("month-label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue("Month must fit in at most two lines", layouts.single().lineCount <= 2)
        saveScreenshot("u02-large-text-warning.png")
        compose.onNodeWithContentDescription("Next month").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("nav-more").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(TopLevelDestination.More, destination)
        compose.onNodeWithTag("spent-card").performScrollTo().performClick()
        assertEquals(TopLevelDestination.Activity, destination)
    }

    @Test
    fun chartDescribesActualDailyIncomeAndTheSelectedMonth() {
        val paid = ReportingTotal(FiatMoney(125_050, Currencies.LKR))
        val overview = HomeOverview(
            month = month,
            summary = PeriodSummary(paid, SpendingBreakdown(zero, zero, zero), 0),
            recent = emptyList(),
            hasRecordsInMonth = true,
            receivedByDay = List(month.lengthOfMonth()) { if (it == 9) paid else zero },
        )
        compose.setContent {
            ZarTheme {
                HomeScreenContent(
                    state = HomeUiState(month, canGoForward = true, HomeContent.Ready(overview)),
                    onPreviousMonth = {}, onNextMonth = {}, onRetry = {}, onNavigate = {}, onAddOption = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Daily received income in LKR for September 2026. Sep 10: LKR 1,250.50")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("received-amount").assertTextContains("LKR 1,250.50")
        saveScreenshot("u02-home-reviewed.png")
    }
    /** Keep deterministic emulator images available for visual review. */
    private fun saveScreenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}
