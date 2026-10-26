package com.example.zar.core.data.remote.firestore

import android.util.Log
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.RejectedLedgerWrite
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.repository.UserSessionProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Write-once ledger on Firestore. Records are set by stable ID and firestore.rules denies
 * updates and deletes, so a replayed save is rejected by the server instead of overwriting.
 * Writes are not awaited: Firestore applies them to the local cache at once and syncs when
 * online, so saving works offline. If the server later refuses a transaction or transfer, Firestore rolls
 * it back locally; the records are then kept in memory and reported by [observeRejectedWrites] so the
 * person can try again. A refusal that arrives after the app process has ended cannot be reported.
 */
@Singleton
class FirestoreLedgerRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : LedgerRepository {

    /** Refused saves per user, oldest first. Lost with the process, like the task that reported them. */
    private val rejected = MutableStateFlow<Map<String, List<RejectedLedgerWrite>>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLedger(): Flow<List<LedgerEntry>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            combine(
                observe(uid, FirestoreCollections.TRANSACTIONS, LedgerDocumentMapper::transactionFromMap),
                observe(uid, FirestoreCollections.CORRECTIONS, LedgerDocumentMapper::correctionFromMap),
                observe(uid, FirestoreCollections.VOIDS, LedgerDocumentMapper::voidFromMap),
                observe(uid, FirestoreCollections.EVIDENCE_LINKS, LedgerDocumentMapper::evidenceLinkFromMap),
            ) { transactions, corrections, voids, links ->
                LedgerProjector.project(transactions, corrections, voids, links)
            }
        }
    }

    override suspend fun saveTransaction(transaction: Transaction) {
        val uid = signedInUser()
        val document = userCollection(uid, FirestoreCollections.TRANSACTIONS).document(transaction.id.value)
        document.set(LedgerDocumentMapper.transactionToMap(transaction))
            .trackRejection(uid, RejectedLedgerWrite(transaction.id.value, listOf(transaction)), document)
    }

    override suspend fun saveTransfer(transfer: Transfer) {
        val uid = signedInUser()
        val records = listOf(transfer.outLeg(), transfer.inLeg()) + listOfNotNull(transfer.fee)
        write(uid, RejectedLedgerWrite(transfer.id.value, records))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRejectedWrites(): Flow<List<RejectedLedgerWrite>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else rejected.map { it[uid.value].orEmpty() }.distinctUntilChanged()
    }

    override suspend fun retryRejectedWrite(id: String) {
        val uid = signedInUser()
        val write = rejected.value[uid.value].orEmpty().firstOrNull { it.id == id } ?: return
        forget(uid, id)
        write(uid, write)
    }

    override fun dismissRejectedWrite(id: String) {
        session.currentUserId()?.let { forget(it, id) }
    }

    /** Writes every record of [write] in one atomic batch under its stable IDs. */
    private fun write(uid: UserId, write: RejectedLedgerWrite) {
        val transactions = userCollection(uid, FirestoreCollections.TRANSACTIONS)
        val batch = firestore.batch()
        write.transactions.forEach { batch.set(transactions.document(it.id.value), LedgerDocumentMapper.transactionToMap(it)) }
        batch.commit().trackRejection(uid, write, transactions.document(write.transactions.first().id.value))
    }

    override suspend fun addCorrection(correction: Correction) {
        collection(FirestoreCollections.CORRECTIONS)
            .document(correction.id.value)
            .set(LedgerDocumentMapper.correctionToMap(correction))
            .logFailure("addCorrection ${correction.id.value}")
    }

    override suspend fun void(markers: List<VoidMarker>) {
        if (markers.isEmpty()) return
        val voids = collection(FirestoreCollections.VOIDS)
        val batch = firestore.batch()
        markers.forEach { batch.set(voids.document(it.transactionId.value), LedgerDocumentMapper.voidToMap(it)) }
        batch.commit().logFailure("void ${markers.map { it.transactionId.value }}")
    }

    override suspend fun linkEvidence(link: EvidenceLink) {
        collection(FirestoreCollections.EVIDENCE_LINKS)
            .document(link.evidenceId.value)
            .set(LedgerDocumentMapper.evidenceLinkToMap(link))
            .logFailure("linkEvidence ${link.evidenceId.value}")
    }

    private fun collection(name: String): CollectionReference = userCollection(signedInUser(), name)

    private fun signedInUser(): UserId = session.currentUserId() ?: throw LedgerError.NotSignedIn()

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

    // A rejected replay (document already exists) also lands here; it is expected and harmless.
    private fun Task<Void>.logFailure(operation: String) {
        addOnFailureListener { Log.w(TAG, "$operation was rejected", it) }
    }

    /**
     * Keeps [write] when the server refuses it. The refusal arrives after Firestore has already rolled the local
     * copy back, so a record still in the cache means the save was a replay of one that is stored (the write-once
     * rules deny the overwrite) and nothing was lost.
     */
    private fun Task<Void>.trackRejection(uid: UserId, write: RejectedLedgerWrite, stored: DocumentReference) {
        addOnFailureListener { error ->
            Log.w(TAG, "save ${write.id} was rejected", error)
            stored.get(Source.CACHE).addOnCompleteListener { read ->
                if (read.isSuccessful && read.result.exists()) return@addOnCompleteListener
                rejected.update { all -> all + (uid.value to all[uid.value].orEmpty().filterNot { it.id == write.id } + write) }
            }
        }
    }

    private fun forget(uid: UserId, id: String) {
        rejected.update { all -> all + (uid.value to all[uid.value].orEmpty().filterNot { it.id == id }) }
    }

    private companion object {
        const val TAG = "FirestoreLedger"
    }
}
