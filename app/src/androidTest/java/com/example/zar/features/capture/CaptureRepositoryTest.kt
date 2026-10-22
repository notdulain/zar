package com.example.zar.features.capture

import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.data.remote.firestore.*
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.accounts.data.repository.AccountDocumentMapper
import com.example.zar.features.capture.domain.CapturedDraftRules
import com.example.zar.features.capture.domain.model.SmsParseResult
import com.example.zar.features.capture.domain.parser.BankSmsParser
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
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
        private lateinit var app: FirebaseApp
        private lateinit var db: FirebaseFirestore
        private var uid: UserId = UserId("not-connected")
        @JvmStatic @BeforeClass fun connect()=runBlocking {
            app=FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext,
                FirebaseOptions.Builder().setProjectId("demo-zar").setApplicationId("1:123:android:capture-review")
                    .setApiKey("fake-emulator-key").build(),"capture-review-${UUID.randomUUID()}")
            FirebaseAuth.getInstance(app).useEmulator("10.0.2.2",9099)
            uid=UserId(checkNotNull(FirebaseAuth.getInstance(app).signInAnonymously().await().user).uid)
            db=FirebaseFirestore.getInstance(app)
            db.useEmulator("10.0.2.2",8080)
        }
        @JvmStatic @AfterClass fun disconnect() { app.delete() }
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
}
