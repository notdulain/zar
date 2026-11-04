package com.example.zar.features.plans.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.repository.PlanPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** `users/{uid}/planPreferences/main`: one pointer at the main goal. Offline writes sync later like every other record. */
@Singleton
class FirestorePlanPreferencesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : PlanPreferencesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMainGoalId(): Flow<GoalId?> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) flowOf(null) else callbackFlow {
            val registration = document(uid.value).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(PlanPreferencesMapper.mainGoalId(snapshot?.data))
            }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun setMainGoalId(id: GoalId) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        document(uid.value)
            .set(PlanPreferencesMapper.toMap(id, Instant.now()))
            .addOnFailureListener { Log.w(TAG, "setMainGoalId was rejected", it) }
    }

    private fun document(uid: String) = firestore.collection(FirestoreCollections.USERS)
        .document(uid)
        .collection(FirestoreCollections.PLAN_PREFERENCES)
        .document(DOCUMENT_ID)

    private companion object {
        const val TAG = "FirestorePlanPrefs"
        const val DOCUMENT_ID = "main"
    }
}

object PlanPreferencesMapper {
    fun toMap(mainGoalId: GoalId, updatedAt: Instant): Map<String, Any> = mapOf(
        "id" to "main",
        "mainGoalId" to mainGoalId.value,
        "updatedAt" to updatedAt.toEpochMilli(),
    )

    fun mainGoalId(map: Map<String, Any?>?): GoalId? =
        (map?.get("mainGoalId") as? String)?.takeIf { it.isNotBlank() }?.let(::GoalId)
}
