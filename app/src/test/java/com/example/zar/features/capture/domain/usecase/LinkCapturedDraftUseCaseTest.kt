package com.example.zar.features.capture.domain.usecase

import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.OCT_10
import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.void
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.EvidenceId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.LinkWrite
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCapturedDraftUseCaseTest {
    private class Drafts : DraftRepository {
        val values = MutableStateFlow<List<Draft>>(emptyList())
        val links = mutableListOf<EvidenceLink>()
        var offline = false
        var failure: Throwable? = null
        override fun observePendingDrafts(): Flow<List<Draft>> = values.map { it.filter { d -> d.status == DraftStatus.PENDING_REVIEW } }
        override fun observeDrafts(): Flow<List<Draft>> = values
        override suspend fun saveDraft(draft: Draft): Draft { values.value = values.value + draft; return draft }
        override suspend fun confirmCapturedDraft(draft: Draft, transaction: Transaction) = Unit
        override suspend fun setStatus(id: DraftId, status: DraftStatus) = Unit
        override fun observeLinkWrite(id: DraftId, target: com.example.zar.core.domain.model.TransactionId): Flow<LinkWrite> = kotlinx.coroutines.flow.flowOf(LinkWrite.SAVED)
        override suspend fun closeAsLinked(draft: Draft, link: EvidenceLink): LinkWrite {
            failure?.let { throw it }
            if (links.none { it.evidenceId == link.evidenceId }) links += link
            values.value = values.value.map { if (it.id == draft.id) it.copy(status = DraftStatus.CONFIRMED) else it }
            return if (offline) LinkWrite.QUEUED else LinkWrite.SAVED
        }
    }

    private val ledger = FakeLedgerRepository()
    private val drafts = Drafts()
    private val draft = Draft(
        DraftId("d1"), DraftSource.SMS,
        listOf(DraftProposal.ActualTransaction(null, lkr(800), null, OCT_10)),
        DraftStatus.PENDING_REVIEW, T0,
    )

    private fun useCase() = LinkCapturedDraftUseCase(drafts, ledger)

    private suspend fun existing(id: String = "earlier") = ledger.saveTransaction(tx(id, TransactionType.EXPENSE, lkr(800), bank, OCT_10))

    @Test
    fun theMessageBecomesEvidenceOnTheEntryAndNoMoneyIsPosted() = runTest {
        existing()

        val result = useCase()(draft, TransactionId("earlier"), T0)

        assertEquals(LinkWrite.SAVED, result.getOrNull())
        assertEquals(listOf(EvidenceLink(EvidenceId("d1"), TransactionId("earlier"), T0)), drafts.links)
        assertEquals(1, ledger.transactions.size)
    }

    @Test
    fun anOfflineWriteIsReportedAsStoredOnThePhone() = runTest {
        existing(); drafts.offline = true

        assertEquals(LinkWrite.QUEUED, useCase()(draft, TransactionId("earlier"), T0).getOrNull())
    }

    @Test
    fun repeatingTheSameLinkChangesNothing() = runTest {
        existing()

        useCase()(draft, TransactionId("earlier"), T0)
        useCase()(draft, TransactionId("earlier"), T0.plusSeconds(30))

        assertEquals(1, drafts.links.size)
        assertEquals(T0, drafts.links.single().linkedAt)
    }

    @Test
    fun anEntryThatNoLongerExistsOrWasUndoneCannotBeLinked() = runTest {
        existing("undone"); ledger.void(listOf(void("undone")))

        assertTrue(useCase()(draft, TransactionId("missing"), T0).isFailure)
        assertTrue(useCase()(draft, TransactionId("undone"), T0).isFailure)
        assertTrue(drafts.links.isEmpty())
    }

    @Test
    fun aMessageThatWasAlreadyReviewedIsNotLinkedAgain() = runTest {
        existing()

        val result = useCase()(draft.copy(status = DraftStatus.CONFIRMED), TransactionId("earlier"), T0)

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(drafts.links.isEmpty())
    }

    @Test
    fun aFailedWriteIsReturnedAsAFailureAndLeavesTheDraftWaiting() = runTest {
        existing(); drafts.saveDraft(draft); drafts.failure = IOException("Denied")

        val result = useCase()(draft, TransactionId("earlier"), T0)

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(DraftStatus.PENDING_REVIEW, drafts.values.value.single().status)
    }

    @Test
    fun theEntriesTheReviewComparesWithExcludeUndoneOnes() = runTest {
        existing("kept"); existing("undone"); ledger.void(listOf(void("undone")))

        val seen = ObserveCaptureLedgerUseCase(ledger)().first().map { it.id.value }

        assertEquals(listOf("kept"), seen)
    }
}
