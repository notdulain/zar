package com.example.zar.features.quicknote.data

import com.example.zar.features.quicknote.data.remote.HttpQuickNoteDraftTransport
import com.example.zar.features.quicknote.data.remote.QuickNoteCredentials
import com.example.zar.features.quicknote.data.remote.QuickNoteTokens
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class HttpQuickNoteDraftTransportTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(2, TimeUnit.SECONDS).build()
    private val payload: Map<String, Any> = mapOf("note" to "lunch 850", "today" to "2026-10-24", "accounts" to emptyList<Any>())

    private fun transport(credentials: QuickNoteCredentials = QuickNoteCredentials { QuickNoteTokens("id-token", "app-token") }) =
        HttpQuickNoteDraftTransport(server.url("/v1/quick-note").toString(), credentials, client)

    @After fun close() { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }

    @Test fun sendsBothFirebaseTokensAndDecodesTheExistingDraftContract() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"version":1,"proposals":[{"kind":"ACTUAL_TRANSACTION","amount":"1250.50","accountId":null}]}"""))
        val result = transport().createDraft(payload) as Map<*, *>
        assertEquals(1L, result["version"])
        val proposal = (result["proposals"] as List<*>).single() as Map<*, *>
        assertEquals("1250.50", proposal["amount"])
        assertNull(proposal["accountId"])
        val request = server.takeRequest()
        assertEquals("Bearer id-token", request.getHeader("Authorization"))
        assertEquals("app-token", request.getHeader("X-Firebase-AppCheck"))
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("lunch 850"))
    }

    @Test fun mapsHttpFailuresToExistingUiErrors() = runBlocking {
        for ((status, reason, expected) in listOf(
            Triple(403, "APP_CHECK", QuickNoteDraftError.AppNotVerified::class.java),
            Triple(401, "UNAUTHENTICATED", QuickNoteDraftError.NotSignedIn::class.java),
            Triple(429, "RATE_LIMITED", QuickNoteDraftError.RateLimited::class.java),
            Triple(504, "TIMEOUT", QuickNoteDraftError.TimedOut::class.java),
            Triple(502, "BAD_RESPONSE", QuickNoteDraftError.InvalidResponse::class.java),
            Triple(503, "PROVIDER_UNAVAILABLE", QuickNoteDraftError.ServiceUnavailable::class.java),
        )) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":{"reason":"$reason","message":"private provider text"}}"""))
            val error = runCatching { transport().createDraft(payload) }.exceptionOrNull()
            assertTrue("$status should map to $expected", expected.isInstance(error))
            assertFalse(error?.message.orEmpty().contains("private provider text"))
        }
    }

    @Test fun handlesHtmlErrorsAndRejectsMalformedOrOversizedSuccesses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>unavailable</html>"))
        assertTrue(runCatching { transport().createDraft(payload) }.exceptionOrNull() is QuickNoteDraftError.ServiceUnavailable)
        for (body in listOf("not-json", "null", "x".repeat(70_000))) {
            server.enqueue(MockResponse().setBody(body))
            assertTrue(runCatching { transport().createDraft(payload) }.exceptionOrNull() is QuickNoteDraftError.InvalidResponse)
        }
    }

    @Test fun neverSendsTheNoteWhenVerificationFails() = runBlocking {
        val transport = transport(QuickNoteCredentials { throw QuickNoteDraftError.AppNotVerified() })
        assertTrue(runCatching { transport.createDraft(payload) }.exceptionOrNull() is QuickNoteDraftError.AppNotVerified)
        assertEquals(0, server.requestCount)
    }

    @Test fun redirectsDoNotForwardFirebaseTokensToAnotherEndpoint() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/unexpected")))
        assertTrue(runCatching { transport().createDraft(payload) }.exceptionOrNull() is QuickNoteDraftError.ServiceUnavailable)
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellingTheScreenCancelsTheNetworkOperation() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = async(Dispatchers.Default) { transport().createDraft(payload) }
        val sent = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertNotNull(sent)
        result.cancel()
        assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
    }
}
