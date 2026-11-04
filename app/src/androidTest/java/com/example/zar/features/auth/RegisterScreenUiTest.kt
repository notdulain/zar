package com.example.zar.features.auth

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.CredentialProblem
import com.example.zar.features.auth.presentation.screen.RegisterContent
import com.example.zar.features.auth.presentation.viewmodel.RegisterUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated production-composable fixtures. Never signs out or writes to the phone's account. */
class RegisterScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private var submits = 0
    private fun show(state: RegisterUiState = RegisterUiState(), dark: Boolean = false) {
        compose.setContent { ZarTheme(darkTheme = dark) {
            RegisterContent(state, {}, {}, {}, { submits++ }, {})
        } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "task5-fixtures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun lightIdleMatchesRegistrationStructure() {
        show()
        compose.onNodeWithText("Create your\naccount").assertIsDisplayed()
        compose.onNodeWithTag("email").assertIsDisplayed()
        compose.onNodeWithTag("password").assertIsDisplayed()
        compose.onNodeWithTag("confirm-password").assertIsDisplayed()
        compose.onNodeWithTag("submit").performScrollTo().performClick()
        assertEquals(1, submits)
        capture("register-light")
    }
    @Test fun darkInlineErrorsRemainReadable() {
        show(RegisterUiState(email = "test@example.com", password = "short", confirmation = "other",
            passwordProblem = CredentialProblem.PASSWORD_TOO_SHORT, confirmationProblem = CredentialProblem.PASSWORDS_DIFFER), dark = true)
        compose.onNodeWithTag("password-error").assertIsDisplayed()
        compose.onNodeWithTag("confirm-password-error").assertIsDisplayed()
        capture("register-errors-dark")
    }
    @Test fun busyDisablesSubmitAndBothPasswordToggles() {
        show(RegisterUiState(isSubmitting = true), dark = true)
        compose.onNodeWithTag("submit").assertIsNotEnabled()
        compose.onNodeWithTag("password-toggle").assertIsNotEnabled()
        compose.onNodeWithTag("confirmation-toggle").assertIsNotEnabled()
    }
    @Test fun passwordToggleAndImeNextKeepConfirmationReachable() {
        show(RegisterUiState(password = "example-password"))
        compose.onNodeWithTag("password-toggle").performClick()
        compose.onNodeWithTag("password").assertTextEquals("example-password")
        compose.onNodeWithTag("password").performClick().performImeAction()
        compose.onNodeWithTag("confirm-password").assertIsFocused().performScrollTo().assertIsDisplayed()
        capture("register-keyboard")
    }
}
