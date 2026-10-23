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
}
