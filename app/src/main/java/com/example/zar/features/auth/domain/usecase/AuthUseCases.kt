package com.example.zar.features.auth.domain.usecase

import com.example.zar.features.auth.domain.model.AuthUser
import com.example.zar.features.auth.domain.model.CredentialsValidator
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.repository.AuthRepository
import com.example.zar.features.auth.domain.repository.SignOutHandler
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(): Flow<SessionState> = auth.observeSession()
}

class SignInUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AuthUser {
        require(CredentialsValidator.forSignIn(email, password).isValid) { "Check credentials before signing in" }
        return auth.signIn(email.trim(), password)
    }
}

class RegisterUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, confirmation: String): AuthUser {
        require(CredentialsValidator.forRegistration(email, password, confirmation).isValid) {
            "Check credentials before registering"
        }
        return auth.register(email.trim(), password)
    }
}

class SendPasswordResetUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String) {
        require(CredentialsValidator.checkEmail(email) == null) { "Enter a valid email first" }
        auth.sendPasswordReset(email.trim())
    }
}

/**
 * Signs out after every [SignOutHandler] has paused its user-scoped work. Callers should check
 * [AuthRepository.hasUnsyncedChanges] first and warn the user, because unsynced writes are lost.
 */
class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val handlers: Set<@JvmSuppressWildcards SignOutHandler>,
) {
    suspend fun hasUnsyncedChanges(): Boolean = auth.hasUnsyncedChanges()

    suspend operator fun invoke() {
        handlers.forEach { it.onSignOut() }
        auth.signOut()
    }
}
