package com.example.zar.features.freelance.domain.usecase

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.freelance.FakeProjectRepository
import com.example.zar.features.freelance.FreelanceFixtures.TODAY
import com.example.zar.features.freelance.FreelanceFixtures.match
import com.example.zar.features.freelance.FreelanceFixtures.milestone
import com.example.zar.features.freelance.FreelanceFixtures.project
import com.example.zar.features.freelance.domain.FreelanceCalculator
import com.example.zar.features.freelance.domain.MilestoneInput
import com.example.zar.features.freelance.domain.ProjectInput
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreelanceUseCasesTest {

    private val expected = FakeExpectedPaymentRepository()
    private val projects = FakeProjectRepository()
    private val p1 = project("p1")

    private class FixedLedger(private val entries: List<LedgerEntry>) : LedgerRepository {
        override fun observeLedger(): Flow<List<LedgerEntry>> = flowOf(entries)
        override suspend fun saveTransaction(transaction: com.example.zar.core.domain.model.Transaction) = Unit
        override suspend fun saveTransfer(transfer: com.example.zar.core.domain.model.Transfer) = Unit
        override suspend fun addCorrection(correction: com.example.zar.core.domain.model.Correction) = Unit
        override suspend fun void(markers: List<com.example.zar.core.domain.model.VoidMarker>) = Unit
        override suspend fun linkEvidence(link: com.example.zar.core.domain.model.EvidenceLink) = Unit
    }

    private val receipt = entry(tx("r1", TransactionType.INCOME, usd(500), account = payoneer))

    private fun summaryFor(milestoneId: String) = FreelanceCalculator.overview(
        projects.projects.value.ifEmpty { listOf(p1) }, expected.payments.value, expected.matches.value, listOf(receipt), TODAY,
    ).projects.flatMap { it.milestones }.first { it.milestone.id.value == milestoneId }

    private fun candidateFor(milestoneId: String) = FreelanceCalculator.receiptCandidates(
        expected.payments.value.first { it.id.value == milestoneId }, expected.payments.value, expected.matches.value, listOf(receipt),
    ).single()

    @Test
    fun createsAProjectAndRenamesItWithoutChangingCurrency() = runBlocking {
        val save = SaveProjectUseCase(projects)
        assertTrue(save.create(ProjectId("p1"), ProjectInput("Website", "Acme"), Currencies.USD, T0).isSuccess)
        val created = projects.projects.value.single()
        assertTrue(save.update(created, ProjectInput("Website v2", null)).isSuccess)
        assertEquals("Website v2", projects.projects.value.single().name)
        assertEquals(Currencies.USD, projects.projects.value.single().currency)
        assertTrue(save.setArchived(projects.projects.value.single(), true).isSuccess)
        assertEquals(ProjectStatus.ARCHIVED, projects.projects.value.single().status)
    }

    @Test
    fun milestoneIsAnIncomingExpectedPaymentForTheProject() = runBlocking {
        val result = SaveMilestoneUseCase(expected)(p1, ExpectedPaymentId("m1"), MilestoneInput("Design", usd(1_000), TODAY), null, T0)
        assertTrue(result.isSuccess)
        val saved = expected.payments.value.single()
        assertEquals(ExpectedSource.FreelanceMilestone("p1", "m1"), saved.source)
        assertEquals(com.example.zar.core.domain.model.Direction.IN, saved.direction)
    }

    @Test
    fun milestoneRejectsWrongCurrencyArchivedProjectAndCancelledEdits() = runBlocking {
        val save = SaveMilestoneUseCase(expected)
        val input = MilestoneInput("Design", usd(1_000), TODAY)
        assertTrue(save(p1, ExpectedPaymentId("m1"), input.copy(amount = lkr(1_000)), null, T0).exceptionOrNull() is FreelanceError.CurrencyMismatch)
        assertTrue(save(p1.copy(status = ProjectStatus.ARCHIVED), ExpectedPaymentId("m1"), input, null, T0).exceptionOrNull() is FreelanceError.ProjectArchived)
        val cancelled = milestone("m2", "p1", usd(1), status = ExpectedPaymentStatus.CANCELLED)
        assertTrue(save(p1, cancelled.id, input, cancelled, T0).exceptionOrNull() is FreelanceError.MilestoneCancelled)
        assertTrue(expected.payments.value.isEmpty())
    }

    @Test
    fun linkingAPartialReceiptAndUnlinkingIt() = runBlocking {
        expected.saveExpectedPayment(milestone("m1", "p1", usd(1_000)))
        val link = LinkReceiptUseCase(expected)
        assertTrue(link(summaryFor("m1"), candidateFor("m1"), usd(300), T0).isSuccess)
        assertEquals(usd(300), summaryFor("m1").progress.matched)

        UnlinkReceiptUseCase(expected)(expected.matches.value.single())
        assertTrue(expected.matches.value.isEmpty())
    }

    @Test
    fun linkRefusesMoreThanTheReceiptOrMilestoneAllows() = runBlocking {
        expected.saveExpectedPayment(milestone("m1", "p1", usd(1_000)))
        expected.saveExpectedPayment(milestone("small", "p1", usd(100)))
        val link = LinkReceiptUseCase(expected)
        assertTrue(link(summaryFor("m1"), candidateFor("m1"), usd(600), T0).exceptionOrNull() is FreelanceError.AmountTooHigh)
        assertTrue(link(summaryFor("small"), candidateFor("small"), usd(150), T0).exceptionOrNull() is FreelanceError.AmountTooHigh)
        assertTrue(link(summaryFor("m1"), candidateFor("m1"), lkr(10), T0).exceptionOrNull() is FreelanceError.CurrencyMismatch)
        assertTrue(expected.matches.value.isEmpty())
    }

    @Test
    fun cancelledMilestonesCannotTakeReceipts() = runBlocking {
        expected.saveExpectedPayment(milestone("m1", "p1", usd(1_000)))
        val candidate = candidateFor("m1")
        CancelMilestoneUseCase(expected)(expected.payments.value.single())
        assertEquals(ExpectedPaymentStatus.CANCELLED, expected.payments.value.single().status)
        val result = LinkReceiptUseCase(expected)(summaryFor("m1"), candidate, usd(100), T0)
        assertTrue(result.exceptionOrNull() is FreelanceError.MilestoneCancelled)
    }

    @Test
    fun observedOutstandingFollowsSavedRecords() = runBlocking {
        projects.saveProject(p1)
        expected.saveExpectedPayment(milestone("m1", "p1", usd(1_000)))
        expected.saveMatch(match("m1", "r1", usd(500)))
        val freelance = ObserveFreelanceUseCase(projects, expected, FixedLedger(listOf(receipt)))

        assertEquals(listOf(usd(500)), ObserveOutstandingUseCase(freelance)(TODAY).first().owed)
        val candidates = ObserveReceiptCandidatesUseCase(expected, FixedLedger(listOf(receipt)))(ExpectedPaymentId("m1")).first()
        assertTrue("The receipt is fully used by m1", candidates.isEmpty())
    }
}
