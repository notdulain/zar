package com.example.zar.core.presentation.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.zar.core.data.local.SharedPreferencesAmountPrivacyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AmountPrivacyStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before @After fun clean() {
        context.getSharedPreferences(SharedPreferencesAmountPrivacyRepository.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaultsToShown() {
        assertFalse(SharedPreferencesAmountPrivacyRepository(context).isHidden())
    }

    @Test
    fun theChoiceIsRememberedByANewInstanceAndReadImmediately() = runBlocking {
        SharedPreferencesAmountPrivacyRepository(context).setHidden(true)
        // A fresh instance is what the next app start builds; the value is there without waiting.
        assertTrue(SharedPreferencesAmountPrivacyRepository(context).isHidden())

        SharedPreferencesAmountPrivacyRepository(context).setHidden(false)
        assertFalse(SharedPreferencesAmountPrivacyRepository(context).isHidden())
    }

    @Test
    fun observersSeeTheChangeAtOnce() = runBlocking {
        val repository = SharedPreferencesAmountPrivacyRepository(context)
        assertEquals(false, repository.observeHidden().first())
        repository.setHidden(true)
        assertEquals(true, repository.observeHidden().first())
    }
}
