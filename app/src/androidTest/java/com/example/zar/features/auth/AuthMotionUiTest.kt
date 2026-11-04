package com.example.zar.features.auth

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.presentation.screen.RegisterContent
import com.example.zar.features.auth.presentation.screen.SessionLoadingScreen
import com.example.zar.features.auth.presentation.viewmodel.RegisterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editingAndThemeChangeDoNotReplayEntrance() {
        val email = mutableStateOf("")
        val dark = mutableStateOf(false)
        compose.setContent { ZarTheme(darkTheme = dark.value) {
            RegisterContent(RegisterUiState(email = email.value), { email.value = it }, {}, {}, {}, {})
        } }
        compose.mainClock.advanceTimeBy(1_000)
        val before = compose.onNodeWithText("Create your\naccount").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { email.value = "test@example.com"; dark.value = true }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(before, compose.onNodeWithText("Create your\naccount").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun reducedMotionKeepsInputsImmediatelyUsable() = immediateForm(MotionPolicy(true, true))
    @Test fun disabledAnimationsKeepInputsImmediatelyUsable() = immediateForm(MotionPolicy(false, false))
    private fun immediateForm(policy: MotionPolicy) {
        var typed = ""
        compose.setContent { CompositionLocalProvider(LocalZarMotion provides policy) { ZarTheme {
            RegisterContent(RegisterUiState(), { typed = it }, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("email").performTextInput("a@b.co")
        assertEquals("a@b.co", typed)
    }

    @Test fun restoreErrorOffersRetry() {
        var retries = 0
        compose.setContent { ZarTheme { SessionLoadingScreen(AuthError.Network()) { retries++ } } }
        compose.onNodeWithTag("session-error").assertIsDisplayed()
        compose.onNodeWithTag("session-retry").performClick()
        assertEquals(1, retries)
    }
}
