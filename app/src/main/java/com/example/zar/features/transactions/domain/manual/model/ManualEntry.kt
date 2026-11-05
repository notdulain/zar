package com.example.zar.features.transactions.domain.manual.model

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.RateSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The built-in expense categories. Stored in `Transaction.categoryId` for manual EXPENSE records. [usualClass]
 * is only a hint that preselects "Counts as"; the user can always change it. [defaultName] is the English
 * display name; it is used to refuse a custom category with the same name, while the UI shows the string resource.
 * IDs are stable and never reused or renamed.
 */
enum class ExpenseCategory(
    override val id: String,
    override val usualClass: SpendingClass?,
    val defaultName: String,
) : ExpenseCategoryChoice {
    COFFEE_DINING("coffee-dining", SpendingClass.DISCRETIONARY, "Coffee & dining"),
    GROCERIES("groceries", SpendingClass.DISCRETIONARY, "Groceries"),
    TRANSPORT("transport", null, "Transport"),
    BILLS("bills", SpendingClass.COMMITTED, "Bills"),
    RENT("rent", SpendingClass.COMMITTED, "Rent"),
    SEASON_TICKET("season-ticket", SpendingClass.COMMITTED, "Season ticket"),
    SUBSCRIPTIONS("subscriptions", SpendingClass.DISCRETIONARY, "Subscriptions"),
    OTHER("other", null, "Other"),
    ;

    companion object {
        fun fromId(id: String?): ExpenseCategory? = entries.firstOrNull { it.id == id }
    }
}

/** What the user typed into the expense form. */
data class ExpenseFormInput(
    val amount: String,
    val account: Account?,
    val date: LocalDate,
    val category: ExpenseCategoryChoice?,
    val spendingClass: SpendingClass,
    /** "What was it for?" Saved as the entry's title (merchant). */
    val title: String,
    val description: String,
    val time: LocalTime? = null,
    /** LKR per unit of the account's currency. Only read for a foreign account; blank means the rate is unknown. */
    val rate: String = "",
    /** Where the number in [rate] came from, as known by the form. */
    val rateSource: RateSource = RateSource.MANUAL,
    val rateAsOf: Instant? = null,
)

enum class ExpenseProblem {
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    ACCOUNT_MISSING,
    CATEGORY_MISSING,
    DATE_IN_FUTURE,
    TITLE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    RATE_INVALID,
}

/** Validated details of money that already left an account. */
data class ExpenseDetails(
    val account: Account,
    val amount: Money,
    val date: LocalDate,
    val category: ExpenseCategoryChoice,
    val spendingClass: SpendingClass,
    val title: String?,
    val description: String?,
    val time: LocalTime? = null,
    /** The category rule that filled in [category], when the person kept its choice. */
    val appliedRuleId: String? = null,
    val appliedRuleVersion: Int? = null,
    /** LKR per unit of the account's currency. Null for an LKR account, or when the rate is unknown. */
    val rateToReporting: BigDecimal? = null,
    /** Typed by the person, or today's rate from the provider. Only meaningful with a [rateToReporting]. */
    val rateSource: RateSource = RateSource.MANUAL,
    /** When the provider published the rate. Null for a typed rate, which is dated by when it is saved. */
    val rateAsOf: Instant? = null,
)

sealed interface ExpenseFormResult {
    data class Valid(val details: ExpenseDetails) : ExpenseFormResult

    data class Invalid(val problems: Set<ExpenseProblem>) : ExpenseFormResult
}

/** What the user typed into the transfer form. */
data class TransferFormInput(
    val amount: String,
    /** Optional. Saved as a separate expense, never as part of the transfer. */
    val fee: String,
    val from: Account?,
    val to: Account?,
    val date: LocalDate,
    val note: String,
    val time: LocalTime? = null,
)

enum class TransferProblem {
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    FEE_INVALID,
    FROM_MISSING,
    TO_MISSING,
    SAME_ACCOUNT,
    DIFFERENT_CURRENCIES,
    DATE_IN_FUTURE,
    NOTE_TOO_LONG,
}

/** Validated details of money moved between two owned accounts of the same currency. */
data class TransferDetails(
    val from: Account,
    val to: Account,
    val amount: Money,
    /** Null when there is no fee. In the source account's currency. */
    val fee: Money?,
    val date: LocalDate,
    val note: String?,
    val time: LocalTime? = null,
)

sealed interface TransferFormResult {
    data class Valid(val details: TransferDetails) : TransferFormResult

    data class Invalid(val problems: Set<TransferProblem>) : TransferFormResult
}

/** A recent expense the user can reuse by typing the start of its title. */
data class EntrySuggestion(
    val title: String,
    val category: ExpenseCategoryChoice?,
    val accountId: AccountId,
    val amount: Money,
    val spendingClass: SpendingClass,
)
