package com.example.zar.features.plans.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.domain.repository.GoalRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@Singleton
class FirestoreGoalRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : GoalRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGoals(): Flow<List<SavingsGoal>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else callbackFlow {
            val registration = goals(uid.value).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.data?.let(GoalDocumentMapper::fromMap)
                        .also { if (it == null) Log.w(TAG, "Skipping malformed goal ${document.id}") }
                })
            }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun saveGoal(goal: SavingsGoal) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        goals(uid.value).document(goal.id.value)
            .set(GoalDocumentMapper.toMap(goal, Instant.now()))
            .addOnFailureListener { Log.w(TAG, "saveGoal ${goal.id.value} was rejected", it) }
    }

    private fun goals(uid: String) = firestore.collection(FirestoreCollections.USERS)
        .document(uid)
        .collection(FirestoreCollections.GOALS)

    private companion object {
        const val TAG = "FirestoreGoals"
    }
}

object GoalDocumentMapper {
    fun toMap(goal: SavingsGoal, updatedAt: Instant): Map<String, Any> = buildMap {
        put("id", goal.id.value)
        put("name", goal.name)
        put("target", LedgerDocumentMapper.moneyToMap(goal.target))
        goal.targetDate?.let { put("targetDate", it.toString()) }
        put("allocations", goal.allocations.map { allocation ->
            mapOf(
                "accountId" to allocation.accountId.value,
                "amount" to LedgerDocumentMapper.moneyToMap(allocation.amount),
            )
        })
        put("updatedAt", updatedAt.toEpochMilli())
    }

    fun fromMap(map: Map<String, Any?>): SavingsGoal? = runCatching {
        val allocations = (map["allocations"] as List<*>).map { raw ->
            val allocation = raw as Map<*, *>
            GoalAllocation(
                accountId = AccountId(allocation["accountId"] as String),
                amount = LedgerDocumentMapper.moneyFromMap(allocation["amount"] as? Map<*, *>)!!,
            )
        }
        SavingsGoal(
            id = GoalId(map["id"] as String),
            name = map["name"] as String,
            target = LedgerDocumentMapper.moneyFromMap(map["target"] as? Map<*, *>)!!,
            allocations = allocations,
            targetDate = if ("targetDate" in map) LocalDate.parse(map["targetDate"] as String) else null,
        )
    }.getOrNull()
}
