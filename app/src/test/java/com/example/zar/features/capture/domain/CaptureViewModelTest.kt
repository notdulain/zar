package com.example.zar.features.capture.domain

import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.repository.DraftRepository
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.example.zar.features.capture.domain.usecase.*
import com.example.zar.features.capture.presentation.SharedTextInbox
import com.example.zar.features.capture.presentation.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val sms="Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private class Drafts : DraftRepository {
        val values=MutableStateFlow<List<Draft>>(emptyList())
        var gate: CompletableDeferred<Unit>?=null
        override fun observeDrafts()=values
        override fun observePendingDrafts()=values.map { it.filter { d -> d.status==DraftStatus.PENDING_REVIEW } }
        override suspend fun saveDraft(draft:Draft):Draft { gate?.await(); values.value=values.value+draft;return draft }
        override suspend fun confirmCapturedDraft(draft:Draft,transaction:Transaction) { }
        override suspend fun setStatus(id:DraftId,status:DraftStatus) { }
        override suspend fun closeAsLinked(draft:Draft,link:EvidenceLink)=LinkWrite.SAVED
    }
    @Before fun setup()=Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown()=Dispatchers.resetMain()
    @Test fun sharesArrivingWhileAlreadyOpenAndBusyAreReadThenCleared()=runTest {
        val drafts=Drafts();val inbox=SharedTextInbox()
        val vm=CaptureViewModel(inbox,CaptureTextUseCase(BankSmsParser(),drafts),ObserveCapturedDraftsUseCase(drafts),ObserveCaptureHealthUseCase(drafts))
        try {
            vm.setActive(true)
            drafts.gate=CompletableDeferred()
            inbox.offer(sms)
            assertTrue(vm.uiState.value.isReading)
            inbox.offer(sms.replace("800.00","900.00"))
            inbox.offer(sms.replace("800.00","1000.00"))
            drafts.gate!!.complete(Unit)
            runCurrent()
            assertEquals(3,drafts.values.value.size)
            assertEquals("",vm.uiState.value.text)
            assertFalse(inbox.hasText.value)
            inbox.offer("Code 123456 is your One Time Password")
            runCurrent()
            assertEquals("",vm.uiState.value.text)
            assertTrue(vm.uiState.value.outcome is CaptureOutcome.Ignored)
            vm.setActive(false)
            inbox.offer(sms)
            assertTrue(inbox.hasText.value)
            assertEquals(3,drafts.values.value.size)
            vm.setActive(true)
            runCurrent()
            assertFalse(inbox.hasText.value)
        } finally { vm.viewModelScope.cancel() }
    }
    @Test fun signOutDropsUnconsumedText()=runTest {
        val inbox=SharedTextInbox();inbox.offer(sms)
        inbox.onSignOut()
        assertNull(inbox.take());assertFalse(inbox.hasText.value)
    }
}
