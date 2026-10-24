package com.example.zar.core.data.local

import android.content.Context
import com.example.zar.core.domain.repository.AmountPrivacyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the hide-amounts choice in a small preferences file that is excluded from backups, so it
 * stays on this phone. SharedPreferences are used instead of DataStore because the value must be
 * readable immediately at start-up: loading it asynchronously would draw one frame of real
 * amounts before the preference arrived.
 */
@Singleton
class SharedPreferencesAmountPrivacyRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AmountPrivacyRepository {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val hidden = MutableStateFlow(preferences.getBoolean(KEY_HIDDEN, false))

    override fun isHidden(): Boolean = hidden.value

    override fun observeHidden(): Flow<Boolean> = hidden.asStateFlow()

    override suspend fun setHidden(hidden: Boolean) {
        // The in-memory value changes first, so every screen follows at once; the file catches up.
        this.hidden.value = hidden
        preferences.edit().putBoolean(KEY_HIDDEN, hidden).apply()
    }

    companion object {
        /** Listed in backup_rules.xml and data_extraction_rules.xml so it is never backed up. */
        const val FILE_NAME = "amount_privacy"
        private const val KEY_HIDDEN = "hidden"
    }
}
