package com.example.zar.integration

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.EmulatorFirebase
import com.example.zar.features.bills.data.repository.FirestoreBillRepository
import com.example.zar.features.bills.domain.BillProblem
import com.example.zar.features.bills.domain.usecase.ObserveBillAccountsUseCase
import com.example.zar.features.bills.domain.usecase.ObserveBillsUseCase
import com.example.zar.features.bills.domain.usecase.SaveBillUseCase
import com.example.zar.features.bills.presentation.screen.BillFormContent
import com.example.zar.features.bills.presentation.screen.BillsContent
import com.example.zar.features.bills.presentation.viewmodel.BillFormViewModel
import com.example.zar.features.bills.presentation.viewmodel.BillsViewModel
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.usecase.*
import com.example.zar.features.capture.presentation.SharedTextInbox
import com.example.zar.features.capture.presentation.screen.CaptureContent
import com.example.zar.features.capture.presentation.screen.CaptureReviewContent
import com.example.zar.features.capture.presentation.viewmodel.CaptureReviewViewModel
import com.example.zar.features.capture.presentation.viewmodel.CaptureViewModel
import com.example.zar.features.reminders.domain.PaymentReminderScheduler
import com.example.zar.features.reminders.domain.RefreshRemindersUseCase
import com.example.zar.features.rules.data.FirestoreCategoryRuleRepository
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * L-area journeys from Kavindu's scenario, driven through the real Compose screens with Espresso-backed
 * Compose testing: production UI -> real ViewModels -> production use cases -> Firestore on the local
 * emulators (project demo-zar). Only navigation and the reminder scheduler are replaced. Each run signs in
 * a fresh anonymous user, so data is synthetic and never touches the live project.
 */
class KavinduJourneyTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var h: IntegrationHarness
    private val models = mutableListOf<ViewModel>()
    private val route = mutableStateOf("")

    private val scheduler = object : PaymentReminderScheduler {
        override fun ensureScheduled() = Unit
        override fun checkNow() = Unit
        override fun cancel() = Unit
    }

    @Before fun prepare(): Unit = runBlocking {
        EmulatorFirebase.requireEmulators()
        h = IntegrationHarness.open("kavindu")
        h.seedAccount("combank", "Commercial Bank", AccountType.BANK)
    }

    @After fun close() {
        if (!::h.isInitialized) return
        main { models.forEach { it.viewModelScope.cancel() } }
        runBlocking { h.flush() }
        h.close()
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun <T : ViewModel> keep(model: T): T = model.also { models += it }

    /** Saves what is on screen to the app cache, for the testing report. */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "kavindu-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    // --- Bills ---------------------------------------------------------------------------------

    private val billRepo get() = FirestoreBillRepository(h.db, h.session)
    private val observeBills get() = ObserveBillsUseCase(billRepo, h.accounts, h.expected, h.ledger)
    private lateinit var form: BillFormViewModel
    private lateinit var list: BillsViewModel

    private fun showBills() {
        main {
            form = keep(BillFormViewModel(SavedStateHandle(mapOf("billId" to null)), ObserveBillAccountsUseCase(h.accounts),
                observeBills, SaveBillUseCase(billRepo), RefreshRemindersUseCase(scheduler)))
            list = keep(BillsViewModel(observeBills, ObserveBillAccountsUseCase(h.accounts)))
            route.value = "form"
        }
        compose.setContent {
            ZarTheme(darkTheme = false) {
                when (route.value) {
                    "form" -> {
                        val state by form.uiState.collectAsState()
                        BillFormContent(state, {}, { _, _ -> route.value = "list" }, form::save, form::onNameChange,
                            form::onAccountSelect, form::onAmountChange, form::onFrequencySelect, form::onFirstDueSelect, form::onReminderSelect)
                    }
                    "list" -> {
                        val state by list.uiState.collectAsState()
                        BillsContent(state, {}, {}, {}, { _, _ -> }, suggestions = {})
                    }
                }
            }
        }
        compose.waitUntil(15_000) { !form.uiState.value.isLoading }
    }

    @Test fun rentTypedIntoTheBillFormIsSavedAndShownWithItsYearlyCost() = runBlocking<Unit> {
        showBills()
        compose.onNodeWithTag("bill-name").performTextReplacement("Rent")
        compose.onNodeWithTag("bill-amount").performTextReplacement("34000")
        Espresso.closeSoftKeyboard()
        screenshot("01-rent-form")

        compose.onNodeWithTag("bill-save").performClick()
        compose.waitUntil(15_000) { route.value == "list" }
        compose.waitUntil(15_000) { list.uiState.value.list?.hasBills == true }
        compose.onNodeWithText("Rent", substring = true).assertExists()
        // 34,000 × 12 is the yearly estimate shown under the list.
        compose.onNodeWithText("408,000", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("02-rent-in-bills-list")

        // The bill really reached Firestore, under this user only.
        h.flush()
        val saved = h.serverDocs("bills")
        assertEquals(1, saved.size)
        assertEquals("Rent", saved.single().getString("name"))
    }

    @Test fun anEmptyBillFormExplainsWhatIsMissingAndSavesNothing() = runBlocking<Unit> {
        showBills()
        compose.onNodeWithTag("bill-save").performClick()
        compose.waitForIdle()
        assertTrue(BillProblem.NAME_MISSING in form.uiState.value.problems)
        assertTrue(BillProblem.AMOUNT_MISSING in form.uiState.value.problems)
        assertEquals("Still on the form", "form", route.value)
        screenshot("03-empty-bill-form-errors")

        h.flush()
        assertTrue("Nothing was written", h.serverDocs("bills").isEmpty())
    }

    // --- Bank SMS capture ----------------------------------------------------------------------

    private val pickMe = "Dear Cardholder, Purchase at PICKME COLOMBO 03 LK for LKR 1,250.00 on " +
        ZarTime.today().let { "%02d/%02d/%02d".format(it.dayOfMonth, it.monthValue, it.year % 100) } +
        " 08:15 AM has been authorised on your debit card ending #1111."

    @Test fun aSharedPickMeSmsBecomesADraftAndCountsAsSpentOnlyAfterHeConfirms() = runBlocking<Unit> {
        val inbox = SharedTextInbox()
        val rules = ObserveCategoryRulesUseCase(FirestoreCategoryRuleRepository(h.db, h.session))
        lateinit var capture: CaptureViewModel
        lateinit var review: CaptureReviewViewModel
        main {
            capture = keep(CaptureViewModel(inbox, CaptureTextUseCase(BankSmsParser(), h.drafts),
                ObserveCapturedDraftsUseCase(h.drafts), ObserveCaptureHealthUseCase(h.drafts)))
            route.value = "capture"
        }
        compose.setContent {
            ZarTheme(darkTheme = false) {
                when (route.value) {
                    "capture" -> {
                        val state by capture.uiState.collectAsState()
                        CaptureContent(state, {}, { id ->
                            review = keep(CaptureReviewViewModel(SavedStateHandle(mapOf("draftId" to id)),
                                ObserveCapturedDraftsUseCase(h.drafts), ObserveCaptureAccountsUseCase(h.accounts),
                                ObserveCaptureLedgerUseCase(h.ledger), ConfirmCapturedDraftUseCase(h.drafts, rules),
                                DiscardCapturedDraftUseCase(h.drafts), LinkCapturedDraftUseCase(h.drafts, h.ledger)))
                            route.value = "review"
                        }, {}, capture::onTextChange, { capture.read() })
                    }
                    "review" -> {
                        val state by review.uiState.collectAsState()
                        CaptureReviewContent(state, {}, review::onAccountSelect, review::onTypeSelect,
                            review::onSpendingClassSelect, review::confirm, review::discard)
                    }
                }
            }
        }

        // Long-press → Share → Zar, as the Android share sheet would deliver it.
        main { capture.setActive(true); inbox.offer(pickMe) }
        val draftId = CapturedDraftRules.draftIdFor(pickMe).value
        compose.waitUntil(15_000) { capture.uiState.value.pending.any { it.id.value == draftId } }
        screenshot("04-sms-draft-waiting")

        // A draft is not money yet: nothing is in the ledger.
        h.flush()
        assertTrue(h.serverLedger().isEmpty())

        compose.onNodeWithTag("capture-draft-$draftId").performScrollTo().performClick()
        compose.waitUntil(15_000) { route.value == "review" && review.uiState.value.draft != null }
        assertEquals("Read as an expense", TransactionType.EXPENSE, review.uiState.value.type)
        screenshot("05-sms-review")

        compose.onNodeWithTag("capture-confirm").performClick()
        compose.waitUntil(15_000) { review.uiState.value.isFinished }
        screenshot("06-sms-confirmed")

        h.flush()
        val ledger = h.serverLedger()
        val summary = h.summaryToday(ledger)
        assertEquals(1, ledger.size)
        assertEquals("PickMe now counts as spent", FiatMoney(125_000, Currencies.LKR), summary.spent.total.amount)
        assertEquals("…and never as income", FiatMoney.zero(Currencies.LKR), summary.received.amount)
        // Only the parsed fields reach the server, never the raw message.
        val drafts = EmulatorAdmin.documents(h.uid, FirestoreCollections.DRAFTS).values.joinToString()
        assertFalse(drafts.contains("Dear Cardholder"))
    }
}
