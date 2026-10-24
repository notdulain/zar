package com.example.zar.features.quicknote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.File
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.quicknote.domain.review.ProposalBlocker
import com.example.zar.features.quicknote.domain.review.ProposalInput
import com.example.zar.features.quicknote.presentation.screen.QuickNoteActions
import com.example.zar.features.quicknote.presentation.screen.QuickNoteContent
import com.example.zar.features.quicknote.presentation.viewmodel.ProposalState
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteFailure
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteStage
import com.example.zar.features.quicknote.presentation.viewmodel.QuickNoteUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Production composables with fixed state. They do not claim backend integration. */
class QuickNoteUiTest {
    @get:Rule val compose = createComposeRule()

    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Known(FiatMoney(100_000, Currencies.LKR)), LocalDate.of(2026, 10, 1))
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(FiatMoney(100_000, Currencies.LKR)), LocalDate.of(2026, 10, 1))
    private val note = "paid 1250 at keells super with cash, and the electricity bill 4500 is due on the 28th"

    private class Recorder {
        var interpreted = 0
        var cancelled = 0
        var filledWith: String? = null
        var confirmed = mutableListOf<Int>()
        var discarded = mutableListOf<Int>()
        var discardedAll = 0
        var editorOpened: Int? = null
        var editNote = 0
    }

    private fun show(state: QuickNoteUiState, rec: Recorder = Recorder()) {
        compose.setContent {
            ZarTheme(darkTheme = false) {
                QuickNoteContent(
                    state,
                    QuickNoteActions(
                        onClose = {}, onNote = {}, onInterpret = { rec.interpreted++ }, onCancelReading = { rec.cancelled++ },
                        onFillForm = { rec.filledWith = it }, onEditNote = { rec.editNote++ }, onOpenEditor = { rec.editorOpened = it },
                        onCloseEditor = {}, onUpdate = { _, _ -> }, onConfirm = { rec.confirmed += it }, onDiscard = { rec.discarded += it },
                        onDiscardAll = { rec.discardedAll++ },
                    ),
                )
            }
        }
    }

    private fun actual(account: AccountId? = cash.id, uncertain: Set<DraftField> = emptySet()) = ProposalInput.Actual(
        TransactionType.EXPENSE, "1250", null, account, LocalDate.of(2026, 10, 22), "groceries", SpendingClass.DISCRETIONARY,
        "Keells Super", "", null, uncertain,
    )

    private val bill = ProposalInput.Expected(Direction.OUT, "4500", null, LocalDate.of(2026, 10, 28), "Electricity bill", null, emptySet())

    @Test fun writingShowsTheNoteTheCounterAndTheReadButton() {
        val rec = Recorder()
        show(QuickNoteUiState(note = note, accounts = listOf(cash)), rec)

        compose.onNodeWithTag("quicknote-counter").assertTextContains("${note.length} / 500")
        compose.onNodeWithText("Zar reads this note once to suggest records. The note is not saved, and nothing is recorded until you confirm.").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-interpret").assertIsEnabled().performClick()
        assertEquals(1, rec.interpreted)
        compose.onNodeWithTag("quicknote-fill-form").performClick()
        assertEquals(note, rec.filledWith)
    }

    @Test fun anEmptyNoteCannotBeRead() {
        show(QuickNoteUiState(note = ""))
        compose.onNodeWithTag("quicknote-interpret").assertIsNotEnabled()
    }

    @Test fun readingShowsProgressAndOnlyCancel() {
        val rec = Recorder()
        show(QuickNoteUiState(stage = QuickNoteStage.READING, note = note), rec)

        compose.onNodeWithTag("quicknote-reading").assertIsDisplayed()
        compose.onNodeWithText("Reading your note…").assertIsDisplayed()
        compose.onAllNodesWithTag("quicknote-interpret").assertCountEquals(0)
        compose.onNodeWithTag("quicknote-cancel").performClick()
        assertEquals(1, rec.cancelled)
    }

    @Test fun offlineSaysSoDisablesTryAgainAndOffersTheForm() {
        val rec = Recorder()
        show(QuickNoteUiState(note = note, isOnline = false), rec)

        compose.onNodeWithTag("quicknote-offline").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-interpret").assertIsNotEnabled()
        compose.onNodeWithTag("quicknote-fill-form").assertIsEnabled().performClick()
        assertEquals(note, rec.filledWith)
    }

    @Test fun aFailedReadKeepsTheNoteAndOffersAnotherTryOrTheForm() {
        val rec = Recorder()
        show(QuickNoteUiState(note = note, failure = QuickNoteFailure.TIMED_OUT), rec)

        compose.onNodeWithText("Could not read your note").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-note").assertTextContains(note)
        compose.onNodeWithTag("quicknote-interpret").assertIsEnabled().performClick()
        assertEquals(1, rec.interpreted)
    }

    @Test fun aFailureThatCannotBeRetriedOnlyOffersTheForm() {
        show(QuickNoteUiState(note = note, failure = QuickNoteFailure.APP_NOT_VERIFIED))
        compose.onNodeWithTag("quicknote-interpret").assertIsNotEnabled()
        compose.onNodeWithTag("quicknote-fill-form").assertIsEnabled()
    }

    @Test fun oneProposalShowsItsGapsAndCannotBeSavedUntilTheyAreFilled() {
        val rec = Recorder()
        val item = ProposalState(0, actual(account = null, uncertain = setOf(DraftField.CATEGORY)), setOf(ProposalBlocker.ACCOUNT_MISSING))
        show(QuickNoteUiState(stage = QuickNoteStage.REVIEWING, note = note, accounts = listOf(cash, bank), proposals = listOf(item)), rec)

        compose.onNodeWithText("Zar read your note as 1 record. Nothing is saved until you tap Save.").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-missing-account", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("quicknote-guessed-category", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("quicknote-hint-0").assertTextContains("Choose an account to save. Zar never picks one for you.")
        compose.onNodeWithTag("quicknote-save-0").assertIsNotEnabled()
        compose.onNodeWithTag("quicknote-row-0-account").performClick()
        assertEquals(0, rec.editorOpened)
        assertEquals(emptyList<Int>(), rec.confirmed)
    }

    @Test fun aCompleteProposalCanBeSavedOrDiscarded() {
        val rec = Recorder()
        show(QuickNoteUiState(stage = QuickNoteStage.REVIEWING, accounts = listOf(cash), proposals = listOf(ProposalState(0, actual()))), rec)

        compose.onNodeWithTag("quicknote-save-0").assertIsEnabled().performClick()
        compose.onNodeWithTag("quicknote-discard-0").performClick()
        assertEquals(listOf(0), rec.confirmed)
        assertEquals(listOf(0), rec.discarded)
    }

    @Test fun twoProposalsKeepAnExpectedPaymentApartFromAPaidExpense() {
        val rec = Recorder()
        show(
            QuickNoteUiState(
                stage = QuickNoteStage.REVIEWING, accounts = listOf(cash),
                proposals = listOf(ProposalState(0, actual()), ProposalState(1, bill)),
            ),
            rec,
        )

        compose.onNodeWithText("Zar found 2 records. Review each one on its own.").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-card-0").assertIsDisplayed()
        compose.onNodeWithText("PAID").assertIsDisplayed()
        compose.onNodeWithText("EXPECTED, NOT PAID").assertIsDisplayed()
        compose.onNodeWithText("It does not change your balance until you pay it.").assertIsDisplayed()
        compose.onNodeWithText("Add as expected payment →").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-save-1").performClick()
        assertEquals(listOf(1), rec.confirmed)
        compose.onNodeWithTag("quicknote-discard-all").performClick()
        assertEquals(1, rec.discardedAll)
    }

    @Test fun aSavedProposalShowsSavedAndNoLongerOffersASave() {
        show(
            QuickNoteUiState(
                stage = QuickNoteStage.REVIEWING, accounts = listOf(cash),
                proposals = listOf(
                    ProposalState(0, actual(), status = com.example.zar.features.quicknote.presentation.viewmodel.ProposalStatus.SAVED),
                    ProposalState(1, bill),
                ),
            ),
        )

        compose.onNodeWithTag("quicknote-saved-0").assertIsDisplayed()
        compose.onAllNodesWithTag("quicknote-save-0").assertCountEquals(0)
        compose.onAllNodesWithTag("quicknote-edit-note").assertCountEquals(0)
    }

    @Test fun theEditorShowsTheFieldsOfTheProposal() {
        val item = ProposalState(0, actual(), emptySet())
        show(QuickNoteUiState(stage = QuickNoteStage.REVIEWING, accounts = listOf(cash, bank), proposals = listOf(item), editing = 0))

        compose.onNodeWithText("Edit this record").assertIsDisplayed()
        compose.onNodeWithTag("quicknote-amount").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("quicknote-account-bank").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("quicknote-done").assertIsDisplayed()
    }

    @Test fun savingPreventsClosingOrReinterpretingTheNote() {
        val rec = Recorder()
        show(QuickNoteUiState(stage = QuickNoteStage.REVIEWING, accounts = listOf(cash), proposals = listOf(
            ProposalState(0, actual(), status = com.example.zar.features.quicknote.presentation.viewmodel.ProposalStatus.SAVING),
            ProposalState(1, bill),
        )), rec)
        compose.onNodeWithTag("quicknote-close").assertIsNotEnabled()
        compose.onAllNodesWithTag("quicknote-edit-note").assertCountEquals(0)
        compose.onNodeWithTag("quicknote-save-0").assertIsNotEnabled()
    }

    /** Native rendering of production composables with fixed data; no backend or Gemini call. */
    @Test fun captureReviewEvidence() {
        val review = QuickNoteUiState(stage = QuickNoteStage.REVIEWING, note = note, accounts = listOf(cash, bank),
            proposals = listOf(ProposalState(0, actual(account = null, uncertain = setOf(DraftField.CATEGORY)), setOf(ProposalBlocker.ACCOUNT_MISSING))))
        val current = mutableStateOf(QuickNoteUiState(note = note, accounts = review.accounts))
        compose.setContent {
            ZarTheme(darkTheme = false) {
                QuickNoteContent(current.value, QuickNoteActions(
                    onClose = {}, onNote = {}, onInterpret = {}, onCancelReading = {}, onFillForm = {}, onEditNote = {},
                    onOpenEditor = { current.value = current.value.copy(editing = it) },
                    onCloseEditor = { current.value = current.value.copy(editing = null) },
                    onUpdate = { index, transform -> current.value = current.value.copy(proposals = current.value.proposals.map {
                        if (it.index == index) it.copy(input = transform(it.input)) else it
                    }) },
                    onConfirm = {}, onDiscard = {}, onDiscardAll = {},
                ))
            }
        }
        fun showState(state: QuickNoteUiState, name: String) {
            compose.runOnIdle { current.value = state }
            capture(name)
        }
        compose.onNodeWithTag("quicknote-note").performClick()
        capture("01-write-keyboard")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        showState(current.value.copy(stage = QuickNoteStage.READING), "02-reading")
        showState(review, "03-missing-account")
        compose.onNodeWithTag("quicknote-row-0-account").performClick()
        capture("04-editor-account-picker")
        compose.onNodeWithTag("quicknote-date").performScrollTo().performClick()
        capture("05-date-picker")
        compose.onNodeWithText("Cancel").performClick()
        showState(review.copy(proposals = listOf(ProposalState(0, actual()))), "06-ready")
        val multiple = review.copy(proposals = listOf(ProposalState(0, actual()), ProposalState(1, bill)))
        showState(multiple, "07-actual-and-expected")
        showState(multiple.copy(proposals = listOf(multiple.proposals[0].copy(status = com.example.zar.features.quicknote.presentation.viewmodel.ProposalStatus.SAVING), multiple.proposals[1])), "08-saving")
        showState(multiple.copy(proposals = listOf(multiple.proposals[0].copy(status = com.example.zar.features.quicknote.presentation.viewmodel.ProposalStatus.SAVED), multiple.proposals[1])), "09-saved")
        showState(QuickNoteUiState(note = note, failure = QuickNoteFailure.TIMED_OUT), "10-read-error")
        showState(QuickNoteUiState(note = note, isOnline = false), "11-offline")
        val purchase = com.example.zar.core.domain.model.Transaction(
            id = com.example.zar.core.domain.model.TransactionId("purchase"), accountId = bank.id, type = TransactionType.EXPENSE,
            amount = FiatMoney(230000, Currencies.LKR), date = LocalDate.of(2026, 10, 20),
            reportingConversion = com.example.zar.core.domain.calculation.CurrencyConverter.toReporting(FiatMoney(230000, Currencies.LKR), null),
            spendingClass = SpendingClass.DISCRETIONARY, merchant = "Keells Super", createdAt = java.time.Instant.parse("2026-10-20T10:00:00Z"),
        )
        val entry = com.example.zar.core.domain.model.LedgerEntry(purchase, purchase, emptyList(), null, emptyList())
        val refund = review.copy(proposals = listOf(ProposalState(0, actual(account = bank.id).copy(type = TransactionType.REFUND, amount = "500"), setOf(ProposalBlocker.PURCHASE_MISSING))), purchases = listOf(entry))
        showState(refund, "12-refund-needs-purchase")
        compose.onNodeWithTag("quicknote-row-0-purchase").performClick()
        compose.onNodeWithTag("quicknote-purchase-purchase").performScrollTo()
        capture("13-purchase-picker")
        val transfer = ProposalInput.Transfer(bank.id, cash.id, "3000", Currencies.LKR, "2900", LocalDate.of(2026, 10, 22), emptySet())
        showState(review.copy(proposals = listOf(ProposalState(0, transfer, setOf(ProposalBlocker.RECEIVED_MISMATCH)))), "14-transfer-mismatch")
        showState(review.copy(proposals = listOf(ProposalState(0, actual(), saveFailed = true))), "15-save-error")
    }

    @Test fun guessedDateAndTimeRemainReadableAndEditable() {
        val input = actual(uncertain = setOf(DraftField.DATE)).copy(time = java.time.LocalTime.of(18, 30))
        val current = mutableStateOf(QuickNoteUiState(stage = QuickNoteStage.REVIEWING, accounts = listOf(cash), proposals = listOf(ProposalState(0, input)), editing = 0))
        compose.setContent {
            ZarTheme(darkTheme = false) {
                QuickNoteContent(current.value, QuickNoteActions(
                    onClose = {}, onNote = {}, onInterpret = {}, onCancelReading = {}, onFillForm = {}, onEditNote = {},
                    onOpenEditor = {}, onCloseEditor = {},
                    onUpdate = { _, transform -> current.value = current.value.copy(proposals = listOf(ProposalState(0, transform(current.value.proposals.single().input)))) },
                    onConfirm = {}, onDiscard = {}, onDiscardAll = {},
                ))
            }
        }
        compose.onNodeWithTag("quicknote-date").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("quicknote-time").performScrollTo().assertIsDisplayed()
        capture("16-edit-date-time")
        compose.onNodeWithTag("quicknote-time").performClick()
        compose.onNodeWithTag("quicknote-time-picker").assertIsDisplayed()
        capture("17-time-picker")
        compose.onNodeWithText("AM").performClick()
        compose.onNodeWithTag("quicknote-time-confirm").performClick()
        compose.runOnIdle { assertEquals(java.time.LocalTime.of(6, 30), (current.value.proposals.single().input as ProposalInput.Actual).time) }
        compose.onNodeWithTag("quicknote-time").performClick()
        compose.onNodeWithTag("quicknote-time-clear").performClick()
        compose.runOnIdle { assertEquals(null, (current.value.proposals.single().input as ProposalInput.Actual).time) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(650)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "pr29-evidence").apply { mkdirs() }
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
