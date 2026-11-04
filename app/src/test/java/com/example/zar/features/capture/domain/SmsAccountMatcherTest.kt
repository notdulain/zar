package com.example.zar.features.capture.domain

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.features.capture.domain.model.BankSmsFormat
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsAccountMatcherTest {

    private fun account(id: String, name: String, type: AccountType = AccountType.BANK, currency: Currency = Currencies.LKR, archived: Boolean = false) =
        Account(AccountId(id), name, type, currency, OpeningBalance.Unknown, LocalDate.of(2026, 1, 1), archived)

    private val combank = account("combank", "Commercial Bank")
    private val sampath = account("sampath", "Sampath Savings")
    private val cash = account("cash", "Cash", AccountType.CASH)
    private val payoneer = account("payoneer", "Payoneer", AccountType.FREELANCE, Currencies.USD)

    private fun match(format: BankSmsFormat, hint: String?, vararg accounts: Account) =
        SmsAccountMatcher.match(format, hint, Currencies.LKR, accounts.toList())

    @Test
    fun aCommercialBankMessageGoesToHisCommercialBankAccount() {
        assertEquals(combank.id, match(BankSmsFormat.COMBANK_CARD_PURCHASE, "1111", combank, sampath, cash, payoneer))
    }

    @Test
    fun aSampathMessageGoesToHisSampathAccount() {
        assertEquals(sampath.id, match(BankSmsFormat.SAMPATH_ACCOUNT_ALERT, "3333", combank, sampath, cash))
    }

    @Test
    fun commonShortNamesForTheBankAreRecognised() {
        val short = account("cb", "ComBank debit")
        assertEquals(short.id, match(BankSmsFormat.COMBANK_ATM_WITHDRAWAL, null, short, sampath))
        val dfcc = account("dfcc", "DFCC salary")
        assertEquals(dfcc.id, match(BankSmsFormat.DFCC_ACCOUNT_ALERT, null, dfcc, combank))
    }

    @Test
    fun twoAccountsAtTheSameBankAreToldApartByTheLastDigitsInTheName() {
        val savings = account("s1", "Sampath Savings 3333")
        val current = account("s2", "Sampath Current 4444")
        assertEquals(current.id, match(BankSmsFormat.SAMPATH_ACCOUNT_ALERT, "4444", savings, current))
    }

    @Test
    fun twoAccountsAtTheSameBankWithoutDigitsAreLeftForHimToPick() {
        val savings = account("s1", "Sampath Savings")
        val current = account("s2", "Sampath Current")
        assertNull(match(BankSmsFormat.SAMPATH_ACCOUNT_ALERT, "4444", savings, current))
    }

    @Test
    fun anAccountNamedOnlyByItsDigitsIsStillFound() {
        val card = account("card", "Visa 1111")
        assertEquals(card.id, match(BankSmsFormat.COMBANK_CARD_PURCHASE, "1111", card, sampath))
    }

    @Test
    fun noAccountForThatBankMeansNoGuess() {
        assertNull(match(BankSmsFormat.DFCC_ACCOUNT_ALERT, "9999", combank, sampath, cash))
    }

    @Test
    fun archivedAccountsAndOtherCurrenciesAreNeverPicked() {
        val old = account("old", "Commercial Bank (old)", archived = true)
        val usd = account("usd", "Commercial Bank USD", currency = Currencies.USD)
        assertNull(match(BankSmsFormat.COMBANK_CARD_PURCHASE, null, old, usd, sampath))
    }

    @Test
    fun cashAndCryptoAccountsAreNeverPickedForABankMessage() {
        val wallet = account("wallet", "Commercial Bank wallet", AccountType.CASH)
        assertNull(match(BankSmsFormat.COMBANK_CARD_PURCHASE, null, wallet, sampath))
    }

    @Test
    fun aDigitInsideALongerNumberIsNotAMatch() {
        val savings = account("s1", "Sampath 123333")
        val current = account("s2", "Sampath Current")
        assertNull(match(BankSmsFormat.SAMPATH_ACCOUNT_ALERT, "3333", savings, current))
    }
}
