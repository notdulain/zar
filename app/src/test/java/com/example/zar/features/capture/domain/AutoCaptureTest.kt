package com.example.zar.features.capture.domain

import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.capture.domain.auto.AutoCapturePreferences
import com.example.zar.features.capture.domain.auto.AutoCaptureRules
import com.example.zar.features.capture.domain.auto.AutoCaptureScheduler
import com.example.zar.features.capture.domain.auto.ObserveAutoCaptureUseCase
import com.example.zar.features.capture.domain.auto.PauseAutoCaptureOnSignOut
import com.example.zar.features.capture.domain.auto.ProcessStagedSmsUseCase
import com.example.zar.features.capture.domain.auto.SetAutoCaptureUseCase
import com.example.zar.features.capture.domain.auto.SmsStagingRepository
import com.example.zar.features.capture.domain.auto.StageIncomingSmsUseCase
import com.example.zar.features.capture.domain.auto.StageOutcome
import com.example.zar.features.capture.domain.auto.StagedMessage
import com.example.zar.features.capture.domain.auto.StagingQueue
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.CaptureTextUseCase
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureTest {

    private val purchase = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private val credit = "LKR 240.00 credited to AC **3333 for Food\n12/10/2026 12:21:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP"
    private val otp = "Your OTP for the transaction of LKR 1,500.00 is 482913. Do not share it with anyone."
    private val personal = "Are we still meeting at 6? Bring the 800 you owe me"

    private val alice = UserId("alice")
    private val bob = UserId("bob")
    private val received = Instant.parse("2026-10-11T05:19:00Z")

    private class FakeSession(initial: UserId?) : UserSessionProvider {
        val user = MutableStateFlow(initial)
        override fun currentUserId(): UserId? = user.value
        override fun observeUserId(): Flow<UserId?> = user
    }

    private class FakePreferences(initial: UserId? = null) : AutoCapturePreferences {
        val enabledFor = MutableStateFlow(initial)
        override fun observeEnabledFor(): Flow<UserId?> = enabledFor
        override suspend fun enabledFor(): UserId? = enabledFor.value
        override suspend fun setEnabledFor(owner: UserId?) { enabledFor.value = owner }
    }

    private class FakeStaging : SmsStagingRepository {
        /** id → message; a null text is an expiry notice. */
        val rows = MutableStateFlow<Map<String, Pair<StagedMessage, Boolean>>>(emptyMap())
        override suspend fun stage(message: StagedMessage): Boolean {
            if (message.id in rows.value) return false
            rows.value = rows.value + (message.id to (message to false))
            return true
        }
        override suspend fun waiting(owner: UserId) = rows.value.values.filter { (m, expired) -> m.owner == owner && !expired }.map { it.first }
        override suspend fun clear(id: String) { rows.value = rows.value - id }
        override suspend fun expire(cutoff: Instant): Int {
            val old = rows.value.filterValues { (m, expired) -> !expired && m.receivedAt.isBefore(cutoff) }
            rows.value = rows.value + old.mapValues { (_, row) -> row.first.copy(text = "") to true }
            return old.size
        }
        override suspend fun discardWaiting(owner: UserId) { rows.value = rows.value.filterValues { (m, expired) -> m.owner != owner || expired } }
        override suspend fun dismissExpired(owner: UserId) { rows.value = rows.value.filterValues { (m, expired) -> m.owner != owner || !expired } }
        override fun observe(owner: UserId): Flow<StagingQueue> = rows.map { all ->
            val mine = all.values.filter { it.first.owner == owner }
            StagingQueue(mine.count { !it.second }, mine.filter { it.second }.map { it.first.receivedAt })
        }
        val texts get() = rows.value.values.map { it.first.text }
    }

    private class FakeScheduler : AutoCaptureScheduler {
        var soon = 0
        var periodic = 0
        var cancelled = 0
        override fun processSoon() { soon++ }
        override fun ensurePeriodic() { periodic++ }
        override fun cancelPending() { cancelled++ }
    }

    /** Drafts per user, so a test can see whose account an upload went to. */
    private class FakeDrafts(val session: FakeSession) : DraftRepository {
        var offline = false
        val byUser = mutableMapOf<UserId, MutableMap<DraftId, Draft>>()
        override fun observePendingDrafts(): Flow<List<Draft>> = MutableStateFlow(emptyList())
        override fun observeDrafts(): Flow<List<Draft>> = MutableStateFlow(emptyList())
        override suspend fun saveDraft(draft: Draft): Draft {
            if (offline) throw IOException("Offline")
            val drafts = byUser.getOrPut(requireNotNull(session.currentUserId())) { mutableMapOf() }
            return drafts.getOrPut(draft.id) { draft }
        }
        override suspend fun confirmCapturedDraft(draft: Draft, transaction: Transaction) = Unit
        override suspend fun setStatus(id: DraftId, status: DraftStatus) = Unit
        override fun observeLinkWrite(id: DraftId, target: com.example.zar.core.domain.model.TransactionId): Flow<LinkWrite> = kotlinx.coroutines.flow.flowOf(LinkWrite.SAVED)
        override suspend fun closeAsLinked(draft: Draft, link: EvidenceLink) = LinkWrite.SAVED
    }

    private class Fixture(signedIn: UserId?, enabledFor: UserId?) {
        val session = FakeSession(signedIn)
        val preferences = FakePreferences(enabledFor)
        val staging = FakeStaging()
        val scheduler = FakeScheduler()
        val drafts = FakeDrafts(session)
        val stage = StageIncomingSmsUseCase(BankSmsParser(), session, preferences, staging, scheduler)
        val process = ProcessStagedSmsUseCase(session, staging, CaptureTextUseCase(BankSmsParser(), drafts))
    }

    // --- Staging ---------------------------------------------------------------------------

    @Test
    fun onlyTestedBankFormatsAreStagedAndProcessingIsScheduled() = runBlocking {
        val f = Fixture(alice, alice)
        assertEquals(StageOutcome.STAGED, f.stage(purchase, received))
        assertEquals(StageOutcome.NOT_A_PAYMENT, f.stage(personal, received))
        assertEquals(StageOutcome.NOT_A_PAYMENT, f.stage(otp, received))
        assertEquals("Personal messages and OTPs are never stored", listOf(purchase), f.staging.texts)
        assertEquals(1, f.scheduler.soon)
    }

    @Test
    fun theSameMessageIsStagedOnce() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        assertEquals(StageOutcome.ALREADY_STAGED, f.stage(" $purchase ", received.plusSeconds(1)))
        assertEquals(1, f.staging.rows.value.size)
    }

    @Test
    fun nothingIsStagedWhenOffSignedOutOrTurnedOnBySomeoneElse() = runBlocking {
        assertEquals(StageOutcome.OFF, Fixture(alice, null).stage(purchase, received))
        assertEquals(StageOutcome.SIGNED_OUT, Fixture(null, alice).stage(purchase, received))
        val other = Fixture(bob, alice)
        assertEquals("Alice turned it on; Bob is signed in", StageOutcome.OFF, other.stage(purchase, received))
        assertTrue(other.staging.rows.value.isEmpty())
    }

    @Test
    fun stagingIdsAreStablePerUserAndHoldNoText() {
        val id = AutoCaptureRules.stagingIdFor(alice, purchase)
        assertEquals(id, AutoCaptureRules.stagingIdFor(alice, purchase.replace(" ", "  ")))
        assertFalse(id == AutoCaptureRules.stagingIdFor(bob, purchase))
        assertFalse(id.contains("800") || id.contains("CAFE", ignoreCase = true))
    }

    // --- Processing ------------------------------------------------------------------------

    @Test
    fun processingMakesAPendingSmsDraftAndDeletesTheText() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        val result = f.process(received.plusSeconds(60))
        assertEquals(1, result.processed)
        assertTrue("Raw text cleared after processing", f.staging.rows.value.isEmpty())
        val draft = f.drafts.byUser.getValue(alice).values.single()
        assertEquals(DraftSource.SMS, draft.source)
        assertEquals(DraftStatus.PENDING_REVIEW, draft.status)
        assertEquals("Captured at the time it arrived", received, draft.createdAt)
        assertEquals(CapturedDraftRules.draftIdFor(purchase), draft.id)
    }

    @Test
    fun aCreditIsNotGuessedAsIncome() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(credit, received)
        f.process(received)
        val proposal = f.drafts.byUser.getValue(alice).values.single().proposals.single() as DraftProposal.ActualTransaction
        assertEquals(null, proposal.type)
        assertTrue(com.example.zar.core.domain.model.DraftField.TYPE in proposal.uncertainFields)
    }

    @Test
    fun aMessageAlreadySharedOrCapturedMakesNoSecondDraft() = runBlocking {
        val f = Fixture(alice, alice)
        CaptureTextUseCase(BankSmsParser(), f.drafts)(purchase, DraftSource.SHARED_TEXT, received)
        f.stage(purchase, received)
        f.process(received)
        f.stage(purchase, received.plusSeconds(5))
        f.process(received.plusSeconds(10))
        assertEquals(1, f.drafts.byUser.getValue(alice).size)
        assertTrue(f.staging.rows.value.isEmpty())
    }

    @Test
    fun offlineMessagesWaitAndAreRetried() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        f.drafts.offline = true
        assertEquals(1, f.process(received).left)
        assertEquals(1, f.staging.rows.value.size)
        f.drafts.offline = false
        assertEquals(0, f.process(received).left)
        assertTrue(f.staging.rows.value.isEmpty())
    }

    @Test
    fun unsavedMessagesExpireAfterSevenDaysWithANotice() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        f.drafts.offline = true
        assertEquals(0, f.process(received.plus(Duration.ofDays(7)).minusSeconds(1)).expired)
        val result = f.process(received.plus(Duration.ofDays(7)).plusSeconds(1))
        assertEquals(1, result.expired)
        val queue = f.staging.observe(alice).first()
        assertEquals(0, queue.waiting)
        assertEquals(listOf(received), queue.expired)
        assertTrue("The expired text is gone", f.staging.texts.none { it.isNotEmpty() })
        f.drafts.offline = false
        f.process(received.plus(Duration.ofDays(8)))
        assertTrue("An expired message is never uploaded later", f.drafts.byUser[alice].isNullOrEmpty())
    }

    // --- Bound to the user -----------------------------------------------------------------

    @Test
    fun signedOutPausesAndAnotherUserNeverUploadsTheQueue() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        PauseAutoCaptureOnSignOut(f.scheduler).onSignOut()
        assertEquals(1, f.scheduler.cancelled)
        f.session.user.value = null
        assertTrue(f.process(received).paused)
        f.session.user.value = bob
        f.process(received)
        assertTrue("Nothing reached Bob's drafts", f.drafts.byUser[bob].isNullOrEmpty())
        assertEquals("Still waiting for Alice", 1, f.staging.waiting(alice).size)
        f.session.user.value = alice
        f.process(received)
        assertEquals(1, f.drafts.byUser.getValue(alice).size)
    }

    @Test
    fun turningItOffDeletesWaitingMessagesAndOnlyAffectsTheSignedInUser() = runBlocking {
        val f = Fixture(alice, null)
        val set = SetAutoCaptureUseCase(f.session, f.preferences, f.staging, f.scheduler)
        val observe = ObserveAutoCaptureUseCase(f.session, f.preferences, f.staging)
        set(true).getOrThrow()
        assertEquals(alice, f.preferences.enabledFor.value)
        assertEquals(1, f.scheduler.periodic)
        assertTrue(observe().first().enabled)
        f.stage(purchase, received)
        assertEquals(1, observe().first().queue.waiting)

        f.session.user.value = bob
        assertFalse("Bob sees it off: Alice turned it on", observe().first().enabled)

        f.session.user.value = alice
        set(false).getOrThrow()
        assertEquals(null, f.preferences.enabledFor.value)
        assertTrue(f.staging.rows.value.isEmpty())
        assertEquals(StageOutcome.OFF, f.stage(purchase, received))
    }

    @Test
    fun turningItOnNeedsASignedInUser() = runBlocking {
        val f = Fixture(null, null)
        assertTrue(SetAutoCaptureUseCase(f.session, f.preferences, f.staging, f.scheduler)(true).isFailure)
        assertEquals(null, f.preferences.enabledFor.value)
    }

    @Test
    fun expiredNoticesCanBeDismissed() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        f.staging.expire(received.plusSeconds(1))
        com.example.zar.features.capture.domain.auto.DismissExpiredSmsUseCase(f.session, f.staging)().getOrThrow()
        assertEquals(StagingQueue(), f.staging.observe(alice).first())
    }

    @Test
    fun confirmingAnSmsDraftRecordsAnSmsSourcedTransaction() = runBlocking {
        val f = Fixture(alice, alice)
        f.stage(purchase, received)
        f.process(received)
        val draft = f.drafts.byUser.getValue(alice).values.single()
        val tx = CapturedDraftRules.transactionFrom(draft, com.example.zar.core.domain.TestLedger.bank, TransactionType.EXPENSE, null, received.plusSeconds(60))
        assertEquals(com.example.zar.core.domain.model.RecordSource.SMS, tx.source)
    }

    // --- Screen state and resuming (L10) ----------------------------------------------------

    private fun <T> onMain(block: suspend kotlinx.coroutines.test.TestScope.() -> T) = kotlinx.coroutines.test.runTest {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        try { block() } finally { kotlinx.coroutines.Dispatchers.resetMain() }
    }

    private fun viewModel(f: Fixture) = com.example.zar.features.capture.presentation.viewmodel.AutoCaptureViewModel(
        ObserveAutoCaptureUseCase(f.session, f.preferences, f.staging),
        SetAutoCaptureUseCase(f.session, f.preferences, f.staging, f.scheduler),
        com.example.zar.features.capture.domain.auto.DismissExpiredSmsUseCase(f.session, f.staging),
    )

    @Test
    fun theSwitchShowsOffThenOnWithWhatIsWaiting() = onMain {
        val f = Fixture(alice, null)
        val vm = viewModel(f)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.status.enabled)
        vm.setEnabled(true)
        assertTrue(vm.uiState.value.status.enabled)
        assertFalse(vm.uiState.value.failed)
        f.stage(purchase, received)
        assertEquals(1, vm.uiState.value.status.queue.waiting)
        vm.viewModelScope.cancel()
    }

    @Test
    fun aFailedChangeIsShownAndLeavesItOff() = onMain {
        val f = Fixture(null, null)
        val vm = viewModel(f)
        vm.setEnabled(true)
        assertTrue("Signed out: can't turn it on", vm.uiState.value.failed)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(null, f.preferences.enabledFor.value)
        vm.viewModelScope.cancel()
    }

    @Test
    fun signingBackInResumesOnlyForTheOwner() = onMain {
        val f = Fixture(null, alice)
        val resume = com.example.zar.features.capture.domain.auto.ResumeAutoCaptureUseCase(f.session, f.preferences, f.scheduler)
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { resume() }
        assertEquals("Nobody signed in: nothing scheduled", 0, f.scheduler.soon)
        f.session.user.value = bob
        assertEquals("Someone else: still nothing", 0, f.scheduler.soon)
        f.session.user.value = alice
        assertEquals(1, f.scheduler.soon)
        assertEquals(1, f.scheduler.periodic)
        job.cancel()
    }
}
