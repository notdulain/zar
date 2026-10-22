package com.example.zar.core.data.remote.firestore

import android.util.Log
import com.example.zar.features.accounts.data.repository.AccountDocumentMapper
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transaction
import kotlinx.coroutines.tasks.await
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.UserSessionProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
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
import kotlinx.coroutines.flow.map

/**
 * Drafts at users/{uid}/drafts. Only structured fields are stored; firestore.rules rejects any
 * raw message text. Capture mutations await an atomic server transaction so failures stay visible.
 * Drafts remain readable from the offline cache; confirming requires a connection.
 */
@Singleton
class FirestoreDraftRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : DraftRepository {

    override fun observePendingDrafts(): Flow<List<Draft>> =
        observeDrafts().map { drafts -> drafts.filter { it.status == DraftStatus.PENDING_REVIEW } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDrafts(): Flow<List<Draft>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = drafts(uid.value).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(
                        snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.data?.let(DraftDocumentMapper::fromMap).also { if (it == null) Log.w(TAG, "Skipping malformed draft ${doc.id}") }
                        },
                    )
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override suspend fun saveDraft(draft: Draft): Draft {
        val uid = requireUid()
        val ref = drafts(uid).document(draft.id.value)
        return firestore.runTransaction { tx ->
            checkSession(uid)
            val existing = tx.get(ref)
            checkSession(uid)
            if (existing.exists()) {
                requireNotNull(existing.data?.let(DraftDocumentMapper::fromMap))
            } else {
                tx.set(ref, DraftDocumentMapper.toMap(draft))
                draft
            }
        }.await().also { checkSession(uid) }
    }

    override suspend fun confirmCapturedDraft(draft: Draft, transaction: Transaction) {
        val uid = requireUid()
        val draftRef = drafts(uid).document(draft.id.value)
        val recordRef = firestore.collection(FirestoreCollections.USERS).document(uid)
            .collection(FirestoreCollections.TRANSACTIONS).document(transaction.id.value)
        val accountRef = firestore.collection(FirestoreCollections.USERS).document(uid)
            .collection(FirestoreCollections.ACCOUNTS).document(transaction.accountId.value)
        try {
            firestore.runTransaction { tx ->
                checkSession(uid)
                val current = requireNotNull(tx.get(draftRef).data?.let(DraftDocumentMapper::fromMap))
                val record = tx.get(recordRef)
                val account = tx.get(accountRef).data?.let(AccountDocumentMapper::fromMap)
                checkSession(uid)
                require(current.source == draft.source && current.proposals == draft.proposals) { "Draft changed; reopen it" }
                if (current.status == DraftStatus.CONFIRMED && record.exists()) return@runTransaction
                require(current.status == DraftStatus.PENDING_REVIEW) { "Draft already reviewed" }
                requireNotNull(account) { "Account no longer exists" }
                if (account.isArchived) throw LedgerError.AccountArchived(account.id)
                if (account.currency != transaction.amount.currency) throw LedgerError.CurrencyMismatch(account.currency.code, transaction.amount.currency.code)
                // A legacy partial confirmation can finish without changing the write-once record.
                if (!record.exists()) tx.set(recordRef, LedgerDocumentMapper.transactionToMap(transaction))
                tx.update(draftRef, "status", DraftStatus.CONFIRMED.name)
            }.await()
        } catch (e: FirebaseFirestoreException) {
            // Another device may commit first. Some servers reject the losing write as a
            // rules violation instead of an optimistic-lock retry; verify server truth.
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED && e.code != FirebaseFirestoreException.Code.ABORTED) throw e
            checkSession(uid)
            val finished = draftRef.get(Source.SERVER).await().data?.let(DraftDocumentMapper::fromMap)
            val saved = recordRef.get(Source.SERVER).await()
            checkSession(uid)
            if (finished?.status != DraftStatus.CONFIRMED || finished.proposals != draft.proposals || !saved.exists()) throw e
        }
        checkSession(uid)
    }

    override suspend fun setStatus(id: DraftId, status: DraftStatus) {
        require(status == DraftStatus.DISCARDED) { "Use atomic confirmation to save a record" }
        val uid = requireUid()
        val ref = drafts(uid).document(id.value)
        firestore.runTransaction { tx ->
            checkSession(uid)
            val current = requireNotNull(tx.get(ref).data?.let(DraftDocumentMapper::fromMap))
            checkSession(uid)
            if (current.status == DraftStatus.DISCARDED) return@runTransaction
            require(current.status == DraftStatus.PENDING_REVIEW) { "Draft already confirmed" }
            tx.update(ref, "status", status.name)
        }.await()
        checkSession(uid)
    }

    private fun checkSession(uid: String) {
        if (requireUid() != uid) throw LedgerError.NotSignedIn()
    }

    private fun requireUid(): String = session.currentUserId()?.value ?: throw LedgerError.NotSignedIn()

    private fun drafts(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.DRAFTS)

    private companion object {
        const val TAG = "FirestoreDrafts"
    }
}

