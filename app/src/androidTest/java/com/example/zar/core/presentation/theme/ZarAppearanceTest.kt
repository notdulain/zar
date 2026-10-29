package com.example.zar.core.presentation.theme

import android.content.res.Configuration
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import com.example.zar.features.settings.domain.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The root resolution of the saved appearance against the (simulated) Android mode, and that switching keeps input. */
class ZarAppearanceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun config(night: Boolean) = Configuration().apply {
        uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }

    private var seenCanvas = ZarLightColors.canvas

    private fun show(appearance: () -> AppearanceMode, androidNight: () -> Boolean) = compose.setContent {
        CompositionLocalProvider(LocalConfiguration provides config(androidNight())) {
            ZarAppearance(appearance()) {
                seenCanvas = ZarTheme.colors.canvas
                var draft by rememberSaveable { mutableStateOf("") }
                BasicTextField(draft, { draft = it }, Modifier.testTag("draft"))
            }
        }
    }

    @Test
    fun explicitModesIgnoreAndroidAndSystemFollowsIt() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        var night by mutableStateOf(true)
        show({ appearance }, { night })

        compose.waitForIdle()
        assertEquals("Light on a dark phone", ZarLightColors.canvas, seenCanvas)

        appearance = AppearanceMode.DARK; night = false
        compose.waitForIdle()
        assertEquals("Dark on a light phone", ZarDarkColors.canvas, seenCanvas)

        appearance = AppearanceMode.SYSTEM
        compose.waitForIdle()
        assertEquals("System follows the light phone", ZarLightColors.canvas, seenCanvas)
        night = true
        compose.waitForIdle()
        assertEquals("System follows the phone turning dark", ZarDarkColors.canvas, seenCanvas)
    }

    @Test
    fun switchingAppearanceKeepsWhatWasTyped() {
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        show({ appearance }, { false })
        compose.onNodeWithTag("draft").performTextInput("Lunch 1,250")

        appearance = AppearanceMode.DARK
        compose.waitForIdle()

        assertEquals(ZarDarkColors.canvas, seenCanvas)
        compose.onNodeWithTag("draft").assertTextEquals("Lunch 1,250")
    }
}
