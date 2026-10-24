package com.example.zar.features.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.export.domain.model.ExportCounts
import com.example.zar.features.export.domain.model.ExportFile
import com.example.zar.features.export.domain.model.ExportKind
import com.example.zar.features.export.presentation.screen.ExportContent
import com.example.zar.features.export.presentation.viewmodel.ExportMessage
import com.example.zar.features.export.presentation.viewmodel.ExportUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExportUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: ExportUiState, onExport: (ExportKind) -> Unit = {}, onRetry: () -> Unit = {}) {
        compose.setContent {
            ZarTheme { ExportContent(state = state, onBack = {}, onExport = onExport, onRetry = onRetry) }
        }
    }

    private fun ready(transactions: Int, expected: Int, extra: ExportUiState.() -> ExportUiState = { this }) =
        ExportUiState(isLoading = false, counts = ExportCounts(transactions, expected)).extra()

    @Test
    fun eachExportShowsItsRowCountAndExportsItsOwnKind() {
        val asked = mutableListOf<ExportKind>()
        show(ready(24, 3), onExport = { asked += it })

        compose.onNodeWithTag("export-transactions-count").assertTextContains("24 rows")
        compose.onNodeWithTag("export-expected-count").assertTextContains("3 rows")
        compose.onNodeWithTag("export-transactions-button").performScrollTo().performClick()
        compose.onNodeWithTag("export-expected-button").performScrollTo().performClick()
        assertEquals(listOf(ExportKind.TRANSACTIONS, ExportKind.EXPECTED_PAYMENTS), asked)
    }

    @Test
    fun anEmptyExportSaysSoAndCannotBeStarted() {
        show(ready(0, 2))
        compose.onNodeWithTag("export-transactions-count").assertTextContains("Nothing to export yet")
        compose.onNodeWithTag("export-transactions-button").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("export-expected-button").performScrollTo().assertIsEnabled()
    }

    @Test
    fun aSavedExportIsConfirmedByName() {
        val file = ExportFile(ExportKind.TRANSACTIONS, "zar-transactions-2026-10-24.csv", "csv", 24)
        show(ready(24, 0) { copy(saved = file) })
        compose.onNodeWithTag("export-status").performScrollTo().assertTextContains("zar-transactions-2026-10-24.csv", substring = true)
    }

    @Test
    fun aSaveFailureIsExplained() {
        show(ready(24, 0) { copy(message = ExportMessage.SAVE_FAILED) })
        compose.onNodeWithTag("export-status").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theButtonsLockWhileAnExportIsBeingPrepared() {
        show(ready(24, 3) { copy(isWorking = true) })
        compose.onNodeWithTag("export-transactions-button").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("export-expected-button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aLoadFailureOffersRetry() {
        var retried = 0
        show(ExportUiState(isLoading = false, loadFailed = true), onRetry = { retried++ })
        compose.onNodeWithTag("export-retry").performClick()
        assertEquals(1, retried)
    }
}
