package com.example.zar.features.transactions

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.presentation.components.TopLevelDestination
import com.example.zar.core.presentation.components.ZarScreenScaffold
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.model.*
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityUiState
import com.example.zar.features.transactions.presentation.screen.ActivityActions
import com.example.zar.features.transactions.presentation.screen.ActivityBody
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pixel checks exercise the actual rendered boundary, independently of the ratio helper. */
class ActivityGradientUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)
    private fun money(major: Long) = FiatMoney(major * 100, Currencies.LKR)
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(money(0)), today.minusDays(20))
    private val cash = bank.copy(id = AccountId("cash"), name = "Cash", type = AccountType.CASH)

    private fun snapshot(received: Long, spent: Long): ActivitySnapshot {
        fun entry(id: String, type: TransactionType, amount: Long, account: Account) = Transaction(
            TransactionId(id), account.id, type, money(amount), today, ReportingConversion.SameCurrency,
            spendingClass = if (type == TransactionType.EXPENSE) SpendingClass.DISCRETIONARY else null,
            merchant = if (type == TransactionType.EXPENSE) "Purchase" else "Client payment", createdAt = Instant.parse("2026-10-21T10:00:00Z"),
        ).let { LedgerEntry(it, it, emptyList(), null, emptyList()) }
        return ActivitySnapshot(buildList {
            if (received > 0) add(entry("income", TransactionType.INCOME, received, bank))
            if (spent > 0) add(entry("expense", TransactionType.EXPENSE, spent, cash))
        }, listOf(bank, cash))
    }

    private fun show(received: Long, spent: Long, dark: Boolean = false) {
        val snapshot = snapshot(received, spent)
        compose.setContent {
            var filter by remember { mutableStateOf(ActivityFilter()) }
            ZarTheme(darkTheme = dark) {
                ZarScreenScaffold(TopLevelDestination.Activity, {}, {}, heroUnderStatusBar = true) { padding ->
                    ActivityBody(
                        ActivityUiState(filter, ActivityContent.Ready(ActivityQuery.apply(snapshot, filter, today)), snapshot.accounts, true, today),
                        ActivityActions({}, {}, { filter = it }, { filter = ActivityFilter() }, { filter = filter.copy(query = it) }, {}, { ActivityQuery.apply(snapshot, it, today).rows.size }),
                        contentPadding = padding,
                    )
                }
            }
        }
    }

    private fun sample(x: Float): Color {
        val image = compose.onNodeWithTag("activity-hero").captureToImage()
        return image.toPixelMap()[(image.width * x).toInt(), (image.height * 0.55f).toInt()]
    }
    private fun teal(x: Float) { val c = sample(x); assertTrue("Expected teal at $x, got $c", c.green > c.red && c.blue > c.red) }
    private fun maroon(x: Float) { val c = sample(x); assertTrue("Expected maroon at $x, got $c", c.red > c.green && c.blue > c.green) }

    @Test fun receivedDominantIsEightyPercentTeal() {
        show(80_000, 20_000)
        teal(0.76f); maroon(0.84f)
        screenshot("activity-mix-80-20.png")
    }
    @Test fun spentDominantIsEightyPercentMaroon() {
        show(20_000, 80_000)
        teal(0.16f); maroon(0.24f)
        screenshot("activity-mix-20-80.png")
    }
    @Test fun equalAmountsHaveAnEvenBoundaryInDarkMode() {
        show(50_000, 50_000, dark = true)
        teal(0.46f); maroon(0.54f)
        screenshot("activity-mix-50-50-dark.png")
    }
    @Test fun receivedOnlyFillsTheHero() {
        show(80_000, 0)
        teal(0.1f); teal(0.9f)
        screenshot("activity-mix-received-only.png")
    }
    @Test fun spentOnlyFillsTheHero() {
        show(0, 80_000)
        maroon(0.1f); maroon(0.9f)
        screenshot("activity-mix-spent-only.png")
    }
    @Test fun zeroTotalsAreNeutral() {
        show(0, 0)
        assertEquals(sample(0.25f), sample(0.75f))
        assertEquals(Color(0xFF0C1625), sample(0.25f))
        screenshot("activity-mix-empty.png")
    }
    @Test fun accountAndDateFiltersRecalculateTheBoundary() {
        show(80_000, 20_000)
        compose.onNodeWithTag("activity-range").performClick()
        screenshot("activity-mix-filter-sheet.png")
        compose.onNodeWithTag("activity-filter-account-cash").performClick()
        compose.onNodeWithTag("activity-filter-apply").performScrollTo().performClick()
        compose.onNodeWithTag("activity-spent").assertTextContains("20,000")
        maroon(0.1f); maroon(0.9f)
        compose.onNodeWithTag("activity-range").performClick()
        compose.onNodeWithTag("activity-filter-preset-custom").performClick()
        compose.onNodeWithTag("activity-filter-from").performScrollTo().performClick()
        screenshot("activity-mix-filter-date-picker.png")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("activity-filter-preset-last_month").performScrollTo().performClick()
        compose.onNodeWithTag("activity-filter-apply").performScrollTo().performClick()
        compose.onNodeWithTag("activity-received").assertTextContains("0")
        compose.onNodeWithTag("activity-spent").assertTextContains("0")
        assertEquals(Color(0xFF0C1625), sample(0.25f))
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500) // Let the platform dialog window finish its entrance.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pr16").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
