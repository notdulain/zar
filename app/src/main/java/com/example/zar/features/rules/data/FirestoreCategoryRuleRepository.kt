package com.example.zar.features.rules.data

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.rules.domain.model.CategoryRule
import com.example.zar.features.rules.domain.repository.CategoryRuleRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Category rules under users/{uid}/categoryRules. A rule is set by its stable ID; firestore.rules only
 * accepts an update that raises the version, and never a delete. Like the ledger, writes are not awaited,
 * so they apply to the local cache at once and work offline.
 */
@Singleton
class FirestoreCategoryRuleRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : CategoryRuleRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRules(): Flow<List<CategoryRule>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = rulesOf(uid.value).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val rules = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.data?.let(CategoryRuleDocumentMapper::fromMap).also { if (it == null) Log.w(TAG, "Skipping malformed rule ${doc.id}") }
                    }
                    trySend(rules)
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override suspend fun saveRule(rule: CategoryRule) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        rulesOf(uid.value).document(rule.id.value).set(CategoryRuleDocumentMapper.toMap(rule))
            .logFailure("saveRule ${rule.id.value}")
    }

    private fun rulesOf(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.CATEGORY_RULES)

    private fun Task<Void>.logFailure(operation: String) {
        addOnFailureListener { Log.w(TAG, "$operation was rejected", it) }
    }

    private companion object {
        const val TAG = "FirestoreCategoryRules"
    }
}
