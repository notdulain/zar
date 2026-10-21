package com.example.zar.features.quicknote.data

import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.quicknote.QuickNoteTestSupport
import com.example.zar.features.quicknote.data.remote.QuickNoteDraftTransport
import com.example.zar.features.quicknote.data.repository.CallableQuickNoteDraftRepository
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallableQuickNoteDraftRepositoryTest {

    private class Session(var user: UserId? = UserId("u1")) : UserSessionProvider {
        override fun currentUserId(): UserId? = user
        override fun observeUserId(): Flow<UserId?> = flowOf(user)
    }

    private class Transport(val behaviour: suspend (Map<String, Any>) -> Any?) : QuickNoteDraftTransport {
        val payloads = mutableListOf<Map<String, Any>>()
        override suspend fun createDraft(payload: Map<String, Any>): Any? {
            payloads += payload
            return behaviour(payload)
        }
    }

    private val now = Instant.parse("2026-10-21T04:00:00Z")
    private var ids = 0

    private fun repo(transport: Transport, session: Session = Session(), budget: Long = 8_000) =
        CallableQuickNoteDraftRepository(transport, session, { "id-${++ids}" }, { now }, budget)

    private val empty = mapOf("version" to 1, "proposals" to emptyList<Any>())

    @Test fun returnsAPendingDraftWithAFreshIdentityPerGeneration() = runTest {
        val repository = repo(Transport { empty })
        val a = repository.generate(QuickNoteTestSupport.request("lunch 850"))
        val b = repository.generate(QuickNoteTestSupport.request("lunch 850"))
        assertEquals(DraftId("id-1"), a.id)
        assertNotEquals(a.id, b.id)
        assertEquals(DraftStatus.PENDING_REVIEW, a.status)
        assertEquals(DraftSource.QUICK_NOTE, a.source)
        assertEquals(now, a.createdAt)
        assertTrue(a.proposals.isEmpty())
    }

    @Test fun sendsOnlyTheNoteDateAndBareAccountFacts() = runTest {
        val transport = Transport { empty }
        repo(transport).generate(QuickNoteTestSupport.request("lunch 850", listOf(QuickNoteTestSupport.boc)))
        val payload = transport.payloads.single()
        assertEquals(setOf("note", "today", "accounts"), payload.keys)
        assertEquals("2026-10-21", payload["today"])
        val account = (payload["accounts"] as List<*>).single() as Map<*, *>
        assertEquals(mapOf("id" to "boc", "name" to "BOC Savings", "type" to "BANK", "currency" to "LKR"), account)
    }

    @Test fun signedOutFailsBeforeCallingTheService() = runTest {
        val transport = Transport { empty }
        val error = runCatching { repo(transport, Session(null)).generate(QuickNoteTestSupport.request("x 1")) }.exceptionOrNull()
        assertTrue(error is QuickNoteDraftError.NotSignedIn)
        assertTrue(transport.payloads.isEmpty())
    }

    @Test fun transportFailuresPropagateAsTheirTypedError() = runTest {
        val failure = QuickNoteDraftError.RateLimited()
        val error = runCatching { repo(Transport { throw failure }).generate(QuickNoteTestSupport.request("x 1")) }.exceptionOrNull()
        assertTrue(error === failure)
    }

    @Test fun malformedServiceAnswerIsAnInvalidResponse() = runTest {
        val error = runCatching { repo(Transport { "garbage" }).generate(QuickNoteTestSupport.request("x 1")) }.exceptionOrNull()
        assertTrue(error is QuickNoteDraftError.InvalidResponse)
    }

    @Test fun exceedingTheEightSecondBudgetIsATimeout() = runTest {
        val repository = repo(Transport { delay(60_000); empty })
        val error = runCatching { repository.generate(QuickNoteTestSupport.request("x 1")) }.exceptionOrNull()
        assertTrue(error is QuickNoteDraftError.TimedOut)
        assertEquals(8_000, currentTime)
    }

    @Test fun anAnswerJustInsideTheBudgetSucceeds() = runTest {
        val repository = repo(Transport { delay(7_999); empty })
        assertTrue(repository.generate(QuickNoteTestSupport.request("x 1")).proposals.isEmpty())
    }

    @Test fun shorterCallerDeadlinePropagatesThroughTheRepository() = runTest {
        val repository = repo(Transport { delay(60_000); empty })
        val result = withTimeoutOrNull(1_000) {
            runCatching { repository.generate(QuickNoteTestSupport.request("x 1")) }
        }
        assertEquals(null, result)
        assertEquals(1_000L, currentTime)
    }

    @Test fun callerCancellationPropagatesAndIsNotATimeout() = runTest {
        val started = CompletableDeferred<Unit>()
        var outcome: Result<*>? = null
        val job = launch {
            outcome = runCatching { repo(Transport { started.complete(Unit); CompletableDeferred<Any?>().await() }).generate(QuickNoteTestSupport.request("x 1")) }
        }
        started.await()
        advanceTimeBy(1_000)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse("cancelled work must not be reported as a result or a TimedOut error", outcome?.exceptionOrNull() is QuickNoteDraftError.TimedOut)
    }

    @Test fun aResponseAfterSignOutOrAccountSwitchIsNotReturnedToTheCaller() = runTest {
        for (nextUser in listOf(null, UserId("u2"))) {
            val session = Session()
            val repository = repo(Transport { session.user = nextUser; empty }, session)
            val error = runCatching { repository.generate(QuickNoteTestSupport.request("private note 850")) }.exceptionOrNull()
            assertTrue(error is QuickNoteDraftError.NotSignedIn)
        }
    }
}