/** Field names match firestore.rules. Null fields are left out. Readers return null for malformed documents. */
object DraftDocumentMapper {

    fun toMap(draft: Draft): Map<String, Any> = mapOf(
        "id" to draft.id.value,
        "source" to draft.source.name,
        "status" to draft.status.name,
        "proposals" to draft.proposals.map(::proposalToMap),
        "createdAt" to draft.createdAt.toEpochMilli(),
    )

    fun fromMap(map: Map<String, Any?>): Draft? = runCatching {
        Draft(
            id = DraftId(map["id"] as String),
            source = DraftSource.valueOf(map["source"] as String),
            proposals = (map["proposals"] as List<*>).map { proposalFromMap(it as Map<*, *>)!! },
            status = DraftStatus.valueOf(map["status"] as String),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
        )
    }.getOrNull()

    private fun proposalToMap(proposal: DraftProposal): Map<String, Any> = buildMap {
        when (proposal) {
            is DraftProposal.ActualTransaction -> {
                put("kind", "ACTUAL_TRANSACTION")
                proposal.type?.let { put("type", it.name) }
                proposal.amount?.let { put("amount", LedgerDocumentMapper.moneyToMap(it)) }
                proposal.accountId?.let { put("accountId", it.value) }
                proposal.date?.let { put("date", it.toString()) }
                proposal.spendingClass?.let { put("spendingClass", it.name) }
                proposal.categoryId?.let { put("categoryId", it) }
                proposal.merchant?.let { put("merchant", it) }
                proposal.note?.let { put("note", it) }
            }
            is DraftProposal.Expected -> {
                put("kind", "EXPECTED")
                proposal.direction?.let { put("direction", it.name) }
                proposal.amount?.let { put("amount", LedgerDocumentMapper.moneyToMap(it)) }
                proposal.dueDate?.let { put("dueDate", it.toString()) }
                proposal.title?.let { put("title", it) }
                proposal.accountId?.let { put("accountId", it.value) }
            }
            is DraftProposal.TransferBetweenAccounts -> {
                put("kind", "TRANSFER")
                proposal.fromAccountId?.let { put("fromAccountId", it.value) }
                proposal.toAccountId?.let { put("toAccountId", it.value) }
                proposal.sent?.let { put("sent", LedgerDocumentMapper.moneyToMap(it)) }
                proposal.received?.let { put("received", LedgerDocumentMapper.moneyToMap(it)) }
                proposal.date?.let { put("date", it.toString()) }
            }
        }
        if (proposal.uncertainFields.isNotEmpty()) put("uncertainFields", proposal.uncertainFields.map { it.name }.sorted())
    }

    private fun proposalFromMap(map: Map<*, *>): DraftProposal? {
        fun string(key: String) = map[key] as String?
        fun money(key: String) = (map[key] as Map<*, *>?)?.let(LedgerDocumentMapper::moneyFromMap)
        fun date(key: String) = string(key)?.let(LocalDate::parse)
        val uncertain = (map["uncertainFields"] as List<*>?).orEmpty().mapNotNull { name -> DraftField.entries.firstOrNull { it.name == name } }.toSet()
        return when (map["kind"]) {
            "ACTUAL_TRANSACTION" -> DraftProposal.ActualTransaction(
                type = string("type")?.let(TransactionType::valueOf),
                amount = money("amount"),
                accountId = string("accountId")?.let(::AccountId),
                date = date("date"),
                spendingClass = string("spendingClass")?.let(SpendingClass::valueOf),
                categoryId = string("categoryId"),
                merchant = string("merchant"),
                note = string("note"),
                uncertainFields = uncertain,
            )
            "EXPECTED" -> DraftProposal.Expected(
                direction = string("direction")?.let(Direction::valueOf),
                amount = money("amount"),
                dueDate = date("dueDate"),
                title = string("title"),
                accountId = string("accountId")?.let(::AccountId),
                uncertainFields = uncertain,
            )
            "TRANSFER" -> DraftProposal.TransferBetweenAccounts(
                fromAccountId = string("fromAccountId")?.let(::AccountId),
                toAccountId = string("toAccountId")?.let(::AccountId),
                sent = money("sent"),
                received = money("received"),
                date = date("date"),
                uncertainFields = uncertain,
            )
            else -> null
        }
    }
}
