package com.example.zar.integration

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.domain.repository.ConnectivityMonitor
import com.example.zar.core.domain.usecase.ObserveLedgerUseCase
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.auth.EmulatorFirebase
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import com.example.zar.features.quicknote.domain.usecase.GenerateQuickNoteDraftUseCase
import com.example.zar.features.quicknote.presentation.screen.*
import com.example.zar.features.quicknote.presentation.viewmodel.*
import com.example.zar.features.rates.domain.ExchangeRateRepository
import com.example.zar.features.rates.domain.FetchTodaysRateUseCase
import com.example.zar.features.rates.domain.RateFetchError
import com.example.zar.features.rates.domain.RateQuote
import com.example.zar.features.rules.data.FirestoreCategoryRuleRepository
import com.example.zar.features.rules.data.InMemoryRuleOfferInbox
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import com.example.zar.features.transactions.domain.activity.usecase.*
import com.example.zar.features.transactions.domain.category.usecase.*
import com.example.zar.features.transactions.domain.manual.usecase.*
import com.example.zar.features.transactions.presentation.activity.screen.*
import com.example.zar.features.transactions.presentation.activity.viewmodel.*
import com.example.zar.features.transactions.presentation.manual.screen.*
import com.example.zar.features.transactions.presentation.manual.viewmodel.*
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Production UI -> real ViewModels -> production use cases -> emulator Firestore, not fixed UI states.
 * This small test host replaces NavHost/Hilt assembly, connectivity and external AI interpretation only.
 * Account creation/authentication are seeded; navigation/auth UI, live AI and WorkManager are not claimed.
 */
class FinancialJourneyTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var h: IntegrationHarness
    private val models = mutableListOf<ViewModel>()
    private val online = MutableStateFlow(true)
    private val connectivity = object : ConnectivityMonitor { override fun observeOnline() = online }
    private val route = mutableStateOf("manual")
    private lateinit var manual: ManualEntryViewModel
    private lateinit var quick: QuickNoteViewModel
    private lateinit var detail: EntryDetailViewModel
    private lateinit var edit: EditEntryViewModel
    private val inbox = InMemoryRuleOfferInbox()
    private val categories get() = ObserveExpenseCategoryCatalogUseCase(h.categories)
    private val rules get() = ObserveCategoryRulesUseCase(FirestoreCategoryRuleRepository(h.db, h.session))
    private val network get() = ObserveOnlineStatusUseCase(connectivity)

    @Before fun prepare(): Unit = runBlocking {
        EmulatorFirebase.requireEmulators()
        h = IntegrationHarness.open("journey")
        h.seedAccount("cash", "Test Cash", AccountType.CASH)
        h.seedAccount("bank", "Test Bank", AccountType.BANK)
    }

    @After fun close() {
        if (!::h.isInitialized) return
        main { models.forEach { it.viewModelScope.cancel() } }
        runBlocking { h.online(); h.flush() }
        h.close()
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun <T : ViewModel> keep(model: T): T = model.also { models += it }
    private fun makeManual(note: String = ""): ManualEntryViewModel = keep(ManualEntryViewModel(
        SavedStateHandle(mapOf("prefillTitle" to note)), ObserveActiveAccountsUseCase(h.accounts),
        ObserveEntrySuggestionsUseCase(h.ledger, categories), network,
        RecordExpenseUseCase(h.saveTransaction), RecordTransferUseCase(h.saveTransfer), rules, inbox,
        categories, CreateExpenseCategoryUseCase(h.categories), FetchTodaysRateUseCase(NoRates),
    ))

    /** These journeys never ask for today's rate; if one did, it would behave as an offline phone. */
    private object NoRates : ExchangeRateRepository {
        override suspend fun todaysRate(base: Currency, quote: Currency): Result<RateQuote> = Result.failure(RateFetchError.Offline())
    }

    private fun showDetail(id: String) {
        main {
            val observe = ObserveEntryDetailUseCase(h.ledger, h.accounts)
            detail = keep(EntryDetailViewModel(SavedStateHandle(mapOf("entryId" to id)), observe, network, h.void, rules, inbox))
            edit = keep(EditEntryViewModel(SavedStateHandle(mapOf("entryId" to id)), observe, network, CorrectExpenseUseCase(h.correct), rules, inbox))
            route.value = "detail"
        }
        compose.waitUntil(15_000) { detail.uiState.value.content is EntryDetailContent.Ready && !edit.uiState.value.isLoading }
    }

    private fun host() {
        compose.setContent {
            ZarTheme(darkTheme = false) {
                when (route.value) {
                    "manual" -> {
                        val state by manual.uiState.collectAsState()
                        ManualEntryContent(state, false, ManualEntryActions(
                            {}, {}, {}, manual::onKindSelect, manual::onAmountChange, manual::onDateSelect,
                            manual::onTimeSelect, manual::onAccountSelect, manual::onCategorySelect,
                            manual::onAddCategoryStart, manual::onNewCategoryNameChange, manual::onAddCategoryCancel,
                            manual::onAddCategorySave, manual::onSpendingClassSelect, manual::onTitleChange,
                            manual::onDescriptionChange, manual::onSuggestionSelect, manual::onFeeChange,
                            manual::onFromSelect, manual::onToSelect, manual::onSwapAccounts, manual::onTransferNoteChange,
                            manual::save,
                        ))
                    }
                    "detail" -> {
                        val state by detail.uiState.collectAsState()
                        EntryDetailBody(state, DetailActions({}, { route.value = "edit" }, {}, {}, {}, {},
                            detail::showVoidSheet, detail::dismissVoidSheet, detail::confirmVoid))
                    }
                    "edit" -> {
                        val state by edit.uiState.collectAsState()
                        LaunchedEffect(state.isFinished) { if (state.isFinished) route.value = "detail" }
                        EditEntryBody(state, EditActions({ route.value = "detail" }, edit::onAmountChange, edit::onDateSelect,
                            edit::onCategorySelect, edit::onSpendingClassSelect, edit::onTitleChange, edit::onDescriptionChange,
                            edit::save, edit::onTimeSelect, edit::onAccountSelect))
                    }
                    "quick" -> {
                        val state by quick.uiState.collectAsState()
                        QuickNoteContent(state, QuickNoteActions({}, quick::onNoteChange, quick::interpret,
                            quick::cancelReading, { note -> manual = makeManual(note); route.value = "manual" },
                            quick::editNote, quick::openEditor, quick::closeEditor, quick::update,
                            quick::confirm, quick::discard, quick::discardAll))
                    }
                }
            }
        }
    }

    private fun fillManual(amount: String, note: String) {
        compose.waitUntil(15_000) { !manual.uiState.value.isLoading }
        compose.onNodeWithTag("manual-amount").performTextReplacement(amount)
        compose.onNodeWithTag("manual-account-cash").performScrollTo().performClick()
        compose.onNodeWithTag("manual-category-groceries").performScrollTo().performClick()
        compose.onNodeWithTag("manual-note").performScrollTo().performTextReplacement(note)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }

    @Test fun manualSaveOfflineCorrectionAndUndoAreVisibleAndPersistAfterReconnect() = runBlocking {
        main { manual = makeManual() }
        host()
        fillManual("500", "Test groceries")
        compose.onNodeWithTag("manual-time").performScrollTo().performClick()
        capture("01-manual-time-picker")
        compose.onNodeWithText("Cancel").performClick()
        capture("02-manual-ready")
        compose.onNodeWithTag("manual-save").performClick()
        compose.waitUntil(15_000) { manual.uiState.value.isFinished }
        h.flush()
        val saved = h.serverLedger().single()
        assertEquals(50_000L, (saved.current.amount as FiatMoney).minorUnits)
        showDetail(saved.id.value)
        compose.onNodeWithTag("detail-edit").assertIsDisplayed()
        capture("03-manual-saved-detail")
        h.offline(); online.value = false
        compose.onNodeWithTag("detail-edit").performClick()
        compose.onNodeWithTag("edit-amount").performTextReplacement("650")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("edit-offline").assertIsDisplayed()
        capture("04-offline-correction")
        compose.onNodeWithTag("edit-save").performClick()
        compose.waitUntil(15_000) {
            (detail.uiState.value.content as? EntryDetailContent.Ready)?.detail?.entry?.current?.amount == FiatMoney(65_000, Currencies.LKR)
        }
        compose.onNodeWithTag("detail-edited").assertIsDisplayed()
        capture("05-corrected-detail")
        compose.onNodeWithTag("detail-void").performClick()
        compose.onNodeWithTag("void-offline").assertIsDisplayed()
        capture("06-offline-undo-confirmation")
        compose.onNodeWithTag("void-confirm").performClick()
        compose.waitUntil(15_000) { (detail.uiState.value.content as? EntryDetailContent.Ready)?.detail?.entry?.isVoided == true }
        compose.onNodeWithTag("detail-voided").assertIsDisplayed()
        capture("07-voided-detail")
        h.online(); online.value = true; h.flush()
        val stored = h.serverLedger().single()
        assertEquals(50_000L, (stored.original.amount as FiatMoney).minorUnits)
        assertEquals(65_000L, (stored.current.amount as FiatMoney).minorUnits)
        assertEquals(1, stored.corrections.size)
        assertTrue(stored.isVoided)
        assertEquals(1, h.serverDocs("voids").size)
        assertEquals(0L, h.summaryToday(listOf(stored)).spent.total.amount.minorUnits)
    }

    /** Only the external model response is deterministic; everything after it is the real application path. */
    private fun makeQuick(): QuickNoteViewModel {
        val generated = object : QuickNoteDraftRepository {
            override suspend fun generate(request: QuickNoteDraftRequest): Draft {
                assertEquals("Paid 1250 for groceries; electricity bill 4500 due tomorrow", request.note)
                assertEquals(2, request.accounts.size)
                return Draft(DraftId("journey-${UUID.randomUUID()}"), DraftSource.QUICK_NOTE, listOf(
                    DraftProposal.ActualTransaction(TransactionType.EXPENSE, FiatMoney(125_000, Currencies.LKR), null,
                        request.today, SpendingClass.DISCRETIONARY, "groceries", "Test store", uncertainFields = setOf(DraftField.CATEGORY)),
                    DraftProposal.Expected(Direction.OUT, FiatMoney(450_000, Currencies.LKR), request.today.plusDays(1), "Test electricity"),
                ), DraftStatus.PENDING_REVIEW, Instant.now())
            }
        }
        return keep(QuickNoteViewModel(GenerateQuickNoteDraftUseCase(generated, h.accounts, h.session), h.confirmQuickNote,
            ObserveActiveAccountsUseCase(h.accounts), ObserveLedgerUseCase(h.ledger), network))
    }

    @Test fun quickNoteInputReviewAccountPickerEditAndConfirmPersistActualAndExpectedSeparately() = runBlocking {
        main { quick = makeQuick(); route.value = "quick" }
        host()
        compose.onNodeWithTag("quicknote-note").performTextInput("Paid 1250 for groceries; electricity bill 4500 due tomorrow")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        capture("08-quick-note-writing")
        compose.onNodeWithTag("quicknote-interpret").performClick()
        compose.waitUntil(15_000) { quick.uiState.value.stage == QuickNoteStage.REVIEWING }
        assertTrue(h.serverDocs("transactions").isEmpty())
        assertTrue(h.serverDocs("expectedPayments").isEmpty())
        compose.onNodeWithTag("quicknote-save-0").assertIsNotEnabled()
        capture("09-review-actual-and-expected")
        // Multi-record cards use their edit button; both paths open the same production editor.
        compose.onNodeWithTag("quicknote-edit-0").performScrollTo().performClick()
        compose.onNodeWithTag("quicknote-account-cash").performScrollTo().performClick()
        compose.onNodeWithTag("quicknote-amount").performScrollTo().performTextReplacement("1300")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        capture("10-review-editor-account")
        compose.onNodeWithTag("quicknote-done").performClick()
        compose.onNodeWithTag("quicknote-save-0").performScrollTo().performClick()
        compose.waitUntil(15_000) { quick.uiState.value.proposals[0].status == ProposalStatus.SAVED }
        capture("11-actual-saved-expectation-open")
        h.flush()
        assertEquals(130_000L, (h.serverLedger().single().current.amount as FiatMoney).minorUnits)
        assertTrue(h.serverDocs("expectedPayments").isEmpty())
        compose.onNodeWithTag("quicknote-save-1").performScrollTo().performClick()
        compose.waitUntil(15_000) { quick.uiState.value.isFinished }
        h.flush()
        assertEquals(1, h.serverDocs("transactions").size)
        assertEquals(1, h.serverDocs("expectedPayments").size)
        assertTrue(h.serverDocs("drafts").isEmpty())
        assertEquals("", quick.uiState.value.note)
        // Repeated confirmation after completion is inert.
        main { quick.confirm(0); quick.confirm(1) }
        h.flush()
        assertEquals(1, h.serverDocs("transactions").size)
        val entries = h.serverLedger()
        assertEquals(130_000L, h.summaryToday(entries).spent.total.amount.minorUnits)
        showDetail(entries.single().id.value)
        capture("12-quick-note-persisted-detail")
    }

    @Test fun offlineQuickNoteKeepsTextAndItsFormFallbackSavesLocallyThenReconnects() = runBlocking {
        h.offline(); online.value = false
        main { quick = makeQuick(); route.value = "quick" }
        host()
        compose.onNodeWithTag("quicknote-note").performTextInput("Test offline purchase")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("quicknote-offline").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-interpret").assertIsNotEnabled()
        capture("13-offline-quick-note")
        compose.onNodeWithTag("quicknote-fill-form").performClick()
        compose.waitUntil(15_000) { !manual.uiState.value.isLoading }
        assertEquals("Test offline purchase", manual.uiState.value.title)
        fillManual("200", "Test offline purchase")
        capture("14-offline-manual-fallback")
        compose.onNodeWithTag("manual-save").performClick()
        compose.waitUntil(15_000) { manual.uiState.value.isFinished }
        assertEquals(1, h.localLedger().size)
        h.online(); online.value = true; h.flush()
        assertEquals(20_000L, (h.serverLedger().single().current.amount as FiatMoney).minorUnits)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // IME/window-inset animations run on the device clock, outside Compose's test clock.
        // Let those settle before taking review evidence; behavior checks do not depend on this delay.
        android.os.SystemClock.sleep(650)
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "d08-evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
