package com.example.zar.features.rates.domain

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateEntryTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val published = Instant.parse("2026-11-04T00:02:31Z")
    private val quote = RateQuote(
        Currencies.USD, Currencies.LKR, "332.444906".toBigDecimal(), published, Instant.parse("2026-11-05T00:18:21Z"),
    )

    @Test
    fun aFetchedRateFillsTheFieldAndIsSavedAsTheProvidersRate() {
        val entry = RateEntry().fetched(quote)
        // Four decimals: what the person sees is exactly what is saved.
        assertEquals("332.4449", entry.text)
        assertEquals(RateSource.PROVIDER, entry.source)
        assertEquals(published, entry.asOf)
        assertEquals(RateHint.FILLED, entry.hint(canOffer = true))
    }

    @Test
    fun typingOverAFetchedRateMakesItTheUsersOwn() {
        val entry = RateEntry().fetched(quote).typed("330")
        assertEquals(RateSource.MANUAL, entry.source)
        assertNull(entry.asOf)
        assertEquals(RateHint.TYPED_OVER, entry.hint(canOffer = true))
        assertEquals("332.4449", entry.quote?.fieldText)
    }

    @Test
    fun typingTheFetchedNumberBackIsTheProvidersRateAgain() {
        val entry = RateEntry().fetched(quote).typed("330").typed("332.4449")
        assertEquals(RateSource.PROVIDER, entry.source)
        assertEquals(RateHint.FILLED, entry.hint(canOffer = true))
    }

    @Test
    fun anEmptyFieldSaysWhatIsPossible() {
        assertEquals(RateHint.AVAILABLE, RateEntry().hint(canOffer = true))
        assertEquals(RateHint.EARLIER_DATE, RateEntry().hint(canOffer = false))
        assertEquals(RateHint.PLAIN, RateEntry(text = "300").hint(canOffer = false))
    }

    @Test
    fun aRunningOrFailedFetchIsShown() {
        assertEquals(RateHint.FETCHING, RateEntry().fetching().hint(canOffer = true))
        assertEquals(RateHint.FAILED, RateEntry().fetching().failed().hint(canOffer = true))
        // Typing means the person has moved on; the failure message goes away.
        assertEquals(RateHint.AVAILABLE, RateEntry().failed().typed("").hint(canOffer = true))
    }

    @Test
    fun aFailedFetchLeavesTheTypedNumberAlone() {
        val entry = RateEntry(text = "330").fetching().failed()
        assertEquals("330", entry.text)
        assertEquals(RateSource.MANUAL, entry.source)
    }

    @Test
    fun movingTheDateAwayFromTodayTakesAFetchedRateOutOfTheField() {
        val entry = RateEntry().fetched(quote).onDateChanged(today.minusDays(1), today)
        assertEquals("", entry.text)
        assertNull(entry.quote)
        assertEquals(RateHint.EARLIER_DATE, entry.hint(canOffer = false))
    }

    @Test
    fun movingTheDateAwayKeepsANumberTheUserTyped() {
        val entry = RateEntry().fetched(quote).typed("330").onDateChanged(today.minusDays(1), today)
        assertEquals("330", entry.text)
        assertNull(entry.quote)
        assertEquals(RateSource.MANUAL, entry.source)
    }

    @Test
    fun keepingTheDateAsTodayChangesNothing() {
        val entry = RateEntry().fetched(quote)
        assertEquals(entry, entry.onDateChanged(today, today))
    }

    @Test
    fun aSavedProviderRateKeepsItsSourceAndTimeUntilItIsEdited() {
        val conversion = ReportingConversion.Converted(
            ExchangeRate(Currencies.USD, Currencies.LKR, "300.50".toBigDecimal(), published, RateSource.PROVIDER), lkr(150_250),
        )
        val entry = RateEntry.of(conversion)
        assertEquals("300.5", entry.text)
        assertEquals(RateSource.PROVIDER, entry.source)
        assertEquals(published, entry.asOf)
        assertEquals(RateHint.SAVED_FROM_PROVIDER, entry.hint(canOffer = false))

        val edited = entry.typed("301")
        assertEquals(RateSource.MANUAL, edited.source)
        assertNull(edited.asOf)
    }

    @Test
    fun aSavedTypedRateIsStillTypedAndASavedMissingRateIsEmpty() {
        val typed = ReportingConversion.Converted(
            ExchangeRate(Currencies.USD, Currencies.LKR, "300".toBigDecimal(), published, RateSource.MANUAL), lkr(150_000),
        )
        assertEquals(RateSource.MANUAL, RateEntry.of(typed).source)
        assertEquals(RateHint.AVAILABLE, RateEntry.of(typed).hint(canOffer = true))

        val missing = RateEntry.of(ReportingConversion.Missing(Currencies.LKR))
        assertEquals("", missing.text)
        assertNull(missing.parsed)
    }
}
