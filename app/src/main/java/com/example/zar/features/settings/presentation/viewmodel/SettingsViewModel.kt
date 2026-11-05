package com.example.zar.features.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.usecase.ObserveSessionUseCase
import com.example.zar.features.auth.domain.usecase.SendPasswordResetUseCase
import com.example.zar.features.auth.domain.usecase.SignOutUseCase
import com.example.zar.features.auth.presentation.viewmodel.onAuthFailure
import com.example.zar.features.auth.presentation.viewmodel.runCatchingAuth
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.domain.usecase.ObserveAppPreferencesUseCase
import com.example.zar.features.settings.domain.usecase.ObserveDisplayPreferencesUseCase
import com.example.zar.features.settings.domain.usecase.SetAppearanceUseCase
import com.example.zar.features.settings.domain.usecase.SetReduceMotionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val currencyCode: String = "",
    val email: String? = null,
    val isSigningOut: Boolean = false,
    val showUnsyncedWarning: Boolean = false,
    /** The saved choice; what the app is actually drawing. A failed write leaves this unchanged. */
    val appearance: AppearanceMode = AppearanceMode.Default,
    val reduceMotion: Boolean = false,
    /** Set when the last attempt to save a display choice failed; [SettingsViewModel.retryDisplayPreference] repeats it. */
    val displayPreferenceError: Boolean = false,
    /** The open "Reset password" sheet, or null when it is closed. */
    val passwordReset: PasswordResetUi? = null,
)

enum class PasswordResetPhase { Confirming, Sending, Sent }

/**
 * The reset-link sheet. [email] is the address the link goes to, fixed when the sheet opened; [error] is the last failed
 * attempt, shown while the sheet is back in [PasswordResetPhase.Confirming] so "Try again" repeats the send.
 */
data class PasswordResetUi(
    val email: String,
    val phase: PasswordResetPhase = PasswordResetPhase.Confirming,
    val error: AuthError? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAppPreferences: ObserveAppPreferencesUseCase,
    observeSession: ObserveSessionUseCase,
    private val signOut: SignOutUseCase,
    observeDisplayPreferences: ObserveDisplayPreferencesUseCase,
    private val setAppearance: SetAppearanceUseCase,
    private val setReduceMotion: SetReduceMotionUseCase,
    private val sendPasswordReset: SendPasswordResetUseCase,
) : ViewModel() {

    /** A display change that could not be saved, kept so "try again" repeats exactly that. */
    private sealed interface DisplayChange {
        data class Appearance(val mode: AppearanceMode) : DisplayChange
        data class ReduceMotion(val enabled: Boolean) : DisplayChange
    }

    private data class SignOutState(val isSigningOut: Boolean = false, val showUnsyncedWarning: Boolean = false)

    private val signOutState = MutableStateFlow(SignOutState())

    private val failedDisplayChange = MutableStateFlow<DisplayChange?>(null)

    /** One reset attempt. [id] tells a late result from a send that was closed, replaced or abandoned. */
    private data class ResetFlow(val id: Int, val ui: PasswordResetUi)

    private val resetFlow = MutableStateFlow<ResetFlow?>(null)
    private var resetJob: Job? = null
    private var resetJobId = 0
    private var resetJobEmail: String? = null
    private var nextResetId = 0

    /** The address of whoever is signed in right now; a reset is only ever sent to this. */
    private var sessionEmail: String? = null

    /** Short-lived screen state, grouped so the main combine stays within five flows. */
    private data class Transient(val out: SignOutState, val failed: DisplayChange?, val reset: ResetFlow?)

    private val transient = combine(signOutState, failedDisplayChange, resetFlow, ::Transient)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeAppPreferences(),
        observeSession(),
        observeDisplayPreferences(),
        transient,
    ) { prefs, session, display, (out, failed, reset) ->
        SettingsUiState(
            isLoading = false,
            currencyCode = prefs.currencyCode,
            email = (session as? SessionState.SignedIn)?.user?.email,
            isSigningOut = out.isSigningOut,
            showUnsyncedWarning = out.showUnsyncedWarning,
            appearance = display.appearance,
            reduceMotion = display.reduceMotion,
            displayPreferenceError = failed != null,
            passwordReset = reset?.ui,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), observeDisplayPreferences.current().let { SettingsUiState(appearance = it.appearance, reduceMotion = it.reduceMotion) })

    init {
        // A different account, or none, ends any reset in progress. The link is never sent for someone else's address.
        viewModelScope.launch {
            observeSession()
                .map { (it as? SessionState.SignedIn)?.user?.email }
                .distinctUntilChanged()
                .collect { email ->
                    sessionEmail = email
                    val shownFor = resetFlow.value?.ui?.email
                    val sendingFor = resetJobEmail
                    if ((shownFor != null && shownFor != email) || (sendingFor != null && sendingFor != email)) {
                        closeReset(cancelSend = true)
                    }
                }
        }
    }

    fun onAppearanceSelected(mode: AppearanceMode) = save(DisplayChange.Appearance(mode))

    fun onReduceMotionChange(enabled: Boolean) = save(DisplayChange.ReduceMotion(enabled))

    fun retryDisplayPreference() {
        failedDisplayChange.value?.let(::save)
    }

    fun dismissDisplayPreferenceError() {
        failedDisplayChange.value = null
    }

    private fun save(change: DisplayChange) {
        viewModelScope.launch {
            val result = when (change) {
                is DisplayChange.Appearance -> setAppearance(change.mode)
                is DisplayChange.ReduceMotion -> setReduceMotion(change.enabled)
            }
            failedDisplayChange.value = if (result.isSuccess) null else change
        }
    }

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

    /** Opens the confirmation. Nothing is sent yet; a send still running from an earlier open is shown, not repeated. */
    fun onResetPasswordClick() {
        val email = sessionEmail ?: return
        if (resetFlow.value != null) return
        if (resetJob?.isActive == true && resetJobEmail == email) {
            resetFlow.value = ResetFlow(resetJobId, PasswordResetUi(email, PasswordResetPhase.Sending))
        } else {
            resetFlow.value = ResetFlow(++nextResetId, PasswordResetUi(email))
        }
    }

    /** Sends the reset link to the address on the sheet, once. Ignored while sending, after sending, or if the account changed. */
    fun confirmPasswordReset() {
        val open = resetFlow.value ?: return
        if (open.ui.phase != PasswordResetPhase.Confirming) return
        if (open.ui.email != sessionEmail || resetJob?.isActive == true) return
        resetFlow.value = open.copy(ui = open.ui.copy(phase = PasswordResetPhase.Sending, error = null))
        resetJobId = open.id
        resetJobEmail = open.ui.email
        resetJob = viewModelScope.launch {
            runCatchingAuth { sendPasswordReset(open.ui.email) }
                .onSuccess { finishReset(open.id) { it.copy(phase = PasswordResetPhase.Sent) } }
                .onAuthFailure { e -> finishReset(open.id) { it.copy(phase = PasswordResetPhase.Confirming, error = e) } }
        }
    }

    /** Closes the sheet. A send already on its way is left to finish quietly; reopening shows it rather than sending again. */
    fun dismissPasswordReset() = closeReset(cancelSend = false)

    private fun finishReset(id: Int, change: (PasswordResetUi) -> PasswordResetUi) {
        resetFlow.update { current -> if (current?.id == id) current.copy(ui = change(current.ui)) else current }
    }

    private fun closeReset(cancelSend: Boolean) {
        resetFlow.value = null
        if (cancelSend) {
            resetJob?.cancel()
            resetJob = null
            resetJobEmail = null
        }
    }
}
