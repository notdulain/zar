package com.example.zar.features.settings.presentation

import com.example.zar.features.auth.FakeAuthRepository
import com.example.zar.features.auth.domain.repository.AuthRepository
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import com.example.zar.features.auth.domain.usecase.SignInUseCase
import com.example.zar.features.auth.domain.usecase.SignOutUseCase
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.settings.FakeDisplayPreferences
import com.example.zar.features.settings.data.repository.ReportingCurrencyPreferencesRepository
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.domain.usecase.ObserveAppPreferencesUseCase
import com.example.zar.features.settings.domain.usecase.ObserveDisplayPreferencesUseCase
import com.example.zar.features.settings.domain.usecase.SetAppearanceUseCase
import com.example.zar.features.settings.domain.usecase.SetReduceMotionUseCase
import com.example.zar.features.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    /** The fake auth repository, counting how often the ViewModel asks and signs out. */
    private class CountingAuth(private val inner: FakeAuthRepository) : AuthRepository by inner {
        var unsyncedChecks = 0
        var signOuts = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun hasUnsyncedChanges(): Boolean {
            unsyncedChecks++
            gate?.await()
            return inner.hasUnsyncedChanges()
        }

        override suspend fun signOut() {
            signOuts++
            inner.signOut()
        }
    }

    private val display = FakeDisplayPreferences()
    private val fake = FakeAuthRepository()
    private val auth = CountingAuth(fake)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.signedInViewModel(unsynced: Boolean): SettingsViewModel {
        fake.accounts["nimal.test@example.com"] = "password1"
        SignInUseCase(fake)("nimal.test@example.com", "password1")
        fake.unsynced = unsynced
        val vm = SettingsViewModel(
            ObserveAppPreferencesUseCase(ReportingCurrencyPreferencesRepository()),
            ObserveSessionUseCase(auth),
            SignOutUseCase(auth, emptySet()),
            ObserveDisplayPreferencesUseCase(display),
            SetAppearanceUseCase(display),
            SetReduceMotionUseCase(display),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading }
        return vm
    }

    @Test
    fun showsEmailAndReportingCurrency() = runTest {
        val state = signedInViewModel(unsynced = false).uiState.value

        assertEquals("nimal.test@example.com", state.email)
        assertEquals("LKR", state.currencyCode)
    }

    @Test
    fun noUnsyncedChangesSignsOutOnceWithoutWarning() = runTest {
        val vm = signedInViewModel(unsynced = false)

        vm.onSignOutClick()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showUnsyncedWarning)
        assertEquals(1, auth.signOuts)
        assertEquals(SessionState.SignedOut, fake.session.value)
    }

    @Test
    fun unsyncedChangesShowWarningAndStaySignedIn() = runTest {
        val vm = signedInViewModel(unsynced = true)

        vm.onSignOutClick()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showUnsyncedWarning)
        assertEquals(0, auth.signOuts)
        assertTrue(fake.session.value is SessionState.SignedIn)
    }

    @Test
    fun dismissClearsWarningAndAnotherTapWorks() = runTest {
        val vm = signedInViewModel(unsynced = true)
        vm.onSignOutClick()
        advanceUntilIdle()

        vm.dismissUnsyncedWarning()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showUnsyncedWarning)
        assertFalse(vm.uiState.value.isSigningOut)

        vm.onSignOutClick()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showUnsyncedWarning)
        assertEquals(2, auth.unsyncedChecks)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun confirmSignsOutOnce() = runTest {
        val vm = signedInViewModel(unsynced = true)
        vm.onSignOutClick()
        advanceUntilIdle()

        vm.confirmSignOut()
        vm.confirmSignOut()
        advanceUntilIdle()

        assertEquals(1, auth.signOuts)
        assertFalse(vm.uiState.value.showUnsyncedWarning)
        assertEquals(SessionState.SignedOut, fake.session.value)
    }

    @Test
    fun quickDoubleTapChecksUnsyncedChangesOnce() = runTest {
        val vm = signedInViewModel(unsynced = false)
        auth.gate = CompletableDeferred()

        vm.onSignOutClick()
        vm.onSignOutClick()
        auth.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, auth.unsyncedChecks)
        assertEquals(1, auth.signOuts)
    }

    @Test
    fun appearanceStartsAtTheSavedChoice() = runTest {
        val vm = signedInViewModel(unsynced = false)

        assertEquals(AppearanceMode.LIGHT, vm.uiState.value.appearance)
        assertFalse(vm.uiState.value.displayPreferenceError)
    }

    @Test
    fun choosingAnAppearanceSavesItAndSigningOutKeepsIt() = runTest {
        val vm = signedInViewModel(unsynced = false)

        vm.onAppearanceSelected(AppearanceMode.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceMode.DARK, vm.uiState.value.appearance)

        vm.onSignOutClick()
        advanceUntilIdle()
        assertEquals(SessionState.SignedOut, fake.session.value)
        assertEquals(AppearanceMode.DARK, display.current().appearance)
    }

    @Test
    fun aFailedSaveKeepsTheOldChoiceShowsAnErrorAndRetryRepeatsTheSameChoice() = runTest {
        val vm = signedInViewModel(unsynced = false)
        display.failing = true

        vm.onAppearanceSelected(AppearanceMode.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceMode.LIGHT, vm.uiState.value.appearance)
        assertTrue(vm.uiState.value.displayPreferenceError)

        display.failing = false
        vm.retryDisplayPreference()
        advanceUntilIdle()
        assertEquals(AppearanceMode.DARK, vm.uiState.value.appearance)
        assertFalse(vm.uiState.value.displayPreferenceError)
    }

    @Test
    fun dismissingTheErrorClearsIt() = runTest {
        val vm = signedInViewModel(unsynced = false)
        display.failing = true
        vm.onAppearanceSelected(AppearanceMode.SYSTEM)
        advanceUntilIdle()

        vm.dismissDisplayPreferenceError()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.displayPreferenceError)
        assertEquals(AppearanceMode.LIGHT, vm.uiState.value.appearance)
    }

    @Test
    fun reduceMotionStartsOffSavesAndKeepsAppearanceIndependent() = runTest {
        val vm = signedInViewModel(unsynced = false)
        assertFalse(vm.uiState.value.reduceMotion)

        vm.onAppearanceSelected(AppearanceMode.DARK)
        vm.onReduceMotionChange(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.reduceMotion)
        assertEquals(AppearanceMode.DARK, vm.uiState.value.appearance)

        vm.onReduceMotionChange(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reduceMotion)
        assertEquals(AppearanceMode.DARK, vm.uiState.value.appearance)
    }

    @Test
    fun aFailedReduceMotionSaveKeepsItOffAndRetryRepeatsIt() = runTest {
        val vm = signedInViewModel(unsynced = false)
        display.failing = true

        vm.onReduceMotionChange(true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reduceMotion)
        assertTrue(vm.uiState.value.displayPreferenceError)

        display.failing = false
        vm.retryDisplayPreference()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reduceMotion)
        assertFalse(vm.uiState.value.displayPreferenceError)
    }

    @Test
    fun aLaterSuccessfulSaveClearsAnEarlierError() = runTest {
        val vm = signedInViewModel(unsynced = false)
        display.failing = true
        vm.onReduceMotionChange(true)
        advanceUntilIdle()

        display.failing = false
        vm.onAppearanceSelected(AppearanceMode.SYSTEM)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.displayPreferenceError)
        assertEquals(AppearanceMode.SYSTEM, vm.uiState.value.appearance)
        assertFalse(vm.uiState.value.reduceMotion)
    }
}
