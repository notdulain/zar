package com.example.zar.features.capture.domain

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.features.capture.domain.model.BankSmsFormat

/**
 * Suggests which of the person's accounts a bank message belongs to, so review usually needs one tap. The tested
 * message format already names the bank, and the last digits in the message tell two accounts at one bank apart
 * when the person put them in the account name. Only a single clear candidate is suggested; anything else is left
 * for the person to pick, and a suggestion is always shown as a guess.
 */
object SmsAccountMatcher {

    /** Words people commonly use for each bank in an account name, compared in lower case. */
    private val BANK_NAMES = mapOf(
        "Commercial Bank" to listOf("commercial", "combank", "com bank"),
        "Sampath Bank" to listOf("sampath"),
        "DFCC Bank" to listOf("dfcc"),
    )

    fun match(format: BankSmsFormat, accountHint: String?, currency: Currency, accounts: List<Account>): AccountId? {
        val eligible = accounts.filter { !it.isArchived && it.type == AccountType.BANK && it.currency == currency }
        val names = BANK_NAMES[format.bank].orEmpty()
        val atBank = eligible.filter { account -> names.any { it in account.name.lowercase() } }
        val hinted = accountHint?.takeIf { it.isNotBlank() }?.let { hint ->
            val digits = Regex("""(?<!\d)${Regex.escape(hint)}(?!\d)""")
            eligible.filter { digits.containsMatchIn(it.name) }
        }.orEmpty()
        return (atBank.filter { it in hinted }.singleOrNull() ?: hinted.singleOrNull() ?: atBank.singleOrNull())?.id
    }
}
