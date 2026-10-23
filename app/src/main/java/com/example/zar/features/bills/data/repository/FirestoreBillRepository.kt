package com.example.zar.features.bills.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.model.BillCancellation
import com.example.zar.features.bills.domain.model.BillFrequency
import com.example.zar.features.bills.domain.model.BillId
import com.example.zar.features.bills.domain.model.BillReminder
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

    fun toMap(bill: Bill): Map<String, Any> = buildMap {
        put("id", bill.id.value)
        put("name", bill.name)
        put("accountId", bill.accountId.value)
        put("amount", LedgerDocumentMapper.moneyToMap(bill.amount))
        put("frequency", bill.frequency.name)
        put("firstDue", bill.firstDue.toString())
        put("status", bill.status.name)
        put("createdAt", bill.createdAt.toEpochMilli())
        bill.reminder?.let { put("reminderDaysBefore", it.daysBefore.toLong()) }
        when (val cancellation = bill.cancellation) {
            null -> Unit
            BillCancellation.Intended -> put("cancellation", "INTENDED")
            is BillCancellation.Confirmed -> {
                put("cancellation", "CONFIRMED")
                put("cancelledFrom", cancellation.effectiveFrom.toString())
            }
        }
    }

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
            reminder = BillReminder.fromDays((map["reminderDaysBefore"] as Number?)?.toInt()),
            cancellation = when (map["cancellation"]) {
                "INTENDED" -> BillCancellation.Intended
                "CONFIRMED" -> BillCancellation.Confirmed(LocalDate.parse(map["cancelledFrom"] as String))
                else -> null
            },
        )
    }.getOrNull()
}
