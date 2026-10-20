package com.example.zar.features.accounts.data

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.accounts.data.repository.AccountDocumentMapper
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDocumentMapperTest {

    private val date = LocalDate.of(2026, 10, 1)

    private fun account(opening: OpeningBalance, type: AccountType = AccountType.BANK, archived: Boolean = false) =
        Account(
            AccountId("a1"), "Sampath", type,
            when (opening) {
                is OpeningBalance.Known -> opening.amount.currency
                is OpeningBalance.Approximate -> opening.amount.currency
                OpeningBalance.Unknown -> Currencies.BTC
            },
            opening, date, archived,
        )

    @Test
    fun roundTripsEveryOpeningBalanceStatus() {
        listOf(
            account(OpeningBalance.Known(FiatMoney(18_230_050, Currencies.LKR))),
            account(OpeningBalance.Approximate(CryptoAmount.of("0.00512".toBigDecimal(), Currencies.BTC)), AccountType.CRYPTO),
            account(OpeningBalance.Unknown, AccountType.CRYPTO),
            account(OpeningBalance.Known(FiatMoney(0, Currencies.LKR)), AccountType.CASH, archived = true),
        ).forEach { original ->
            assertEquals(original, AccountDocumentMapper.fromMap(AccountDocumentMapper.toMap(original, T0)))
        }
    }

    @Test
    fun writesExactlyTheFieldsFirestoreRulesAllow() {
        val map = AccountDocumentMapper.toMap(account(OpeningBalance.Unknown, AccountType.CRYPTO), T0)
        assertEquals(
            setOf("id", "name", "type", "currency", "opening", "openingDate", "archived", "updatedAt"),
            map.keys,
        )
        assertEquals(mapOf("status" to "UNKNOWN"), map["opening"])
        assertEquals("2026-10-01", map["openingDate"])
        assertEquals(T0.toEpochMilli(), map["updatedAt"])
        assertEquals(mapOf("code" to "BTC", "kind" to "CRYPTO", "digits" to 8L), map["currency"])
    }

    @Test
    fun knownOpeningCarriesAMoneyInTheAccountCurrency() {
        val map = AccountDocumentMapper.toMap(account(OpeningBalance.Known(FiatMoney(500, Currencies.LKR))), T0)
        val opening = map["opening"] as Map<*, *>
        assertEquals("KNOWN", opening["status"])
        assertEquals(mapOf("currency" to "LKR", "kind" to "FIAT", "digits" to 2L, "minor" to 500L), opening["amount"])
    }

    @Test
    fun malformedDocumentsAreSkippedNotCrashing() {
        assertNull(AccountDocumentMapper.fromMap(emptyMap()))
        val good = AccountDocumentMapper.toMap(account(OpeningBalance.Known(FiatMoney(500, Currencies.LKR))), T0)
        assertNull(AccountDocumentMapper.fromMap(good + ("type" to "SAVINGS")))
        assertNull(AccountDocumentMapper.fromMap(good + ("opening" to mapOf("status" to "KNOWN"))))
        assertNull(AccountDocumentMapper.fromMap(good + ("openingDate" to "yesterday")))
    }
}
