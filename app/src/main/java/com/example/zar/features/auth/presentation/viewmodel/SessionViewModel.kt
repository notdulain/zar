package com.example.zar.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import com.example.zar.features.auth.domain.model.AuthError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Decides which part of the app is shown: signed-out screens or the signed-in app. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(observeSession: ObserveSessionUseCase) : ViewModel() {
    private val attempt = MutableStateFlow(0)
    val session: StateFlow<SessionState> = attempt.flatMapLatest {
        observeSession().onStart { emit(SessionState.Loading) }
            .catch { emit(SessionState.Error(it as? AuthError ?: AuthError.Unknown(it))) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    fun retry() { attempt.value += 1 }
}
