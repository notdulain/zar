package com.example.zar.core.domain.model

import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun parsesFiatIntoMinorUnits() {
        assertEquals(FiatMoney(125_050, Currencies.LKR), FiatMoney.parse("1,250.50", Currencies.LKR))
    }

    @Test
    fun rejectsFiatWithTooManyDecimals() {
        assertNull(FiatMoney.parse("10.005", Currencies.LKR))
    }

    @Test
    fun fiatArithmeticIsExact() {
        val sum = FiatMoney.parse("0.10", Currencies.LKR)!! + FiatMoney.parse("0.20", Currencies.LKR)!!
        assertEquals(FiatMoney(30, Currencies.LKR), sum)
    }

    @Test
    fun cannotMixCurrencies() {
        assertThrows(IllegalArgumentException::class.java) {
            FiatMoney(100, Currencies.LKR) + FiatMoney(100, Currencies.USD)
        }
    }

    @Test
    fun cryptoKeepsPrecisionAndIgnoresTrailingZeros() {
        val a = CryptoAmount.parse("0.00012345", Currencies.BTC)!!
        val b = CryptoAmount.parse("0.000123450", Currencies.BTC)!!
        assertEquals(a, b)
        assertEquals("0.00024690", (a + b).toDecimal().setScale(8).toPlainString())
    }

    @Test
    fun cryptoRejectsMorePrecisionThanTheAsset() {
        assertNull(CryptoAmount.parse("0.000000001", Currencies.BTC))
    }
}
