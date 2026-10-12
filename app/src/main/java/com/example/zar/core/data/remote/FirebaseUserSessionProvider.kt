package com.example.zar.core.data.remote

import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.UserSessionProvider
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class FirebaseUserSessionProvider @Inject constructor(
    private val auth: FirebaseAuth,
) : UserSessionProvider {

    override fun currentUserId(): UserId? = auth.currentUser?.uid?.let(::UserId)

    override fun observeUserId(): Flow<UserId?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid?.let(::UserId)) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()
}
