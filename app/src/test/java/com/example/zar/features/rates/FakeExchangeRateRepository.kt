package com.example.zar.features.rates

import com.example.zar.core.domain.model.money.Currency
import com.example.zar.features.rates.domain.ExchangeRateRepository
import com.example.zar.features.rates.domain.RateFetchError
import com.example.zar.features.rates.domain.RateQuote
import kotlinx.coroutines.CompletableDeferred

/** A rate source for tests. [gate], when set, holds the answer until it is completed, so a fetch can be left in flight. */
class FakeExchangeRateRepository(
    var answer: Result<RateQuote> = Result.failure(RateFetchError.Offline()),
) : ExchangeRateRepository {
    val requests = mutableListOf<Pair<Currency, Currency>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun todaysRate(base: Currency, quote: Currency): Result<RateQuote> {
        requests += base to quote
        gate?.await()
        return answer
    }
}
