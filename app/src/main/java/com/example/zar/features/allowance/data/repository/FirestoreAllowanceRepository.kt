package com.example.zar.features.allowance.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.model.AllowanceSyncState
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** One document per user: `users/{uid}/allowance/weekly`. Nothing saved means the allowance is off. */
@Singleton
class FirestoreAllowanceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : AllowanceRepository {

    private val syncStates = MutableStateFlow<Map<String, AllowanceSyncState>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSettings(): Flow<AllowanceSettings> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) flowOf(AllowanceSettings.Disabled) else callbackFlow {
            val registration = document(uid.value).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                syncStates.update { states ->
                    val state = when {
                        snapshot?.metadata?.hasPendingWrites() == true -> AllowanceSyncState.QUEUED
                        states[uid.value] == AllowanceSyncState.FAILED -> AllowanceSyncState.FAILED
                        else -> AllowanceSyncState.SAVED
                    }
                    states + (uid.value to state)
                }
                val data = snapshot?.data
                trySend(
                    when {
                        data == null -> AllowanceSettings.Disabled
                        else -> AllowanceDocumentMapper.fromMap(data)
                            ?: AllowanceSettings.Disabled.also { Log.w(TAG, "Ignoring malformed allowance settings") }
                    },
                )
            }
            awaitClose { registration.remove() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSyncState(): Flow<AllowanceSyncState> = session.observeUserId().flatMapLatest { uid ->
        syncStates.map { states -> uid?.value?.let(states::get) ?: AllowanceSyncState.SAVED }
    }

    override suspend fun saveSettings(settings: AllowanceSettings) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        syncStates.update { it + (uid.value to AllowanceSyncState.QUEUED) }
        val write = document(uid.value).set(AllowanceDocumentMapper.toMap(settings, Instant.now()))
        write.addOnSuccessListener { syncStates.update { it + (uid.value to AllowanceSyncState.SAVED) } }
        write.addOnFailureListener {
            syncStates.update { it + (uid.value to AllowanceSyncState.FAILED) }
            Log.w(TAG, "saveSettings was rejected", it)
        }
        // Local persistence remains usable offline; fast server rejection keeps the form open.
        // Later acknowledgement/rejection is still observed after the form closes.
        withTimeoutOrNull(4_000L) { write.await() }
        if (session.currentUserId() != uid) throw LedgerError.NotSignedIn()
    }

    private fun document(uid: String): DocumentReference = firestore.collection(FirestoreCollections.USERS)
        .document(uid)
        .collection(FirestoreCollections.ALLOWANCE)
        .document(AllowanceDocumentMapper.DOCUMENT_ID)

    private companion object {
        const val TAG = "FirestoreAllowance"
    }
}

object AllowanceDocumentMapper {
    const val DOCUMENT_ID = "weekly"

    fun toMap(settings: AllowanceSettings, updatedAt: Instant): Map<String, Any> = buildMap {
        put("id", DOCUMENT_ID)
        put("enabled", settings.isEnabled)
        settings.amount?.let { put("amount", LedgerDocumentMapper.moneyToMap(it)) }
        put("categoryIds", settings.categoryIds.sorted())
        put("weekStart", settings.weekStart.name)
        put("updatedAt", updatedAt.toEpochMilli())
    }

    /** Null for a malformed document, so one bad value can never crash Home or Plans. */
    fun fromMap(map: Map<String, Any?>): AllowanceSettings? = runCatching {
        AllowanceSettings(
            isEnabled = map["enabled"] as Boolean,
            amount = if ("amount" in map) LedgerDocumentMapper.moneyFromMap(map["amount"] as? Map<*, *>) as FiatMoney else null,
            categoryIds = (map["categoryIds"] as List<*>).map { it as String }.toSet(),
            weekStart = DayOfWeek.valueOf(map["weekStart"] as String),
        )
    }.getOrNull()
}
