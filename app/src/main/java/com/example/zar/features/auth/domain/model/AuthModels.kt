package com.example.zar.features.auth.domain.model

import com.example.zar.core.domain.model.UserId

data class AuthUser(val id: UserId, val email: String?)

/** Whether someone is signed in. [Loading] lasts only until the saved session is restored. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: AuthUser) : SessionState
}

/** Sign-in failures the UI can explain, each with a clear next action. */
sealed class AuthError(message: String) : Exception(message) {
    class WrongCredentials : AuthError("Email or password is incorrect")
    class EmailAlreadyInUse : AuthError("An account already uses this email")
    class WeakPassword : AuthError("Password is too weak")
    class InvalidEmail : AuthError("Email address is not valid")
    class AccountDisabled : AuthError("This account is disabled")
    class TooManyAttempts : AuthError("Too many attempts")
    class Network : AuthError("No internet connection")
    class Unknown(cause: Throwable? = null) : AuthError(cause?.message ?: "Something went wrong")
}
