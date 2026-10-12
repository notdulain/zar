package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import java.time.LocalDate

enum class AccountType { BANK, CASH, FREELANCE, CRYPTO }

/**
 * Funds already held when the account was added. Opening funds are never received income.
 * Crypto accounts may start with an unknown or approximate value and be fixed later.
 */
sealed interface OpeningBalance {
    data class Known(val amount: Money) : OpeningBalance
    data class Approximate(val amount: Money) : OpeningBalance
    data object Unknown : OpeningBalance
}

/** One account holds one currency or asset. A crypto wallet with two assets is two accounts. */
data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingBalance: OpeningBalance,
    /** Opening funds are the balance at the start of this day. */
    val openingDate: LocalDate,
    val isArchived: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Account name is required" }
        val opening = when (openingBalance) {
            is OpeningBalance.Known -> openingBalance.amount
            is OpeningBalance.Approximate -> openingBalance.amount
            OpeningBalance.Unknown -> null
        }
        require(opening == null || opening.currency == currency) { "Opening balance must use ${currency.code}" }
    }
}
