package com.example.zar.features.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.presentation.screen.SignInContent
import com.example.zar.features.auth.presentation.viewmodel.SignInUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Figma sign-in states on the real composable: idle, error, field problem, reset sent, loading, and the eye toggle. */
class SignInScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private var submits = 0
    private var resets = 0
    private var registers = 0
    private var typedEmail = ""

    private fun show(state: SignInUiState, dark: Boolean = false) {
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                SignInContent(
                    state = state,
                    onEmailChange = { typedEmail = it }, onPasswordChange = {},
                    onSubmit = { submits++ }, onForgotPassword = { resets++ }, onCreateAccount = { registers++ },
                )
            }
        }
    }

    @Test
    fun idleStateShowsHeadlineFieldsAndActions() {
        show(SignInUiState())
        compose.onNodeWithText("Sign in to\nyour account").assertIsDisplayed()
        compose.onNodeWithTag("email").assertIsDisplayed().performTextInput("a@b.co")
        assertEquals("a@b.co", typedEmail)
        compose.onNodeWithTag("submit").assertIsDisplayed().performClick()
        compose.onNodeWithTag("forgot-password").performClick()
        compose.onNodeWithTag("to-register").performClick()
        assertEquals(listOf(1, 1, 1), listOf(submits, resets, registers))
    }

    @Test
    fun wrongPasswordShowsTheMessageWithItsLiveRegion() {
        show(SignInUiState(email = "dulain@example.com", password = "x", error = AuthError.WrongCredentials()))
        compose.onNodeWithTag("auth-error").assertIsDisplayed()
            .assertTextEquals("Email or password is incorrect. Try again, or reset your password.")
        compose.onNodeWithText("EMAIL").assertIsDisplayed()
    }

    @Test
    fun aFieldProblemAndTheResetConfirmationUseTheSameMessageRow() {
        show(SignInUiState(emailProblem = CredentialProblem.EMAIL_INVALID))
        compose.onNodeWithTag("auth-error").assertIsDisplayed()
    }

    @Test
    fun resetSentConfirmsTheAddress() {
        show(SignInUiState(email = "dulain@example.com", resetSentTo = "dulain@example.com"))
        compose.onNodeWithTag("reset-sent").assertIsDisplayed()
    }

    @Test
    fun loadingDisablesTheFormAndRelabelsTheButton() {
        show(SignInUiState(email = "a@b.co", password = "pw", isSubmitting = true), dark = true)
        compose.onNodeWithText("Signing in…").assertIsDisplayed()
        compose.onNodeWithTag("submit").assertIsNotEnabled()
        compose.onNodeWithTag("forgot-password").assertIsNotEnabled()
        compose.onNodeWithTag("to-register").assertIsNotEnabled()
    }
}
