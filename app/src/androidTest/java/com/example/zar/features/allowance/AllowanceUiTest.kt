package com.example.zar.features.allowance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.allowance.domain.model.AllowanceOverview
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceStatus
import com.example.zar.features.allowance.presentation.screen.AllowanceCard
import com.example.zar.features.allowance.presentation.screen.AllowanceEditorDialog
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceEditorState
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceMessage
import com.example.zar.features.allowance.presentation.viewmodel.AllowanceUiState
import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AllowanceUiTest {
    @get:Rule val compose = createComposeRule()

    private fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)
    private val week = LocalDate.of(2026, 10, 19)..LocalDate.of(2026, 10, 25)

    private fun state(status: AllowanceStatus?) = AllowanceUiState(
        isLoading = false,
        overview = AllowanceOverview(
            AllowanceSettings(isEnabled = status != null, amount = status?.allowance),
            status,
        ),
    )

    private fun status(spent: Long, unresolved: Int = 0, unclassified: Int = 0, adjustments: Int = 0) = AllowanceStatus(
        week = week,
        allowance = lkr(8_000),
        spent = ReportingTotal(lkr(spent), unresolved),
        unclassifiedExpenseCount = unclassified,
        adjustmentCount = adjustments,
    )

    @Test
    fun offShowsNotSetAndOffersToSetItUp() {
        var edited = 0
        compose.setContent { ZarTheme { AllowanceCard(state(null), onEdit = { edited++ }, onRetry = {}) } }
        compose.onNodeWithTag("allowance-off").assertTextContains("Not set")
        compose.onNodeWithTag("allowance-set").performClick()
        assertEquals(1, edited)
    }

    @Test
    fun remainingShowsTheSpentAndTheWeek() {
        compose.setContent { ZarTheme { AllowanceCard(state(status(3_800)), onEdit = {}, onRetry = {}) } }
        compose.onNodeWithTag("allowance-remaining").assertTextContains("LKR 4,200 left")
        compose.onNodeWithTag("allowance-spent").assertTextContains("LKR 3,800", substring = true)
        compose.onNodeWithTag("allowance-week").assertTextContains("19 Oct", substring = true)
    }

    @Test
    fun overspendingIsNamedAndCoverageWarningsStayBesideIt() {
        compose.setContent {
            ZarTheme {
                AllowanceCard(state(status(9_000, unresolved = 1, unclassified = 2, adjustments = 1)), onEdit = {}, onRetry = {})
            }
        }
        compose.onNodeWithTag("allowance-remaining").assertTextContains("Overspent by LKR 1,000")
        compose.onNodeWithTag("allowance-warning-rate").assertIsDisplayed().assertTextContains("Check:", substring = true)
        compose.onNodeWithTag("allowance-warning-unclassified").assertIsDisplayed()
        compose.onNodeWithTag("allowance-warning-adjustment").assertIsDisplayed()
    }

    @Test
    fun aLoadFailureOffersRetry() {
        var retried = 0
        compose.setContent {
            ZarTheme { AllowanceCard(AllowanceUiState(isLoading = false, loadFailed = true), onEdit = {}, onRetry = { retried++ }) }
        }
        compose.onNodeWithTag("allowance-retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun theFormShowsEveryWeekdayAndReportsChoices() {
        var amount = ""
        var weekStart: DayOfWeek? = null
        val toggled = mutableListOf<String>()
        compose.setContent {
            ZarTheme {
                AllowanceEditorDialog(
                    editor = AllowanceEditorState(),
                    isSaving = false,
                    message = AllowanceMessage.INVALID_AMOUNT,
                    onEnabledChange = {},
                    onAmountChange = { amount += it },
                    onToggleCategory = { toggled += it },
                    onWeekStartChange = { weekStart = it },
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        compose.onNodeWithTag("allowance-error").assertIsDisplayed()
        compose.onNodeWithTag("allowance-amount").performTextInput("5000")
        compose.onNodeWithTag("allowance-category-groceries").performScrollTo().performClick()
        compose.onNodeWithTag("allowance-week-sunday").performScrollTo().performClick()
        assertEquals("5000", amount)
        assertEquals(listOf("groceries"), toggled)
        assertEquals(DayOfWeek.SUNDAY, weekStart)
    }

    @Test
    fun theFormListsAUsersOwnCategoriesToo() {
        val custom = CustomExpenseCategory("custom-pets-1", "Pets", CategoryNameRules.keyOf("Pets"), Instant.ofEpochMilli(1_000))
        val toggled = mutableListOf<String>()
        compose.setContent {
            ZarTheme {
                AllowanceEditorDialog(
                    editor = AllowanceEditorState(),
                    isSaving = false,
                    message = null,
                    onEnabledChange = {},
                    onAmountChange = {},
                    onToggleCategory = { toggled += it },
                    onWeekStartChange = {},
                    onDismiss = {},
                    onSave = {},
                    categories = ExpenseCategoryCatalog(listOf(custom)),
                )
            }
        }
        compose.onNodeWithTag("allowance-category-custom-pets-1").performScrollTo().performClick()
        assertEquals(listOf("custom-pets-1"), toggled)
    }
}
