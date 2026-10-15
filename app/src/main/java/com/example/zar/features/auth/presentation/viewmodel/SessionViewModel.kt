package com.example.zar.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Decides which part of the app is shown: signed-out screens or the signed-in app. */
@HiltViewModel
class SessionViewModel @Inject constructor(observeSession: ObserveSessionUseCase) : ViewModel() {
    val session: StateFlow<SessionState> = observeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)
}
