package com.example.zar.features.quicknote.data.remote

import com.example.zar.BuildConfig
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Carries why an App Check token could not be had, from the credential lookup to the transport's one diagnostic line. */
internal class AttestationUnavailable(val failure: AttestationFailure) : Exception(failure.name)

internal data class QuickNoteTokens(val idToken: String, val appCheckToken: String)
internal fun interface QuickNoteCredentials {
    /** [forceRefresh] asks Firebase for a new ID token and a new App Check token instead of the cached ones. */
    suspend fun tokens(forceRefresh: Boolean): QuickNoteTokens
}

/** The app sends short-lived Firebase tokens. The OpenRouter key never leaves the Worker. */
@Singleton
class HttpQuickNoteDraftTransport internal constructor(
    private val endpoint: String,
    private val credentials: QuickNoteCredentials,
    private val client: OkHttpClient,
    private val diagnose: (String) -> Unit = { Log.w(TAG, it) },
) : QuickNoteDraftTransport {
    @Inject
    constructor(auth: FirebaseAuth, appCheck: FirebaseAppCheck) : this(
        BuildConfig.QUICK_NOTE_ENDPOINT,
        QuickNoteCredentials { forceRefresh ->
            val user = auth.currentUser ?: throw QuickNoteDraftError.NotSignedIn()
            val idToken = try {
                user.getIdToken(forceRefresh).await().token ?: throw QuickNoteDraftError.NotSignedIn()
            } catch (e: CancellationException) { throw e }
            catch (_: FirebaseNetworkException) { throw QuickNoteDraftError.Offline() }
            catch (_: Exception) { throw QuickNoteDraftError.NotSignedIn() }
            val appToken = try {
                appCheck.getAppCheckToken(forceRefresh).await().token
            } catch (e: CancellationException) { throw e }
            catch (e: FirebaseNetworkException) { throw QuickNoteDraftError.Offline() }
            catch (e: Exception) {
                // The reason is classified and logged as a category only (see AttestationDiagnostics); the SDK text is not.
                val failure = AttestationDiagnostics.classify(e)
                if (failure == AttestationFailure.NETWORK) throw QuickNoteDraftError.Offline()
                throw AttestationUnavailable(failure)
            }
            if (auth.currentUser?.uid != user.uid) throw QuickNoteDraftError.NotSignedIn()
            QuickNoteTokens(idToken, appToken)
        },
        OkHttpClient.Builder()
            .callTimeout(QuickNoteLimits.GENERATION_BUDGET_MILLIS, TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build(),
    )

    override suspend fun createDraft(payload: Map<String, Any>): Any? {
        if (endpoint.isBlank() || (!BuildConfig.DEBUG && !endpoint.startsWith("https://"))) {
            throw QuickNoteDraftError.ServiceUnavailable()
        }
        val tokens = obtain(forceRefresh = false)
        return try {
            post(payload, tokens)
        } catch (rejected: QuickNoteDraftError) {
            // A cached token can be stale: an expired ID token (401) or an App Check token Firebase has since invalidated (403).
            // Ask for fresh ones once and send the same request again. A second refusal is real and is reported as such.
            val second = when (rejected) {
                is QuickNoteDraftError.AppNotVerified -> AttestationFailure.WORKER_REFUSED
                is QuickNoteDraftError.NotSignedIn -> AttestationFailure.SESSION_REFUSED
                else -> throw rejected
            }
            val fresh = obtain(forceRefresh = true)
            try {
                post(payload, fresh)
            } catch (again: QuickNoteDraftError) {
                if (again is QuickNoteDraftError.AppNotVerified || again is QuickNoteDraftError.NotSignedIn) diagnose("${second.name}: ${second.action}")
                throw again
            }
        }
    }

    /** The tokens, or the typed error for why there are none. An attestation failure is logged here as a category. */
    private suspend fun obtain(forceRefresh: Boolean): QuickNoteTokens = try {
        credentials.tokens(forceRefresh)
    } catch (unavailable: AttestationUnavailable) {
        diagnose("${unavailable.failure.name}: ${unavailable.failure.action}")
        throw QuickNoteDraftError.AppNotVerified()
    }

    private suspend fun post(payload: Map<String, Any>, tokens: QuickNoteTokens): Any? {
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer ${tokens.idToken}")
            .header("X-Firebase-AppCheck", tokens.appCheckToken)
            .post(QuickNoteHttpCodec.encode(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val error = if (e is InterruptedIOException) QuickNoteDraftError.TimedOut() else QuickNoteDraftError.Offline()
                    continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            val body = it.body
                            val source = body.source()
                            // Bound even chunked bodies before decoding them or constructing an in-memory String.
                            source.request(MAX_RESPONSE_BYTES + 1)
                            if (source.buffer.size > MAX_RESPONSE_BYTES) throw QuickNoteDraftError.InvalidResponse()
                            QuickNoteHttpCodec.decode(it.code, source.readUtf8())
                        }
                    }
                    continuation.resumeWith(result.recoverCatching { error ->
                        when (error) {
                            is InterruptedIOException -> throw QuickNoteDraftError.TimedOut()
                            is IOException -> throw QuickNoteDraftError.Offline()
                            else -> throw error
                        }
                    })
                }
            })
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64L * 1024
        const val TAG = "ZarQuickNote"
    }
}
