package com.example.zar.features.rates.data

import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.rates.domain.RateFetchError
import com.example.zar.features.settings.data.FakeSharedPreferences
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExchangeRateApiRepositoryTest {

    private val server = MockWebServer()
    private val cache = SharedPreferencesRateCache(FakeSharedPreferences())
    private var now: Instant = Instant.ofEpochSecond(1_793_760_000) // 4 Nov 2026, a little after the provider's update

    private val updatedAt = 1_793_750_551L
    private val nextUpdateAt = 1_793_837_901L

    private fun repository() = ExchangeRateApiRepository(
        server.url("/").toString().trimEnd('/'), OkHttpClient(), cache, { now },
    )

    private fun body(
        result: String = "success",
        base: String = "USD",
        rates: String = """"LKR": 332.444906, "EUR": 0.86""",
        updated: String = "$updatedAt",
        next: String = "$nextUpdateAt",
    ) = """{"result":"$result","provider":"https://www.exchangerate-api.com","base_code":"$base",
        |"time_last_update_unix":$updated,"time_next_update_unix":$next,"time_eol_unix":0,"rates":{$rates}}""".trimMargin()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Before fun start() = server.start()

    @After fun stop() = runCatching { server.shutdown() }.let { }

    @Test
    fun readsTodaysRateAndWhenItWasPublished() = runTest {
        server.enqueue(ok(body()))
        val quote = repository().todaysRate(Currencies.USD, Currencies.LKR).getOrThrow()
        assertEquals("332.444906", quote.rate.toPlainString())
        assertEquals("332.4449", quote.fieldText)
        assertEquals(Instant.ofEpochSecond(updatedAt), quote.updatedAt)
        assertEquals(Instant.ofEpochSecond(nextUpdateAt), quote.nextUpdateAt)
        val request = server.takeRequest()
        assertEquals("/v6/latest/USD", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun keepsTheAnswerUntilTheProvidersNextUpdate() = runTest {
        server.enqueue(ok(body()))
        val repository = repository()
        repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow()
        now = Instant.ofEpochSecond(nextUpdateAt - 60)
        assertEquals("332.4449", repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().fieldText)
        assertEquals("one request, the second answer came from the cache", 1, server.requestCount)

        now = Instant.ofEpochSecond(nextUpdateAt + 1)
        server.enqueue(ok(body(rates = """"LKR": 333.5""", updated = "${nextUpdateAt}", next = "${nextUpdateAt + 86_400}")))
        assertEquals("333.5", repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().fieldText)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aTooManyRequestsAnswerIsRememberedForTwentyMinutes() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val repository = repository()
        assertTrue(repository.todaysRate(Currencies.USD, Currencies.LKR).exceptionOrNull() is RateFetchError.RateLimited)
        assertEquals(1, server.requestCount)

        now = now.plus(Duration.ofMinutes(19))
        assertTrue(repository.todaysRate(Currencies.USD, Currencies.LKR).exceptionOrNull() is RateFetchError.RateLimited)
        assertEquals("nothing is sent while the wait lasts", 1, server.requestCount)

        now = now.plus(Duration.ofMinutes(2))
        server.enqueue(ok(body()))
        assertEquals("332.4449", repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().fieldText)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun anErrorStatusIsUnavailableAndNothingIsKept() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val repository = repository()
        assertTrue(repository.todaysRate(Currencies.USD, Currencies.LKR).exceptionOrNull() is RateFetchError.Unavailable)
        server.enqueue(ok(body()))
        assertEquals("332.4449", repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().fieldText)
    }

    @Test
    fun answersThatCannotBeTrustedAreUnavailable() = runTest {
        val bad = listOf(
            "not json at all",
            """{"result":"error","error-type":"unsupported-code"}""",
            body(result = "error"),
            body(base = "EUR"),
            body(rates = """"EUR": 0.86"""),          // no LKR
            body(rates = """"LKR": 0"""),
            body(rates = """"LKR": -3"""),
            body(rates = """"LKR": "abc""""),
            body(rates = """"LKR": 5000000000"""),
            body(updated = "${now.epochSecond + 7 * 86_400}"), // published in the future
            "",
        )
        for (answer in bad) {
            server.enqueue(ok(answer))
            val failure = repository().todaysRate(Currencies.USD, Currencies.LKR).exceptionOrNull()
            assertTrue("'$answer' should be unavailable but was $failure", failure is RateFetchError.Unavailable)
        }
    }

    @Test
    fun aBodyOverTheLimitIsRefused() = runTest {
        server.enqueue(ok(body(rates = """"LKR": 332.4, "X": "${"a".repeat(300 * 1024)}"""")))
        assertTrue(repository().todaysRate(Currencies.USD, Currencies.LKR).exceptionOrNull() is RateFetchError.Unavailable)
    }

    @Test
    fun noConnectionIsOffline() = runTest {
        val repository = repository()
        server.shutdown()
        assertTrue(repository.todaysRate(Currencies.USD, Currencies.LKR).exceptionOrNull() is RateFetchError.Offline)
    }

    @Test
    fun aMissingOrPastNextUpdateIsKeptForAnHourAndAFarOneForADay() = runTest {
        val repository = repository()
        server.enqueue(ok(body(next = "0")))
        assertEquals(now.plus(Duration.ofHours(1)), repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().nextUpdateAt)

        now = now.plus(Duration.ofHours(2))
        server.enqueue(ok(body(next = "${now.epochSecond + 30 * 86_400}")))
        assertEquals(now.plus(Duration.ofHours(24)), repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().nextUpdateAt)
    }

    @Test
    fun eachCurrencyPairHasItsOwnAnswer() = runTest {
        val repository = repository()
        server.enqueue(ok(body()))
        server.enqueue(ok(body(base = "EUR", rates = """"LKR": 385.5""")))
        assertEquals("332.4449", repository.todaysRate(Currencies.USD, Currencies.LKR).getOrThrow().fieldText)
        assertEquals("385.5", repository.todaysRate(Currencies.EUR, Currencies.LKR).getOrThrow().fieldText)
        assertEquals(listOf("/v6/latest/USD", "/v6/latest/EUR"), listOf(server.takeRequest().path, server.takeRequest().path))
    }
}
