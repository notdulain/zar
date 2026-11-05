package com.example.zar.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import com.example.zar.core.presentation.evidence.PrScreenshots
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.presentation.screen.SettingsContent
import com.example.zar.features.settings.presentation.viewmodel.PasswordResetPhase
import com.example.zar.features.settings.presentation.viewmodel.PasswordResetUi
import com.example.zar.features.settings.presentation.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The Settings password-reset flow drawn by [SettingsContent] over a fixture state: the row, the confirmation, the
 * sending, failed and sent sheets, and how Back behaves in each. The state is driven by the test here, not by a
 * ViewModel or a backend; `SettingsPasswordResetEmulatorTest` covers the real request against the Auth emulator.
 * Screenshots are fixture renders with a synthetic address, saved only for the pull request.
 */
class SettingsPasswordResetUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val email = "nimal.test@example.com"
    private var state by mutableStateOf(SettingsUiState(isLoading = false, currencyCode = "LKR", email = email))
    private var opened = 0
    private var sends = 0
    private var dismissed = 0

    private fun show(dark: Boolean = false, initial: SettingsUiState = state) {
        state = if (dark) initial.copy(appearance = AppearanceMode.DARK) else initial
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                SettingsContent(
                    uiState = state,
                    onBack = {},
                    onSignOut = {},
                    onConfirmSignOut = {},
                    onDismissWarning = {},
                    onResetPassword = {
                        opened++
                        state = state.copy(passwordReset = PasswordResetUi(email))
                    },
                    onConfirmPasswordReset = {
                        sends++
                        state = state.copy(passwordReset = state.passwordReset?.copy(phase = PasswordResetPhase.Sending, error = null))
                    },
                    onDismissPasswordReset = {
                        dismissed++
                        state = state.copy(passwordReset = null)
                    },
                )
            }
        }
    }

    private fun reset(phase: PasswordResetPhase, error: AuthError? = null) =
        state.copy(passwordReset = PasswordResetUi(email, phase, error))

    @Test
    fun theRowSitsUnderSignedInAsAndOpensTheConfirmationWithoutSending() {
        show()
        compose.onNodeWithTag("settings-email").assertIsDisplayed()
        compose.onNodeWithTag("reset-password-row").assertIsDisplayed().assertTextContains("Password")
        PrScreenshots.capture("settings-reset-1-settings-light")

        compose.onNodeWithTag("reset-password-row").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("reset-password-sheet").assertIsDisplayed()
        compose.onNodeWithTag("reset-password-body").assertTextContains(email, substring = true)
        compose.onNodeWithTag("reset-password-send").assertIsEnabled()
        assertEquals(1, opened)
        assertEquals(0, sends)
        Thread.sleep(600)
        PrScreenshots.capture("settings-reset-2-confirm-light")
    }

    @Test
    fun withoutAnEmailThereIsNoPasswordRow() {
        show(initial = SettingsUiState(isLoading = false, currencyCode = "LKR", email = null))
        compose.onNodeWithTag("reset-password-row").assertDoesNotExist()
    }

    @Test
    fun cancelAndBackCloseTheConfirmationWithoutSending() {
        show()
        compose.onNodeWithTag("reset-password-row").performClick()
        compose.onNodeWithTag("reset-password-cancel").performClick()
        compose.waitUntil(5_000) { dismissed == 1 }
        compose.onNodeWithTag("reset-password-sheet").assertDoesNotExist()

        compose.onNodeWithTag("reset-password-row").performClick()
        compose.waitForIdle()
        pressBack()
        compose.waitUntil(5_000) { dismissed == 2 }
        compose.onNodeWithTag("reset-password-sheet").assertDoesNotExist()
        assertEquals(0, sends)
    }

    @Test
    fun sendingDisablesTheButtonsAndBackIsIgnored() {
        show()
        compose.onNodeWithTag("reset-password-row").performClick()
        compose.onNodeWithTag("reset-password-send").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("reset-password-send").assertIsNotEnabled().assertTextContains("Sending…")
        compose.onNodeWithTag("reset-password-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("reset-password-send").performClick()
        pressBack()
        compose.waitForIdle()

        assertEquals(1, sends)
        assertEquals(0, dismissed)
        compose.onNodeWithTag("reset-password-sheet").assertIsDisplayed()
        Thread.sleep(600)
        PrScreenshots.capture("settings-reset-3-sending-light")
    }

    @Test
    fun aFailedSendShowsTheMessageAndTryAgainSendsOnce() {
        show(initial = reset(PasswordResetPhase.Confirming, AuthError.Network()))
        compose.waitForIdle()

        compose.onNodeWithTag("reset-password-error").assertIsDisplayed()
            .assertTextContains("No internet connection. Connect and try again.")
        compose.onNodeWithTag("reset-password-send").assertTextContains("Try again").performClick()
        compose.waitForIdle()

        assertEquals(1, sends)
        compose.onNodeWithTag("reset-password-error").assertDoesNotExist()
        state = reset(PasswordResetPhase.Confirming, AuthError.Network())
        compose.waitForIdle()
        Thread.sleep(600)
        PrScreenshots.capture("settings-reset-4-error-light")
    }

    @Test
    fun anAccountThatCannotGetAReplyUsesAResetSpecificMessage() {
        show(initial = reset(PasswordResetPhase.Confirming, AuthError.WrongCredentials()))
        compose.onNodeWithTag("reset-password-error")
            .assertTextContains("We couldn't send a link to this account. Try again later.")
    }

    @Test
    fun theSentSheetPointsToTheInboxAndDoneCloses() {
        show(initial = reset(PasswordResetPhase.Sent))
        compose.waitForIdle()

        compose.onNodeWithTag("reset-password-body").assertTextContains(email, substring = true)
        compose.onNodeWithTag("reset-password-send").assertDoesNotExist()
        Thread.sleep(600)
        PrScreenshots.capture("settings-reset-5-sent-light")
        compose.onNodeWithTag("reset-password-done").performClick()
        compose.waitUntil(5_000) { dismissed == 1 }
        compose.onNodeWithTag("reset-password-sheet").assertDoesNotExist()
    }

    @Test
    fun darkThemeShowsEveryPart() {
        show(dark = true, initial = reset(PasswordResetPhase.Confirming, AuthError.TooManyAttempts()))
        compose.waitForIdle()
        compose.onNodeWithTag("reset-password-body").assertIsDisplayed()
        compose.onNodeWithTag("reset-password-error").assertIsDisplayed()
        compose.onNodeWithTag("reset-password-send").assertIsDisplayed()
        compose.onNodeWithTag("reset-password-cancel").assertIsDisplayed()
        Thread.sleep(600)
        PrScreenshots.capture("settings-reset-6-error-dark")
    }

    @Test
    fun theRowIsReachableByScrollingOnASmallWindow() {
        show(dark = true)
        compose.onNodeWithTag("reset-password-row").performScrollTo().assertIsDisplayed()
        PrScreenshots.capture("settings-reset-7-settings-dark")
    }
}
