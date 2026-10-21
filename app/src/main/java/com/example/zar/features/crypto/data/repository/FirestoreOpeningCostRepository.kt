package com.example.zar.features.crypto.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.example.zar.features.crypto.domain.repository.OpeningCostRepository
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

/** Opening costs at users/{uid}/cryptoOpeningCosts/{accountId}. Writes apply locally first (offline). */
@Singleton
class FirestoreOpeningCostRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : OpeningCostRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOpeningCosts(): Flow<Map<AccountId, OpeningCost>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyMap())
        } else {
            callbackFlow {
                val registration = collection(uid.value).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val costs = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.data?.let(OpeningCostDocumentMapper::fromMap)
                            .also { if (it == null) Log.w(TAG, "Skipping malformed opening cost ${doc.id}") }
                    }
                    trySend(costs.associateBy { it.accountId })
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override suspend fun saveOpeningCost(cost: OpeningCost) {
        collection(requireUid()).document(cost.accountId.value)
            .set(OpeningCostDocumentMapper.toMap(cost))
            .addOnFailureListener { Log.w(TAG, "saveOpeningCost ${cost.accountId.value} was rejected", it) }
    }

    override suspend fun clearOpeningCost(accountId: AccountId) {
        collection(requireUid()).document(accountId.value).delete()
            .addOnFailureListener { Log.w(TAG, "clearOpeningCost ${accountId.value} was rejected", it) }
    }

    private fun requireUid(): String = session.currentUserId()?.value ?: throw LedgerError.NotSignedIn()

    private fun collection(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.CRYPTO_OPENING_COSTS)

    private companion object {
        const val TAG = "FirestoreOpeningCosts"
    }
}

/** Field names match firestore.rules. Returns null for malformed documents. */
object OpeningCostDocumentMapper {

    fun toMap(cost: OpeningCost): Map<String, Any> = mapOf(
        "accountId" to cost.accountId.value,
        "cost" to LedgerDocumentMapper.moneyToMap(cost.cost),
        "approximate" to cost.isApproximate,
        "updatedAt" to cost.updatedAt.toEpochMilli(),
    )

    fun fromMap(map: Map<String, Any?>): OpeningCost? = runCatching {
        OpeningCost(
            accountId = AccountId(map["accountId"] as String),
            cost = LedgerDocumentMapper.moneyFromMap(map["cost"] as Map<*, *>) as FiatMoney,
            isApproximate = map["approximate"] as Boolean,
            updatedAt = Instant.ofEpochMilli((map["updatedAt"] as Number).toLong()),
        )
    }.getOrNull()
}
