package com.example.zar.features.quicknote.data.remote

import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Calls the `createQuickNoteDraft` callable. Firebase adds the signed-in user's ID token and an App Check token
 * to every call; the service rejects the request without both. Nothing here logs or stores the payload.
 */
@Singleton
class FirebaseQuickNoteDraftTransport @Inject constructor(
    private val functions: FirebaseFunctions,
) : QuickNoteDraftTransport {

    override suspend fun createDraft(payload: Map<String, Any>): Any? = try {
        functions.getHttpsCallable(CALLABLE_NAME)
            .withTimeout(QuickNoteLimits.GENERATION_BUDGET_MILLIS, TimeUnit.MILLISECONDS)
            .call(payload)
            .await()
            .getData()
    } catch (e: FirebaseFunctionsException) {
        val reason = (e.details as? Map<*, *>)?.get("reason") as? String
        throw CallableErrorMapper.map(e.code.name, reason)
    } catch (_: IOException) {
        throw QuickNoteDraftError.Offline()
    }

    companion object {
        const val CALLABLE_NAME = "createQuickNoteDraft"
        const val REGION = "asia-south1"
    }
}
