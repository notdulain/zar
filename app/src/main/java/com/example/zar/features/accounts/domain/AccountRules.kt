package com.example.zar.features.accounts.domain

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.accounts.domain.model.AccountDetails
import com.example.zar.features.accounts.domain.model.AccountFormInput
import com.example.zar.features.accounts.domain.model.AccountFormResult
import com.example.zar.features.accounts.domain.model.AccountProblem
import com.example.zar.features.accounts.domain.model.OpeningKind
import java.time.LocalDate

/** Pure rules for turning the account form into an [Account]. */
object AccountRules {
    // Limit matches firestore.rules.
    const val MAX_NAME = 80

    /** The order accounts and type choices are shown in: cash first, crypto last. */
    val TYPE_ORDER: List<AccountType> = listOf(AccountType.CASH, AccountType.BANK, AccountType.FREELANCE, AccountType.CRYPTO)

    /** Crypto accounts hold crypto assets; every other type holds fiat. */
    fun currencyKindFor(type: AccountType): AssetKind =
        if (type == AccountType.CRYPTO) AssetKind.CRYPTO else AssetKind.FIAT

    fun currencyChoices(type: AccountType): List<Currency> =
        Currencies.all.filter { it.kind == currencyKindFor(type) }

    fun typesFor(currency: Currency): List<AccountType> =
        TYPE_ORDER.filter { currencyKindFor(it) == currency.kind }

    /**
     * Only crypto can start with an approximate or unknown value. A bank, cash or freelance
     * balance is something the user can read off, so it must be a known amount.
     */
    fun openingKindsFor(type: AccountType): List<OpeningKind> =
        if (type == AccountType.CRYPTO) OpeningKind.entries else listOf(OpeningKind.KNOWN)

    fun validate(input: AccountFormInput, today: LocalDate): AccountFormResult {
        val problems = mutableSetOf<AccountProblem>()

        val name = input.name.trim()
        if (name.isEmpty()) problems += AccountProblem.NAME_MISSING
        if (name.length > MAX_NAME) problems += AccountProblem.NAME_TOO_LONG

        if (input.currency.kind != currencyKindFor(input.type)) problems += AccountProblem.TYPE_CURRENCY_MISMATCH

        if (input.openingKind !in openingKindsFor(input.type)) problems += AccountProblem.OPENING_NOT_ALLOWED

        // Opening funds are the balance at the start of a day that has already begun.
        if (input.openingDate.isAfter(today)) problems += AccountProblem.OPENING_DATE_IN_FUTURE

        var amount: Money? = null
        if (input.openingKind != OpeningKind.UNKNOWN) {
            if (input.openingAmount.isBlank()) {
                problems += AccountProblem.OPENING_MISSING
            } else {
                amount = Money.parse(input.openingAmount, input.currency)?.takeUnless { it.isNegative }
                if (amount == null) problems += AccountProblem.OPENING_INVALID
            }
        }

        if (problems.isNotEmpty()) return AccountFormResult.Invalid(problems)
        val opening = when (input.openingKind) {
            OpeningKind.KNOWN -> OpeningBalance.Known(amount!!)
            OpeningKind.APPROXIMATE -> OpeningBalance.Approximate(amount!!)
            OpeningKind.UNKNOWN -> OpeningBalance.Unknown
        }
        return AccountFormResult.Valid(
            AccountDetails(name, input.type, input.currency, opening, input.openingDate),
        )
    }

    fun newAccount(id: AccountId, details: AccountDetails): Account = Account(
        id = id,
        name = details.name,
        type = details.type,
        currency = details.currency,
        openingBalance = details.openingBalance,
        openingDate = details.openingDate,
    )

    /** An edit keeps the ID, currency and archived state. */
    fun edited(existing: Account, details: AccountDetails): Account {
        require(details.currency == existing.currency) { "An account's currency can't change" }
        return existing.copy(
            name = details.name,
            type = details.type,
            openingBalance = details.openingBalance,
            openingDate = details.openingDate,
        )
    }

    fun openingKind(balance: OpeningBalance): OpeningKind = when (balance) {
        is OpeningBalance.Known -> OpeningKind.KNOWN
        is OpeningBalance.Approximate -> OpeningKind.APPROXIMATE
        OpeningBalance.Unknown -> OpeningKind.UNKNOWN
    }

    fun openingAmount(balance: OpeningBalance): Money? = when (balance) {
        is OpeningBalance.Known -> balance.amount
        is OpeningBalance.Approximate -> balance.amount
        OpeningBalance.Unknown -> null
    }
}
