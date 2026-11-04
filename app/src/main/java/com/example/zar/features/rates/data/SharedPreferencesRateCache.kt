package com.example.zar.features.rates.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A rate kept from the provider's last answer, in the provider's own units (unix seconds). */
data class CachedRate(val rate: String, val updatedAtSeconds: Long, val nextUpdateAtSeconds: Long)

/** Keeps the last answer for each currency pair, and the time until which the provider asked this phone to wait. */
interface RateCache {
    fun read(base: String, quote: String): CachedRate?

    fun write(base: String, quote: String, cached: CachedRate)

    /** The time before which the provider must not be asked again after it refused with "too many requests". */
    fun blockedUntil(): Instant?

    fun blockUntil(until: Instant)
}

/**
 * Plain preferences, not DataStore: the values are tiny, public data, and are read in the middle of a tap. A rate that
 * is restored from a backup is harmless because it is only used until its own next-update time.
 */
@Singleton
class SharedPreferencesRateCache internal constructor(
    private val preferences: SharedPreferences,
) : RateCache {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun read(base: String, quote: String): CachedRate? {
        val parts = preferences.getString(key(base, quote), null)?.split(SEPARATOR) ?: return null
        if (parts.size != 3) return null
        val updated = parts[1].toLongOrNull() ?: return null
        val next = parts[2].toLongOrNull() ?: return null
        return CachedRate(parts[0], updated, next)
    }

    override fun write(base: String, quote: String, cached: CachedRate) {
        preferences.edit()
            .putString(key(base, quote), listOf(cached.rate, cached.updatedAtSeconds, cached.nextUpdateAtSeconds).joinToString(SEPARATOR))
            .apply()
    }

    override fun blockedUntil(): Instant? =
        preferences.getLong(KEY_BLOCKED_UNTIL, 0L).takeIf { it > 0 }?.let(Instant::ofEpochSecond)

    override fun blockUntil(until: Instant) {
        preferences.edit().putLong(KEY_BLOCKED_UNTIL, until.epochSecond).apply()
    }

    private fun key(base: String, quote: String) = "rate_${base}_$quote"

    companion object {
        const val FILE_NAME = "exchange_rates"
        private const val KEY_BLOCKED_UNTIL = "blocked_until"
        private const val SEPARATOR = "|"
    }
}
