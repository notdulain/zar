package com.example.zar.features.auth.data.repository

import com.example.zar.core.domain.model.UserId
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.AuthUser
import com.example.zar.features.auth.domain.model.SessionState
import com.example.zar.features.auth.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override fun observeSession(): Flow<SessionState> = callbackFlow {
        // Firebase calls the listener right away with the restored session, then on every change.
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toSession()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): AuthUser = mapErrors {
        auth.signInWithEmailAndPassword(email, password).await().user!!.toAuthUser()
    }

    override suspend fun register(email: String, password: String): AuthUser = mapErrors {
        auth.createUserWithEmailAndPassword(email, password).await().user!!.toAuthUser()
    }

    override suspend fun sendPasswordReset(email: String) = mapErrors {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    // waitForPendingWrites() resolves to null on success, so map it to a value to tell it apart from a timeout.
    override suspend fun hasUnsyncedChanges(): Boolean =
        withTimeoutOrNull(PENDING_WRITES_TIMEOUT_MS) { firestore.waitForPendingWrites().await(); true } == null

    override suspend fun signOut() = auth.signOut()

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toAuthError()
    }

    private fun Exception.toAuthError(): AuthError = when (this) {
        is AuthError -> this
        is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword()
        is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse()
        is FirebaseAuthInvalidUserException ->
            if (errorCode == "ERROR_USER_DISABLED") AuthError.AccountDisabled() else AuthError.WrongCredentials()
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail() else AuthError.WrongCredentials()
        is FirebaseNetworkException -> AuthError.Network()
        is FirebaseTooManyRequestsException -> AuthError.TooManyAttempts()
        else -> AuthError.Unknown(this)
    }

    private fun FirebaseUser?.toSession(): SessionState =
        this?.let { SessionState.SignedIn(it.toAuthUser()) } ?: SessionState.SignedOut

    private fun FirebaseUser.toAuthUser() = AuthUser(UserId(uid), email)

    private companion object {
        const val PENDING_WRITES_TIMEOUT_MS = 2_000L
    }
}
