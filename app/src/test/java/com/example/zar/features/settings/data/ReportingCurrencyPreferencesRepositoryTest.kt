package com.example.zar.features.settings.data

import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.settings.data.repository.ReportingCurrencyPreferencesRepository
import com.example.zar.features.settings.domain.usecase.ObserveAppPreferencesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportingCurrencyPreferencesRepositoryTest {

    private val repository = ReportingCurrencyPreferencesRepository()

    @Test
    fun emitsTheDomainReportingCurrency() = runBlocking {
        val preferences = repository.observePreferences().first()

        assertEquals(Currencies.REPORTING.code, preferences.currencyCode)
    }

    @Test
    fun reportingCurrencyIsLkrByContract() = runBlocking {
        assertEquals("LKR", ObserveAppPreferencesUseCase(repository)().first().currencyCode)
    }
}
