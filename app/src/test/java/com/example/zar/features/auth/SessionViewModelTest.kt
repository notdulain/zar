package com.example.zar.features.auth

import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.repository.AuthRepository
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import com.example.zar.features.auth.presentation.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun restoresLocalSessionWithoutSubscribersOrResumeReplay() = runTest {
        val fake = FakeAuthRepository()
        val vm = SessionViewModel(ObserveSessionUseCase(fake))
        assertEquals(SessionState.SignedOut, vm.session.value)
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(SessionState.SignedOut, vm.session.value)
    }

    @Test fun failedRestorationCanBeRetriedWithoutSigningOut() = runTest {
        var attempts = 0
        val repo = object : AuthRepository by FakeAuthRepository() {
            override fun observeSession() = flow {
                attempts++
                if (attempts == 1) throw AuthError.Network()
                emit(SessionState.SignedOut)
            }
        }
        val vm = SessionViewModel(ObserveSessionUseCase(repo))
        assertTrue((vm.session.value as SessionState.Error).error is AuthError.Network)
        vm.retry()
        assertEquals(2, attempts)
        assertEquals(SessionState.SignedOut, vm.session.value)
    }
}
