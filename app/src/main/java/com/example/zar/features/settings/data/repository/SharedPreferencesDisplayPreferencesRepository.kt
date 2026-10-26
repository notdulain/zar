package com.example.zar.features.settings.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.domain.model.DisplayPreferences
import com.example.zar.features.settings.domain.repository.DisplayPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the device-local display choices in a small preferences file that is excluded from backups and left alone
 * by sign-out. SharedPreferences are used instead of DataStore for the same reason as the hide-amounts choice: the
 * saved appearance must be readable immediately at start-up, because loading it asynchronously would draw the
 * default colours for a few frames before the saved ones arrived.
 *
 * A change is written with `commit()` and only published once that succeeds, so a full disk or a failed write
 * leaves the screen and the stored value in agreement on the previous choice.
 */
@Singleton
class SharedPreferencesDisplayPreferencesRepository internal constructor(
    private val preferences: SharedPreferences,
    private val io: CoroutineDispatcher,
) : DisplayPreferencesRepository {

    @Inject constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE), Dispatchers.IO)

    private val writes = Mutex()
    private val state = MutableStateFlow(read())

    override fun current(): DisplayPreferences = state.value

    override fun observe(): Flow<DisplayPreferences> = state.asStateFlow()

    override suspend fun setAppearance(mode: AppearanceMode) = write(
        edit = { putString(KEY_APPEARANCE, mode.name) },
        publish = { it.copy(appearance = mode) },
    )

    private suspend fun write(edit: SharedPreferences.Editor.() -> Unit, publish: (DisplayPreferences) -> DisplayPreferences) {
        writes.withLock {
            val saved = withContext(io) { preferences.edit().apply(edit).commit() }
            if (!saved) throw IOException("Could not save the display preference")
            state.value = publish(state.value)
        }
    }

    private fun read(): DisplayPreferences = DisplayPreferences(
        appearance = AppearanceMode.fromStored(string(KEY_APPEARANCE)),
    )

    // A value written with another type by some other version reads as missing rather than crashing start-up.
    private fun string(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    companion object {
        /** Listed in backup_rules.xml and data_extraction_rules.xml so it is never backed up. */
        const val FILE_NAME = "display_preferences"
        private const val KEY_APPEARANCE = "appearance"
    }
}
