package com.example.zar.features.bills.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillStatus
import com.example.zar.features.bills.domain.repository.BillRepository
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

/** Bills at users/{uid}/bills. Writes apply to the local cache at once (offline first). */
@Singleton
class FirestoreBillRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : BillRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeBills(): Flow<List<Bill>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = bills(uid.value).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(
                        snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.data?.let(BillDocumentMapper::fromMap).also { if (it == null) Log.w(TAG, "Skipping malformed bill ${doc.id}") }
                        },
                    )
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override suspend fun saveBill(bill: Bill) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        bills(uid.value).document(bill.id.value)
            .set(BillDocumentMapper.toMap(bill))
            .addOnFailureListener { Log.w(TAG, "saveBill ${bill.id.value} was rejected", it) }
    }

    private fun bills(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.BILLS)

    private companion object {
        const val TAG = "FirestoreBills"
    }
}

/** Field names match firestore.rules. Returns null for malformed documents. */
object BillDocumentMapper {

    fun toMap(bill: Bill): Map<String, Any> = mapOf(
        "id" to bill.id.value,
        "name" to bill.name,
        "accountId" to bill.accountId.value,
        "amount" to LedgerDocumentMapper.moneyToMap(bill.amount),
        "frequency" to bill.frequency.name,
        "firstDue" to bill.firstDue.toString(),
        "status" to bill.status.name,
        "createdAt" to bill.createdAt.toEpochMilli(),
    )

    fun fromMap(map: Map<String, Any?>): Bill? = runCatching {
        Bill(
            id = BillId(map["id"] as String),
            name = map["name"] as String,
            accountId = AccountId(map["accountId"] as String),
            amount = LedgerDocumentMapper.moneyFromMap(map["amount"] as Map<*, *>)!!,
            frequency = BillFrequency.valueOf(map["frequency"] as String),
            firstDue = LocalDate.parse(map["firstDue"] as String),
            status = BillStatus.valueOf(map["status"] as String),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
        )
    }.getOrNull()
}
