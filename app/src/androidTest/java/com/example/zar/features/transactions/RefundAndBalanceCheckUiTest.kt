package com.example.zar.features.transactions

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.accounts.domain.reconcile.CheckAvailability
import com.example.zar.features.accounts.domain.reconcile.CheckProblem
import com.example.zar.features.accounts.domain.reconcile.CheckResult
import com.example.zar.features.accounts.domain.reconcile.ReconcileRules
import com.example.zar.features.accounts.presentation.screen.CheckActions
import com.example.zar.features.accounts.presentation.screen.CheckBalanceBody
import com.example.zar.features.accounts.presentation.viewmodel.BalanceCheckUiState
import com.example.zar.features.accounts.presentation.viewmodel.CheckMessage
import com.example.zar.features.accounts.presentation.viewmodel.GapChoice
import com.example.zar.features.transactions.domain.activity.EntryHistory
import com.example.zar.features.transactions.domain.activity.model.EditKind
import com.example.zar.features.transactions.domain.activity.model.EntryDetail
import com.example.zar.features.transactions.domain.refund.RefundContext
import com.example.zar.features.transactions.domain.refund.RefundProblem
import com.example.zar.features.transactions.domain.refund.RefundRules
import com.example.zar.features.transactions.presentation.activity.screen.DetailActions
import com.example.zar.features.transactions.presentation.activity.screen.EntryDetailBody
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailUiState
import com.example.zar.features.transactions.presentation.refund.screen.RefundActions
import com.example.zar.features.transactions.presentation.refund.screen.RefundFormBody
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundFormUiState
import com.example.zar.features.transactions.presentation.refund.viewmodel.RefundMessage
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The refund form, the refund and adjustment details, and the balance-check sheet (Figma Z59–Z72). */
class RefundAndBalanceCheckUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)
    private val opened = LocalDate.of(2026, 10, 1)
    private val t0 = Instant.parse("2026-10-24T10:00:00Z")
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Known(lkr(2_000)), opened)
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(lkr(10_000)), opened)

    private fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        account: Account = cash,
        merchant: String? = null,
        refundOf: String? = null,
        date: LocalDate = today,
        source: RecordSource = RecordSource.MANUAL,
    ) = Transaction(
        id = TransactionId(id),
        accountId = account.id,
        type = type,
        amount = lkr(amount),
        date = date,
        reportingConversion = ReportingConversion.SameCurrency,
        spendingClass = if (type == TransactionType.EXPENSE) SpendingClass.DISCRETIONARY else null,
        merchant = merchant,
        refundOf = refundOf?.let(::TransactionId),
        source = source,
        createdAt = t0,
    )

    private fun entry(tx: Transaction) = LedgerEntry(tx, tx, emptyList(), null, emptyList())

    private val purchase = entry(tx("keells", TransactionType.EXPENSE, 8_400, merchant = "Keells Super"))
    private fun refundEntry(id: String, amount: Long) = entry(tx(id, TransactionType.REFUND, amount, merchant = "Keells Super", refundOf = "keells"))

    // --- Refund form (Z60, Z61) ------------------------------------------------------------------

    private class RefundRecorder {
        var closed = 0
        var amount: String? = null
        var note: String? = null
        var saves = 0
    }

    private fun refundState(
        refunds: List<LedgerEntry> = emptyList(),
        block: RefundFormUiState.() -> RefundFormUiState = { this },
    ): RefundFormUiState {
        val summary = RefundRules.summary(purchase, listOf(purchase) + refunds)
        return RefundFormUiState(isLoading = false, context = RefundContext(summary, cash), date = today).block()
    }

    private fun showRefund(state: RefundFormUiState, rec: RefundRecorder = RefundRecorder()) {
        compose.setContent {
            ZarTheme {
                RefundFormBody(
                    state,
                    RefundActions(
                        onClose = { rec.closed++ },
                        onAmount = { rec.amount = it },
                        onDate = {},
                        onNote = { rec.note = it },
                        onSave = { rec.saves++ },
                    ),
                )
            }
        }
    }

    @Test
    fun theRefundFormNamesThePurchaseAndHowMuchCanBeRefunded() {
        showRefund(refundState())

        compose.onNodeWithTag("refund-banner").assertIsDisplayed()
        compose.onNodeWithText("Refund of Keells Super").assertIsDisplayed()
        compose.onNodeWithTag("refund-helper").assertTextContains("Up to LKR 8,400 · nothing refunded yet")
        compose.onNodeWithTag("refund-currency").assertTextContains("LKR")
        compose.onNodeWithTag("refund-account").assertTextContains("Cash")
        compose.onNodeWithText("Same as the purchase").assertIsDisplayed()
        compose.onNodeWithTag("refund-save").assertIsEnabled()
    }

    @Test
    fun theHelperSaysWhatIsAlreadyRefunded() {
        showRefund(refundState(listOf(refundEntry("r1", 2_000))))

        compose.onNodeWithTag("refund-helper").assertTextContains("Up to LKR 6,400 · LKR 2,000 already refunded")
    }

    @Test
    fun typingSavingAndClosingAreReported() {
        val rec = RefundRecorder()
        showRefund(refundState(), rec)

        // The form is shown with fixed state, so each field is checked right after it is typed into.
        compose.onNodeWithTag("refund-amount").performTextInput("2000")
        assertEquals("2000", rec.amount)
        compose.onNodeWithTag("refund-note").performTextInput("Damaged")
        assertEquals("Damaged", rec.note)
        compose.onNodeWithTag("refund-save").performClick()
        compose.onNodeWithTag("refund-cancel").performClick()
        compose.onNodeWithTag("refund-close").performClick()

        assertEquals(1, rec.saves)
        assertEquals(2, rec.closed)
    }

    @Test
    fun anAmountOverWhatIsLeftShowsTheLimitInRed() {
        showRefund(refundState(listOf(refundEntry("r1", 2_000))) { copy(amount = "9000", problems = setOf(RefundProblem.AMOUNT_TOO_HIGH)) })

        capture("refund-too-high-reviewed.png")
        compose.onNodeWithTag("refund-error-amount")
            .assertTextContains("More than is left to refund: LKR 6,400 (LKR 2,000 already refunded).")
    }

    @Test
    fun anAmountOverThePurchaseWithNoRefundsYetStatesThePurchaseLimit() {
        showRefund(refundState { copy(problems = setOf(RefundProblem.AMOUNT_TOO_HIGH)) })

        compose.onNodeWithTag("refund-error-amount").assertTextContains("You can refund up to LKR 8,400.", substring = true)
    }

    @Test
    fun theRefundFormShowsAnOfflineNoteAndSaveFailures() {
        showRefund(refundState { copy(isOnline = false, message = RefundMessage.FAILED) })

        compose.onNodeWithTag("refund-offline").assertIsDisplayed()
        compose.onNodeWithTag("refund-message").assertTextContains("Could not save the refund", substring = true)
    }

    @Test
    fun aPurchaseThatCannotTakeARefundSaysSoAndDisablesSave() {
        showRefund(RefundFormUiState(isLoading = false, notRefundable = true))

        compose.onNodeWithTag("refund-not-refundable").assertIsDisplayed()
        compose.onNodeWithTag("refund-save").assertIsNotEnabled()
    }

    // --- Entry detail: refunds and adjustments (Z63–Z65, Z71) --------------------------------------

    private class DetailRecorder {
        var refund: String? = null
        var opened: String? = null
        var voidTapped = 0
    }

    private fun detail(
        entry: LedgerEntry,
        account: Account = cash,
        refunds: List<LedgerEntry>? = null,
        canRefund: Boolean = false,
        refundedPurchase: LedgerEntry? = null,
    ): EntryDetail {
        val summary = refunds?.let { RefundRules.summary(entry, listOf(entry) + it) }
        return EntryDetail(
            entry = entry,
            account = account,
            counterpart = null,
            counterpartAccount = null,
            fee = null,
            history = EntryHistory.build(entry),
            editKind = if (entry.current.type == TransactionType.EXPENSE) EditKind.EXPENSE else EditKind.NONE,
            canVoid = summary?.hasRefunds != true,
            refunds = summary,
            canRefund = canRefund,
            refundedPurchase = refundedPurchase,
            refundedPurchaseAccount = refundedPurchase?.let { cash },
        )
    }

    private fun showDetail(detail: EntryDetail, rec: DetailRecorder = DetailRecorder()) {
        compose.setContent {
            ZarTheme {
                EntryDetailBody(
                    EntryDetailUiState(content = EntryDetailContent.Ready(detail)),
                    DetailActions(
                        onBack = {},
                        onEdit = {},
                        onEditIncome = {},
                        onAddAgain = {},
                        onRefund = { rec.refund = it },
                        onOpenEntry = { rec.opened = it },
                        onVoid = { rec.voidTapped++ },
                        onDismissVoid = {},
                        onConfirmVoid = {},
                    ),
                )
            }
        }
    }

    @Test
    fun anUnrefundedPurchaseOffersToLinkARefund() {
        val rec = DetailRecorder()
        showDetail(detail(purchase, refunds = emptyList(), canRefund = true), rec)

        compose.onNodeWithTag("detail-refund-prompt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Got money back for this?").assertExists()
        compose.onNodeWithTag("detail-refund-prompt").performClick()

        assertEquals("keells", rec.refund)
    }

    @Test
    fun aPurchaseThatCannotTakeARefundDoesNotOfferOne() {
        showDetail(detail(purchase, refunds = emptyList(), canRefund = false))

        compose.onNodeWithTag("detail-refund-prompt").assertDoesNotExist()
        compose.onNodeWithTag("detail-refunds").assertDoesNotExist()
    }

    @Test
    fun aPartlyRefundedPurchaseListsItsRefundsAndWhatIsStillRefundable() {
        val rec = DetailRecorder()
        showDetail(detail(purchase, refunds = listOf(refundEntry("r1", 2_000)), canRefund = true), rec)

        compose.onNodeWithTag("detail-refunded").assertTextContains("Refunded LKR 2,000 · net LKR 6,400 in October")
        compose.onNodeWithTag("detail-refunds").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Still refundable: LKR 6,400").assertExists()

        compose.onNodeWithTag("detail-refund-r1").performClick()
        compose.onNodeWithTag("detail-refund-more").performClick()

        assertEquals("r1", rec.opened)
        assertEquals("keells", rec.refund)
    }

    @Test
    fun aFullyRefundedPurchaseSaysSoAndOffersNoMoreRefunds() {
        showDetail(detail(purchase, refunds = listOf(refundEntry("r1", 8_400)), canRefund = false))

        compose.onNodeWithTag("detail-refunds").performScrollTo()
        compose.onNodeWithText("Fully refunded").assertExists()
        compose.onNodeWithTag("detail-refund-more").assertDoesNotExist()
    }

    @Test
    fun aPurchaseWithActiveRefundsCannotBeVoidedUntilTheyAre() {
        val rec = DetailRecorder()
        showDetail(detail(purchase, refunds = listOf(refundEntry("r1", 2_000)), canRefund = true), rec)

        compose.onNodeWithTag("detail-void").assertIsNotEnabled()
        compose.onNodeWithText("Undo this purchase's refunds first.", substring = true).assertExists()
    }

    @Test
    fun aRefundDetailLinksBackToItsPurchaseAndIsNotIncome() {
        val rec = DetailRecorder()
        showDetail(detail(refundEntry("r1", 2_000), refundedPurchase = purchase), rec)

        compose.onNodeWithTag("detail-title").assertTextContains("Refund · Keells Super")
        compose.onNodeWithTag("detail-amount").assertTextContains("2,000", substring = true)
        compose.onNodeWithText("Keells Super · LKR 8,400").assertExists()
        compose.onNodeWithText("October · the purchase's month").assertExists()
        compose.onNodeWithTag("detail-linked-purchase").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not income.", substring = true).assertExists()
        capture("refund-detail-reviewed.png")
        compose.onNodeWithText("Void refund").assertExists()
        compose.onNodeWithTag("detail-edit").assertDoesNotExist()

        compose.onNodeWithTag("detail-linked-purchase").performClick()
        assertEquals("keells", rec.opened)
    }

    @Test
    fun aRefundWhosePurchaseIsMissingStillOpens() {
        showDetail(detail(refundEntry("r1", 2_000), refundedPurchase = null))

        compose.onNodeWithTag("detail-linked-purchase").performScrollTo()
        compose.onNodeWithText("The purchase this refund belongs to could not be found.").assertExists()
    }

    @Test
    fun aCashShortfallAdjustmentIsUnknownSpendingAndNeverDiscretionary() {
        val adjustment = entry(tx("a1", TransactionType.ADJUSTMENT_OUT, 450, source = RecordSource.RECONCILIATION))
        showDetail(detail(adjustment))

        compose.onNodeWithTag("detail-title").assertTextContains("Cash adjustment")
        compose.onNodeWithText("Cash count was LKR 450 short").assertExists()
        compose.onNodeWithText("Unknown").assertExists()
        compose.onNodeWithTag("detail-adjustment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Why it shows as unknown", ignoreCase = true).assertExists()
        compose.onNodeWithText("Never discretionary").assertExists()
        compose.onNodeWithTag("detail-edit").assertDoesNotExist()
    }

    @Test
    fun aBankSurplusAdjustmentIsBalanceOnly() {
        val adjustment = entry(tx("a2", TransactionType.ADJUSTMENT_IN, 450, bank, source = RecordSource.RECONCILIATION))
        showDetail(detail(adjustment, account = bank))

        compose.onNodeWithTag("detail-title").assertTextContains("Balance adjustment")
        compose.onNodeWithText("Balance was LKR 450 higher than recorded").assertExists()
        // "Balance only" is both the Counts as value and the first line of the explanation card.
        compose.onAllNodesWithText("Balance only").assertCountEquals(2)
        compose.onNodeWithText("Why it is not income", ignoreCase = true).assertExists()
    }

    // --- Balance check sheet (Z29, Z66–Z68, Z70) ------------------------------------------------------

    private class CheckRecorder {
        var dismissed = 0
        var counted: String? = null
        var choice: GapChoice? = null
        var confirmed = 0
    }

    private fun checkState(
        account: Account = cash,
        counted: String = "",
        choice: GapChoice = GapChoice.LEAVE,
        block: BalanceCheckUiState.() -> BalanceCheckUiState = { this },
    ): BalanceCheckUiState {
        // The account has one 500 expense, so cash is recorded at 1,500 and the bank at 9,500.
        val spent = entry(tx("e1", TransactionType.EXPENSE, 500, account))
        val balance = AccountBalanceCalculator.calculate(account, listOf(spent))
        val check = (ReconcileRules.evaluate(balance, counted) as? CheckResult.Valid)?.check
        return BalanceCheckUiState(
            isLoading = false,
            balance = balance,
            availability = ReconcileRules.availability(balance),
            counted = counted,
            check = check,
            choice = choice,
        ).block()
    }

    private fun showCheck(state: BalanceCheckUiState, rec: CheckRecorder = CheckRecorder()) {
        compose.setContent {
            ZarTheme {
                CheckBalanceBody(
                    state,
                    CheckActions(
                        onDismiss = { rec.dismissed++ },
                        onCounted = { rec.counted = it },
                        onChoice = { rec.choice = it },
                        onConfirm = { rec.confirmed++ },
                    ),
                )
            }
        }
    }

    @Test
    fun theCashSheetShowsTheRecordedBalanceBeforeAnythingIsTyped() {
        showCheck(checkState())

        compose.onNodeWithTag("check-sheet").assertIsDisplayed()
        compose.onNodeWithText("Check cash", substring = false).assertExists()
        compose.onNodeWithTag("check-recorded").assertTextContains("LKR 1,500")
        compose.onNodeWithTag("check-counted").assertTextContains("—")
        compose.onNodeWithTag("check-choice-leave").assertDoesNotExist()
        compose.onNodeWithTag("check-confirm").assertIsEnabled()
    }

    @Test
    fun typingAndConfirmingAreReported() {
        val rec = CheckRecorder()
        showCheck(checkState(), rec)

        compose.onNodeWithTag("check-amount").performTextInput("1000")
        compose.onNodeWithTag("check-confirm").performClick()
        compose.onNodeWithTag("check-cancel").performClick()

        assertEquals("1000", rec.counted)
        assertEquals(1, rec.confirmed)
        assertEquals(1, rec.dismissed)
    }

    @Test
    fun aCashShortfallShowsTheGapAndTheTwoWaysToHandleIt() {
        val rec = CheckRecorder()
        showCheck(checkState(counted = "1000"), rec)

        compose.onNodeWithTag("check-counted").assertTextContains("LKR 1,000")
        compose.onNodeWithTag("check-difference").assertTextContains("− LKR 500")
        compose.onNodeWithText("The LKR 500", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithTag("check-choice-leave").assertIsDisplayed()
        compose.onNodeWithTag("check-choice-find").assertIsDisplayed()
        compose.onNodeWithTag("check-note").assertTextContains("cash adjustment", substring = true)
        compose.onNodeWithText("Confirm count").assertExists()

        compose.onNodeWithTag("check-choice-find").performClick()
        assertEquals(GapChoice.FIND, rec.choice)
        capture("check-balance-shortfall-reviewed.png")
    }

    @Test
    fun choosingToFindTheMissingEntryChangesTheNoteAndTheButton() {
        showCheck(checkState(counted = "1000", choice = GapChoice.FIND))

        compose.onNodeWithTag("check-note").assertTextContains("no adjustment is saved", substring = true)
        compose.onNodeWithText("Add the entry").assertExists()
    }

    @Test
    fun anExtraIsSavedAsAnIncomingAdjustmentAndCannotBeExplainedByAnExpense() {
        showCheck(checkState(counted = "1800"))

        compose.onNodeWithTag("check-difference").assertTextContains("+ LKR 300")
        compose.onNodeWithTag("check-choice-find").assertDoesNotExist()
        compose.onNodeWithTag("check-note").assertTextContains("isn't counted as income", substring = true)
    }

    @Test
    fun aMatchingCountNeedsNoAdjustment() {
        showCheck(checkState(counted = "1500"))

        compose.onNodeWithTag("check-difference").assertTextContains("LKR 0")
        compose.onNodeWithTag("check-choice-leave").assertDoesNotExist()
        compose.onNodeWithTag("check-note").assertTextContains("Nothing needs adjusting", substring = true)
        compose.onNodeWithText("Mark as checked").assertExists()
    }

    @Test
    fun aBankSheetTalksAboutTheStatement() {
        showCheck(checkState(account = bank, counted = "9000"))

        compose.onNodeWithText("Check balance").assertExists()
        compose.onNodeWithText("Statement says").assertExists()
        compose.onNodeWithTag("check-note").assertTextContains("balance adjustment", substring = true)
        compose.onNodeWithText("Confirm balance").assertExists()
    }

    @Test
    fun missingAndUnreadableAmountsShowTheirErrors() {
        showCheck(checkState { copy(problems = setOf(CheckProblem.AMOUNT_MISSING)) })
        compose.onNodeWithTag("check-error-amount").assertTextContains("Enter the amount, or 0")
    }

    @Test
    fun theSheetShowsOfflineAndSaveFailures() {
        showCheck(checkState(counted = "1000") { copy(isOnline = false, message = CheckMessage.FAILED) })

        compose.onNodeWithTag("check-offline").assertIsDisplayed()
        compose.onNodeWithTag("check-message").assertTextContains("Could not save", substring = true)
    }

    @Test
    fun anAccountThatCannotBeCheckedSaysWhy() {
        showCheck(checkState { copy(availability = CheckAvailability.BALANCE_UNKNOWN) })

        compose.onNodeWithTag("check-unavailable").assertIsDisplayed()
        compose.onNodeWithTag("check-amount").assertDoesNotExist()
        compose.onNodeWithTag("check-close").assertIsDisplayed()
    }

    @Test
    fun aSheetThatIsStillLoadingShowsNoForm() {
        showCheck(BalanceCheckUiState())

        compose.onNodeWithTag("check-amount").assertDoesNotExist()
        compose.onNodeWithTag("check-unavailable").assertDoesNotExist()
    }

    @Test
    fun refundDatePickerAndFormAreVisible() {
        val date = com.example.zar.core.domain.calculation.ZarTime.today()
        val datedPurchase = purchase.copy(original = purchase.original.copy(date = date), current = purchase.current.copy(date = date))
        val summary = RefundRules.summary(datedPurchase, listOf(datedPurchase))
        showRefund(RefundFormUiState(isLoading = false, context = RefundContext(summary, cash), date = date))
        capture("refund-form-reviewed.png")
        compose.onNodeWithTag("manual-date").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        capture("refund-date-picker-reviewed.png")
    }

    @Test
    fun extremeBalanceInputShowsAnErrorWithoutCrashing() {
        showCheck(checkState(counted = "-92233720368547758.08") { copy(problems = setOf(CheckProblem.AMOUNT_INVALID)) })
        compose.onNodeWithTag("check-error-amount").assertIsDisplayed()
        capture("check-balance-invalid-reviewed.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(500) // Include the fully opened platform window.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pr16").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
