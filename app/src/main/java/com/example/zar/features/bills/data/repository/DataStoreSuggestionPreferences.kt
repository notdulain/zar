package com.example.zar.features.bills.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.bills.domain.usecase.SuggestionPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.suggestionStore by preferencesDataStore(name = "subscription_suggestions")

/** Dismissed subscription suggestions, on this phone only. */
@Singleton
class DataStoreSuggestionPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : SuggestionPreferencesRepository {

    override fun observeDismissed(): Flow<Set<String>> = context.suggestionStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[DISMISSED].orEmpty() }

    override suspend fun dismiss(key: String) {
        context.suggestionStore.edit { it[DISMISSED] = it[DISMISSED].orEmpty() + key }
    }

    override suspend fun clear() {
        context.suggestionStore.edit { it.clear() }
    }

    private companion object {
        val DISMISSED = stringSetPreferencesKey("dismissed")
    }
}

/** Dismissals belong to the person who signed out. */
class ResetSuggestionsOnSignOut @Inject constructor(
    private val preferences: SuggestionPreferencesRepository,
) : SignOutHandler {
    override suspend fun onSignOut() {
        preferences.clear()
    }
}
