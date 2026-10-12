package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.model.money.ReportingConversion
import java.math.RoundingMode

object CurrencyConverter {

    /** Converts with banker's rounding to the target currency's minor unit. */
    fun convert(amount: Money, rate: ExchangeRate): FiatMoney {
        require(amount.currency == rate.base) { "Rate is for ${rate.base.code}, amount is ${amount.currency.code}" }
        require(rate.quote.kind == AssetKind.FIAT) { "Can only convert into fiat" }
        val digits = rate.quote.fractionDigits
        val minor = amount.toDecimal()
            .multiply(rate.rate)
            .setScale(digits, RoundingMode.HALF_EVEN)
            .movePointRight(digits)
            .longValueExact()
        return FiatMoney(minor, rate.quote)
    }

    /** Builds the conversion to store with a new record. A null [rate] is recorded as missing. */
    fun toReporting(
        amount: Money,
        rate: ExchangeRate?,
        reporting: Currency = Currencies.REPORTING,
    ): ReportingConversion = when {
        amount.currency == reporting -> ReportingConversion.SameCurrency
        rate == null -> ReportingConversion.Missing(reporting)
        else -> {
            require(rate.quote == reporting) { "Rate must convert into ${reporting.code}" }
            ReportingConversion.Converted(rate, convert(amount, rate))
        }
    }

    /** Recomputes the stored conversion for a corrected amount using the same historical rate. */
    fun reconvert(newAmount: Money, previous: ReportingConversion): ReportingConversion = when (previous) {
        ReportingConversion.SameCurrency -> previous
        is ReportingConversion.Missing -> previous
        is ReportingConversion.Converted -> ReportingConversion.Converted(previous.rate, convert(newAmount, previous.rate))
    }

    /** The record's reporting value, or null when its rate is missing. */
    fun reportingAmount(amount: Money, conversion: ReportingConversion): FiatMoney? = when (conversion) {
        ReportingConversion.SameCurrency -> amount as? FiatMoney
        is ReportingConversion.Converted -> conversion.amount
        is ReportingConversion.Missing -> null
    }
}
