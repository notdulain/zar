package com.example.zar.features.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import com.example.zar.features.auth.domain.usecase.SignOutUseCase
import com.example.zar.features.settings.domain.usecase.ObserveAppPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val currencyCode: String = "",
    val email: String? = null,
    val isSigningOut: Boolean = false,
    val showUnsyncedWarning: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAppPreferences: ObserveAppPreferencesUseCase,
    observeSession: ObserveSessionUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private data class SignOutState(val isSigningOut: Boolean = false, val showUnsyncedWarning: Boolean = false)

    private val signOutState = MutableStateFlow(SignOutState())

    val uiState: StateFlow<SettingsUiState> = combine(observeAppPreferences(), observeSession(), signOutState) { prefs, session, out ->
        SettingsUiState(
            isLoading = false,
            currencyCode = prefs.currencyCode,
            email = (session as? SessionState.SignedIn)?.user?.email,
            isSigningOut = out.isSigningOut,
            showUnsyncedWarning = out.showUnsyncedWarning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Warns first when local changes would be lost; otherwise signs out straight away. */
    fun onSignOutClick() {
        if (signOutState.value.isSigningOut) return
        signOutState.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            if (signOut.hasUnsyncedChanges()) {
                signOutState.update { SignOutState(showUnsyncedWarning = true) }
            } else {
                signOut()
            }
        }
    }

    fun confirmSignOut() {
        if (signOutState.value.isSigningOut) return
        signOutState.update { SignOutState(isSigningOut = true) }
        viewModelScope.launch { signOut() }
    }

    fun dismissUnsyncedWarning() = signOutState.update { SignOutState() }
}
