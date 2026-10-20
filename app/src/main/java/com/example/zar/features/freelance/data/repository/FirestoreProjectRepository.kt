package com.example.zar.features.freelance.data.repository

import android.util.Log
import com.example.zar.core.data.remote.firestore.FirestoreCollections
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.money.AssetKind
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectStatus
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Projects at users/{uid}/projects. Writes apply to the local cache at once (offline first). */
@Singleton
class FirestoreProjectRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: UserSessionProvider,
) : ProjectRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProjects(): Flow<List<Project>> = session.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = projects(uid.value).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(
                        snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.data?.let(ProjectDocumentMapper::fromMap)
                                .also { if (it == null) Log.w(TAG, "Skipping malformed project ${doc.id}") }
                        },
                    )
                }
                awaitClose { registration.remove() }
            }
        }
    }

    override suspend fun saveProject(project: Project) {
        val uid = session.currentUserId() ?: throw LedgerError.NotSignedIn()
        projects(uid.value).document(project.id.value)
            .set(ProjectDocumentMapper.toMap(project))
            .addOnFailureListener { Log.w(TAG, "saveProject ${project.id.value} was rejected", it) }
    }

    private fun projects(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid).collection(FirestoreCollections.PROJECTS)

    private companion object {
        const val TAG = "FirestoreProjects"
    }
}

/** Field names match firestore.rules. Returns null for malformed documents. */
object ProjectDocumentMapper {

    fun toMap(project: Project): Map<String, Any> = buildMap {
        put("id", project.id.value)
        put("name", project.name)
        project.client?.let { put("client", it) }
        put(
            "currency",
            mapOf(
                "code" to project.currency.code,
                "kind" to project.currency.kind.name,
                "digits" to project.currency.fractionDigits.toLong(),
            ),
        )
        put("status", project.status.name)
        put("createdAt", project.createdAt.toEpochMilli())
    }

    fun fromMap(map: Map<String, Any?>): Project? = runCatching {
        val currency = map["currency"] as Map<*, *>
        Project(
            id = ProjectId(map["id"] as String),
            name = map["name"] as String,
            client = map["client"] as String?,
            currency = Currency(
                code = currency["code"] as String,
                kind = AssetKind.valueOf(currency["kind"] as String),
                fractionDigits = (currency["digits"] as Number).toInt(),
            ),
            status = ProjectStatus.valueOf(map["status"] as String),
            createdAt = Instant.ofEpochMilli((map["createdAt"] as Number).toLong()),
        )
    }.getOrNull()
}
