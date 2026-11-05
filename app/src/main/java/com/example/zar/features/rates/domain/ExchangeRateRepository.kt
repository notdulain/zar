package com.example.zar.features.rates.domain

import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import java.time.LocalDate
import javax.inject.Inject

/** Where today's rate comes from. Implementations keep a quote until the provider's next update. */
interface ExchangeRateRepository {
    /** The latest rate for one unit of [base] in [quote]. The failure is a [RateFetchError]. */
    suspend fun todaysRate(base: Currency, quote: Currency): Result<RateQuote>
}

/** When today's rate may be offered next to a rate field. */
object TodaysRateRules {
    /**
     * Only for an ordinary currency other than LKR, and only for today: an earlier income or expense needs the rate of
     * its own day, so today's rate could be wrong for it. A crypto asset has no rate from this provider.
     */
    fun canOffer(currency: Currency?, date: LocalDate, today: LocalDate, reporting: Currency = Currencies.REPORTING): Boolean =
        currency != null && currency.kind == AssetKind.FIAT && currency != reporting && date == today
}

/** Gets today's rate into the reporting currency for a form's rate field. */
class FetchTodaysRateUseCase @Inject constructor(
    private val repository: ExchangeRateRepository,
) {
    suspend operator fun invoke(currency: Currency, reporting: Currency = Currencies.REPORTING): Result<RateQuote> =
        if (currency.kind != AssetKind.FIAT || currency == reporting) {
            Result.failure(RateFetchError.Unavailable())
        } else {
            repository.todaysRate(currency, reporting)
        }
}
