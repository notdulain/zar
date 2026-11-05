package com.example.zar.features.rates.domain

import com.example.zar.core.domain.model.money.Currency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * One unit of [base] is worth [rate] units of [quote], as published by the rate provider at [updatedAt].
 * [nextUpdateAt] is when the provider will publish again, so the quote can be kept until then.
 */
data class RateQuote(
    val base: Currency,
    val quote: Currency,
    val rate: BigDecimal,
    val updatedAt: Instant,
    val nextUpdateAt: Instant,
) {
    init {
        require(base != quote) { "A rate needs two different currencies" }
        require(savedRate.signum() > 0) { "Rate must be positive" }
    }

    /** The rate that is shown in the field and saved: the provider's number to four decimals. */
    val savedRate: BigDecimal get() = rate.setScale(RATE_DIGITS, RoundingMode.HALF_UP)

    /** [savedRate] as the text put in the rate field, without trailing zeros. */
    val fieldText: String get() = savedRate.stripTrailingZeros().toPlainString()

    companion object {
        const val RATE_DIGITS = 4
    }
}

/** Why today's rate could not be had. None of these block saving: the rate is then typed or left empty. */
sealed class RateFetchError(message: String) : Exception(message) {
    /** The phone could not reach the provider. */
    class Offline : RateFetchError("offline")

    /** The provider asked this phone to slow down. Asking again straight away would only extend the wait. */
    class RateLimited : RateFetchError("rate limited")

    /** The provider answered with something that cannot be used, or has no rate for this currency. */
    class Unavailable : RateFetchError("unavailable")
}
