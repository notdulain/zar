package com.example.zar.features.transactions.data.category

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Custom categories under users/{uid}/expenseCategories, set by their stable ID. firestore.rules accepts a replayed
 * identical create and never an edit or delete. Reads come from Firestore's local cache first, so the catalog works
 * offline and after a restart.
 *
 * Account isolation: every user's listener starts from an empty list, so while another account's snapshot loads
 * nothing of the previous account is visible.
 */
@Singleton
class FirestoreExpenseCategoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : ExpenseCategoryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCustomCategories(): Flow<List<CustomExpenseCategory>> =
        session.observeUserId().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration = categoriesOf(uid.value).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        val categories = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.data?.let(ExpenseCategoryDocumentMapper::fromMap)
                                .also { if (it == null) Log.w(TAG, "Skipping malformed category ${doc.id}") }
                        }
                        trySend(categories)
                    }
                    awaitClose { registration.remove() }
                }.onStart { emit(emptyList()) }
            }
        }

    override suspend fun currentCustomCategories(): List<CustomExpenseCategory> {
        val uid = session.currentUserId() ?: return emptyList()
        val snapshot = categoriesOf(uid.value).get(Source.CACHE).await()
        return snapshot.documents.mapNotNull { it.data?.let(ExpenseCategoryDocumentMapper::fromMap) }
    }

    override suspend fun create(category: CustomExpenseCategory) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        val write = categoriesOf(uid.value).document(category.id).set(ExpenseCategoryDocumentMapper.toMap(category))
        write.addOnFailureListener { Log.w(TAG, "create ${category.id} was rejected", it) }
        // The write is accepted locally at once and works offline. Wait briefly so a fast server rejection
        // (a rules failure) is reported to the caller; an unacknowledged offline write is simply queued.
        withTimeoutOrNull(WAIT_FOR_SERVER_MS) { write.await() }
        if (session.currentUserId() != uid) throw LedgerError.NotSignedIn()
    }

    private fun categoriesOf(uid: String): CollectionReference =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.EXPENSE_CATEGORIES)

    private companion object {
        const val TAG = "FirestoreExpenseCategories"
        const val WAIT_FOR_SERVER_MS = 4_000L
    }
}
