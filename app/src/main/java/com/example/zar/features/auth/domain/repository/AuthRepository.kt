package com.example.zar.features.auth.domain.repository

import com.example.zar.features.auth.domain.model.AuthUser
import com.example.zar.features.auth.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

/** Email and password accounts. Failures are thrown as AuthError. */
interface AuthRepository {
    fun observeSession(): Flow<SessionState>
    suspend fun signIn(email: String, password: String): AuthUser
    suspend fun register(email: String, password: String): AuthUser
    suspend fun sendPasswordReset(email: String)

    /** True when local changes have not reached the server yet; signing out now would lose them. */
    suspend fun hasUnsyncedChanges(): Boolean
    suspend fun signOut()
}
