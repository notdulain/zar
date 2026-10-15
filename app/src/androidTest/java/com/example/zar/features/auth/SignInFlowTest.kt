package com.example.zar.features.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zar.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SignInFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val timeout = 15_000L

    @Before
    fun startSignedOut() {
        EmulatorFirebase.requireEmulators()
        EmulatorFirebase.auth.signOut()
        compose.waitUntilAtLeastOneExists(hasTestTag("email"), timeout)
    }

    @Test
    fun registerSignOutAndSignInAgain() {
        val email = EmulatorFirebase.newEmail()
        val password = "correct-horse-1"

        // Register: lands in the signed-in app.
        compose.onNodeWithTag("to-register").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("confirm-password"), timeout)
        compose.onNodeWithTag("email").performTextInput(email)
        compose.onNodeWithTag("password").performTextInput(password)
        compose.onNodeWithTag("confirm-password").performTextInput(password)
        compose.onNodeWithTag("submit").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Home is coming soon."), timeout)

        // Settings shows who is signed in; signing out returns to the sign-in screen.
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText(email), timeout)
        compose.onNodeWithTag("sign-out").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("email"), timeout)

        // A wrong password explains what to do next.
        compose.onNodeWithTag("email").performTextInput(email)
        compose.onNodeWithTag("password").performTextInput("wrong-password")
        compose.onNodeWithTag("submit").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("auth-error"), timeout)
        compose.onNodeWithText("Email or password is incorrect", substring = true).assertExists()

        // The right password signs in again.
        compose.onNodeWithTag("password").performTextReplacement(password)
        compose.onNodeWithTag("submit").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Home is coming soon."), timeout)
    }

    @Test
    fun registeringATakenEmailSuggestsSigningIn() {
        val email = EmulatorFirebase.newEmail()
        compose.onNodeWithTag("to-register").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("confirm-password"), timeout)
        compose.onNodeWithTag("email").performTextInput(email)
        compose.onNodeWithTag("password").performTextInput("correct-horse-1")
        compose.onNodeWithTag("confirm-password").performTextInput("correct-horse-1")
        compose.onNodeWithTag("submit").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Home is coming soon."), timeout)

        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("sign-out"), timeout)
        compose.onNodeWithTag("sign-out").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("to-register"), timeout)
        compose.onNodeWithTag("to-register").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("confirm-password"), timeout)
        compose.onNodeWithTag("email").performTextInput(email)
        compose.onNodeWithTag("password").performTextInput("correct-horse-1")
        compose.onNodeWithTag("confirm-password").performTextInput("correct-horse-1")
        compose.onNodeWithTag("submit").performClick()
        compose.waitUntilAtLeastOneExists(hasText("An account already uses this email", substring = true), timeout)
    }

    @Test
    fun invalidInputIsCaughtBeforeFirebase() {
        compose.onNodeWithTag("email").performTextInput("not-an-email")
        compose.onNodeWithTag("submit").performClick()
        compose.onNodeWithText("That doesn’t look like an email address").assertExists()
        compose.onNodeWithText("Enter your password").assertExists()
    }
}
