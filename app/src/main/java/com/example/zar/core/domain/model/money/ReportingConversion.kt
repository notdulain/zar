package com.example.zar.core.domain.model.money

import java.math.BigDecimal
import java.time.Instant

enum class RateSource { MANUAL, BANK_MESSAGE, PROVIDER }

/** One unit of [base] equals [rate] units of [quote], as known at [asOf]. */
data class ExchangeRate(
    val base: Currency,
    val quote: Currency,
    val rate: BigDecimal,
    val asOf: Instant,
    val source: RateSource,
) {
    init {
        require(rate.signum() > 0) { "Rate must be positive" }
        require(base != quote) { "Rate needs two different currencies" }
    }
}

/**
 * A record's value in the reporting currency, fixed when the record is saved. Historical
 * conversions are kept as saved and never silently recalculated with today's rate.
 */
sealed interface ReportingConversion {
    /** The record is already in the reporting currency. */
    data object SameCurrency : ReportingConversion

    data class Converted(val rate: ExchangeRate, val amount: FiatMoney) : ReportingConversion

    /** No rate was known. Totals must show this record as unresolved instead of guessing. */
    data class Missing(val target: Currency) : ReportingConversion
}
