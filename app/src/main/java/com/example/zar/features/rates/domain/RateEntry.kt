package com.example.zar.features.rates.domain

import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class RateFetch { IDLE, FETCHING, FAILED }

/** The rate a saved record already carries, with where it came from. Shown again when the record is edited. */
data class SavedRate(val rate: BigDecimal, val source: RateSource, val asOf: Instant) {
    companion object {
        fun of(conversion: ReportingConversion): SavedRate? =
            (conversion as? ReportingConversion.Converted)?.rate?.let { SavedRate(it.rate, it.source, it.asOf) }
    }
}

/** Which line sits under the rate field. */
enum class RateHint {
    /** Today's rate can be fetched. */
    AVAILABLE,
    FETCHING,
    FAILED,

    /** The field holds the fetched number. */
    FILLED,

    /** The user typed over a fetched number. */
    TYPED_OVER,

    /** The rate of a saved record, taken from the provider. */
    SAVED_FROM_PROVIDER,

    /** Today's rate is not offered because the date is not today. */
    EARLIER_DATE,

    /** Nothing to say beyond "leave it empty if you don't know". */
    PLAIN,
}

/**
 * Everything one rate field needs, without any screen code: the typed [text], the quote it may still equal, whether a
 * fetch is running or failed, and the rate a saved record already had. All changes return a new value.
 */
data class RateEntry(
    val text: String = "",
    /** The last fetched quote. It stays so the field can say "today's rate is 332.44" after the user types over it. */
    val quote: RateQuote? = null,
    val fetch: RateFetch = RateFetch.IDLE,
    val saved: SavedRate? = null,
) {
    /** The number in the field, or null when it is empty or not a valid rate. */
    val parsed: BigDecimal? get() = RateText.parse(text)

    /** The fetched quote, while the field still holds exactly its number. Null once the user has typed something else. */
    val providerQuoteInUse: RateQuote?
        get() = quote?.takeIf { q -> parsed?.compareTo(q.savedRate) == 0 }

    /** The line under the field. [canOffer] says whether today's rate may be fetched for the current date and currency. */
    fun hint(canOffer: Boolean): RateHint = when {
        fetch == RateFetch.FETCHING -> RateHint.FETCHING
        fetch == RateFetch.FAILED -> RateHint.FAILED
        providerQuoteInUse != null -> RateHint.FILLED
        quote != null && text.isNotBlank() -> RateHint.TYPED_OVER
        savedFromProvider != null -> RateHint.SAVED_FROM_PROVIDER
        !canOffer && text.isBlank() -> RateHint.EARLIER_DATE
        canOffer -> RateHint.AVAILABLE
        else -> RateHint.PLAIN
    }

    /** The saved record's provider rate, while the field still holds exactly that number. */
    val savedFromProvider: SavedRate?
        get() = saved?.takeIf { it.source == RateSource.PROVIDER && parsed?.compareTo(it.rate) == 0 }

    /** How the saved rate was got: from the provider while the field still holds the fetched number, otherwise typed. */
    val source: RateSource
        get() = when {
            providerQuoteInUse != null -> RateSource.PROVIDER
            savedFromProvider != null -> RateSource.PROVIDER
            else -> RateSource.MANUAL
        }

    /** When the rate was published, for a provider rate. */
    val asOf: Instant?
        get() = providerQuoteInUse?.updatedAt ?: savedFromProvider?.asOf

    fun typed(newText: String): RateEntry = copy(text = newText, fetch = RateFetch.IDLE)

    fun fetching(): RateEntry = copy(fetch = RateFetch.FETCHING)

    fun failed(): RateEntry = copy(fetch = RateFetch.FAILED)

    /** Puts the fetched number in the field. */
    fun fetched(newQuote: RateQuote): RateEntry = copy(text = newQuote.fieldText, quote = newQuote, fetch = RateFetch.IDLE)

    /**
     * The date changed. A fetched rate is today's, so it is taken out of the field when the date is no longer today.
     * A number the user typed stays.
     */
    fun onDateChanged(date: LocalDate, today: LocalDate): RateEntry {
        if (date == today) return this
        val keepText = if (providerQuoteInUse != null) "" else text
        return copy(text = keepText, quote = null, fetch = RateFetch.IDLE)
    }

    companion object {
        /** The field of an existing record: its rate typed in, with where it came from. */
        fun of(conversion: ReportingConversion): RateEntry {
            val saved = SavedRate.of(conversion)
            return RateEntry(text = saved?.rate?.stripTrailingZeros()?.toPlainString().orEmpty(), saved = saved)
        }
    }
}
