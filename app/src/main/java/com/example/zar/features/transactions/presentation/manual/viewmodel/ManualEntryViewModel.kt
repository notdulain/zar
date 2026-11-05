package com.example.zar.features.transactions.presentation.manual.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RecordIds
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.navigation.ManualEntryRoute
import com.example.zar.features.accounts.domain.usecase.ObserveActiveAccountsUseCase
import com.example.zar.features.rates.domain.FetchTodaysRateUseCase
import com.example.zar.features.rates.domain.RateEntry
import com.example.zar.features.rates.domain.RateFetch
import com.example.zar.features.rates.domain.TodaysRateRules
import com.example.zar.features.rules.domain.CategoryRuleMatcher
import com.example.zar.features.rules.domain.RuleOfferRules
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.model.OfferDecision
import com.example.zar.features.rules.domain.model.RuleMatch
import com.example.zar.features.rules.domain.repository.RuleOfferInbox
import com.example.zar.features.rules.domain.usecase.ObserveCategoryRulesUseCase
import com.example.zar.features.transactions.domain.category.CategoryNameProblem
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.usecase.CategoryNameRejected
import com.example.zar.features.transactions.domain.category.usecase.CreateExpenseCategoryUseCase
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.manual.EntrySuggestions
import com.example.zar.features.transactions.domain.manual.ManualEntryRules
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.example.zar.features.transactions.domain.manual.model.EntrySuggestion
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategoryChoice
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormInput
import com.example.zar.features.transactions.domain.manual.model.ExpenseFormResult
import com.example.zar.features.transactions.domain.manual.model.ExpenseProblem
import com.example.zar.features.transactions.domain.manual.model.TransferFormInput
import com.example.zar.features.transactions.domain.manual.model.TransferFormResult
import com.example.zar.features.transactions.domain.manual.model.TransferProblem
import com.example.zar.features.transactions.domain.manual.usecase.ObserveEntrySuggestionsUseCase
import com.example.zar.features.transactions.domain.manual.usecase.ObserveOnlineStatusUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordExpenseUseCase
import com.example.zar.features.transactions.domain.manual.usecase.RecordTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ManualEntryKind { EXPENSE, TRANSFER }

enum class ManualEntryMessage { NOT_SIGNED_IN, ACCOUNT_ARCHIVED, FAILED }

/**
 * The "Add category" form while it is open. [problem] is a name problem shown inline; [failed] means the write was
 * rejected or unavailable, so the same name can be retried (it reuses one category ID). Creating a category never
 * saves the transaction or touches any other field of the draft.
 */
data class NewCategoryState(
    val name: String = "",
    val problem: CategoryNameProblem? = null,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
)

