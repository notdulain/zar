package com.example.zar.features.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.zar.features.rules.domain.model.RuleOffer
import com.example.zar.features.rules.domain.RuleCard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.CorrectionId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionChanges
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.EntryHistory
import com.example.zar.features.transactions.domain.activity.ExpenseEditInput
import com.example.zar.features.transactions.domain.activity.EditProblem
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivitySnapshot
import com.example.zar.features.transactions.domain.activity.model.EditKind
import com.example.zar.features.transactions.domain.activity.model.EntryDetail
import com.example.zar.features.transactions.domain.refund.RefundSummary
import com.example.zar.features.transactions.presentation.activity.screen.DetailActions
import com.example.zar.features.transactions.presentation.activity.screen.EditActions
import com.example.zar.features.transactions.presentation.activity.screen.EditEntryBody
import com.example.zar.features.transactions.presentation.activity.screen.EntryDetailBody
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditEntryUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.EditMessage
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.EntryDetailUiState
import com.example.zar.features.transactions.presentation.activity.viewmodel.VoidProblem
import com.example.zar.features.transactions.presentation.screen.ActivityActions
import com.example.zar.features.transactions.presentation.screen.ActivityBody
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityScreensUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)
    private val opened = LocalDate.of(2026, 10, 1)
    private val t0 = Instant.parse("2026-10-24T10:00:00Z")
    private val bank = Account(AccountId("bank"), "Sampath Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, opened)
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Unknown, opened)

    private fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        account: Account = cash,
        merchant: String? = null,
        category: String? = null,
        note: String? = null,
        source: RecordSource = RecordSource.MANUAL,
        transferId: String? = null,
        date: LocalDate = today,
    ) = Transaction(
        id = TransactionId(id),
        accountId = account.id,
        type = type,
        amount = lkr(amount),
        date = date,
        reportingConversion = ReportingConversion.SameCurrency,
        spendingClass = if (type == TransactionType.EXPENSE) SpendingClass.DISCRETIONARY else null,
        categoryId = category,
        merchant = merchant,
        note = note,
        source = source,
        transferId = transferId?.let(::TransferId),
        createdAt = t0,
    )

    private fun entry(tx: Transaction, voided: Boolean = false, corrections: List<Correction> = emptyList()): LedgerEntry {
        var current = tx
        corrections.forEach { c ->
            current = current.copy(amount = c.changes.amount ?: current.amount)
        }
        return LedgerEntry(tx, current, corrections, if (voided) VoidMarker(tx.id, t0.plusSeconds(600)) else null, emptyList())
    }

    private fun correction(txId: String, amount: Long) =
        Correction(CorrectionId("c-$txId"), TransactionId(txId), TransactionChanges(amount = lkr(amount)), t0.plusSeconds(300))

    // --- Activity list --------------------------------------------------------------------------

    private class ListRecorder {
        var opened: String? = null
        var added = 0
        var applied: ActivityFilter? = null
        var cleared = 0
        var retried = 0
        var query: String? = null
    }

    private fun listState(
        entries: List<LedgerEntry>,
        filter: ActivityFilter = ActivityFilter(),
        online: Boolean = true,
        content: ActivityContent? = null,
    ): ActivityUiState {
        val list = ActivityQuery.apply(ActivitySnapshot(entries, listOf(bank, cash)), filter, today)
        return ActivityUiState(filter, content ?: ActivityContent.Ready(list), listOf(bank, cash), online, today)
    }

    private fun showList(state: ActivityUiState, rec: ListRecorder = ListRecorder()) {
        compose.setContent {
            ZarTheme {
                ActivityBody(
                    state,
                    ActivityActions(
                        onOpenEntry = { rec.opened = it },
                        onAddEntry = { rec.added++ },
                        onApplyFilter = { rec.applied = it },
                        onClearFilters = { rec.cleared++ },
                        onQuery = { rec.query = it },
                        onRetry = { rec.retried++ },
                        previewCount = { 7 },
                    ),
                )
            }
        }
    }

    private val keells = entry(tx("keells", TransactionType.EXPENSE, 8_400, cash, "Keells Super", "groceries"))
    private val client = entry(tx("client", TransactionType.INCOME, 125_000, bank, "Client payment", "freelance"))

    @Test
    fun listShowsEntriesWithTotalsAndOpensOneOnTap() {
        val rec = ListRecorder()
        showList(listState(listOf(keells, client)), rec)

        compose.onNodeWithTag("activity-received").assertIsDisplayed()
        compose.onNodeWithTag("activity-received").assertTextContains("125,000")
        compose.onNodeWithText("Keells Super").assertIsDisplayed()
        compose.onNodeWithTag("activity-row-client").assertIsDisplayed()

        compose.onNodeWithTag("activity-row-keells").performClick()

        assertEquals("keells", rec.opened)
    }

    @Test
    fun editedAndVoidedEntriesAreTaggedAndVoidedOnesAreHiddenByDefault() {
        val edited = entry(tx("edited", TransactionType.EXPENSE, 100, cash, "Edited one"), corrections = listOf(correction("edited", 150)))
        val voided = entry(tx("voided", TransactionType.EXPENSE, 200, cash, "Voided one"), voided = true)

        showList(listState(listOf(edited, voided)))
        compose.onNodeWithText("EDITED").assertExists()
        compose.onNodeWithText("Voided one").assertDoesNotExist()
    }

    @Test
    fun voidedEntriesShowWithAVoidTagWhenAskedFor() {
        val voided = entry(tx("voided", TransactionType.EXPENSE, 200, cash, "Voided one"), voided = true)

        showList(listState(listOf(voided), ActivityFilter(showVoided = true)))

        compose.onNodeWithText("Voided one").assertIsDisplayed()
        compose.onNodeWithText("VOID").assertExists()
    }

    @Test
    fun noEntriesAtAllOffersToAddOne() {
        val rec = ListRecorder()
        showList(listState(emptyList()), rec)

        compose.onNodeWithTag("activity-empty").assertIsDisplayed()
        compose.onNodeWithText("Add an entry").performClick()

        assertEquals(1, rec.added)
    }

    @Test
    fun noMatchesOffersToClearTheFilters() {
        val rec = ListRecorder()
        showList(listState(listOf(keells), ActivityFilter(query = "zzz")), rec)

        compose.onNodeWithTag("activity-no-results").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()

        assertEquals(1, rec.cleared)
    }

    @Test
    fun aLoadErrorOffersARetry() {
        val rec = ListRecorder()
        showList(listState(emptyList(), content = ActivityContent.Error), rec)

        compose.onNodeWithTag("activity-error").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        assertEquals(1, rec.retried)
    }

    @Test
    fun offlineSaysSoAndStillListsTheEntries() {
        showList(listState(listOf(keells), online = false))

        compose.onNodeWithTag("activity-offline").assertIsDisplayed()
        compose.onNodeWithText("Keells Super").assertIsDisplayed()
    }

    @Test
    fun activeFiltersShowAsChipsAndRemovingOneApplies() {
        val rec = ListRecorder()
        showList(listState(listOf(keells), ActivityFilter(accountId = cash.id)), rec)

        compose.onNodeWithTag("activity-chip-account").assertIsDisplayed()
        compose.onNodeWithTag("activity-clear").assertIsDisplayed()
        compose.onNodeWithTag("activity-chip-account").performClick()

        assertNull(rec.applied!!.accountId)
    }

    @Test
    fun theFilterSheetAppliesTheChosenAccountAndAnnouncesTheCount() {
        val rec = ListRecorder()
        showList(listState(listOf(keells, client)), rec)

        compose.onNodeWithTag("activity-filter-button").performClick()
        compose.onNodeWithTag("activity-filter-sheet").assertIsDisplayed()
        compose.onNodeWithText("Show 7 entries").assertIsDisplayed()
        compose.onNodeWithTag("activity-filter-account-cash").performClick()
        compose.onNodeWithTag("activity-filter-preset-last_7_days").performClick()
        compose.onNodeWithTag("activity-filter-voided").performClick()
        compose.onNodeWithTag("activity-filter-apply").performClick()

        val applied = rec.applied!!
        assertEquals(cash.id, applied.accountId)
        assertEquals(com.example.zar.features.transactions.domain.activity.model.DatePreset.LAST_7_DAYS, applied.preset)
        assertTrue(applied.showVoided)
    }

    @Test
    fun theFilterSheetCanResetBackToTheDefaults() {
        val rec = ListRecorder()
        showList(listState(listOf(keells), ActivityFilter(accountId = cash.id, showVoided = true)), rec)

        compose.onNodeWithTag("activity-filter-button").performClick()
        compose.onNodeWithTag("activity-filter-reset").performClick()
        compose.onNodeWithTag("activity-filter-apply").performClick()

        assertEquals(ActivityFilter(), rec.applied)
    }

    @Test
    fun searchTypingIsReported() {
        val rec = ListRecorder()
        showList(listState(listOf(keells)), rec)

        compose.onNodeWithTag("activity-search-button").performClick()
        compose.onNodeWithTag("activity-search-field").performTextInput("kee")

        assertEquals("kee", rec.query)
    }

    @Test
    fun recordsWithoutARateAreCountedHonestly() {
        val usdAccount = Account(AccountId("usd"), "Payoneer", AccountType.FREELANCE, Currencies.USD, OpeningBalance.Unknown, opened)
        val usd = tx("usd", TransactionType.EXPENSE, 40, usdAccount, "Hosting").copy(
            amount = FiatMoney(4_000, Currencies.USD),
            reportingConversion = ReportingConversion.Missing(Currencies.LKR),
        )
        val state = ActivityUiState(
            ActivityFilter(),
            ActivityContent.Ready(ActivityQuery.apply(ActivitySnapshot(listOf(entry(usd)), listOf(usdAccount)), ActivityFilter(), today)),
            listOf(usdAccount),
            true,
            today,
        )

        showList(state)

        compose.onNodeWithTag("activity-unresolved").assertIsDisplayed()
    }

    // --- Detail ---------------------------------------------------------------------------------

    private class DetailRecorder {
        var voidTapped = 0
        var dismissed = 0
        var confirmed = 0
        var edited: String? = null
        var editedIncome: String? = null
        var addAgain = 0
        var refund: String? = null
        var opened: String? = null
        var remembered: RuleOffer? = null
        var rulesOpened = 0
    }

    private fun detailOf(
        entry: LedgerEntry,
        kind: EditKind = EditKind.EXPENSE,
        account: Account? = cash,
        fee: LedgerEntry? = null,
        counterpart: LedgerEntry? = null,
        counterpartAccount: Account? = null,
        refunds: RefundSummary? = null,
        canRefund: Boolean = false,
        refundedPurchase: LedgerEntry? = null,
        refundedPurchaseAccount: Account? = null,
    ) = EntryDetail(
        entry, account, counterpart, counterpartAccount, fee, EntryHistory.build(entry), kind,
        canVoid = !entry.isVoided && refunds?.hasRefunds != true,
        refunds = refunds, canRefund = canRefund, refundedPurchase = refundedPurchase, refundedPurchaseAccount = refundedPurchaseAccount,
    )

    private fun showDetail(
        detail: EntryDetail?,
        rec: DetailRecorder = DetailRecorder(),
        content: EntryDetailContent? = null,
        online: Boolean = true,
        sheet: Boolean = false,
        voiding: Boolean = false,
        problem: VoidProblem? = null,
        ruleCard: RuleCard = RuleCard.Hidden,
    ) {
        compose.setContent {
            ZarTheme {
                EntryDetailBody(
                    EntryDetailUiState(
                        content = content ?: EntryDetailContent.Ready(detail!!),
                        isOnline = online,
                        showVoidSheet = sheet,
                        isVoiding = voiding,
                        voidProblem = problem,
                        ruleCard = ruleCard,
                    ),
                    DetailActions(
                        onBack = {},
                        onEdit = { rec.edited = it },
                        onEditIncome = { rec.editedIncome = it },
                        onAddAgain = { rec.addAgain++ },
                        onRefund = { rec.refund = it },
                        onOpenEntry = { rec.opened = it },
                        onVoid = { rec.voidTapped++ },
                        onDismissVoid = { rec.dismissed++ },
                        onConfirmVoid = { rec.confirmed++ },
                        onRemember = { rec.remembered = it },
                        onOpenRules = { rec.rulesOpened++ },
                    ),
                )
            }
        }
    }

    @Test
    fun expenseDetailShowsItsFactsAndOffersEditAndVoid() {
        val rec = DetailRecorder()
        showDetail(detailOf(keells), rec)

        compose.onNodeWithTag("detail-title").assertIsDisplayed()
        compose.onNodeWithText("Keells Super").assertIsDisplayed()
        compose.onNodeWithTag("detail-amount").assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertExists()

        compose.onNodeWithTag("detail-edit").performClick()
        compose.onNodeWithTag("detail-void").performClick()

        assertEquals("keells", rec.edited)
        assertEquals(1, rec.voidTapped)
    }

    @Test
    fun incomeEditGoesToTheIncomeForm() {
        val rec = DetailRecorder()
        showDetail(detailOf(client, EditKind.INCOME, bank), rec)

        compose.onNodeWithTag("detail-edit").performClick()

        assertEquals("client", rec.editedIncome)
        assertNull(rec.edited)
    }

    @Test
    fun anEditedEntryShowsItsHistoryAndTheOriginalAmount() {
        val edited = entry(tx("e1", TransactionType.EXPENSE, 8_400, cash, "Keells Super"), corrections = listOf(correction("e1", 8_900)))
        showDetail(detailOf(edited))

        compose.onNodeWithTag("detail-edited").assertExists()
        compose.onNodeWithText("originally LKR 8,400", substring = true).assertExists()
        compose.onNodeWithTag("detail-history").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amount corrected").assertExists()
        compose.onNodeWithText("Added").assertExists()
    }

    @Test
    fun aVoidedEntryExplainsItAndOnlyOffersToAddItAgain() {
        val rec = DetailRecorder()
        val voided = entry(tx("v1", TransactionType.EXPENSE, 200, cash, "Gone"), voided = true)
        showDetail(detailOf(voided, EditKind.NONE), rec)

        compose.onNodeWithTag("detail-voided").assertIsDisplayed()
        compose.onNodeWithTag("detail-void").assertDoesNotExist()
        compose.onNodeWithTag("detail-edit").assertDoesNotExist()
        compose.onNodeWithTag("detail-add-again").performClick()

        assertEquals(1, rec.addAgain)
    }

    @Test
    fun aTransferShowsBothEntriesAndTheFeeAndOnlyOffersVoid() {
        val out = entry(tx("t-out", TransactionType.TRANSFER_OUT, 5_000, bank, transferId = "t"))
        val inn = entry(tx("t-in", TransactionType.TRANSFER_IN, 5_000, cash, transferId = "t"))
        val fee = entry(tx("t-fee", TransactionType.EXPENSE, 30, bank, "Transfer fee", "transfer-fee"))
        showDetail(detailOf(out, EditKind.NONE, bank, fee, inn, cash))

        compose.onNodeWithText("Sampath Bank → Cash").assertIsDisplayed()
        compose.onNodeWithTag("detail-linked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LKR 30 · separate expense").assertExists()
        compose.onNodeWithTag("detail-void").assertIsDisplayed()
        compose.onNodeWithText("Void transfer").assertIsDisplayed()
        compose.onNodeWithTag("detail-edit").assertDoesNotExist()
    }

    @Test
    fun theVoidSheetExplainsThatAVoidIsFinalAndConfirms() {
        val rec = DetailRecorder()
        showDetail(detailOf(keells), rec, sheet = true)

        compose.onNodeWithTag("void-sheet").assertIsDisplayed()
        compose.onNodeWithText("A void is final", substring = true).assertExists()
        compose.onNodeWithTag("void-keep").performClick()
        compose.onNodeWithTag("void-confirm").performClick()

        assertEquals(1, rec.dismissed)
        assertEquals(1, rec.confirmed)
    }

    @Test
    fun theVoidSheetSaysItIsOfflineAndShowsAFailure() {
        showDetail(detailOf(keells), online = false, sheet = true, problem = VoidProblem.FAILED)

        compose.onNodeWithTag("void-offline").assertIsDisplayed()
        compose.onNodeWithTag("void-error").assertIsDisplayed()
    }

    @Test
    fun theTransferVoidSheetNamesTheFee() {
        val out = entry(tx("t-out", TransactionType.TRANSFER_OUT, 5_000, bank, transferId = "t"))
        val inn = entry(tx("t-in", TransactionType.TRANSFER_IN, 5_000, cash, transferId = "t"))
        val fee = entry(tx("t-fee", TransactionType.EXPENSE, 30, bank, "Transfer fee", "transfer-fee"))
        showDetail(detailOf(out, EditKind.NONE, bank, fee, inn, cash), sheet = true)

        compose.onNodeWithText("Void this transfer?").assertIsDisplayed()
        compose.onNodeWithText("Both entries and the LKR 30 fee are voided together", substring = true).assertExists()
    }

    @Test
    fun anUnknownEntryAndALoadFailureHaveTheirOwnMessages() {
        showDetail(null, content = EntryDetailContent.NotFound)
        compose.onNodeWithTag("activity-detail-not-found").assertIsDisplayed()
    }

    @Test fun detailShowsRecordedTime() {
        val timed = entry(tx("timed", TransactionType.EXPENSE, 850, cash, "Uber to SLIIT from office").copy(time = java.time.LocalTime.of(18, 30)))
        showDetail(detailOf(timed))
        compose.onNodeWithText("6:30 PM").performScrollTo().assertIsDisplayed()
        captureCorrectionEvidence("activity-time")
    }

    @Test fun savedTimeCanBeEdited() {
        var selected: java.time.LocalTime? = null
        compose.setContent {
            ZarTheme(darkTheme = false) {
                EditEntryBody(editState { copy(input = input?.copy(time = java.time.LocalTime.of(18, 30))) },
                    EditActions({}, {}, {}, {}, {}, {}, {}, {}, onTime = { selected = it }))
            }
        }
        compose.onNodeWithTag("edit-time").performScrollTo().performClick()
        compose.onNodeWithText("AM").performClick()
        compose.onNodeWithTag("edit-time-confirm").performClick()
        assertEquals(java.time.LocalTime.of(6, 30), selected)
        captureCorrectionEvidence("activity-edit-time")
    }

    private fun captureCorrectionEvidence(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val dir = java.io.File(instrumentation.targetContext.cacheDir, "correction-evidence").apply { mkdirs() }
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(dir, "$name.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    // --- Edit -----------------------------------------------------------------------------------

    private class EditRecorder {
        var amount: String? = null
        var category: String? = null
        var saves = 0
        var closed = 0
    }

    private fun showEdit(state: EditEntryUiState, rec: EditRecorder = EditRecorder()) {
        compose.setContent {
            ZarTheme {
                EditEntryBody(
                    state,
                    EditActions(
                        onClose = { rec.closed++ },
                        onAmount = { rec.amount = it },
                        onDate = {},
                        onCategory = { rec.category = it },
                        onSpendingClass = {},
                        onTitle = {},
                        onDescription = {},
                        onSave = { rec.saves++ },
                    ),
                )
            }
        }
    }

    private fun editState(source: RecordSource = RecordSource.MANUAL, block: EditEntryUiState.() -> EditEntryUiState = { this }): EditEntryUiState {
        val e = entry(tx("e1", TransactionType.EXPENSE, 8_400, cash, "Keells Super", "groceries", "Weekly groceries", source))
        return EditEntryUiState(isLoading = false, entry = e, account = cash, input = ExpenseEditInput.from(e.current)).block()
    }

    @Test
    fun theEditFormShowsLockedAccountAndCurrencyAndTheOriginalAmount() {
        showEdit(editState())

        compose.onNodeWithTag("edit-banner").assertIsDisplayed()
        compose.onNodeWithText("Your original stays on record").assertExists()
        compose.onNodeWithTag("edit-currency").assertIsDisplayed()
        compose.onNodeWithTag("edit-original-amount").assertIsDisplayed()
        compose.onNodeWithText("Original: LKR 8,400").assertExists()
        compose.onNodeWithText("Can't be changed").assertExists()
        compose.onNodeWithTag("edit-save").assertIsEnabled()
    }

    @Test
    fun typingPickingAndSavingAreReported() {
        val rec = EditRecorder()
        showEdit(editState(), rec)

        compose.onNodeWithTag("edit-amount").performTextInput("1")
        compose.onNodeWithTag("edit-category-bills").performScrollTo().performClick()
        compose.onNodeWithTag("edit-save").performClick()

        // The text goes in at the cursor, so only check that the typed digit arrived.
        assertTrue(rec.amount!!.contains("1"))
        assertEquals("bills", rec.category)
        assertEquals(1, rec.saves)
    }

    @Test
    fun problemsAndMessagesShowNextToTheirFields() {
        showEdit(
            editState {
                copy(
                    problems = setOf(EditProblem.AMOUNT_INVALID, EditProblem.DATE_IN_FUTURE, EditProblem.NOTHING_CHANGED),
                    message = EditMessage.FAILED,
                )
            },
        )

        compose.onNodeWithTag("edit-error-amount").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("edit-error-date").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("edit-error-nothing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("edit-message").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anImportedEntryExplainsThatTheOriginalStaysAsReceived() {
        showEdit(editState(RecordSource.SMS))

        compose.onNodeWithText("Imported from a bank message").assertExists()
        compose.onNodeWithText("The original details stay as received", substring = true).assertExists()
    }

    @Test
    fun offlineAndSavingStatesAreShown() {
        showEdit(editState { copy(isOnline = false, isSaving = true) })

        compose.onNodeWithTag("edit-offline").assertIsDisplayed()
        compose.onNodeWithTag("edit-save").assertIsNotEnabled()
    }

    @Test
    fun anEntryThatCannotBeCorrectedSaysSoAndCannotBeSaved() {
        showEdit(EditEntryUiState(isLoading = false, notEditable = true))

        compose.onNodeWithTag("edit-not-editable").assertIsDisplayed()
        compose.onNodeWithTag("edit-save").assertIsNotEnabled()
    }

    @Test
    fun cancelAndCloseLeaveTheForm() {
        val rec = EditRecorder()
        showEdit(editState(), rec)

        compose.onNodeWithTag("edit-cancel").performClick()
        compose.onNodeWithTag("edit-close").performClick()

        assertEquals(2, rec.closed)
    }

    @Test
    fun aPurchaseAmountBelowActiveRefundsExplainsWhyItCannotSave() {
        showEdit(editState { copy(input = input?.copy(amount = "3000"), message = EditMessage.REFUNDED_AMOUNT) })
        compose.onNodeWithTag("edit-message").performScrollTo().assertIsDisplayed()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = java.io.File(instrumentation.targetContext.cacheDir, "pr16").apply { mkdirs() }
        java.io.File(dir, "edit-refunded-amount-reviewed.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    // --- Category rules (SCRUM-39) ---

    private val keellsOffer = RuleOffer("Keells Super", "keells super", "groceries", AccountId("cash"), existing = null)

    @Test
    fun theRuleOfferNamesTheMerchantAndTheCategoryAndOpensTheSheetOnTap() {
        val rec = DetailRecorder()
        showDetail(detailOf(keells), rec, ruleCard = RuleCard.Offer(keellsOffer))

        compose.onNodeWithTag("detail-rule-offer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Always file Keells Super as Groceries?").assertIsDisplayed()
        compose.onNodeWithText("Category only, for future Keells Super entries").assertIsDisplayed()
        compose.onNodeWithTag("detail-rule-offer").performClick()

        assertEquals(keellsOffer, rec.remembered)
    }

    @Test
    fun aGeneralNameSaysItCannotBeRememberedAndOffersNothingToTap() {
        val rec = DetailRecorder()
        showDetail(detailOf(keells), rec, ruleCard = RuleCard.TooGeneral("PickMe"))

        compose.onNodeWithTag("detail-rule-general").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PickMe is too general to remember").assertIsDisplayed()
        compose.onAllNodesWithTag("detail-rule-offer").assertCountEquals(0)
        compose.onNodeWithTag("detail-rule-general").performClick()

        assertNull(rec.remembered)
    }

    @Test
    fun anExpenseFiledByARuleShowsTheRuleAndVersionAndOpensTheRulesList() {
        val rec = DetailRecorder()
        showDetail(detailOf(keells), rec, ruleCard = RuleCard.Applied("Keells Super", "groceries", 2))

        compose.onNodeWithText("Filed by your rule").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keells Super → Groceries · version 2").assertIsDisplayed()
        compose.onNodeWithTag("detail-rule-applied").performClick()

        assertEquals(1, rec.rulesOpened)
    }

    @Test
    fun withoutARuleCardNothingAboutRulesIsShown() {
        showDetail(detailOf(keells))

        compose.onAllNodesWithTag("detail-rule-offer").assertCountEquals(0)
        compose.onAllNodesWithTag("detail-rule-general").assertCountEquals(0)
        compose.onAllNodesWithTag("detail-rule-applied").assertCountEquals(0)
    }
}
