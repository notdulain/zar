package com.example.zar.features.allowance.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.repository.AllowanceRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSettings(): Flow<AllowanceSettings> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) flowOf(AllowanceSettings.Disabled) else callbackFlow {
            val registration = document(uid.value).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
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

    override suspend fun saveSettings(settings: AllowanceSettings) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        document(uid.value)
            .set(AllowanceDocumentMapper.toMap(settings, Instant.now()))
            .addOnFailureListener { Log.w(TAG, "saveSettings was rejected", it) }
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
