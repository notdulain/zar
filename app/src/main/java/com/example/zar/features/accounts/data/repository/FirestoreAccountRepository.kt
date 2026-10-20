package com.example.zar.features.accounts.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.UserSessionProvider
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

/** Accounts at users/{uid}/accounts. Writes apply to the local cache at once (offline first). */
@Singleton
class FirestoreAccountRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : AccountRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAccounts(): Flow<List<Account>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = accounts(uid.value).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(
                        snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.data?.let(AccountDocumentMapper::fromMap)
                                .also { if (it == null) Log.w(TAG, "Skipping malformed account ${doc.id}") }
                        },
                    )
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override suspend fun saveAccount(account: Account) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        // Not awaited: the write is in the local cache at once and syncs when online.
        accounts(uid.value).document(account.id.value)
            .set(AccountDocumentMapper.toMap(account, Instant.now()))
            .addOnFailureListener { Log.w(TAG, "saveAccount ${account.id.value} was rejected", it) }
    }

    private fun accounts(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.ACCOUNTS)

    private companion object {
        const val TAG = "FirestoreAccounts"
    }
}

/** Field names match firestore.rules. Returns null for malformed documents. */
object AccountDocumentMapper {

    fun toMap(account: Account, updatedAt: Instant): Map<String, Any> = buildMap {
        put("id", account.id.value)
        put("name", account.name)
        put("type", account.type.name)
        put(
            "currency",
            mapOf(
                "code" to account.currency.code,
                "kind" to account.currency.kind.name,
                "digits" to account.currency.fractionDigits.toLong(),
            ),
        )
        put("opening", openingToMap(account.openingBalance))
        put("openingDate", account.openingDate.toString())
        put("archived", account.isArchived)
        put("updatedAt", updatedAt.toEpochMilli())
    }

    fun fromMap(map: Map<String, Any?>): Account? = runCatching {
        val currency = map["currency"] as Map<*, *>
        Account(
            id = AccountId(map["id"] as String),
            name = map["name"] as String,
            type = AccountType.valueOf(map["type"] as String),
            currency = Currency(
                code = currency["code"] as String,
                kind = AssetKind.valueOf(currency["kind"] as String),
                fractionDigits = (currency["digits"] as Number).toInt(),
            ),
            openingBalance = openingFromMap(map["opening"] as Map<*, *>)!!,
            openingDate = LocalDate.parse(map["openingDate"] as String),
            isArchived = map["archived"] as Boolean,
        )
    }.getOrNull()

    private fun openingToMap(opening: OpeningBalance): Map<String, Any> = when (opening) {
        is OpeningBalance.Known -> mapOf("status" to "KNOWN", "amount" to LedgerDocumentMapper.moneyToMap(opening.amount))
        is OpeningBalance.Approximate -> mapOf("status" to "APPROXIMATE", "amount" to LedgerDocumentMapper.moneyToMap(opening.amount))
        OpeningBalance.Unknown -> mapOf("status" to "UNKNOWN")
    }

    private fun openingFromMap(map: Map<*, *>): OpeningBalance? {
        if (map["status"] == "UNKNOWN") return OpeningBalance.Unknown
        val amount = LedgerDocumentMapper.moneyFromMap(map["amount"] as? Map<*, *>) ?: return null
        return when (map["status"]) {
            "KNOWN" -> OpeningBalance.Known(amount)
            "APPROXIMATE" -> OpeningBalance.Approximate(amount)
            else -> null
        }
    }
}
