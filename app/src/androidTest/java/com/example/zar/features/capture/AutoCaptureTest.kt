package com.example.zar.features.capture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.model.UserId
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.capture.data.auto.CaptureStagingDatabase
import com.example.zar.features.capture.data.auto.RoomSmsStagingRepository
import com.example.zar.features.capture.data.auto.SmsCipher
import com.example.zar.features.capture.domain.auto.AutoCaptureRules
import com.example.zar.features.capture.domain.auto.AutoCaptureStatus
import com.example.zar.features.capture.domain.auto.StagedMessage
import com.example.zar.features.capture.domain.auto.StagingQueue
import com.example.zar.features.capture.presentation.screen.AutoCaptureCard
import com.example.zar.features.capture.presentation.viewmodel.AutoCaptureUiState
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The real Room staging and Keystore encryption on a device, and the opt-in card's states. */
class AutoCaptureTest {
    @get:Rule val compose = createComposeRule()

    private val sms = "Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 11/10/26 10:49 AM has been authorised on your debit card ending #1111."
    private val alice = UserId("alice")
    private val received = Instant.parse("2026-10-11T05:19:00Z")

    @Test
    fun stagedTextIsEncryptedOnDiskAndDeletedOnceProcessed() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "staging-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, CaptureStagingDatabase::class.java, name).build()
        val repository = RoomSmsStagingRepository(db.stagedSms(), SmsCipher())
        val id = AutoCaptureRules.stagingIdFor(alice, sms)

        assertTrue(repository.stage(StagedMessage(id, alice, sms, received)))
        assertFalse("Staged once", repository.stage(StagedMessage(id, alice, sms, received)))
        assertEquals(sms, repository.waiting(alice).single().text)
        assertTrue("Another user can't read it", repository.waiting(UserId("bob")).isEmpty())

        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val onDisk = context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Plain text never reaches the file", onDisk.contains("CAFE SEVENTY THREE"))

        repository.clear(id)
        assertEquals(StagingQueue(), repository.observe(alice).first())
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun expiredMessagesLoseTheirTextButKeepANotice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CaptureStagingDatabase::class.java).build()
        val repository = RoomSmsStagingRepository(db.stagedSms(), SmsCipher())
        repository.stage(StagedMessage(AutoCaptureRules.stagingIdFor(alice, sms), alice, sms, received))
        assertEquals(1, repository.expire(AutoCaptureRules.expiryCutoff(received.plus(AutoCaptureRules.EXPIRY).plusSeconds(1))))
        assertTrue(repository.waiting(alice).isEmpty())
        assertEquals(StagingQueue(0, listOf(received)), repository.observe(alice).first())
        repository.dismissExpired(alice)
        assertEquals(StagingQueue(), repository.observe(alice).first())
        db.close()
    }

    private fun card(state: AutoCaptureUiState, granted: Boolean = true, refused: Boolean = false, onToggle: (Boolean) -> Unit = {}, onAllow: () -> Unit = {}) {
        compose.setContent { ZarTheme(darkTheme = false) { AutoCaptureCard(state, true, granted, refused, onToggle, onAllow, {}) } }
    }

    @Test
    fun offExplainsAndAsksOnlyWhenSwitchedOn() {
        var toggled: Boolean? = null
        card(AutoCaptureUiState(isLoading = false), granted = false, onToggle = { toggled = it })
        compose.onNodeWithTag("auto-capture-switch").assertIsOff().performClick()
        assertEquals(true, toggled)
        compose.onNodeWithText("Past messages are never read", substring = true).assertIsDisplayed()
    }

    @Test
    fun onShowsWaitingMessagesAndExpiryNotice() {
        card(AutoCaptureUiState(isLoading = false, status = AutoCaptureStatus(true, StagingQueue(2, listOf(received)))))
        compose.onNodeWithTag("auto-capture-switch").assertIsOn()
        compose.onNodeWithTag("auto-capture-waiting").assertIsDisplayed()
        compose.onNodeWithTag("auto-capture-expired").assertIsDisplayed()
    }

    @Test
    fun revokedAccessOffersToAllowAgain() {
        var allowed = false
        card(AutoCaptureUiState(isLoading = false, status = AutoCaptureStatus(true)), granted = false, onAllow = { allowed = true })
        compose.onNodeWithText("On, but SMS access is off").assertIsDisplayed()
        compose.onNodeWithText("Allow SMS access").performClick()
        assertTrue(allowed)
    }
}
