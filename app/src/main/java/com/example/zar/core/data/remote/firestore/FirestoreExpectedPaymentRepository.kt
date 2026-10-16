package com.example.zar.core.data.remote.firestore

import android.util.Log
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.UserSessionProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
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
 * Expected payments and their matches under users/{uid}/. Expectations are updated in place
 * (cancelled, never deleted); a match is created once and removed to unlink. Like the ledger,
 * writes are not awaited, so they apply to the local cache at once and work offline.
 */
@Singleton
class FirestoreExpectedPaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : ExpectedPaymentRepository {

    override fun observeExpectedPayments(): Flow<List<ExpectedPayment>> =
        observeForUser(FirestoreCollections.EXPECTED_PAYMENTS, ExpectedPaymentDocumentMapper::expectedPaymentFromMap)

    override fun observeMatches(): Flow<List<PaymentMatch>> =
        observeForUser(FirestoreCollections.PAYMENT_MATCHES, ExpectedPaymentDocumentMapper::matchFromMap)

    override suspend fun saveExpectedPayment(payment: ExpectedPayment) {
        collection(FirestoreCollections.EXPECTED_PAYMENTS)
            .document(payment.id.value)
            .set(ExpectedPaymentDocumentMapper.expectedPaymentToMap(payment))
            .logFailure("saveExpectedPayment ${payment.id.value}")
    }

    override suspend fun saveMatch(match: PaymentMatch) {
        collection(FirestoreCollections.PAYMENT_MATCHES)
            .document(match.id)
            .set(ExpectedPaymentDocumentMapper.matchToMap(match))
            .logFailure("saveMatch ${match.id}")
    }

    override suspend fun removeMatch(expectedPaymentId: ExpectedPaymentId, transactionId: TransactionId) {
        val id = "${expectedPaymentId.value}_${transactionId.value}"
        collection(FirestoreCollections.PAYMENT_MATCHES).document(id).delete().logFailure("removeMatch $id")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T : Any> observeForUser(name: String, parse: (Map<String, Any?>) -> T?): Flow<List<T>> =
        session.observeUserId().flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else observe(uid, name, parse) }

    private fun collection(name: String): CollectionReference {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        return userCollection(uid, name)
    }

    private fun userCollection(uid: UserId, name: String): CollectionReference =
        firestore.collection(FirestoreCollections.USERS).document(uid.value).collection(name)

    private fun <T : Any> observe(uid: UserId, name: String, parse: (Map<String, Any?>) -> T?): Flow<List<T>> =
        callbackFlow {
            val registration = userCollection(uid, name).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.data?.let(parse).also { if (it == null) Log.w(TAG, "Skipping malformed $name/${doc.id}") }
                }
                trySend(items)
            }
            awaitClose { registration.remove() }
        }

    private fun Task<Void>.logFailure(operation: String) {
        addOnFailureListener { Log.w(TAG, "$operation was rejected", it) }
    }

    private companion object {
        const val TAG = "FirestoreExpected"
    }
}
