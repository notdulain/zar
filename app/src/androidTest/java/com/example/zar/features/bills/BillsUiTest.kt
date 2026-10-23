package com.example.zar.features.bills

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.bills.domain.BillSchedule
import com.example.zar.features.bills.domain.BillsCalculator
import com.example.zar.features.bills.domain.model.*
import com.example.zar.features.bills.presentation.model.BillList
import com.example.zar.features.bills.presentation.screen.*
import com.example.zar.features.bills.presentation.viewmodel.*
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native production content with fixed local data; these tests do not exercise Firebase writes. */
class BillsUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = ZarTime.today()
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private fun money(value: Long) = FiatMoney(value * 100, Currencies.LKR)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Unknown, today.minusMonths(3))
    private fun bill(id: String = "internet", name: String = "Dialog Internet", amount: Long = 5850) =
        Bill(BillId(id), name, bank.id, money(amount), BillFrequency.MONTHLY, today.minusMonths(1), BillStatus.ACTIVE, now)
    private fun expense(id: String, amount: Long): LedgerEntry {
        val tx = Transaction(TransactionId(id), bank.id, TransactionType.EXPENSE, money(amount), today,
            ReportingConversion.SameCurrency, SpendingClass.COMMITTED, merchant = id, createdAt = now)
        return LedgerEntry(tx, tx, emptyList(), null, emptyList())
    }
    private fun overview(bills: List<Bill>, paid: Boolean = false): BillsOverview {
        val due = BillSchedule.expectedPayment(bills.first(), today.minusMonths(1))
        val tx = expense("Internet payment", 5850)
        return BillsCalculator.overview(bills, listOf(bank), if (paid) listOf(due) else emptyList(),
            if (paid) listOf(PaymentMatch(due.id, tx.id, money(5850), now)) else emptyList(),
            if (paid) listOf(tx) else emptyList(), today)
    }
    private fun show(content: @Composable () -> Unit) = compose.setContent { ZarTheme(darkTheme = false, content = content) }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Include platform dialogs and the sheet window after its entrance animation.
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "pr25-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun detail(b: Bill = bill(), onKeep: () -> Unit = {}, onResume: () -> Unit = {},
        onCancelled: (java.time.LocalDate) -> Unit = {}, saving: Boolean = false) {
        val summary = overview(listOf(b), paid = true).bills.single()
        show {
            BillDetailContent(BillUiState(false, summary, mapOf(bank.id to bank.name)), null, saving,
                {}, {}, { _, _ -> }, onResume, {}, onCancelled, {}, onKeep)
        }
    }

    @Test fun paidAndAttentionLinksJumpPastTheHero() {
        val bills = listOf(bill(), bill("ceb", "CEB Electricity", 4200).copy(firstDue = today.minusDays(3))) + (1..7).map { bill("later$it", "Later bill $it").copy(firstDue = today.plusDays(10)) }
        val initial = BillList.from(overview(bills, paid = true), today)
        // Enough paid history below the first row to avoid clamping the jump at the scroll end.
        val list = initial.copy(paidThisMonth = initial.paidThisMonth + (1..12).map {
            initial.paidThisMonth.first().copy(summary = initial.paidThisMonth.first().summary.copy(bill = bill("paid$it", "Paid bill $it")))
        })
        show { BillsContent(BillsUiState(false, list), {}, {}, {}, { _, _ -> }, suggestions = {}) }
        screenshot("list")
        compose.onNodeWithText("Paid ↓").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bills-paid").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("bills-paid").getUnclippedBoundsInRoot().top < 150.dp)
        screenshot("paid-jump")
    }

    @Test fun attentionLinkRevealsFirstAttentionRow() {
        val list = BillList.from(overview(listOf(bill()) + (1..8).map { bill("later$it", "Later bill $it").copy(firstDue = today.plusDays(10)) }), today)
        show { BillsContent(BillsUiState(false, list), {}, {}, {}, { _, _ -> }, suggestions = {}) }
        compose.onNodeWithText("1 bill needs a look →").performClick()
        compose.onNodeWithTag("bills-needs-look").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("bills-needs-look").getUnclippedBoundsInRoot().top < 150.dp)
        screenshot("attention-jump")
    }

    @Test fun missedBillRowOpensDetailsAndItsPaymentActionOpensTheDueDate() {
        val list = BillList.from(overview(listOf(bill())), today)
        var openedBill: String? = null
        var openedDate: String? = null
        show { BillsContent(BillsUiState(false, list), {}, {}, { openedBill = it }, { _, date -> openedDate = date }, suggestions = {}) }
        compose.onAllNodesWithText("Dialog Internet")[0].performClick()
        assertEquals("internet", openedBill)
        compose.onNodeWithText("I paid it →").performClick()
        assertEquals(today.minusMonths(1).toString(), openedDate)
    }

    @Test fun canKeepBillAfterPlanningToCancel() {
        var kept = 0
        detail(bill().copy(cancellation = BillCancellation.Intended), onKeep = { kept++ })
        compose.onNodeWithTag("bill-end").performClick()
        compose.onNodeWithText("Keep this bill").performClick()
        screenshot("keep-bill")
        compose.onNodeWithTag("bill-end-confirm").performClick()
        assertEquals(1, kept)
        compose.onNodeWithTag("bill-end-sheet").assertDoesNotExist()
    }

    @Test fun cancellationDatePickerAndConfirmationRemainReachable() {
        var cancelled: java.time.LocalDate? = null
        detail(onCancelled = { cancelled = it })
        screenshot("detail")
        compose.onNodeWithTag("bill-end").performClick()
        compose.onNodeWithText("It's cancelled").performClick()
        screenshot("end-sheet")
        compose.onNodeWithTag("bill-end-from").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        screenshot("end-date-picker")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("bill-end-confirm").performClick()
        assertEquals(today, cancelled)
    }

    @Test fun stoppedBillCanResume() {
        var resumed = 0
        detail(bill().copy(status = BillStatus.STOPPED), onResume = { resumed++ })
        compose.onNodeWithTag("bill-resume").performClick()
        assertEquals(1, resumed)
        screenshot("stopped")
    }

    @Test fun candidateSelectionIsExposedAndUsesTheSelectedAmount() {
        val b = bill()
        val occurrence = overview(listOf(b)).bills.single().occurrences.first { it.dueDate == today }
        val candidates = listOf(expense("Internet payment", 5850), expense("Other payment", 2000)).map { PaymentCandidate(it, it.current.amount) }
        var selected: String? = null
        show {
            var state by remember { mutableStateOf(BillOccurrenceUiState(false, b, occurrence, candidates,
                mapOf(bank.id to bank.name), selectedId = candidates.first().entry.id.value)) }
            BillPaymentContent(state, {}, {}, {}, {
                selected = it.entry.id.value
                state = state.copy(selectedId = selected, amountText = null)
            }, { state = state.copy(amountText = it) })
        }
        compose.onAllNodesWithTag("bill-candidate")[0].assertIsSelected()
        compose.onAllNodesWithTag("bill-candidate")[1].performClick().assertIsSelected()
        compose.onAllNodesWithTag("bill-candidate")[0].assertIsNotSelected()
        compose.onNodeWithTag("bill-payment-amount").assertTextEquals("2000")
        assertEquals("Other payment", selected)
        screenshot("payment")
    }

    @Test fun emptyAndLoadFailureAreHonest() {
        val empty = BillList.from(overview(listOf(bill())).copy(bills = emptyList(), annual = emptyList()), today)
        var failed by mutableStateOf(false)
        show { BillsContent(if (failed) BillsUiState(false, loadFailed = true) else BillsUiState(false, empty), {}, {}, {}, { _, _ -> }, suggestions = {}) }
        compose.onNodeWithTag("bills-hero-text").assertTextContains("No bills yet.", substring = true)
        screenshot("empty")
        compose.runOnIdle { failed = true }
        compose.onNodeWithText("Couldn't load your bills. Check your connection and try again.").assertIsDisplayed()
        screenshot("load-error")
    }

    @Test fun alreadyMatchedActiveBillDoesNotSayNothingIsRunning() {
        val b = bill()
        val due = BillSchedule.expectedPayment(b, today)
        val tx = expense("Internet payment", 5850)
        val overview = BillsCalculator.overview(listOf(b), listOf(bank), listOf(due),
            listOf(PaymentMatch(due.id, tx.id, money(5850), now)), listOf(tx), today)
        val list = BillList.from(overview, today)
        show { BillsContent(BillsUiState(false, list), {}, {}, {}, { _, _ -> }, suggestions = {}) }
        compose.onNodeWithTag("bills-hero-text").assertTextContains("No upcoming payments left to match.", substring = true)
        screenshot("all-matched")
    }

    @Test fun earlyPaymentFromPreviousMonthKeepsBillReachable() {
        val b = bill().copy(firstDue = today)
        val due = BillSchedule.expectedPayment(b, today)
        val payment = expense("Early payment", 5850).let {
            val tx = it.current.copy(date = today.withDayOfMonth(1).minusDays(1))
            it.copy(original = tx, current = tx)
        }
        val overview = BillsCalculator.overview(listOf(b), listOf(bank), listOf(due),
            listOf(PaymentMatch(due.id, payment.id, money(5850), now)), listOf(payment), today)
        val list = BillList.from(overview, today)
        var opened: String? = null
        show { BillsContent(BillsUiState(false, list), {}, {}, { opened = it }, { _, _ -> }, suggestions = {}) }
        compose.onNodeWithTag("bills-other-running").assertIsDisplayed()
        compose.onNodeWithText("Dialog Internet").performClick()
        assertEquals(b.id.value, opened)
        screenshot("early-payment")
    }

    @Test fun heroGrowsToKeepLargeTextAndStatusReadable() {
        val list = BillList.from(overview(listOf(bill())), today)
        show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BillsContent(BillsUiState(false, list), {}, {}, {}, { _, _ -> }, suggestions = {})
            }
        }
        val text = compose.onNodeWithTag("bills-hero-text").getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("bills-status-link").getUnclippedBoundsInRoot()
        val hero = compose.onNodeWithTag("bills-hero").getUnclippedBoundsInRoot()
        assertTrue(text.bottom <= status.top)
        assertTrue(status.bottom <= hero.bottom)
        screenshot("large-text")
    }

    @Test fun formShowsReminderAndNativeDatePicker() {
        val b = bill()
        var saved = 0
        show {
            var state by remember { mutableStateOf(BillFormUiState(false, b, listOf(bank), b.name, bank.id,
                "5850", firstDue = today, reminder = BillReminder.ONE_DAY_BEFORE)) }
            BillFormContent(state, {}, { _, _ -> }, { saved++ }, { state = state.copy(name = it) },
                { state = state.copy(accountId = it) }, { state = state.copy(amount = it) },
                { state = state.copy(frequency = it) }, { state = state.copy(firstDue = it) },
                { state = state.copy(reminder = it) })
        }
        screenshot("form")
        compose.onNodeWithTag("bill-first-due").performScrollTo().performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        screenshot("form-date-picker")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("bill-save").performClick()
        assertEquals(1, saved)
    }
}
