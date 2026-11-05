package com.example.zar.features.rates.data

import com.example.zar.features.settings.data.FakeSharedPreferences
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreferencesRateCacheTest {

    private val store = FakeSharedPreferences()
    private val cache = SharedPreferencesRateCache(store)

    @Test
    fun aRateComesBackAsWritten() {
        cache.write("USD", "LKR", CachedRate("332.444906", 1_793_750_551, 1_793_837_901))
        assertEquals(CachedRate("332.444906", 1_793_750_551, 1_793_837_901), cache.read("USD", "LKR"))
    }

    @Test
    fun nothingIsReadForAnotherPairOrBeforeAnythingIsWritten() {
        assertNull(cache.read("USD", "LKR"))
        cache.write("USD", "LKR", CachedRate("332.4", 1, 2))
        assertNull(cache.read("EUR", "LKR"))
    }

    @Test
    fun aDamagedValueIsIgnored() {
        store.values["rate_USD_LKR"] = "332.4|abc|2"
        assertNull(cache.read("USD", "LKR"))
        store.values["rate_USD_LKR"] = "only-one-part"
        assertNull(cache.read("USD", "LKR"))
    }

    @Test
    fun theWaitAfterTooManyRequestsIsRemembered() {
        assertNull(cache.blockedUntil())
        cache.blockUntil(Instant.ofEpochSecond(1_793_761_200))
        assertEquals(Instant.ofEpochSecond(1_793_761_200), cache.blockedUntil())
    }
}
