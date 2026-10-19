package com.example.zar.features.accounts.domain.model

import com.example.zar.core.domain.calculation.AccountBalance
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import java.time.LocalDate

/** How sure the user is about the funds an account started with. */
enum class OpeningKind { KNOWN, APPROXIMATE, UNKNOWN }

enum class AccountProblem {
    NAME_MISSING,
    NAME_TOO_LONG,
    TYPE_CURRENCY_MISMATCH,
    OPENING_MISSING,
    OPENING_INVALID,
    OPENING_NOT_ALLOWED,
    OPENING_DATE_IN_FUTURE,
}

/** The add/edit form exactly as the user typed it. */
data class AccountFormInput(
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingKind: OpeningKind,
    val openingAmount: String,
    val openingDate: LocalDate,
)

/** A validated form, ready to become an [Account]. */
data class AccountDetails(
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingBalance: OpeningBalance,
    val openingDate: LocalDate,
)

sealed interface AccountFormResult {
    data class Valid(val details: AccountDetails) : AccountFormResult
    data class Invalid(val problems: Set<AccountProblem>) : AccountFormResult
}

/** Everything the Accounts list shows. */
data class AccountsOverview(
    /** Active accounts: cash first, then bank and freelance, then crypto. */
    val balances: List<AccountBalance>,
    val archived: List<AccountBalance>,
    /** Sum of known balances held in the reporting currency; null when none is known. */
    val total: Money?,
    /** Accounts included in [total]. */
    val countedAccounts: Int,
    /** Active accounts in another currency. They have no rate yet, so they stay out of [total]. */
    val otherCurrencyAccounts: Int,
) {
    val isEmpty: Boolean get() = balances.isEmpty() && archived.isEmpty()
}

/** One account with its balance and latest activity. */
data class AccountDetail(
    val balance: AccountBalance,
    /** Latest first, active entries only. */
    val recent: List<LedgerEntry>,
) {
    val account: Account get() = balance.account
}
