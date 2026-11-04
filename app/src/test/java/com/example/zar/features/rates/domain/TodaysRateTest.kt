package com.example.zar.features.rates.domain

import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaysRateTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun todaysRateIsOfferedForAForeignCurrencyOnTodayOnly() {
        assertTrue(TodaysRateRules.canOffer(Currencies.USD, today, today))
        assertFalse(TodaysRateRules.canOffer(Currencies.USD, today.minusDays(1), today))
    }

    @Test
    fun itIsNotOfferedForLkrOrCryptoOrNoCurrency() {
        assertFalse(TodaysRateRules.canOffer(Currencies.LKR, today, today))
        assertFalse(TodaysRateRules.canOffer(Currencies.BTC, today, today))
        assertFalse(TodaysRateRules.canOffer(null, today, today))
    }

    private class FakeRepository(private val answer: Result<RateQuote>) : ExchangeRateRepository {
        var calls = 0
        override suspend fun todaysRate(base: Currency, quote: Currency): Result<RateQuote> {
            calls++
            return answer
        }
    }

    private val quote = RateQuote(
        Currencies.USD, Currencies.LKR, "332.44".toBigDecimal(), Instant.parse("2026-11-04T00:02:31Z"), Instant.parse("2026-11-05T00:18:21Z"),
    )

    @Test
    fun theUseCaseAsksTheRepositoryForAForeignCurrency() = runTest {
        val repository = FakeRepository(Result.success(quote))
        assertEquals(quote, FetchTodaysRateUseCase(repository)(Currencies.USD).getOrThrow())
        assertEquals(1, repository.calls)
    }

    @Test
    fun theUseCaseNeverAsksForLkrOrCrypto() = runTest {
        val repository = FakeRepository(Result.success(quote))
        val useCase = FetchTodaysRateUseCase(repository)
        assertTrue(useCase(Currencies.LKR).exceptionOrNull() is RateFetchError.Unavailable)
        assertTrue(useCase(Currencies.BTC).exceptionOrNull() is RateFetchError.Unavailable)
        assertEquals(0, repository.calls)
    }

    @Test
    fun aFailureIsPassedOnAsIs() = runTest {
        val repository = FakeRepository(Result.failure(RateFetchError.Offline()))
        assertTrue(FetchTodaysRateUseCase(repository)(Currencies.USD).exceptionOrNull() is RateFetchError.Offline)
    }
}
