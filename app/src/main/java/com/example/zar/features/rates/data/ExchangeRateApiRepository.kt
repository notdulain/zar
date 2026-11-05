package com.example.zar.features.rates.data

import com.example.zar.core.domain.model.money.Currency
import com.example.zar.features.rates.domain.ExchangeRateRepository
import com.example.zar.features.rates.domain.RateFetchError
import com.example.zar.features.rates.domain.RateQuote
import java.io.IOException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Today's rate from ExchangeRate-API's open endpoint. It needs no key and publishes once a day, so an answer is kept
 * until the time the provider itself gives for its next update, and a phone asks about once a day. The provider limits
 * requests per address; after a "too many requests" answer nothing is sent until the wait it names has passed.
 * Where the rate is shown the screen must link "Rates By Exchange Rate API" (the provider's terms).
 */
@Singleton
class ExchangeRateApiRepository internal constructor(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val cache: RateCache,
    private val now: () -> Instant,
) : ExchangeRateRepository {
    @Inject
    constructor(cache: SharedPreferencesRateCache) : this(
        BASE_URL,
        OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build(),
        cache,
        Instant::now,
    )

    override suspend fun todaysRate(base: Currency, quote: Currency): Result<RateQuote> {
        val current = now()
        cache.read(base.code, quote.code)?.let { cached ->
            val kept = cached.toQuote(base, quote)
            if (kept != null && current.isBefore(kept.nextUpdateAt) && !kept.updatedAt.isAfter(current)) return Result.success(kept)
        }
        cache.blockedUntil()?.takeIf { it.isAfter(current) }?.let { return Result.failure(RateFetchError.RateLimited()) }
        return try {
            val body = download(base)
            val fetched = parse(body, base, quote, now())
            cache.write(base.code, quote.code, CachedRate(fetched.rate.toPlainString(), fetched.updatedAt.epochSecond, fetched.nextUpdateAt.epochSecond))
            Result.success(fetched)
        } catch (error: RateFetchError) {
            if (error is RateFetchError.RateLimited) cache.blockUntil(current.plus(RATE_LIMIT_WAIT))
            Result.failure(error)
        }
    }

    /** The response body, or the [RateFetchError] that says why there is none. */
    private suspend fun download(base: Currency): String {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("v6/latest").addPathSegment(base.code).build()
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(RateFetchError.Offline()))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { Result.success(read(it)) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RateFetchError) {
                        Result.failure(e)
                    } catch (_: IOException) {
                        Result.failure(RateFetchError.Offline())
                    }
                    continuation.resumeWith(result)
                }
            })
        }
    }

    private fun read(response: Response): String {
        if (response.code == HTTP_TOO_MANY_REQUESTS) throw RateFetchError.RateLimited()
        if (!response.isSuccessful) throw RateFetchError.Unavailable()
        val source = response.body.source()
        // Bound the body before it is turned into a String.
        source.request(MAX_RESPONSE_BYTES + 1)
        if (source.buffer.size > MAX_RESPONSE_BYTES) throw RateFetchError.Unavailable()
        return source.readUtf8()
    }

    private fun parse(body: String, base: Currency, quote: Currency, current: Instant): RateQuote = try {
        val root: JsonObject = Json.parseToJsonElement(body).jsonObject
        if (root["result"]?.jsonPrimitive?.contentOrNull != "success") throw RateFetchError.Unavailable()
        if (root["base_code"]?.jsonPrimitive?.contentOrNull != base.code) throw RateFetchError.Unavailable()
        val rate = root["rates"]?.jsonObject?.get(quote.code)?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
        if (rate == null || rate.signum() <= 0 || rate >= MAX_RATE) throw RateFetchError.Unavailable()
        val updatedAt = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond) ?: current
        if (updatedAt.isAfter(current.plus(CLOCK_SLACK))) throw RateFetchError.Unavailable()
        // Keep the answer until the provider's next update, but never so long that one odd value pins it.
        val next = root["time_next_update_unix"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
        val nextUpdateAt = when {
            next == null || !next.isAfter(current) -> current.plus(FALLBACK_KEEP)
            next.isAfter(current.plus(LONGEST_KEEP)) -> current.plus(LONGEST_KEEP)
            else -> next
        }
        RateQuote(base, quote, rate, updatedAt, nextUpdateAt)
    } catch (error: RateFetchError) {
        throw error
    } catch (_: Exception) {
        // Not JSON, wrong shape, or a value the model refuses.
        throw RateFetchError.Unavailable()
    }

    private fun CachedRate.toQuote(base: Currency, quote: Currency): RateQuote? = try {
        RateQuote(base, quote, BigDecimal(rate), Instant.ofEpochSecond(updatedAtSeconds), Instant.ofEpochSecond(nextUpdateAtSeconds))
    } catch (_: Exception) {
        null
    }

    companion object {
        const val BASE_URL = "https://open.er-api.com"
        private const val CALL_TIMEOUT_SECONDS = 10L
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_RESPONSE_BYTES = 256L * 1024
        private val MAX_RATE = BigDecimal("1000000000")
        private val RATE_LIMIT_WAIT: Duration = Duration.ofMinutes(20)
        private val FALLBACK_KEEP: Duration = Duration.ofHours(1)
        private val LONGEST_KEEP: Duration = Duration.ofHours(24)
        private val CLOCK_SLACK: Duration = Duration.ofMinutes(10)
    }
}
