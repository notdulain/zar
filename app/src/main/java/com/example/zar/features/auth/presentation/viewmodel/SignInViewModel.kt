package com.example.zar.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.domain.model.CredentialsValidator
import com.example.zar.features.auth.domain.usecase.SendPasswordResetUseCase
import com.example.zar.features.auth.domain.usecase.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailProblem: CredentialProblem? = null,
    val passwordProblem: CredentialProblem? = null,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
    /** Set after a reset email was sent to this address. */
    val resetSentTo: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val sendPasswordReset: SendPasswordResetUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailProblem = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordProblem = null, error = null) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        val check = CredentialsValidator.forSignIn(current.email, current.password)
        if (!check.isValid) {
            _state.update { it.copy(emailProblem = check.email, passwordProblem = check.password) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null, resetSentTo = null) }
        viewModelScope.launch {
            // On success the session changes and the signed-in app replaces this screen.
            runCatchingAuth { signIn(current.email, current.password) }
                .onAuthFailure { e -> _state.update { it.copy(isSubmitting = false, error = e) } }
        }
    }

    fun sendReset() {
        val email = _state.value.email
        val problem = CredentialsValidator.checkEmail(email)
        if (problem != null) {
            _state.update { it.copy(emailProblem = problem) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatchingAuth { sendPasswordReset(email) }
                .onSuccess { _state.update { it.copy(isSubmitting = false, resetSentTo = email.trim()) } }
                .onAuthFailure { e -> _state.update { it.copy(isSubmitting = false, error = e) } }
        }
    }
}
