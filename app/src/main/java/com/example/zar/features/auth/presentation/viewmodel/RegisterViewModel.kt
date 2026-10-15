package com.example.zar.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.domain.model.CredentialsValidator
import com.example.zar.features.auth.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val emailProblem: CredentialProblem? = null,
    val passwordProblem: CredentialProblem? = null,
    val confirmationProblem: CredentialProblem? = null,
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(private val register: RegisterUseCase) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailProblem = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordProblem = null, error = null) }

    fun onConfirmationChange(value: String) =
        _state.update { it.copy(confirmation = value, confirmationProblem = null, error = null) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        val check = CredentialsValidator.forRegistration(current.email, current.password, current.confirmation)
        if (!check.isValid) {
            _state.update {
                it.copy(emailProblem = check.email, passwordProblem = check.password, confirmationProblem = check.confirmation)
            }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatchingAuth { register(current.email, current.password, current.confirmation) }
                .onAuthFailure { e -> _state.update { it.copy(isSubmitting = false, error = e) } }
        }
    }
}
