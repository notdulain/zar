package com.example.zar.features.capture

import com.example.zar.core.data.remote.firestore.*
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.domain.repository.LinkWrite
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.accounts.data.repository.AccountDocumentMapper
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*

/** Real Android repository against local Auth/Firestore emulators, project demo-zar. */
class CaptureRepositoryTest {
    companion object {
        private lateinit var client: IsolatedFirebaseApp
        private lateinit var db: FirebaseFirestore
        private var uid: UserId = UserId("not-connected")
        // An isolated client with its own API key: sharing one fixed key with other classes made them share Auth's
        // backend, and this class's app.delete() then left the next class with "FirebaseApp was deleted".
        @JvmStatic @BeforeClass fun connect()=runBlocking {
            client=IsolatedFirebaseApp.open("capture-review-${UUID.randomUUID()}")
            uid=UserId(checkNotNull(client.auth.signInAnonymously().await().user).uid)
            db=client.db
        }
        @JvmStatic @AfterClass fun disconnect() { client.close() }
    }
    private val session=object:UserSessionProvider {
        var current:UserId?=null
        override fun currentUserId()=current
        override fun observeUserId():Flow<UserId?> = flowOf(current)
    }
    private val now=Instant.parse("2026-10-22T04:00:00Z")
    private val sms="Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private fun draft()=CapturedDraftRules.draftFrom(DraftId("capture-${UUID.randomUUID()}"),
        (BankSmsParser().parse(sms) as SmsParseResult.Parsed).sms,DraftSource.SHARED_TEXT,now)
    private fun bank()=Account(AccountId("bank-${UUID.randomUUID()}"),"Bank",AccountType.BANK,Currencies.LKR,
        OpeningBalance.Known(FiatMoney(1000000,Currencies.LKR)),LocalDate.of(2026,10,1))
    private fun userCollection(name:String)=db.collection("users").document(uid.value).collection(name)
    private fun repository():FirestoreDraftRepository { session.current=uid;return FirestoreDraftRepository(db,session) }
    private suspend fun seed(account:Account)=userCollection("accounts").document(account.id.value).set(AccountDocumentMapper.toMap(account,now)).await()
    @Test fun replayPreservesOriginalDraftAndTerminalStatus()=runBlocking {
        val repo=repository();val d=draft()
        assertEquals(d,repo.saveDraft(d))
        assertEquals(d,repo.saveDraft(d.copy(createdAt=now.plusSeconds(60))))
        repo.setStatus(d.id,DraftStatus.DISCARDED)
        assertEquals(DraftStatus.DISCARDED,repo.saveDraft(d).status)
        repo.setStatus(d.id,DraftStatus.DISCARDED)
    }
    @Test fun confirmationIsAtomicAndRetryNeverRewritesTheRecord()=runBlocking {
        val repo=repository();val d=draft();val bank=bank();seed(bank);repo.saveDraft(d)
        val expense=CapturedDraftRules.transactionFrom(d,bank,TransactionType.EXPENSE,SpendingClass.UNKNOWN,now)
        repo.confirmCapturedDraft(d,expense)
        repo.confirmCapturedDraft(d,expense.copy(type=TransactionType.INCOME,spendingClass=null))
        val saved=userCollection("transactions").document(expense.id.value).get().await()
        assertEquals("EXPENSE",saved.getString("type"))
        assertEquals("CONFIRMED",userCollection("drafts").document(d.id.value).get().await().getString("status"))
        assertTrue(runCatching { repo.setStatus(d.id,DraftStatus.DISCARDED) }.isFailure)
    }
    @Test fun changedAccountFailsWithoutClosingDraftAndCanRetry()=runBlocking {
        val repo=repository();val d=draft();val bank=bank();seed(bank.copy(isArchived=true));repo.saveDraft(d)
        val expense=CapturedDraftRules.transactionFrom(d,bank,TransactionType.EXPENSE,SpendingClass.UNKNOWN,now)
        assertTrue(runCatching { repo.confirmCapturedDraft(d,expense) }.isFailure)
        assertFalse(userCollection("transactions").document(expense.id.value).get().await().exists())
        assertEquals("PENDING_REVIEW",userCollection("drafts").document(d.id.value).get().await().getString("status"))
        seed(bank);repo.confirmCapturedDraft(d,expense)
        assertTrue(userCollection("transactions").document(expense.id.value).get().await().exists())
    }
    @Test fun concurrentConfirmationsKeepOneOriginalRecord()=runBlocking {
        val repo=repository();val d=draft();val bank=bank();seed(bank);repo.saveDraft(d)
        val expense=CapturedDraftRules.transactionFrom(d,bank,TransactionType.EXPENSE,SpendingClass.UNKNOWN,now)
        coroutineScope {
            listOf(async { repo.confirmCapturedDraft(d,expense) },async { repo.confirmCapturedDraft(d,expense.copy(type=TransactionType.INCOME,spendingClass=null)) }).awaitAll()
        }
        val saved=userCollection("transactions").document(expense.id.value).get().await()
        assertTrue(saved.exists())
        assertEquals("CONFIRMED",userCollection("drafts").document(d.id.value).get().await().getString("status"))
        session.current=null
        assertTrue(runCatching { repo.saveDraft(draft()) }.exceptionOrNull() is LedgerError.NotSignedIn)
    }

    private suspend fun entryFrom(repo:FirestoreDraftRepository,bank:Account):Transaction {
        val d=draft();repo.saveDraft(d)
        val expense=CapturedDraftRules.transactionFrom(d,bank,TransactionType.EXPENSE,SpendingClass.UNKNOWN,now)
        repo.confirmCapturedDraft(d,expense);return expense
    }
    @Test fun linkingAttachesTheMessageAndClosesTheDraftWithoutPostingMoneyAndReplaysAreSafe()=runBlocking {
        val repo=repository();val bank=bank();seed(bank);val existing=entryFrom(repo,bank)
        val d=draft();repo.saveDraft(d)
        val link=EvidenceLink(EvidenceId(d.id.value),existing.id,now)
        val recordsBefore=userCollection("transactions").get().await().size()
        assertEquals(LinkWrite.SAVED,repo.closeAsLinked(d,link))
        assertEquals(LinkWrite.SAVED,repo.closeAsLinked(d,link))
        assertEquals(existing.id.value,userCollection("evidenceLinks").document(d.id.value).get().await().getString("transactionId"))
        assertEquals("CONFIRMED",userCollection("drafts").document(d.id.value).get().await().getString("status"))
        assertEquals("no money was posted",recordsBefore,userCollection("transactions").get().await().size())
        assertTrue(runCatching { repo.closeAsLinked(d,link.copy(transactionId=TransactionId("other"))) }.isFailure)
        assertEquals(existing.id.value,userCollection("evidenceLinks").document(d.id.value).get().await().getString("transactionId"))
    }
    @Test fun aDraftThatWasDiscardedCannotBeLinked()=runBlocking {
        val repo=repository();val bank=bank();seed(bank);val existing=entryFrom(repo,bank)
        val d=draft();repo.saveDraft(d);repo.setStatus(d.id,DraftStatus.DISCARDED)
        assertTrue(runCatching { repo.closeAsLinked(d,EvidenceLink(EvidenceId(d.id.value),existing.id,now)) }.isFailure)
        assertFalse(userCollection("evidenceLinks").document(d.id.value).get().await().exists())
        assertEquals("DISCARDED",userCollection("drafts").document(d.id.value).get().await().getString("status"))
    }
    @Test fun theMessageDirectionIsStoredWithTheDraftAndReadBack()=runBlocking {
        val repo=repository();val d=draft();repo.saveDraft(d)
        val stored=userCollection("drafts").document(d.id.value).get().await().get("proposals") as List<*>
        assertEquals("OUT",(stored.single() as Map<*,*>)["direction"])
        assertEquals(Direction.OUT,((repo.observeDrafts().first().first{it.id==d.id}.proposals.single()) as DraftProposal.ActualTransaction).direction)
    }

    @Test fun anOfflineLinkReportsQueuedThenAcknowledgedAfterReconnecting() = runBlocking {
        val repo = repository(); val bank = bank(); seed(bank)
        val existing = entryFrom(repo, bank); val d = draft(); repo.saveDraft(d)
        try {
            db.disableNetwork().await()
            assertEquals(LinkWrite.QUEUED, repo.closeAsLinked(d, EvidenceLink(EvidenceId(d.id.value), existing.id, now)))
            assertEquals(LinkWrite.QUEUED, withTimeout(5_000) { repo.observeLinkWrite(d.id, existing.id).first() })
        } finally { db.enableNetwork().await() }
        assertEquals(LinkWrite.SAVED, withTimeout(15_000) { repo.observeLinkWrite(d.id, existing.id).first { it == LinkWrite.SAVED } })
        assertEquals(existing.id.value, userCollection("evidenceLinks").document(d.id.value).get(com.google.firebase.firestore.Source.SERVER).await().getString("transactionId"))
    }

    @Test fun aQueuedLinkRejectedAfterReconnectFailsItsStatusStreamAndRestoresTheDraft() = runBlocking {
        val repo = repository(); val bank = bank(); seed(bank)
        val existing = entryFrom(repo, bank); val d = draft(); repo.saveDraft(d)
        try {
            db.disableNetwork().await()
            assertEquals(LinkWrite.QUEUED, repo.closeAsLinked(d, EvidenceLink(EvidenceId(d.id.value), existing.id, now)))
            // Another device discarded this draft while this device was offline. The emulator's admin REST
            // endpoint simulates that server state without touching the device's queued write.
            withContext(Dispatchers.IO) {
                val url = java.net.URL("http://${IsolatedFirebaseApp.EMULATOR_HOST}:8080/v1/projects/demo-zar/databases/(default)/documents/users/${uid.value}/drafts/${d.id.value}?updateMask.fieldPaths=status")
                val connection = url.openConnection() as java.net.HttpURLConnection
                try {
                    connection.requestMethod = "PATCH"
                    connection.setRequestProperty("Authorization", "Bearer owner")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.use { it.write("""{"fields":{"status":{"stringValue":"DISCARDED"}}}""".toByteArray()) }
                    assertEquals(200, connection.responseCode)
                } finally { connection.disconnect() }
            }
        } finally { db.enableNetwork().await() }
        val result = runCatching {
            withTimeout(15_000) { repo.observeLinkWrite(d.id, existing.id).first { it == LinkWrite.SAVED } }
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(userCollection("evidenceLinks").document(d.id.value).get(com.google.firebase.firestore.Source.SERVER).await().exists())
        assertEquals("DISCARDED", userCollection("drafts").document(d.id.value).get(com.google.firebase.firestore.Source.SERVER).await().getString("status"))
    }

    @Test fun anotherTargetForTheSameMessageIsNotReportedAsThisChoicesSuccess() = runBlocking {
        val repo = repository(); val bank = bank(); seed(bank)
        val existing = entryFrom(repo, bank); val d = draft(); repo.saveDraft(d)
        repo.closeAsLinked(d, EvidenceLink(EvidenceId(d.id.value), existing.id, now))
        val result = runCatching { withTimeout(5_000) { repo.observeLinkWrite(d.id, TransactionId("another-entry")).first() } }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

}
