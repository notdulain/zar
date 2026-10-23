package com.example.zar.features.auth

import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.repository.SignOutHandler
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import com.example.zar.features.auth.domain.usecase.RegisterUseCase
import com.example.zar.features.auth.domain.usecase.SendPasswordResetUseCase
import com.example.zar.features.auth.domain.usecase.SignInUseCase
import com.example.zar.features.auth.domain.usecase.SignOutUseCase
import com.example.zar.features.auth.presentation.viewmodel.RegisterViewModel
import com.example.zar.features.auth.presentation.viewmodel.SignInViewModel
import com.example.zar.features.settings.data.repository.ReportingCurrencyPreferencesRepository
import com.example.zar.features.settings.domain.usecase.ObserveAppPreferencesUseCase
import com.example.zar.features.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class AuthViewModelsTest {

    private val repo = FakeAuthRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun signInViewModel() = SignInViewModel(SignInUseCase(repo), SendPasswordResetUseCase(repo))

    @Test
    fun invalidInputIsReportedWithoutCallingFirebase() = runTest {
        val vm = signInViewModel()
        vm.onEmailChange("not-an-email")
        vm.submit()
        assertEquals(CredentialProblem.EMAIL_INVALID, vm.state.value.emailProblem)
        assertEquals(CredentialProblem.PASSWORD_BLANK, vm.state.value.passwordProblem)
        assertEquals(SessionState.SignedOut, repo.session.value)
    }

    @Test
    fun wrongPasswordShowsErrorAndUnlocksForm() = runTest {
        repo.accounts["dulain@example.lk"] = "correct-horse"
        val vm = signInViewModel()
        vm.onEmailChange("dulain@example.lk")
        vm.onPasswordChange("wrong-password")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.error is AuthError.WrongCredentials)
        assertFalse(vm.state.value.isSubmitting)
        vm.onPasswordChange("x")
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun correctCredentialsSignIn() = runTest {
        repo.accounts["dulain@example.lk"] = "correct-horse"
        val vm = signInViewModel()
        vm.onEmailChange(" dulain@example.lk ")
        vm.onPasswordChange("correct-horse")
        vm.submit()
        advanceUntilIdle()
        assertTrue(repo.session.value is SessionState.SignedIn)
    }

    @Test
    fun networkFailureIsExplained() = runTest {
        repo.nextError = AuthError.Network()
        val vm = signInViewModel()
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("x")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.error is AuthError.Network)
    }

    @Test
    fun passwordResetNeedsAValidEmail() = runTest {
        val vm = signInViewModel()
        vm.sendReset()
        assertEquals(CredentialProblem.EMAIL_BLANK, vm.state.value.emailProblem)
        vm.onEmailChange("dulain@example.lk")
        vm.sendReset()
        advanceUntilIdle()
        assertEquals(listOf("dulain@example.lk"), repo.resetsSent)
        assertEquals("dulain@example.lk", vm.state.value.resetSentTo)
    }

    @Test
    fun registeringAnExistingEmailSuggestsSigningIn() = runTest {
        repo.accounts["dulain@example.lk"] = "correct-horse"
        val vm = RegisterViewModel(RegisterUseCase(repo))
        vm.onEmailChange("dulain@example.lk")
        vm.onPasswordChange("another-pass")
        vm.onConfirmationChange("another-pass")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.error is AuthError.EmailAlreadyInUse)
    }

    @Test
    fun registrationSignsTheUserIn() = runTest {
        val vm = RegisterViewModel(RegisterUseCase(repo))
        vm.onEmailChange("new@example.lk")
        vm.onPasswordChange("long-enough")
        vm.onConfirmationChange("long-enough")
        vm.submit()
        advanceUntilIdle()
        assertTrue(repo.session.value is SessionState.SignedIn)
    }

    private fun settingsViewModel(handlers: Set<SignOutHandler> = emptySet()) = SettingsViewModel(
        ObserveAppPreferencesUseCase(ReportingCurrencyPreferencesRepository()),
        ObserveSessionUseCase(repo),
        SignOutUseCase(repo, handlers),
    )

    @Test
    fun signOutPausesHandlersThenSignsOut() = runTest {
        repo.accounts["a@b.co"] = "password1"
        SignInUseCase(repo)("a@b.co", "password1")
        val calls = mutableListOf<String>()
        val vm = settingsViewModel(setOf(SignOutHandler { calls += "capture paused" }))
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals("a@b.co", vm.uiState.first { it.email != null }.email)
        vm.onSignOutClick()
        advanceUntilIdle()
        assertEquals(listOf("capture paused"), calls)
        assertEquals(SessionState.SignedOut, repo.session.value)
    }

    @Test
    fun unsyncedChangesAskBeforeSigningOut() = runTest {
        repo.accounts["a@b.co"] = "password1"
        SignInUseCase(repo)("a@b.co", "password1")
        repo.unsynced = true
        val vm = settingsViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onSignOutClick()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showUnsyncedWarning)
        assertTrue(repo.session.value is SessionState.SignedIn)
        vm.confirmSignOut()
        advanceUntilIdle()
        assertEquals(SessionState.SignedOut, repo.session.value)
    }
}
