package com.example.zar.features.capture

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.*
import com.example.zar.features.capture.presentation.screen.*
import com.example.zar.features.capture.presentation.viewmodel.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Native production composables with fixed data. These do not claim backend integration. */
class CaptureUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-22T04:00:00Z")
    private val sms = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private val parsed = (BankSmsParser().parse(sms) as SmsParseResult.Parsed).sms
    private val draft = CapturedDraftRules.draftFrom(DraftId("purchase"), parsed, DraftSource.SHARED_TEXT, now)
    private val bank = Account(AccountId("bank"), "Commercial Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(FiatMoney(1000000, Currencies.LKR)), LocalDate.of(2026,10,1))
    private fun review(state: CaptureReviewUiState, onType: (TransactionType) -> Unit = {}, onConfirm: () -> Unit = {}, onDiscard: () -> Unit = {}) {
        compose.setContent { ZarTheme(darkTheme = false) { CaptureReviewContent(state, {}, {}, onType, {}, onConfirm, onDiscard) } }
    }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(500)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "pr22").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun captureStatusAndBankCompatibility() {
        compose.setContent { ZarTheme(darkTheme = false) { CaptureContent(CaptureUiState(health = CaptureHealth(null,null,0)), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Nothing captured yet").assertIsDisplayed()
        compose.onNodeWithTag("capture-read").assertIsNotEnabled()
        capture("01-capture-empty")
        compose.onNodeWithText("BOC, People's, HNB, NSB, NDB, Seylan and others").performScrollTo().assertIsDisplayed()
        capture("02-supported-banks")
    }
    @Test fun parsedMessageAppearsAsReviewableDraft() {
        var opened = ""
        compose.setContent { ZarTheme(darkTheme = false) { CaptureContent(CaptureUiState(cameFromShare = true,
            health = CaptureHealth(now,DraftSource.SHARED_TEXT,1), pending = listOf(draft), outcome = CaptureOutcome.Drafted(draft.id,parsed)), {}, {opened=it}, {}, {}, {}) } }
        compose.onNodeWithTag("capture-draft-purchase").performScrollTo().assertIsDisplayed()
        capture("03-waiting-for-review")
        compose.onNodeWithTag("capture-draft-purchase").performClick()
        assertEquals("purchase",opened)
    }
    @Test fun purchaseReviewOffersAccountTypeAndClassification() {
        review(CaptureReviewUiState(isLoading=false,draft=draft,accounts=listOf(bank),accountId=bank.id,type=TransactionType.EXPENSE))
        compose.onNodeWithText("Commercial Bank · LKR").assertIsDisplayed()
        compose.onNodeWithText("Unknown").performScrollTo().assertIsSelected()
        capture("04-purchase-review")
    }
    @Test fun creditRequiresAnExplicitTypeAndShowsValidation() {
        val creditText = "LKR 240.00 credited to AC **3333 for Food\n12/10/2026 12:21:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP"
        val credit = CapturedDraftRules.draftFrom(DraftId("credit"), (BankSmsParser().parse(creditText) as SmsParseResult.Parsed).sms, DraftSource.SHARED_TEXT, now)
        var chosen: TransactionType?=null
        review(CaptureReviewUiState(isLoading=false,draft=credit,accounts=listOf(bank),accountId=bank.id,showErrors=true),onType={chosen=it})
        compose.onNodeWithText("Income").assertIsNotSelected()
        compose.onNodeWithText("Choose a type").performScrollTo().assertIsDisplayed()
        capture("05-choose-type")
        compose.onNodeWithText("Income").performClick()
        assertEquals(TransactionType.INCOME,chosen)
    }
    @Test fun savingPreventsChangingTheConfirmedChoicesOrDiscarding() {
        review(CaptureReviewUiState(isLoading=false,draft=draft,accounts=listOf(bank),accountId=bank.id,
            type=TransactionType.EXPENSE,isSaving=true,confirmationStarted=true))
        compose.onNodeWithTag("capture-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("capture-discard").assertIsNotEnabled()
        compose.onNodeWithText("Income").assertIsNotEnabled()
        capture("06-saving")
    }
    @Test fun failureKeepsRetryAndBlocksDiscard() {
        var saves=0
        review(CaptureReviewUiState(isLoading=false,draft=draft,accounts=listOf(bank),accountId=bank.id,
            type=TransactionType.EXPENSE,message=CaptureMessage.FAILED,confirmationStarted=true),onConfirm={saves++})
        compose.onNodeWithTag("capture-discard").assertIsNotEnabled()
        compose.onNodeWithTag("capture-confirm").performClick()
        assertEquals(1,saves)
        capture("07-save-error-retry")
    }
    @Test fun missingAccountLeavesManualRecoveryInstructionsVisible() {
        review(CaptureReviewUiState(isLoading=false,draft=draft,type=TransactionType.EXPENSE))
        compose.onNodeWithText("No LKR account yet.",substring=true).assertIsDisplayed()
        capture("08-no-account")
    }
    @Test fun otpOutcomeAndClearedTextAreVisible() {
        compose.setContent { ZarTheme(darkTheme=false) { CaptureContent(CaptureUiState(outcome=CaptureOutcome.Ignored(com.example.zar.features.capture.domain.model.SmsIgnoreReason.OTP)),{},{},{},{},{}) } }
        compose.onNodeWithText("This is a one-time password",substring=true).performScrollTo().assertIsDisplayed()
        capture("09-otp-not-recorded")
    }
    @Test fun moreKeepsBillsAndOpensCapture() {
        var opens=0
        compose.setContent { ZarTheme(darkTheme=false) {
            com.example.zar.features.settings.presentation.screen.MoreScreen(onNavigate={},onAddOption={},onOpenSettings={},
                onOpenAccounts={},onOpenIncome={},onOpenFreelance={},onOpenCrypto={},onOpenCapture={opens++},onOpenBills={},onOpenReminder={})
        } }
        compose.onNodeWithTag("open-capture").performScrollTo().assertIsDisplayed()
        capture("10-more-capture-entry")
        compose.onNodeWithTag("open-capture").performClick()
        assertEquals(1,opens)
        compose.onNodeWithTag("open-bills").performScrollTo().assertIsDisplayed()
    }


    // --- SCRUM-40: duplicates, transfers and already-counted answers -------------------------

    private fun spendEntry(id: String, merchant: String = "Cafe Seventy Three"): LedgerEntry {
        val amount = FiatMoney(80000, Currencies.LKR)
        val saved = Transaction(TransactionId(id), bank.id, TransactionType.EXPENSE, amount, LocalDate.of(2026, 10, 11),
            com.example.zar.core.domain.calculation.CurrencyConverter.toReporting(amount, null), SpendingClass.DISCRETIONARY, merchant = merchant, createdAt = now)
        return LedgerEntry(saved, saved, emptyList(), null, emptyList())
    }
    private fun legEntry(id: String, type: TransactionType, account: Account): LedgerEntry {
        val amount = FiatMoney(24000, Currencies.LKR)
        val saved = Transaction(TransactionId(id), account.id, type, amount, LocalDate.of(2026, 10, 12),
            com.example.zar.core.domain.calculation.CurrencyConverter.toReporting(amount, null), transferId = TransferId("t1"), createdAt = now)
        return LedgerEntry(saved, saved, emptyList(), null, emptyList())
    }
    private val savings = Account(AccountId("savings"), "Savings", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(FiatMoney(5000000, Currencies.LKR)), LocalDate.of(2026,10,1))
    private val creditDraft = CapturedDraftRules.draftFrom(DraftId("credit"),
        (BankSmsParser().parse("LKR 240.00 credited to AC **3333 for Food\n12/10/2026 12:21:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP") as SmsParseResult.Parsed).sms,
        DraftSource.SHARED_TEXT, now)
    private fun settleReview(state: CaptureReviewUiState, settle: CaptureSettleActions) {
        compose.setContent { ZarTheme(darkTheme = false) { CaptureReviewContent(state, {}, {}, {}, {}, {}, {}, settle) } }
    }

    @Test fun anEarlierEntryIsOfferedAsADuplicateWithTwoAnswers() {
        var same = 0; var apart = 0
        settleReview(CaptureReviewUiState(isLoading = false, draft = draft, accounts = listOf(bank), accountId = bank.id, ledger = listOf(spendEntry("earlier"))),
            CaptureSettleActions(onSamePayment = { same++ }, onKeepApart = { apart++ }))
        compose.onNodeWithTag("capture-duplicate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cafe Seventy Three").assertIsDisplayed()
        compose.onNodeWithTag("capture-confirm").assertDoesNotExist()
        compose.onNodeWithTag("capture-same-payment").performClick()
        compose.onNodeWithTag("capture-keep-apart").performClick()
        assertEquals(1, same); assertEquals(1, apart)
    }

    @Test fun onceDifferentTheEntryCanBeSavedAsNew() {
        settleReview(CaptureReviewUiState(isLoading = false, draft = draft, accounts = listOf(bank), accountId = bank.id, type = TransactionType.EXPENSE,
            ledger = listOf(spendEntry("earlier")), keptApart = setOf(TransactionId("earlier"))), CaptureSettleActions())
        compose.onNodeWithTag("capture-duplicate").assertDoesNotExist()
        compose.onNodeWithTag("capture-confirm").assertIsEnabled()
    }

    @Test fun anAnswerWaitingToBeWrittenCanBeUndoneOrFinished() {
        var undo = 0; var finish = 0
        val settlement = CaptureSettlement(SettleKind.SAME_PAYMENT, spendEntry("earlier"))
        settleReview(CaptureReviewUiState(isLoading = false, draft = draft, accounts = listOf(bank), settlement = settlement),
            CaptureSettleActions(onUndo = { undo++ }, onFinish = { finish++ }))
        compose.onNodeWithTag("capture-settled").assertIsDisplayed()
        compose.onNodeWithText("Marked as the same payment").assertIsDisplayed()
        compose.onNodeWithTag("capture-undo").performClick()
        compose.onNodeWithTag("capture-done").performClick()
        assertEquals(1, undo); assertEquals(1, finish)
    }

    @Test fun aWrittenAnswerHasNoUndo() {
        val written = CaptureSettlement(SettleKind.SAME_PAYMENT, spendEntry("earlier"), stage = SettleStage.SAVED)
        settleReview(CaptureReviewUiState(isLoading = false, draft = draft, accounts = listOf(bank), settlement = written), CaptureSettleActions())
        compose.onNodeWithTag("capture-undo").assertDoesNotExist()
        compose.onNodeWithTag("capture-done").assertIsEnabled()
    }

    @Test fun anOfflineAnswerIsSavedOnThePhone() {
        val queued = CaptureSettlement(SettleKind.SAME_PAYMENT, spendEntry("earlier"), stage = SettleStage.QUEUED)
        settleReview(CaptureReviewUiState(isLoading = false, draft = draft, accounts = listOf(bank), settlement = queued), CaptureSettleActions())
        compose.onNodeWithText("saved on this phone", substring = true).assertIsDisplayed()
    }

    @Test fun aFailedWriteOffersTryAgainAndUndo() {
        var retry = 0; var undo = 0
        val failed = CaptureSettlement(SettleKind.SAME_PAYMENT, spendEntry("earlier"), stage = SettleStage.FAILED)
        settleReview(CaptureReviewUiState(isLoading = false, draft = draft, accounts = listOf(bank), settlement = failed, message = CaptureMessage.FAILED),
            CaptureSettleActions(onRetry = { retry++ }, onUndo = { undo++ }))
        compose.onNodeWithTag("capture-retry").performClick()
        compose.onNodeWithTag("capture-undo-choice").performClick()
        assertEquals(1, retry); assertEquals(1, undo)
    }

    @Test fun aCreditCanBeChosenAsATransfer() {
        var transfer = 0
        val ledger = listOf(legEntry("t1-in", TransactionType.TRANSFER_IN, bank), legEntry("t1-out", TransactionType.TRANSFER_OUT, savings))
        val base = CaptureReviewUiState(isLoading = false, draft = creditDraft, accounts = listOf(bank, savings), accountId = bank.id, ledger = ledger)
        settleReview(base, CaptureSettleActions(onTransfer = { transfer++ }))
        compose.onNodeWithTag("capture-type-transfer").performScrollTo().performClick()
        assertEquals(1, transfer)
    }

    @Test fun theMatchingTransferIsListedAndCanBeLinkedOnlyOnceChosen() {
        var picked: TransactionId? = null
        val ledger = listOf(legEntry("t1-in", TransactionType.TRANSFER_IN, bank), legEntry("t1-out", TransactionType.TRANSFER_OUT, savings))
        val base = CaptureReviewUiState(isLoading = false, draft = creditDraft, accounts = listOf(bank, savings), accountId = bank.id, ledger = ledger, asTransfer = true)
        settleReview(base, CaptureSettleActions(onTransferChoice = { picked = it }))
        compose.onNodeWithText("Savings → Commercial Bank").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capture-link-transfer").assertIsNotEnabled()
        compose.onNodeWithTag("capture-transfer-t1-in").performClick()
        assertEquals(TransactionId("t1-in"), picked)
    }

    @Test fun aChosenTransferCanBeLinked() {
        var link = 0
        val ledger = listOf(legEntry("t1-in", TransactionType.TRANSFER_IN, bank), legEntry("t1-out", TransactionType.TRANSFER_OUT, savings))
        settleReview(CaptureReviewUiState(isLoading = false, draft = creditDraft, accounts = listOf(bank, savings), accountId = bank.id, ledger = ledger,
            asTransfer = true, transferChoice = TransactionId("t1-in")), CaptureSettleActions(onLinkTransfer = { link++ }))
        compose.onNodeWithTag("capture-link-transfer").assertIsEnabled().performClick()
        assertEquals(1, link)
    }

    @Test fun withNoMatchingTransferNothingCanBeLinked() {
        settleReview(CaptureReviewUiState(isLoading = false, draft = creditDraft, accounts = listOf(bank), accountId = bank.id, asTransfer = true), CaptureSettleActions())
        compose.onNodeWithText("No transfer between your accounts matches", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capture-link-transfer").assertIsNotEnabled()
    }

}