data class ManualEntryUiState(
    val isLoading: Boolean = true,
    val kind: ManualEntryKind = ManualEntryKind.EXPENSE,
    val accounts: List<Account> = emptyList(),
    /** Saving works offline; this only lets the form say where the entry is kept. */
    val isOnline: Boolean = true,
    // Shared by both kinds, so switching tabs keeps what was typed.
    val amount: String = "",
    val date: LocalDate = ZarTime.today(),
    val time: LocalTime = LocalTime.now(ZarTime.ZONE).truncatedTo(ChronoUnit.MINUTES),
    // Expense
    val accountId: AccountId? = null,
    val category: ExpenseCategoryChoice? = null,
    /** Built-ins plus the user's own categories, in picker order. Built-ins only until the catalog arrives. */
    val categoryCatalog: ExpenseCategoryCatalog = ExpenseCategoryCatalog.BUILT_IN_ONLY,
    /** Non-null while the Add category form is open. */
    val newCategory: NewCategoryState? = null,
    /** True once the user picked the category themselves, so a remembered rule no longer fills it in. */
    val categoryChosen: Boolean = false,
    /** The rule that filled in [category] for the typed title. Cleared as soon as the user picks another category. */
    val appliedRule: CategoryRule? = null,
    val spendingClass: SpendingClass = SpendingClass.DISCRETIONARY,
    /** True once the user picked "Counts as" themselves, so a category no longer overrides it. */
    val spendingClassChosen: Boolean = false,
    val title: String = "",
    val description: String = "",
    /** The rate field of a foreign-currency expense: what is typed and, if it was fetched, today's rate. */
    val rateEntry: RateEntry = RateEntry(),
    val suggestions: List<EntrySuggestion> = emptyList(),
    val expenseProblems: Set<ExpenseProblem> = emptySet(),
    // Transfer
    val fee: String = "",
    val fromId: AccountId? = null,
    val toId: AccountId? = null,
    val transferNote: String = "",
    val transferProblems: Set<TransferProblem> = emptySet(),
    val message: ManualEntryMessage? = null,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    /**
     * Set when the form was opened from a balance check as "Add the missing entry": the gap being explained,
     * as typed into the amount. The account is then fixed to the one that was checked.
     */
    val gapAmount: String? = null,
    val accountLocked: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }
    val fromAccount: Account? get() = accounts.firstOrNull { it.id == fromId }
    val toAccount: Account? get() = accounts.firstOrNull { it.id == toId }

    /** An expense on a foreign-currency account has a rate field, as foreign income does. */
    val needsRate: Boolean get() = kind == ManualEntryKind.EXPENSE && selectedAccount?.currency.let { it != null && it != Currencies.REPORTING }

    /** The number in the rate field, as typed. */
    val rate: String get() = rateEntry.text

    /** Today's rate is only offered for an ordinary foreign currency on today's date. */
    val canUseTodaysRate: Boolean
        get() = kind == ManualEntryKind.EXPENSE && TodaysRateRules.canOffer(selectedAccount?.currency, date, ZarTime.today())

    /** The currency the amount is typed in: always the account's, never chosen separately. */
    val currency: Currency
        get() = when (kind) {
            ManualEntryKind.EXPENSE -> selectedAccount?.currency
            ManualEntryKind.TRANSFER -> fromAccount?.currency
        } ?: Currencies.REPORTING

    /** Accounts money can be sent to: any other account in the sender's currency. */
    val transferTargets: List<Account>
        get() = accounts.filter { it.id != fromId && it.currency == fromAccount?.currency }

    /** True when some accounts are left out of the "to" choices only because of their currency. */
    val hidesOtherCurrencies: Boolean
        get() = accounts.any { it.id != fromId && it.currency != fromAccount?.currency }

    private val amountMoney: Money? get() = Money.parse(amount, currency)?.takeIf { it.isPositive }
    private val feeMoney: Money? get() = if (fee.isBlank()) null else Money.parse(fee, currency)?.takeIf { it.isPositive }

    /** What leaves the sender: the amount plus any fee. Null until the amount is valid. */
    val transferOutTotal: Money?
        get() = amountMoney?.let { amount -> runCatching { feeMoney?.let { amount + it } ?: amount }.getOrNull() }

    /** What arrives. Null until the amount is valid. */
    val transferInTotal: Money? get() = amountMoney
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeAccounts: ObserveActiveAccountsUseCase,
    private val observeSuggestions: ObserveEntrySuggestionsUseCase,
    private val observeOnline: ObserveOnlineStatusUseCase,
    private val recordExpense: RecordExpenseUseCase,
    private val recordTransfer: RecordTransferUseCase,
    observeRules: ObserveCategoryRulesUseCase,
    private val ruleInbox: RuleOfferInbox,
    observeCategories: ObserveExpenseCategoryCatalogUseCase,
    private val createCategory: CreateExpenseCategoryUseCase,
    private val fetchTodaysRate: FetchTodaysRateUseCase,
) : ViewModel() {

    // Read by name instead of toRoute so the ViewModel can be created in plain unit tests.
    private val prefillAmount: String? = savedStateHandle.get<String>(ManualEntryRoute::prefillAmount.name)
    private val prefillAccountId: String? = savedStateHandle.get<String>(ManualEntryRoute::prefillAccountId.name)
    private val prefillTitle: String? = savedStateHandle.get<String>(ManualEntryRoute::prefillTitle.name)
    private val initialKind: ManualEntryKind =
        if (savedStateHandle.get<String>(ManualEntryRoute::initialKind.name) == "transfer") ManualEntryKind.TRANSFER else ManualEntryKind.EXPENSE

    // Created once per screen and reused on every retry, so a repeated save can't duplicate.
    private val expenseId = TransactionId(RecordIds.newId())
    private val transferId = TransferId(RecordIds.newId())
    private var allSuggestions: List<EntrySuggestion> = emptyList()
    private var rules: List<CategoryRule> = emptyList()

    // Created when "Add category" opens and reused on every retry, so repeated Save is one category.
    private var pendingCategoryId: String? = null

    private val _uiState = MutableStateFlow(
        ManualEntryUiState(
            kind = initialKind,
            amount = if (prefillAccountId != null) prefillAmount.orEmpty() else "",
            title = prefillTitle.orEmpty(),
        ),
    )
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAccounts()
                .catch { _uiState.update { it.copy(isLoading = false, message = ManualEntryMessage.FAILED) } }
                .collect { accounts -> _uiState.update { it.withAccounts(accounts) } }
        }
        viewModelScope.launch {
            observeSuggestions().catch { }.collect { suggestions ->
                allSuggestions = suggestions
                _uiState.update { it.copy(suggestions = suggestionsFor(it.title)) }
            }
        }
        viewModelScope.launch {
            observeOnline().catch { }.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            observeCategories().collect { catalog -> _uiState.update { it.copy(categoryCatalog = catalog).withRule() } }
        }
        viewModelScope.launch {
            createCategory.lateRejections.collect(::onCategoryRejectedLate)
        }
        viewModelScope.launch {
            observeRules().catch { }.collect { list ->
                rules = list
                _uiState.update { it.withRule() }
            }
        }
    }

    fun onKindSelect(kind: ManualEntryKind) = _uiState.update {
        // Explaining a gap is always an expense.
        if (it.gapAmount != null) it else it.copy(kind = kind, message = null)
    }

    fun onAmountChange(value: String) = _uiState.update {
        it.copy(
            amount = value,
            expenseProblems = it.expenseProblems - setOf(ExpenseProblem.AMOUNT_MISSING, ExpenseProblem.AMOUNT_INVALID),
            transferProblems = it.transferProblems - setOf(TransferProblem.AMOUNT_MISSING, TransferProblem.AMOUNT_INVALID),
        )
    }

    fun onDateSelect(value: LocalDate) = _uiState.update {
        it.copy(
            date = value,
            rateEntry = it.rateEntry.onDateChanged(value, ZarTime.today()),
            expenseProblems = it.expenseProblems - ExpenseProblem.DATE_IN_FUTURE,
            transferProblems = it.transferProblems - TransferProblem.DATE_IN_FUTURE,
        )
    }

    fun onTimeSelect(value: LocalTime) = _uiState.update {
        it.copy(time = value.truncatedTo(ChronoUnit.MINUTES))
    }

    fun onAccountSelect(id: AccountId) = _uiState.update {
        if (it.accountLocked) return@update it
        it.copy(
            accountId = id,
            // A rate belongs to one currency: another account starts with an empty rate field.
            rateEntry = if (it.accountId == id) it.rateEntry else RateEntry(),
            expenseProblems = it.expenseProblems - setOf(ExpenseProblem.ACCOUNT_MISSING, ExpenseProblem.RATE_INVALID),
        ).withRule()
    }

    fun onRateChange(value: String) = _uiState.update {
        it.copy(rateEntry = it.rateEntry.typed(value), expenseProblems = it.expenseProblems - ExpenseProblem.RATE_INVALID)
    }

    /** Fills the rate field with today's rate. A failure only shows a message: the rate can still be typed or left empty. */
    fun onUseTodaysRate() {
        val state = _uiState.value
        val currency = state.selectedAccount?.currency ?: return
        if (!state.canUseTodaysRate || state.rateEntry.fetch == RateFetch.FETCHING) return
        _uiState.update { it.copy(rateEntry = it.rateEntry.fetching()) }
        viewModelScope.launch {
            val result = fetchTodaysRate(currency)
            _uiState.update { now ->
                // The form may have moved on while the answer was on its way: another account, an earlier date, or a
                // rate the person typed meanwhile (typing ends the fetch). A late answer must not replace what they typed.
                if (now.rateEntry.fetch != RateFetch.FETCHING) {
                    now
                } else if (now.selectedAccount?.currency != currency || !now.canUseTodaysRate) {
                    now.copy(rateEntry = now.rateEntry.copy(fetch = RateFetch.IDLE))
                } else {
                    result.fold(
                        onSuccess = { quote ->
                            now.copy(rateEntry = now.rateEntry.fetched(quote), expenseProblems = now.expenseProblems - ExpenseProblem.RATE_INVALID)
                        },
                        onFailure = { now.copy(rateEntry = now.rateEntry.failed()) },
                    )
                }
            }
        }
    }

    fun onCategorySelect(value: ExpenseCategoryChoice) = _uiState.update {
        val usual = value.usualClass
        it.copy(
            category = value,
            categoryChosen = true,
            appliedRule = null,
            spendingClass = if (!it.spendingClassChosen && usual != null) usual else it.spendingClass,
            expenseProblems = it.expenseProblems - ExpenseProblem.CATEGORY_MISSING,
        )
    }

    /** Opens the Add category form. The transaction draft and the current selection are left as they are. */
    fun onAddCategoryStart() {
        pendingCategoryId = CustomCategoryIds.newId()
        _uiState.update { it.copy(newCategory = NewCategoryState()) }
    }

    fun onNewCategoryNameChange(value: String) = _uiState.update { state ->
        state.newCategory?.let { state.copy(newCategory = it.copy(name = value, problem = null, failed = false)) } ?: state
    }

    /** Closes the form without creating anything; the selection and every typed field stay. */
    fun onAddCategoryCancel() {
        pendingCategoryId = null
        _uiState.update { it.copy(newCategory = null) }
    }

    /**
     * Saves the new category and selects it, without saving the transaction. A name problem or a failed write
     * keeps the form open with the typed name so it can be corrected or retried.
     */
    fun onAddCategorySave() {
        val form = _uiState.value.newCategory ?: return
        if (form.isSaving) return
        val id = pendingCategoryId ?: CustomCategoryIds.newId().also { pendingCategoryId = it }
        _uiState.update { it.copy(newCategory = form.copy(isSaving = true, problem = null, failed = false)) }
        viewModelScope.launch {
            val outcome = createCategory(id, form.name, _uiState.value.categoryCatalog, Instant.now())
            outcome.fold(
                onSuccess = { created ->
                    pendingCategoryId = null
                    _uiState.update { it.copy(newCategory = null) }
                    onCategorySelect(created)
                },
                onFailure = { error ->
                    val problem = (error as? CategoryNameRejected)?.problem
                    _uiState.update {
                        it.copy(newCategory = it.newCategory?.copy(isSaving = false, problem = problem, failed = problem == null))
                    }
                },
            )
        }
    }

    /**
     * The server refused a category that was already saved and selected (an offline create, refused on reconnect), and
     * the local copy is gone. Taking it back and reopening "Add category" with the same name and ID keeps the person's
     * typing and offers a retry, instead of leaving a selection that points at a category that no longer exists.
     * Nothing else in the draft changes. A category the person has since replaced with another is left alone.
     */
    private fun onCategoryRejectedLate(rejected: CustomExpenseCategory) {
        if (_uiState.value.category?.id != rejected.id) return
        if (_uiState.value.newCategory == null) pendingCategoryId = rejected.id
        _uiState.update {
            it.copy(
                category = null,
                categoryChosen = false,
                newCategory = it.newCategory ?: NewCategoryState(name = rejected.name, failed = true),
            ).withRule()
        }
    }

    fun onSpendingClassSelect(value: SpendingClass) = _uiState.update {
        it.copy(spendingClass = value, spendingClassChosen = true)
    }

    fun onTitleChange(value: String) = _uiState.update {
        it.copy(
            title = value,
            suggestions = suggestionsFor(value),
            expenseProblems = it.expenseProblems - ExpenseProblem.TITLE_TOO_LONG,
        ).withRule()
    }

    fun onDescriptionChange(value: String) = _uiState.update {
        it.copy(description = value, expenseProblems = it.expenseProblems - ExpenseProblem.DESCRIPTION_TOO_LONG)
    }

    /** Fills the form from a recent entry. The amount is only filled if none was typed yet. */
    fun onSuggestionSelect(suggestion: EntrySuggestion) = _uiState.update {
        val account = it.accounts.firstOrNull { account -> account.id == suggestion.accountId && account.currency == suggestion.amount.currency } ?: return@update it
        it.copy(
            kind = ManualEntryKind.EXPENSE,
            title = suggestion.title,
            suggestions = emptyList(),
            category = suggestion.category ?: it.category,
            appliedRule = if (suggestion.category != null) null else it.appliedRule,
            accountId = account.id,
            rateEntry = if (it.accountId == account.id) it.rateEntry else RateEntry(),
            amount = it.amount.ifBlank { suggestion.amount.toDecimal().stripTrailingZeros().toPlainString() },
            spendingClass = suggestion.spendingClass,
            spendingClassChosen = true,
            expenseProblems = emptySet(),
        ).withRule()
    }

    fun onFeeChange(value: String) = _uiState.update {
        it.copy(fee = value, transferProblems = it.transferProblems - TransferProblem.FEE_INVALID)
    }

    fun onFromSelect(id: AccountId) = _uiState.update {
        val from = it.accounts.firstOrNull { account -> account.id == id } ?: return@update it
        val keepTo = it.toAccount?.takeIf { to -> to.id != from.id && to.currency == from.currency }
        it.copy(
            fromId = id,
            toId = (keepTo ?: it.accounts.firstOrNull { to -> to.id != from.id && to.currency == from.currency })?.id,
            transferProblems = it.transferProblems - setOf(
                TransferProblem.FROM_MISSING,
                TransferProblem.SAME_ACCOUNT,
                TransferProblem.DIFFERENT_CURRENCIES,
            ),
        )
    }

    /** Ignored for the sending account and for accounts in another currency. */
    fun onToSelect(id: AccountId) = _uiState.update {
        if (it.transferTargets.none { account -> account.id == id }) return@update it
        it.copy(
            toId = id,
            transferProblems = it.transferProblems - setOf(
                TransferProblem.TO_MISSING,
                TransferProblem.SAME_ACCOUNT,
                TransferProblem.DIFFERENT_CURRENCIES,
            ),
        )
    }

    fun onSwapAccounts() = _uiState.update {
        val to = it.toAccount ?: return@update it
        it.copy(fromId = to.id, toId = it.fromId, transferProblems = emptySet())
    }

    fun onTransferNoteChange(value: String) = _uiState.update {
        it.copy(transferNote = value, transferProblems = it.transferProblems - TransferProblem.NOTE_TOO_LONG)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.isFinished) return
        when (state.kind) {
            ManualEntryKind.EXPENSE -> saveExpense(state)
            ManualEntryKind.TRANSFER -> saveTransfer(state)
        }
    }

    private fun saveExpense(state: ManualEntryUiState) {
        val input = ExpenseFormInput(
            amount = state.amount,
            account = state.selectedAccount,
            date = state.date,
            time = state.time,
            category = state.category,
            spendingClass = state.spendingClass,
            title = state.title,
            description = state.description,
            rate = state.rate,
            rateSource = state.rateEntry.source,
            rateAsOf = state.rateEntry.asOf,
        )
        when (val result = ManualEntryRules.validateExpense(input, ZarTime.today())) {
            is ExpenseFormResult.Invalid -> _uiState.update { it.copy(expenseProblems = result.problems, message = null) }
            is ExpenseFormResult.Valid -> {
                _uiState.update { it.copy(isSaving = true, expenseProblems = emptySet(), message = null) }
                // Only a category the person kept from the rule counts as filed by it.
                val applied = state.appliedRule?.takeIf { it.categoryId == result.details.category.id }
                val details = result.details.copy(appliedRuleId = applied?.id?.value, appliedRuleVersion = applied?.version)
                viewModelScope.launch {
                    finish(recordExpense(expenseId, details, Instant.now())) {
                        val decision = RuleOfferRules.decide(details.title, details.category.id, details.account.id, rules)
                        if (decision is OfferDecision.Offer) ruleInbox.post(decision.offer)
                    }
                }
            }
        }
    }

    private fun saveTransfer(state: ManualEntryUiState) {
        val input = TransferFormInput(
            amount = state.amount,
            fee = state.fee,
            from = state.fromAccount,
            to = state.toAccount,
            date = state.date,
            time = state.time,
            note = state.transferNote,
        )
        when (val result = ManualEntryRules.validateTransfer(input, ZarTime.today())) {
            is TransferFormResult.Invalid -> _uiState.update { it.copy(transferProblems = result.problems, message = null) }
            is TransferFormResult.Valid -> {
                _uiState.update { it.copy(isSaving = true, transferProblems = emptySet(), message = null) }
                viewModelScope.launch { finish(recordTransfer(transferId, result.details, Instant.now())) }
            }
        }
    }

    private fun finish(outcome: Result<Unit>, onSaved: () -> Unit = {}) {
        outcome.fold(
            onSuccess = {
                onSaved()
                _uiState.update { it.copy(isSaving = false, isFinished = true) }
            },
            onFailure = { error -> _uiState.update { it.copy(isSaving = false, message = error.toMessage()) } },
        )
    }

    /**
     * Fills in the category a remembered rule gives for the typed title, unless the person already chose one.
     * A category a rule filled in is taken back when the title or account no longer matches that rule.
     */
    private fun ManualEntryUiState.withRule(): ManualEntryUiState {
        if (kind != ManualEntryKind.EXPENSE || categoryChosen) return this
        val rule = (CategoryRuleMatcher.match(rules, title, accountId) as? RuleMatch.Applied)?.rule
        val filed = rule?.let { categoryCatalog.find(it.categoryId) }
            ?: return if (appliedRule != null) copy(category = null, appliedRule = null) else this
        val usual = filed.usualClass
        return copy(
            category = filed,
            appliedRule = rule,
            spendingClass = if (!spendingClassChosen && usual != null) usual else spendingClass,
            expenseProblems = expenseProblems - ExpenseProblem.CATEGORY_MISSING,
        )
    }

    private fun suggestionsFor(typed: String, accounts: List<Account> = _uiState.value.accounts): List<EntrySuggestion> =
        EntrySuggestions.matching(allSuggestions.filter { suggestion ->
            accounts.any { it.id == suggestion.accountId && it.currency == suggestion.amount.currency }
        }, typed).filterNot { it.title.equals(typed.trim(), ignoreCase = true) }

    /** Keeps each choice that is still valid and fills the rest with the first sensible account. */
    private fun ManualEntryUiState.withAccounts(list: List<Account>): ManualEntryUiState {
        val first = list.firstOrNull()
        // A balance check fixes the account; if it is no longer available the form is an ordinary one.
        val locked = prefillAccountId?.let { id -> list.firstOrNull { it.id.value == id } }
        val expenseAccount = locked ?: list.firstOrNull { it.id == accountId } ?: first
        val from = list.firstOrNull { it.id == fromId } ?: first
        val to = list.firstOrNull { it.id == toId && it.id != from?.id && it.currency == from?.currency }
            ?: list.firstOrNull { it.id != from?.id && it.currency == from?.currency }
        return copy(
            isLoading = false,
            accounts = list,
            accountId = expenseAccount?.id,
            rateEntry = if (expenseAccount?.id == accountId) rateEntry else RateEntry(),
            accountLocked = locked != null,
            gapAmount = if (locked != null) prefillAmount else null,
            fromId = from?.id,
            toId = to?.id,
            suggestions = suggestionsFor(title, list),
        )
    }

    private fun Throwable.toMessage(): ManualEntryMessage = when (this) {
        is LedgerError.NotSignedIn -> ManualEntryMessage.NOT_SIGNED_IN
        is LedgerError.AccountArchived -> ManualEntryMessage.ACCOUNT_ARCHIVED
        else -> ManualEntryMessage.FAILED
    }
}
