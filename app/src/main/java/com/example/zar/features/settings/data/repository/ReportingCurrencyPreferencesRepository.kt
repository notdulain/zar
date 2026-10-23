package com.example.zar.features.settings.data.repository

import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.settings.domain.model.AppPreferences
import com.example.zar.features.settings.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Zar reports in one fixed currency (see docs/domain-contracts.md), so the preference is read from the domain
 * rather than stored: nothing can change it, and nothing is lost on a restart or sign-out.
 */
@Singleton
class ReportingCurrencyPreferencesRepository @Inject constructor() : AppPreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> =
        flowOf(AppPreferences(currencyCode = Currencies.REPORTING.code))
}
