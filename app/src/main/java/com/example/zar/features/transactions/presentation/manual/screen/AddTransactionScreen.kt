package com.example.zar.features.transactions.presentation.manual.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zar.core.presentation.components.rememberSelectorIndicator
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormContent
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormViewModel
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryViewModel

/** The three tabs of the one Add-transaction session. */
enum class AddTransactionTab { EXPENSE, INCOME, TRANSFER }

/**
 * One Add-transaction session (Figma Z06/Z11/Z12): a single sheet whose Expense, Income and Transfer tabs each keep
 * their own draft until the session is dismissed. Both engines live under this one route, so switching tabs never
 * tears a draft down; nothing is written to the ledger until Save is pressed on the active tab.
 *
 * [initialKind] picks the opening tab ("expense" default, "income" from More → Received income, "transfer").
 * [focusNote] is the Add menu's "Quick note". Income edit is a separate route and is not opened here.
 */
@Composable
fun AddTransactionScreen(
    focusNote: Boolean,
    initialKind: String?,
    onDone: () -> Unit,
    onAddAccount: () -> Unit,
    manualViewModel: ManualEntryViewModel = hiltViewModel(),
    incomeViewModel: IncomeFormViewModel = hiltViewModel(key = "add-income"),
) {
    val manualUi by manualViewModel.uiState.collectAsStateWithLifecycle()
    val incomeUi by incomeViewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(tabOf(initialKind)) }

    // The manual engine owns the Expense and Transfer drafts; keep its active kind in step with the shown tab.
    LaunchedEffect(tab) {
        when (tab) {
            AddTransactionTab.EXPENSE -> manualViewModel.onKindSelect(ManualEntryKind.EXPENSE)
            AddTransactionTab.TRANSFER -> manualViewModel.onKindSelect(ManualEntryKind.TRANSFER)
            AddTransactionTab.INCOME -> Unit
        }
    }
    // Exactly one tab saves; whichever finishes ends the whole session.
    val finished = manualUi.isFinished || incomeUi.isFinished
    LaunchedEffect(finished) { if (finished) onDone() }

    AddTransactionHostContent(
        tab = tab,
        manualUi = manualUi,
        incomeUi = incomeUi,
        focusNote = focusNote,
        manualActions = ManualEntryActions(
            onClose = onDone,
            onOpenIncome = { tab = AddTransactionTab.INCOME },
            onAddAccount = onAddAccount,
            onKind = { kind ->
                tab = if (kind == ManualEntryKind.TRANSFER) AddTransactionTab.TRANSFER else AddTransactionTab.EXPENSE
                manualViewModel.onKindSelect(kind)
            },
            onAmount = manualViewModel::onAmountChange,
            onDate = manualViewModel::onDateSelect,
            onTime = manualViewModel::onTimeSelect,
            onAccount = manualViewModel::onAccountSelect,
            onCategory = manualViewModel::onCategorySelect,
            onAddCategoryStart = manualViewModel::onAddCategoryStart,
            onNewCategoryName = manualViewModel::onNewCategoryNameChange,
            onAddCategoryCancel = manualViewModel::onAddCategoryCancel,
            onAddCategorySave = manualViewModel::onAddCategorySave,
            onSpendingClass = manualViewModel::onSpendingClassSelect,
            onTitle = manualViewModel::onTitleChange,
            onDescription = manualViewModel::onDescriptionChange,
            onSuggestion = manualViewModel::onSuggestionSelect,
            onFee = manualViewModel::onFeeChange,
            onFrom = manualViewModel::onFromSelect,
            onTo = manualViewModel::onToSelect,
            onSwap = manualViewModel::onSwapAccounts,
            onTransferNote = manualViewModel::onTransferNoteChange,
            onSave = manualViewModel::save,
            onNewCategoryIcon = manualViewModel::onNewCategoryIconSelect,
        ),
        incomeActions = IncomeFormActions(
            onBack = onDone,
            onAmountChange = incomeViewModel::onAmountChange,
            onAccountSelect = incomeViewModel::onAccountSelect,
            onDateSelect = incomeViewModel::onDateSelect,
            onRateChange = incomeViewModel::onRateChange,
            onReceivedFromChange = incomeViewModel::onReceivedFromChange,
            onCategorySelect = incomeViewModel::onCategorySelect,
            onNoteChange = incomeViewModel::onNoteChange,
            onSave = incomeViewModel::save,
            onUndo = incomeViewModel::undo,
            onTimeSelect = incomeViewModel::onTimeSelect,
            onSelectKind = { index ->
                tab = when (index) {
                    0 -> AddTransactionTab.EXPENSE
                    2 -> AddTransactionTab.TRANSFER
                    else -> AddTransactionTab.INCOME
                }
            },
        ),
    )
}

/**
 * The shared shell dispatch: exactly one tab's body is composed at a time, but both engines (and so both drafts)
 * stay alive under the route. Kept separate from the ViewModel wiring so a Compose test can drive the tab switch.
 */
@Composable
internal fun AddTransactionHostContent(
    tab: AddTransactionTab,
    manualUi: ManualEntryUiState,
    incomeUi: IncomeFormUiState,
    focusNote: Boolean,
    manualActions: ManualEntryActions,
    incomeActions: IncomeFormActions,
) {
    // One indicator for the whole session, so it keeps travelling when Income replaces the manual body and back.
    val typeIndicator = rememberSelectorIndicator(selectedIndex = tab.ordinal)
    when (tab) {
        AddTransactionTab.INCOME -> IncomeFormContent(incomeUi, incomeActions, typeIndicator, AddTransactionSheetHeight)
        AddTransactionTab.EXPENSE, AddTransactionTab.TRANSFER -> ManualEntryContent(manualUi, focusNote, manualActions, typeIndicator, AddTransactionSheetHeight)
    }
}

private fun tabOf(initialKind: String?): AddTransactionTab = when (initialKind) {
    "income" -> AddTransactionTab.INCOME
    "transfer" -> AddTransactionTab.TRANSFER
    else -> AddTransactionTab.EXPENSE
}
