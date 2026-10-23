package com.example.zar.features.crypto.domain

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.features.crypto.data.repository.OpeningCostDocumentMapper
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.OpeningCost
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoMovementRulesTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val btcWallet = Account(AccountId("btc"), "Binance BTC", AccountType.CRYPTO, Currencies.BTC, OpeningBalance.Unknown, today)
    private val ledgerBtc = btcWallet.copy(id = AccountId("ledger"), name = "Ledger")
    private val usdtWallet = Account(AccountId("usdt"), "Binance USDT", AccountType.CRYPTO, Currencies.USDT, OpeningBalance.Unknown, today)

    private fun input(from: Account?, to: Account?, sent: String = "0.01", received: String = "", value: String = "", date: LocalDate = today) =
        CryptoMovementInput(from, to, sent, received, value, date, "")

    private fun problems(result: CryptoCheck<*>) = (result as CryptoCheck.Invalid).problems

    @Test
    fun kindComesFromTheTwoAccounts() {
        assertEquals(CryptoMovementKind.BUY, CryptoMovementRules.kindOf(bank, btcWallet))
        assertEquals(CryptoMovementKind.SELL, CryptoMovementRules.kindOf(btcWallet, bank))
        assertEquals(CryptoMovementKind.TRADE, CryptoMovementRules.kindOf(usdtWallet, btcWallet))
        assertEquals(CryptoMovementKind.MOVE, CryptoMovementRules.kindOf(btcWallet, ledgerBtc))
        assertNull(CryptoMovementRules.kindOf(bank, payoneer))
    }

    @Test
    fun buyingWithLkrUsesThatAmountAsTheValue() {
        val result = CryptoMovementRules.validate(input(bank, btcWallet, sent = "300,000", received = "0.01"), today) as CryptoCheck.Valid
        assertEquals(lkr(300_000), result.value.lkrValue)
        val transfer = CryptoMovementRules.newTransfer(TransferId("t1"), result.value, T0)
        assertEquals(ReportingConversion.SameCurrency, transfer.sentConversion)
        val converted = transfer.receivedConversion as ReportingConversion.Converted
        assertEquals(lkr(300_000), converted.amount)
        assertEquals(0, "30000000".toBigDecimal().compareTo(converted.rate.rate))
        assertEquals(TransactionType.TRANSFER_IN, transfer.inLeg().type)
    }

    @Test
    fun tradeValueIsOptionalAndStaysMissingWhenBlank() {
        val result = CryptoMovementRules.validate(input(usdtWallet, btcWallet, sent = "500", received = "0.005"), today) as CryptoCheck.Valid
        val transfer = CryptoMovementRules.newTransfer(TransferId("t1"), result.value, T0)
        assertEquals(ReportingConversion.Missing(Currencies.LKR), transfer.sentConversion)
        assertEquals(ReportingConversion.Missing(Currencies.LKR), transfer.receivedConversion)
    }

    @Test
    fun moveBetweenOwnWalletsDefaultsReceivedToSent() {
        val result = CryptoMovementRules.validate(input(btcWallet, ledgerBtc, sent = "0.01"), today) as CryptoCheck.Valid
        assertEquals(CryptoAmount.of("0.01".toBigDecimal(), Currencies.BTC), result.value.received)
    }

    @Test
    fun rejectsBadInput() {
        assertEquals(setOf(CryptoProblem.FROM_MISSING, CryptoProblem.TO_MISSING), problems(CryptoMovementRules.validate(input(null, null), today)))
        assertTrue(CryptoProblem.SAME_ACCOUNT in problems(CryptoMovementRules.validate(input(btcWallet, btcWallet), today)))
        assertTrue(CryptoProblem.NO_CRYPTO_ACCOUNT in problems(CryptoMovementRules.validate(input(bank, payoneer, sent = "1", received = "1"), today)))
        assertTrue(CryptoProblem.SENT_INVALID in problems(CryptoMovementRules.validate(input(btcWallet, bank, sent = "0.000000001", received = "100"), today)))
        assertTrue(CryptoProblem.RECEIVED_MISSING in problems(CryptoMovementRules.validate(input(usdtWallet, btcWallet, sent = "5"), today)))
        assertTrue(CryptoProblem.VALUE_INVALID in problems(CryptoMovementRules.validate(input(usdtWallet, btcWallet, sent = "5", received = "0.1", value = "-1"), today)))
        assertTrue(CryptoProblem.DATE_IN_FUTURE in problems(CryptoMovementRules.validate(input(bank, btcWallet, sent = "1", received = "0.1", date = today.plusDays(1)), today)))
    }

    @Test
    fun openingCostIsOptionalLkr() {
        assertEquals(CryptoCheck.Valid(null), CryptoMovementRules.parseOpeningCost(" "))
        assertEquals(CryptoCheck.Valid(lkr(500_000)), CryptoMovementRules.parseOpeningCost("500,000"))
        assertEquals(setOf(CryptoProblem.COST_INVALID), problems(CryptoMovementRules.parseOpeningCost("0")))
    }

    @Test
    fun openingCostRoundTripsThroughFirestoreMaps() {
        val cost = OpeningCost(AccountId("btc"), lkr(500_000), isApproximate = true, updatedAt = T0)
        assertEquals(cost, OpeningCostDocumentMapper.fromMap(OpeningCostDocumentMapper.toMap(cost)))
        assertNull(OpeningCostDocumentMapper.fromMap(mapOf("accountId" to "btc")))
    }
}
