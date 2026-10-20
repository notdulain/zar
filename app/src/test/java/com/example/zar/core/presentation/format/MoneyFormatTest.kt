package com.example.zar.core.presentation.format

import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun wholeAmountsHaveNoDecimals() {
        assertEquals("148,350", FiatMoney(14_835_000, Currencies.LKR).formatNumber())
        assertEquals("0", FiatMoney(0, Currencies.LKR).formatNumber())
    }

    @Test
    fun fractionalAmountsKeepTwoDecimals() {
        assertEquals("1,250.50", FiatMoney(125_050, Currencies.LKR).formatNumber())
        assertEquals("0.05", FiatMoney(5, Currencies.LKR).formatNumber())
    }

    @Test
    fun codeIsAddedWhenRequested() {
        assertEquals("LKR 8,400", FiatMoney(840_000, Currencies.LKR).formatWithCode())
    }

    @Test
    fun rowsShowLkrBareAndOtherCurrenciesWithCode() {
        assertEquals("8,400", FiatMoney(840_000, Currencies.LKR).formatForRow())
        assertEquals("USD 100", FiatMoney(10_000, Currencies.USD).formatForRow())
    }

    @Test
    fun cryptoKeepsItsPrecision() {
        val btc = CryptoAmount.of("0.00012345".toBigDecimal(), Currencies.BTC)
        assertEquals("BTC 0.00012345", btc.formatWithCode())
    }
    @Test
    fun tinyEthAmountsNeverRoundDownToZero() {
        val eth = CryptoAmount.of("0.000000000000000001".toBigDecimal(), Currencies.ETH)
        assertEquals("ETH 0.000000000000000001", eth.formatWithCode())
        assertEquals("ETH 1.123456789012345678", CryptoAmount.of("1.123456789012345678".toBigDecimal(), Currencies.ETH).formatWithCode())
    }

}
