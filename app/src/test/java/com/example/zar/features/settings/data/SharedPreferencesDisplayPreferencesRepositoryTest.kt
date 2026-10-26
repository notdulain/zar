package com.example.zar.features.settings.data

import com.example.zar.features.settings.data.repository.SharedPreferencesDisplayPreferencesRepository
import com.example.zar.features.settings.domain.model.AppearanceMode
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedPreferencesDisplayPreferencesRepositoryTest {

    private fun repository(prefs: FakeSharedPreferences) =
        SharedPreferencesDisplayPreferencesRepository(prefs, UnconfinedTestDispatcher())

    @Test
    fun noSavedChoiceIsLightEvenIfAndroidIsDark() {
        val repo = repository(FakeSharedPreferences())

        assertEquals(AppearanceMode.LIGHT, repo.current().appearance)
        // Android being dark must not turn the default into an explicit choice or change what is rendered.
        assertFalse(repo.current().appearance.resolveDark(systemDark = true))
    }

    @Test
    fun unknownOrWronglyTypedStoredValuesFallBackToLight() {
        listOf<Any>("SEPIA", "", "dark", 7, true).forEach { stored ->
            val repo = repository(FakeSharedPreferences(mapOf("appearance" to stored)))
            assertEquals("stored=$stored", AppearanceMode.LIGHT, repo.current().appearance)
        }
    }

    @Test
    fun everyModeIsSavedAndSurvivesARestart() = runTest {
        val prefs = FakeSharedPreferences()
        AppearanceMode.entries.forEach { mode ->
            repository(prefs).setAppearance(mode)

            val afterRestart = repository(prefs)
            assertEquals(mode, afterRestart.current().appearance)
            assertEquals(mode, afterRestart.observe().first().appearance)
            assertEquals(mode.name, prefs.values["appearance"])
        }
    }

    @Test
    fun aSuccessfulSaveIsPublishedToObservers() = runTest {
        val repo = repository(FakeSharedPreferences())
        repo.setAppearance(AppearanceMode.DARK)

        assertEquals(AppearanceMode.DARK, repo.observe().first().appearance)
        assertEquals(AppearanceMode.DARK, repo.current().appearance)
    }

    @Test
    fun aFailedWriteKeepsThePreviousChoiceEverywhereAndCanBeRetried() = runTest {
        val prefs = FakeSharedPreferences()
        val repo = repository(prefs)
        repo.setAppearance(AppearanceMode.LIGHT)

        prefs.failCommits = true
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { repo.setAppearance(AppearanceMode.DARK) } }

        assertEquals(AppearanceMode.LIGHT, repo.current().appearance)
        assertEquals(AppearanceMode.LIGHT, repo.observe().first().appearance)
        assertEquals(AppearanceMode.LIGHT, repository(prefs).current().appearance)

        prefs.failCommits = false
        repo.setAppearance(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, repository(prefs).current().appearance)
    }

    @Test
    fun resolutionMatrix() {
        assertFalse(AppearanceMode.LIGHT.resolveDark(systemDark = false))
        assertFalse(AppearanceMode.LIGHT.resolveDark(systemDark = true))
        assertTrue(AppearanceMode.DARK.resolveDark(systemDark = false))
        assertTrue(AppearanceMode.DARK.resolveDark(systemDark = true))
        assertFalse(AppearanceMode.SYSTEM.resolveDark(systemDark = false))
        assertTrue(AppearanceMode.SYSTEM.resolveDark(systemDark = true))
    }

    @Test
    fun reduceMotionDefaultsOffSavesAndSurvivesARestart() = runTest {
        val prefs = FakeSharedPreferences()
        assertFalse(repository(prefs).current().reduceMotion)

        repository(prefs).setReduceMotion(true)
        assertTrue(repository(prefs).current().reduceMotion)

        repository(prefs).setReduceMotion(false)
        assertFalse(repository(prefs).current().reduceMotion)
    }

    @Test
    fun aWronglyTypedReduceMotionValueReadsAsOff() {
        assertFalse(repository(FakeSharedPreferences(mapOf("reduce_motion" to "yes"))).current().reduceMotion)
    }

    @Test
    fun appearanceAndReduceMotionAreStoredIndependently() = runTest {
        val prefs = FakeSharedPreferences()
        repository(prefs).setAppearance(AppearanceMode.DARK)
        repository(prefs).setReduceMotion(true)

        val restored = repository(prefs).current()
        assertEquals(AppearanceMode.DARK, restored.appearance)
        assertTrue(restored.reduceMotion)
    }

    @Test
    fun aFailedReduceMotionWriteKeepsThePreviousChoice() = runTest {
        val prefs = FakeSharedPreferences()
        val repo = repository(prefs)
        prefs.failCommits = true

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { repo.setReduceMotion(true) } }

        assertFalse(repo.current().reduceMotion)
        assertFalse(repository(prefs).current().reduceMotion)
    }
}
