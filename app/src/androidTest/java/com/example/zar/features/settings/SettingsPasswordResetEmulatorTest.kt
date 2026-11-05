package com.example.zar.features.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zar.MainActivity
import com.example.zar.core.presentation.evidence.PrScreenshots
import com.example.zar.features.auth.EmulatorFirebase
import com.example.zar.features.auth.IsolatedFirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app, signed in with a throwaway account on the local Firebase Auth emulator (project demo-zar): the Settings
 * reset asks Firebase for one password-reset email, and the emulator's out-of-band code list is the proof. No email
 * leaves the machine and the live project is never touched. Run with `-Pzar.useEmulators=true` while
 * `firebase emulators:start --only auth,firestore` is up.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsPasswordResetEmulatorTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val timeout = 20_000L

    @Before
    fun startSignedOut() {
        EmulatorFirebase.requireEmulators()
        compose.waitUntilAtLeastOneExists(hasTestTag("email") or hasTestTag("nav-more"), timeout)
        FirebaseAuth.getInstance().signOut()
        compose.waitUntilAtLeastOneExists(hasTestTag("email"), timeout)
    }

    @Test
    fun confirmingFromSettingsRequestsExactlyOneResetEmailAndKeepsYouSignedIn() {
        val email = EmulatorFirebase.newEmail()
        val password = "correct-horse-1"
        compose.onNodeWithTag("to-register").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("confirm-password"), timeout)
        compose.onNodeWithTag("email").performTextInput(email)
        compose.onNodeWithTag("password").performTextInput(password)
        compose.onNodeWithTag("confirm-password").performTextInput(password)
        compose.onNodeWithTag("submit").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("nav-more"), timeout)
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithTag("open-settings").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("reset-password-row"), timeout)
        assertEquals(0, resetCodesFor(email))

        compose.onNodeWithTag("reset-password-row").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("reset-password-send"), timeout)
        assertEquals("Opening the sheet must not send anything", 0, resetCodesFor(email))
        Thread.sleep(700)
        PrScreenshots.capture("settings-reset-e2e-1-confirm")

        // A quick double tap still asks Firebase once.
        compose.onNodeWithTag("reset-password-send").performClick()
        runCatching { compose.onNodeWithTag("reset-password-send").performClick() }
        compose.waitUntilAtLeastOneExists(hasTestTag("reset-password-done"), timeout)
        Thread.sleep(700)
        PrScreenshots.capture("settings-reset-e2e-2-sent")
        assertEquals(1, resetCodesFor(email))

        compose.onNodeWithTag("reset-password-done").performClick()
        compose.waitUntil(timeout) { compose.onAllNodes(hasTestTag("reset-password-sheet")).fetchSemanticsNodes().isEmpty() }
        // Still signed in, and the account is untouched.
        assertNotNull(FirebaseAuth.getInstance().currentUser)
        assertEquals(email, FirebaseAuth.getInstance().currentUser?.email)
        compose.onNodeWithTag("sign-out").assertExists()
    }

    /** How many password-reset codes the Auth emulator has issued for [email]. */
    private fun resetCodesFor(email: String): Int {
        val url = URL("http://${IsolatedFirebaseApp.EMULATOR_HOST}:${IsolatedFirebaseApp.AUTH_PORT}/emulator/v1/projects/demo-zar/oobCodes")
        val connection = url.openConnection() as HttpURLConnection
        try {
            check(connection.responseCode == 200) { "oobCodes read failed: ${connection.responseCode}" }
            val codes = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("oobCodes") ?: return 0
            return (0 until codes.length()).count {
                val code = codes.getJSONObject(it)
                code.optString("email") == email && code.optString("requestType") == "PASSWORD_RESET"
            }
        } finally {
            connection.disconnect()
        }
    }
}
