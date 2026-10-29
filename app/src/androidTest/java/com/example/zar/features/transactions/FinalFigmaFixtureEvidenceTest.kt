package com.example.zar.features.transactions

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.ReportingTotal
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.auth.domain.model.AuthError
import com.example.zar.features.auth.presentation.screen.AuthErrorText
import com.example.zar.features.auth.presentation.screen.PrimaryAuthButton
import com.example.zar.features.transactions.presentation.income.screen.IncomeListContent
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeListUiState
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * Compose FIXTURE screenshots of the real production composables, rendered on the emulator, for states the running app
 * cannot reach on demand with the seeded account (Received empty / couldn't load, Dark auth button and error). These are
 * fixture renders, not app-runtime captures of a flow. Written to the app cache dir `final-figma-fixtures`.
 */
class FinalFigmaFixtureEvidenceTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(500)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "final-figma-fixtures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun income(dark: Boolean, state: IncomeListUiState, name: String) {
        compose.setContent { ZarTheme(darkTheme = dark) { IncomeListContent(state, {}, {}, {}, {}) } }
        capture(name)
    }

    private val empty = IncomeListUiState(isLoading = false, receivedThisMonth = ReportingTotal.zero())
    private val failed = IncomeListUiState(isLoading = false, loadFailed = true)

    @Test fun lightReceivedEmpty() = income(false, empty, "light-received-empty")
    @Test fun darkReceivedEmpty() = income(true, empty, "dark-received-empty")
    @Test fun lightReceivedError() = income(false, failed, "light-received-error")
    @Test fun darkReceivedError() = income(true, failed, "dark-received-error")

    @Test fun authButtonAndErrorLight() = authFixture(false, "light-auth-button-error")
    @Test fun authButtonAndErrorDark() = authFixture(true, "dark-auth-button-error")

    private fun authFixture(dark: Boolean, name: String) {
        compose.setContent {
            ZarTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(com.example.zar.core.presentation.theme.ZarTheme.colors.canvas).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AuthErrorText(AuthError.WrongCredentials())
                    PrimaryAuthButton("Sign in", isLoading = false, onClick = {})
                    PrimaryAuthButton("Sign in", isLoading = true, onClick = {})
                }
            }
        }
        capture(name)
    }
}
