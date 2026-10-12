package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger.usdRate
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyConverterTest {

    @Test
    fun convertsWithBankersRounding() {
        val rate = ExchangeRate(Currencies.USD, Currencies.LKR, "300.555".toBigDecimal(), T0, RateSource.MANUAL)
        // 10.00 * 300.555 = 3005.55 exactly; 0.01 * 300.555 = 3.00555 -> 3.01
        assertEquals(FiatMoney(300_555, Currencies.LKR), CurrencyConverter.convert(usd(10), rate))
        assertEquals(FiatMoney(301, Currencies.LKR), CurrencyConverter.convert(FiatMoney(1, Currencies.USD), rate))
    }

    @Test
    fun convertsCrypto() {
        val rate = ExchangeRate(Currencies.USDT, Currencies.LKR, "299.5".toBigDecimal(), T0, RateSource.MANUAL)
        val amount = CryptoAmount.parse("12.5", Currencies.USDT)!!
        assertEquals(FiatMoney(374_375, Currencies.LKR), CurrencyConverter.convert(amount, rate))
    }

    @Test
    fun reportingCurrencyNeedsNoRate() {
        assertEquals(ReportingConversion.SameCurrency, CurrencyConverter.toReporting(lkr(500), null))
    }

    @Test
    fun missingRateIsRecordedAndHasNoValue() {
        val conversion = CurrencyConverter.toReporting(usd(10), null)
        assertEquals(ReportingConversion.Missing(Currencies.LKR), conversion)
        assertNull(CurrencyConverter.reportingAmount(usd(10), conversion))
    }

    @Test
    fun reconvertKeepsTheHistoricalRate() {
        val saved = CurrencyConverter.toReporting(usd(10), usdRate)
        val corrected = CurrencyConverter.reconvert(usd(12), saved) as ReportingConversion.Converted
        assertEquals(usdRate, corrected.rate)
        assertEquals(lkr(3_600), corrected.amount)
    }
}
