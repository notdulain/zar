package com.example.zar.features.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.settings.domain.model.AppearanceMode
import com.example.zar.features.settings.presentation.screen.AppearanceSheet
import com.example.zar.features.settings.presentation.screen.SettingsContent
import com.example.zar.features.settings.presentation.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The Settings Appearance control and Reduce motion switch (add-appearance-preference 2.2/2.3). Runs on an emulator;
 * no runtime pass is claimed from a compile-only build.
 */
class SettingsAppearanceUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSettingsRowShowsTheChoiceAndTheReduceMotionSwitchToggles() {
        var reduceMotion: Boolean? = null
        compose.setContent {
            ZarTheme {
                SettingsContent(
                    uiState = SettingsUiState(isLoading = false, currencyCode = "LKR", appearance = AppearanceMode.DARK),
                    onBack = {},
                    onSignOut = {},
                    onConfirmSignOut = {},
                    onDismissWarning = {},
                    onAppearanceSelected = {},
                    onReduceMotionChange = { reduceMotion = it },
                )
            }
        }

        compose.onNodeWithTag("settings-currency").assertIsDisplayed()
        compose.onNodeWithTag("appearance-row").assertTextContains("Dark")
        compose.onNodeWithText("On this phone").assertIsDisplayed()
        compose.onNodeWithTag("settings-reduce-motion").performScrollTo().performClick()
        assertEquals(true, reduceMotion)
    }

    @Test
    fun thePickerIsARadioGroupThatReportsTheChosenMode() {
        var chosen: AppearanceMode? = null
        compose.setContent {
            ZarTheme {
                AppearanceSheet(
                    selected = AppearanceMode.LIGHT,
                    saveFailed = false,
                    onSelect = { chosen = it },
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("appearance-option-light").assertIsSelected()
        compose.onNodeWithTag("appearance-option-dark").assertIsNotSelected()
        compose.onNodeWithTag("appearance-option-system").assertIsNotSelected()

        compose.onNodeWithTag("appearance-option-dark").performClick()
        assertEquals(AppearanceMode.DARK, chosen)
    }

    @Test
    fun aFailedSaveShowsThePreviousChoiceAndARetry() {
        var retried = false
        compose.setContent {
            ZarTheme {
                AppearanceSheet(
                    selected = AppearanceMode.LIGHT,
                    saveFailed = true,
                    onSelect = {},
                    onRetry = { retried = true },
                    onDismiss = {},
                )
            }
        }

        // The previous choice stays selected and the error row offers a repeat.
        compose.onNodeWithTag("appearance-option-light").assertIsSelected()
        compose.onNodeWithTag("appearance-save-failed").assertIsDisplayed()
        compose.onNodeWithTag("appearance-save-retry").performClick()
        assertEquals(true, retried)
    }
}
