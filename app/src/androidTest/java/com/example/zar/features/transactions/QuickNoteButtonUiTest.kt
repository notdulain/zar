package com.example.zar.features.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryContent
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Quick note button in the Add sheet opens the real Quick note window; it no longer just focuses the note field. */
class QuickNoteButtonUiTest {
    @get:Rule val compose = createComposeRule()

    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))

    @Test
    fun tappingQuickNoteAsksToOpenTheQuickNoteWindowAndChangesNothingElse() {
        var opened = 0
        val kinds = mutableListOf<ManualEntryKind>()
        compose.setContent {
            ZarTheme {
                ManualEntryContent(
                    ManualEntryUiState(isLoading = false, kind = ManualEntryKind.TRANSFER, accounts = listOf(bank), accountId = bank.id),
                    focusNote = false,
                    actions = ManualEntryActions(
                        onClose = {}, onOpenIncome = {}, onAddAccount = {}, onKind = { kinds += it }, onAmount = {}, onDate = {}, onTime = {},
                        onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {}, onAddCategoryCancel = {},
                        onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {}, onFee = {},
                        onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
                        onQuickNote = { opened++ },
                    ),
                )
            }
        }
        compose.onNodeWithTag("manual-quick-note").performClick()
        compose.runOnIdle {
            assertEquals(1, opened)
            assertEquals("the tab is not switched behind the new window", emptyList<ManualEntryKind>(), kinds)
        }
    }
}
