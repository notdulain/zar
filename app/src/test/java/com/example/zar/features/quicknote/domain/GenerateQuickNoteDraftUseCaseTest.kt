package com.example.zar.features.quicknote.domain

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.features.quicknote.QuickNoteTestSupport.TODAY
import com.example.zar.features.quicknote.QuickNoteTestSupport.Session
import com.example.zar.core.domain.model.UserId
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError.InputProblem
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import com.example.zar.features.quicknote.domain.usecase.GenerateQuickNoteDraftUseCase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateQuickNoteDraftUseCaseTest {

    private fun account(id: String, name: String, archived: Boolean = false, type: AccountType = AccountType.BANK) = Account(
        id = AccountId(id), name = name, type = type, currency = Currencies.LKR,
        openingBalance = OpeningBalance.Known(FiatMoney(987_654_321, Currencies.LKR)),
        openingDate = LocalDate.of(2026, 1, 1), isArchived = archived,
    )

    private class FakeAccounts(initial: List<Account>) : AccountRepository {
        val state = MutableStateFlow(initial)
        override fun observeAccounts(): Flow<List<Account>> = state
        override suspend fun saveAccount(account: Account) = error("generation must not write accounts")
    }

    private class FakeDrafts(var behaviour: suspend (QuickNoteDraftRequest) -> Draft) : QuickNoteDraftRepository {
        val requests = mutableListOf<QuickNoteDraftRequest>()
        override suspend fun generate(request: QuickNoteDraftRequest): Draft {
            requests += request
            return behaviour(request)
        }
    }

    private val pending = Draft(DraftId("d1"), DraftSource.QUICK_NOTE, emptyList(), DraftStatus.PENDING_REVIEW, Instant.EPOCH)

    private fun useCase(drafts: FakeDrafts, accounts: List<Account> = listOf(account("boc", "BOC Savings"))) =
        GenerateQuickNoteDraftUseCase(drafts, FakeAccounts(accounts), Session()) { TODAY }

    @Test
    fun returnsThePendingDraftAndSendsMinimalContext() = runTest {
        val drafts = FakeDrafts { pending }
        val result = useCase(
            drafts,
            listOf(account("boc", "  BOC Savings "), account("old", "Old", archived = true), account("cash", "Cash", type = AccountType.CASH)),
        )("  lunch 850  ")

        assertEquals(pending, result.getOrThrow())
        val request = drafts.requests.single()
        assertEquals("lunch 850", request.note)
        assertEquals(TODAY, request.today)
        assertEquals(listOf("BOC Savings", "Cash"), request.accounts.map { it.name })
        assertFalse("archived accounts are not sent", request.accounts.any { it.id.value == "old" })
    }

    @Test
    fun zeroProposalsIsASuccess() = runTest {
        val result = useCase(FakeDrafts { pending })("nothing financial here")
        assertTrue(result.getOrThrow().proposals.isEmpty())
    }

    @Test
    fun rejectsBlankAndOversizedNotesWithoutCallingTheRepository() = runTest {
        val drafts = FakeDrafts { pending }
        val blank = useCase(drafts)("   \n ").exceptionOrNull() as QuickNoteDraftError.InvalidInput
        assertEquals(InputProblem.BLANK, blank.problem)
        val long = useCase(drafts)("x".repeat(501)).exceptionOrNull() as QuickNoteDraftError.InvalidInput
        assertEquals(InputProblem.TOO_LONG, long.problem)
        assertTrue(drafts.requests.isEmpty())
        assertTrue(useCase(drafts)("x".repeat(500)).isSuccess)
    }

    @Test
    fun surfacesTypedRepositoryFailures() = runTest {
        for (error in listOf(QuickNoteDraftError.NotSignedIn(), QuickNoteDraftError.AppNotVerified(), QuickNoteDraftError.Offline(), QuickNoteDraftError.TimedOut())) {
            val result = useCase(FakeDrafts { throw error })("lunch 850")
            assertTrue(result.exceptionOrNull() === error)
        }
    }

    @Test
    fun retriableFlagsGuideTheCaller() {
        assertFalse(QuickNoteDraftError.NotSignedIn().isRetriable)
        assertFalse(QuickNoteDraftError.InvalidInput(InputProblem.BLANK).isRetriable)
        assertTrue(QuickNoteDraftError.Offline().isRetriable)
        assertTrue(QuickNoteDraftError.RateLimited().isRetriable)
    }

    @Test
    fun unexpectedExceptionsBecomeAGenericFailureWithoutTheirMessage() = runTest {
        val result = useCase(FakeDrafts { throw IllegalStateException("echo: lunch 850 secret") })("lunch 850 secret")
        val error = result.exceptionOrNull()
        assertTrue(error is QuickNoteDraftError.ServiceUnavailable)
        assertFalse(error!!.message!!.contains("secret"))
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingAResult() = runTest(StandardTestDispatcher()) {
        val started = CompletableDeferred<Unit>()
        val drafts = FakeDrafts {
            started.complete(Unit)
            CompletableDeferred<Draft>().await()
        }
        var finished: Result<Draft>? = null
        val job = launch { finished = useCase(drafts)("lunch 850") }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertNull("a cancelled generation must not produce a result", finished)
    }

    @Test
    fun capsAccountContext() = runTest {
        val drafts = FakeDrafts { pending }
        useCase(drafts, (1..40).map { account("a$it", "Account ${it.toString().padStart(2, '0')}") })("hi 5")
        assertEquals(30, drafts.requests.single().accounts.size)
    }

    @Test
    fun accountContextWaitIsIncludedInTheEightSecondBudget() = runTest {
        val drafts = FakeDrafts { pending }
        val waitingAccounts = object : AccountRepository {
            override fun observeAccounts(): Flow<List<Account>> = flow { delay(60_000); emit(emptyList()) }
            override suspend fun saveAccount(account: Account) = error("must not save")
        }
        val generate = GenerateQuickNoteDraftUseCase(drafts, waitingAccounts, Session()) { TODAY }
        assertTrue(generate("lunch 850").exceptionOrNull() is QuickNoteDraftError.TimedOut)
        assertEquals(8_000L, currentTime)
        assertTrue(drafts.requests.isEmpty())
    }

    @Test
    fun generationBudgetIncludesContextAndRepositoryTogether() = runTest {
        val drafts = FakeDrafts { delay(5_000); pending }
        val slowAccounts = object : AccountRepository {
            override fun observeAccounts(): Flow<List<Account>> = flow { delay(4_000); emit(emptyList()) }
            override suspend fun saveAccount(account: Account) = error("must not save")
        }
        val generate = GenerateQuickNoteDraftUseCase(drafts, slowAccounts, Session()) { TODAY }
        assertTrue(generate("lunch 850").exceptionOrNull() is QuickNoteDraftError.TimedOut)
        assertEquals(8_000L, currentTime)
    }

    @Test
    fun signingOutWhileAccountsLoadDoesNotSendTheNoteOrContext() = runTest {
        val session = Session()
        val drafts = FakeDrafts { pending }
        val changingAccounts = object : AccountRepository {
            override fun observeAccounts(): Flow<List<Account>> = flow {
                session.user = null
                emit(listOf(account("private", "Private account")))
            }
            override suspend fun saveAccount(account: Account) = error("must not save")
        }
        val generate = GenerateQuickNoteDraftUseCase(drafts, changingAccounts, session) { TODAY }
        assertTrue(generate("lunch 850").exceptionOrNull() is QuickNoteDraftError.NotSignedIn)
        assertTrue(drafts.requests.isEmpty())
    }

    @Test
    fun switchingAccountsDuringGenerationDiscardsTheOldUsersResult() = runTest {
        val session = Session()
        val drafts = FakeDrafts { session.user = UserId("u2"); pending }
        val generate = GenerateQuickNoteDraftUseCase(drafts, FakeAccounts(emptyList()), session) { TODAY }
        assertTrue(generate("lunch 850").exceptionOrNull() is QuickNoteDraftError.NotSignedIn)
    }

    @Test
    fun signedOutDoesNotWaitForAccountsOrSendTheNote() = runTest {
        val drafts = FakeDrafts { pending }
        val unreadableAccounts = object : AccountRepository {
            override fun observeAccounts(): Flow<List<Account>> = error("must not read signed-out accounts")
            override suspend fun saveAccount(account: Account) = error("must not save")
        }
        val generate = GenerateQuickNoteDraftUseCase(drafts, unreadableAccounts, Session(null)) { TODAY }
        assertTrue(generate("lunch 850").exceptionOrNull() is QuickNoteDraftError.NotSignedIn)
        assertTrue(drafts.requests.isEmpty())
    }
}
