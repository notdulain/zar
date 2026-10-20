package com.example.zar.features.accounts.domain

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.accounts.domain.model.AccountFormInput
import com.example.zar.features.accounts.domain.model.AccountFormResult
import com.example.zar.features.accounts.domain.model.AccountProblem
import com.example.zar.features.accounts.domain.model.OpeningKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountRulesTest {

    private val today = LocalDate.of(2026, 10, 19)

    private fun input(
        name: String = "Sampath Bank",
        type: AccountType = AccountType.BANK,
        currency: com.example.zar.core.domain.model.money.Currency = Currencies.LKR,
        kind: OpeningKind = OpeningKind.KNOWN,
        amount: String = "182,300.50",
        date: LocalDate = today,
    ) = AccountFormInput(name, type, currency, kind, amount, date)

    private fun problems(input: AccountFormInput) =
        (AccountRules.validate(input, today) as AccountFormResult.Invalid).problems

    @Test
    fun validBankAccountKeepsExactOpeningFunds() {
        val result = AccountRules.validate(input(name = "  Sampath Bank  "), today) as AccountFormResult.Valid
        assertEquals("Sampath Bank", result.details.name)
        assertEquals(OpeningBalance.Known(lkr(0).let { com.example.zar.core.domain.model.money.FiatMoney(18_230_050, Currencies.LKR) }), result.details.openingBalance)
    }

    @Test
    fun zeroStartingBalanceIsAllowed() {
        assertTrue(AccountRules.validate(input(amount = "0"), today) is AccountFormResult.Valid)
    }

    @Test
    fun nameIsRequiredAndLimited() {
        assertEquals(setOf(AccountProblem.NAME_MISSING), problems(input(name = "   ")))
        assertEquals(setOf(AccountProblem.NAME_TOO_LONG), problems(input(name = "x".repeat(81))))
        assertTrue(AccountRules.validate(input(name = "x".repeat(80)), today) is AccountFormResult.Valid)
    }

    @Test
    fun knownAmountMustBeAPresentNonNegativeNumber() {
        assertEquals(setOf(AccountProblem.OPENING_MISSING), problems(input(amount = "")))
        assertEquals(setOf(AccountProblem.OPENING_INVALID), problems(input(amount = "abc")))
        assertEquals(setOf(AccountProblem.OPENING_INVALID), problems(input(amount = "-5")))
        // LKR has two decimals; a third would silently round, so it is rejected.
        assertEquals(setOf(AccountProblem.OPENING_INVALID), problems(input(amount = "10.005")))
    }

    @Test
    fun cryptoMayStartApproximateOrUnknown() {
        val approximate = AccountRules.validate(
            input("Binance", AccountType.CRYPTO, Currencies.USDT, OpeningKind.APPROXIMATE, "250.5"), today,
        ) as AccountFormResult.Valid
        assertTrue(approximate.details.openingBalance is OpeningBalance.Approximate)

        val unknown = AccountRules.validate(
            input("Binance", AccountType.CRYPTO, Currencies.BTC, OpeningKind.UNKNOWN, ""), today,
        ) as AccountFormResult.Valid
        assertEquals(OpeningBalance.Unknown, unknown.details.openingBalance)
    }

    @Test
    fun unknownAmountIsIgnoredNotRequired() {
        val result = AccountRules.validate(
            input("Wallet", AccountType.CRYPTO, Currencies.ETH, OpeningKind.UNKNOWN, "garbage"), today,
        )
        assertTrue(result is AccountFormResult.Valid)
    }

    @Test
    fun fiatAccountsCannotStartUnknownOrApproximate() {
        assertEquals(setOf(AccountProblem.OPENING_NOT_ALLOWED), problems(input(kind = OpeningKind.UNKNOWN)))
        assertEquals(setOf(AccountProblem.OPENING_NOT_ALLOWED), problems(input(kind = OpeningKind.APPROXIMATE)))
    }

    @Test
    fun typeAndCurrencyMustMatch() {
        assertEquals(setOf(AccountProblem.TYPE_CURRENCY_MISMATCH), problems(input(type = AccountType.CRYPTO, currency = Currencies.LKR)))
        assertEquals(
            setOf(AccountProblem.TYPE_CURRENCY_MISMATCH),
            problems(input(type = AccountType.BANK, currency = Currencies.BTC)),
        )
    }

    @Test
    fun openingDateCannotBeInTheFuture() {
        assertEquals(setOf(AccountProblem.OPENING_DATE_IN_FUTURE), problems(input(date = today.plusDays(1))))
        assertTrue(AccountRules.validate(input(date = today), today) is AccountFormResult.Valid)
    }

    @Test
    fun choicesFollowTheAssetKind() {
        assertTrue(AccountRules.currencyChoices(AccountType.CASH).all { it.kind == com.example.zar.core.domain.model.money.AssetKind.FIAT })
        assertTrue(AccountRules.currencyChoices(AccountType.CRYPTO).all { it.kind == com.example.zar.core.domain.model.money.AssetKind.CRYPTO })
        assertEquals(listOf(AccountType.CRYPTO), AccountRules.typesFor(Currencies.USDT))
        assertEquals(listOf(OpeningKind.KNOWN), AccountRules.openingKindsFor(AccountType.CASH))
        assertEquals(OpeningKind.entries, AccountRules.openingKindsFor(AccountType.CRYPTO))
    }

    @Test
    fun editKeepsIdCurrencyAndArchivedState() {
        val archived = bank.copy(isArchived = true)
        val details = (AccountRules.validate(input(name = "Renamed", type = AccountType.CASH, amount = "5"), today) as AccountFormResult.Valid).details
        val edited = AccountRules.edited(archived, details)
        assertEquals(AccountId("bank"), edited.id)
        assertEquals("Renamed", edited.name)
        assertEquals(AccountType.CASH, edited.type)
        assertTrue(edited.isArchived)
    }

    @Test
    fun editCannotChangeCurrency() {
        val details = (AccountRules.validate(input(currency = Currencies.USD, amount = "5"), today) as AccountFormResult.Valid).details
        assertThrows(IllegalArgumentException::class.java) { AccountRules.edited(bank, details) }
        assertEquals(usd(5), details.openingBalance.let { (it as OpeningBalance.Known).amount })
    }
}
