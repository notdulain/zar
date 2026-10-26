package com.example.zar.features.transactions.data.category

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.transactions.domain.category.repository.CustomCategorySnapshot
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Custom categories under users/{uid}/expenseCategories, set by their stable ID. firestore.rules accepts a replayed
 * identical create and never an edit or delete. Reads come from Firestore's local cache first, so the catalog works
 * offline and after a restart.
 *
 * Account isolation: every user's listener starts from an empty list, so while another account's snapshot loads
 * nothing of the previous account is visible.
 *
 * Resilience: a listener error (a transient permission or network failure) does not end the flow. The last known
 * list is emitted with [CustomCategorySnapshot.isDegraded] set and the listener is re-registered, so the user's own
 * category names stay on screen and recover on their own.
 */
@Singleton
class FirestoreExpenseCategoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : ExpenseCategoryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCustomCategories(): Flow<CustomCategorySnapshot> =
        session.observeUserId().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(CustomCategorySnapshot(emptyList()))
            } else {
                flow {
                    // Start empty so another account's categories are never shown while this one loads.
                    emit(CustomCategorySnapshot(emptyList()))
                    emitAll(
                        retrying(
                            retryDelayMillis = RETRY_DELAY_MS,
                            source = { listener(uid.value) },
                            degraded = { last ->
                                (last ?: CustomCategorySnapshot(emptyList())).copy(isDegraded = true)
                            },
                        ),
                    )
                }
            }
        }

    /** One listener registration. It emits on each snapshot and fails (so [retrying] re-subscribes) on an error. */
    private fun listener(uid: String): Flow<CustomCategorySnapshot> = callbackFlow {
        val registration = categoriesOf(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val categories = snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.data?.let(ExpenseCategoryDocumentMapper::fromMap)
                    .also { if (it == null) Log.w(TAG, "Skipping malformed category ${doc.id}") }
            }
            trySend(CustomCategorySnapshot(categories))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun currentCustomCategories(): List<CustomExpenseCategory> {
        val uid = session.currentUserId() ?: return emptyList()
        val snapshot = categoriesOf(uid.value).get(Source.CACHE).await()
        return snapshot.documents.mapNotNull { it.data?.let(ExpenseCategoryDocumentMapper::fromMap) }
    }

    private val late = MutableSharedFlow<CustomExpenseCategory>(extraBufferCapacity = LATE_BUFFER)
    override val lateRejections: Flow<CustomExpenseCategory> = late.asSharedFlow()

    override suspend fun create(category: CustomExpenseCategory) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        val write = categoriesOf(uid.value).document(category.id).set(ExpenseCategoryDocumentMapper.toMap(category))
        val handedBack = AtomicBoolean(false)
        write.addOnFailureListener { error ->
            Log.w(TAG, "create ${category.id} was rejected", error)
            // A rejection after create() returned (an offline write the server refused on reconnect) rolls the local
            // category back. Tell the screen, so the person is not left holding a category that has silently gone.
            if (handedBack.get()) late.tryEmit(category)
        }
        // The write is accepted locally at once and works offline. Wait briefly so a fast server rejection
        // (a rules failure) is reported to the caller; an unacknowledged offline write is simply queued.
        withTimeoutOrNull(WAIT_FOR_SERVER_MS) { write.await() }
        handedBack.set(true)
        // The write may have failed in the instant between the timeout and the flag; the listener missed it.
        if (write.isComplete && !write.isSuccessful) late.tryEmit(category)
        if (session.currentUserId() != uid) throw LedgerError.NotSignedIn()
    }

    private fun categoriesOf(uid: String): CollectionReference =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.EXPENSE_CATEGORIES)

    private companion object {
        const val TAG = "FirestoreExpenseCategories"
        const val WAIT_FOR_SERVER_MS = 4_000L
        const val RETRY_DELAY_MS = 3_000L
        const val LATE_BUFFER = 8
    }
}
