package com.example.zar.features.auth

import com.example.zar.core.domain.model.UserId
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.AuthUser
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory accounts: email -> password. */
class FakeAuthRepository : AuthRepository {
    val accounts = mutableMapOf<String, String>()
    val session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    var nextError: AuthError? = null
    var unsynced = false
    val resetsSent = mutableListOf<String>()

    override fun observeSession(): Flow<SessionState> = session

    override suspend fun signIn(email: String, password: String): AuthUser {
        nextError?.let { nextError = null; throw it }
        if (accounts[email] != password) throw AuthError.WrongCredentials()
        return signInAs(email)
    }

    override suspend fun register(email: String, password: String): AuthUser {
        nextError?.let { nextError = null; throw it }
        if (email in accounts) throw AuthError.EmailAlreadyInUse()
        accounts[email] = password
        return signInAs(email)
    }

    override suspend fun sendPasswordReset(email: String) {
        resetsSent += email
    }

    override suspend fun hasUnsyncedChanges(): Boolean = unsynced

    override suspend fun signOut() {
        session.value = SessionState.SignedOut
    }

    private fun signInAs(email: String): AuthUser =
        AuthUser(UserId("uid-$email"), email).also { session.value = SessionState.SignedIn(it) }
}
