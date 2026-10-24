package com.example.zar.features.allowance

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.allowance.domain.model.*
import com.example.zar.features.allowance.presentation.screen.*
import com.example.zar.features.allowance.presentation.viewmodel.*
import com.example.zar.features.home.domain.model.HomeOverview
import com.example.zar.features.home.presentation.screen.HomeScreenContent
import com.example.zar.features.home.presentation.screen.WeeklyLimitCard
import com.example.zar.features.home.presentation.viewmodel.*
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.presentation.screen.PlansScreenContent
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual native production composables, rendered with explicit fixed data. No backend claim. */
class AllowanceEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private fun lkr(major: Long) = FiatMoney(major * 100, Currencies.LKR)
    private val week = LocalDate.of(2026,10,19)..LocalDate.of(2026,10,25)
    private fun overview(spent: Long? = null, warnings: Boolean = false, sync: AllowanceSyncState = AllowanceSyncState.SAVED): AllowanceOverview {
        val settings = AllowanceSettings(spent != null, lkr(8_000))
        return AllowanceOverview(settings, spent?.let { AllowanceStatus(week, lkr(8_000), ReportingTotal(lkr(it), if(warnings) 1 else 0), if(warnings) 2 else 0, if(warnings) 1 else 0) }, sync)
    }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(600)
        val inst = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir = File(inst.targetContext.cacheDir, "pr31").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    private fun assertNoOverflow(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("$tag must be fully readable: size=${layout.size}, width=${layout.multiParagraph.width}, height=${layout.multiParagraph.height}, lines=${layout.lineCount}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}", layout.hasVisualOverflow)
    }
    @Test fun plansOffRemainingOverspentAndSyncRecoveryStates() {
        val fixture = mutableStateOf(overview())
        compose.setContent { ZarTheme(darkTheme = false) {
            PlansScreenContent(PlansUiState(isLoading=false, overview=PlansOverview(null, emptyList())), {}, {}, {}, {}, {},
                allowanceSection = { AllowanceCard(AllowanceUiState(isLoading=false, overview=fixture.value), {}, {}) })
        } }
        compose.onNodeWithTag("allowance-set").performScrollTo().assertIsDisplayed(); capture("01-plans-off")
        compose.runOnIdle { fixture.value = overview(3_800) }
        compose.onNodeWithTag("allowance-edit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("allowance-remaining").assertTextContains("LKR 4,200 left"); capture("02-plans-remaining")
        compose.runOnIdle { fixture.value = overview(9_000, warnings=true) }
        compose.onNodeWithTag("allowance-edit").performScrollTo()
        compose.onNodeWithTag("allowance-warning-rate").assertIsDisplayed()
        compose.onNodeWithTag("allowance-warning-unclassified").assertIsDisplayed()
        compose.onNodeWithTag("allowance-warning-adjustment").assertIsDisplayed(); capture("03-plans-overspent-coverage")
        compose.runOnIdle { fixture.value = overview(3_800, sync=AllowanceSyncState.QUEUED) }
        compose.onNodeWithTag("allowance-sync").performScrollTo().assertTextContains("Saved on this phone", substring=true); capture("04-plans-queued")
        compose.runOnIdle { fixture.value = overview(3_800, sync=AllowanceSyncState.FAILED) }
        compose.onNodeWithTag("allowance-sync").performScrollTo().assertTextContains("try again", substring=true); capture("05-plans-sync-failed")
    }
    @Test fun homeShowsTheSameRemainingAndAnUnclippedOverspendWarning() {
        val fixture = mutableStateOf(overview(3_800))
        val zero = ReportingTotal.zero()
        val home = HomeOverview(YearMonth.of(2026,10), PeriodSummary(zero, SpendingBreakdown(zero,zero,zero),0), emptyList(), true)
        compose.setContent { ZarTheme(darkTheme=false) {
            HomeScreenContent(HomeUiState(home.month, false, HomeContent.Ready(home), allowance=HomeAllowanceContent.Ready(fixture.value)), {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("weekly-limit-card").performScrollTo()
        compose.onNodeWithTag("weekly-limit-value", useUnmergedTree=true).assertTextContains("LKR 4,200 left"); capture("06-home-remaining"); assertNoOverflow("weekly-limit-value")
        compose.runOnIdle { fixture.value = overview(9_000, warnings=true) }
        compose.onNodeWithTag("weekly-limit-card").performScrollTo()
        compose.onNodeWithText("View allowance →", useUnmergedTree=true).performScrollTo()
        assertNoOverflow("weekly-limit-value"); assertNoOverflow("weekly-limit-detail"); capture("07-home-overspent-coverage")
        compose.runOnIdle { fixture.value = overview() }
        compose.onNodeWithTag("weekly-limit-card").performScrollTo()
        compose.onNodeWithTag("weekly-limit-value", useUnmergedTree=true).assertTextContains("Not set"); capture("14-home-off")
    }
    @Test fun narrowHomeCardAtLargeFontKeepsAmountAndCoverageReadable() {
        compose.setContent { ZarTheme(darkTheme=false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
                    Box(Modifier.width(160.dp)) { WeeklyLimitCard(HomeAllowanceContent.Ready(overview(9_000,true)), {}, {}) }
                }
            }
        } }
        assertNoOverflow("weekly-limit-value"); assertNoOverflow("weekly-limit-detail"); capture("08-home-large-font")
    }
    @Test fun editorAllowsIndependentCategoriesAndOneWeekdayAndKeepsActionsReachable() {
        var editor by mutableStateOf(AllowanceEditorState())
        var hideKeyboard: (() -> Unit)? = null
        compose.setContent { ZarTheme(darkTheme=false) {
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            SideEffect { hideKeyboard = { focus.clearFocus(force=true); keyboard?.hide() } }
            AllowanceEditorDialog(editor,false,null,{editor=editor.copy(enabled=it)},{editor=editor.copy(amount=it)},
                {id->editor=editor.copy(categoryIds=if(id in editor.categoryIds) editor.categoryIds-id else editor.categoryIds+id)},
                {editor=editor.copy(weekStart=it)}, {}, {})
        } }
        compose.onNodeWithContentDescription("Use a weekly allowance").assertIsOn()
        capture("09-editor-empty")
        compose.onNodeWithTag("allowance-amount").performTextInput("8000")
        compose.runOnIdle { hideKeyboard?.invoke() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("allowance-category-groceries").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("allowance-category-coffee-dining").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("allowance-category-groceries").assertIsOn()
        compose.onNodeWithTag("allowance-amount").performScrollTo(); capture("10-editor-amount-categories")
        compose.onNodeWithTag("allowance-week-sunday").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("allowance-week-monday").assertIsNotSelected()
        compose.onNodeWithTag("plans-save").performScrollTo().assertIsEnabled(); capture("11-editor-week-picker")
        assertEquals(DayOfWeek.SUNDAY, editor.weekStart)
        assertEquals(setOf("groceries","coffee-dining"),editor.categoryIds)
        compose.onNodeWithContentDescription("Use a weekly allowance").performScrollTo().performClick().assertIsOff()
        assertEquals("8000", editor.amount)
        capture("15-editor-off-values-kept")
    }
    @Test fun savingBlocksChangesAndSaveFailureKeepsTheEnteredChoice() {
        val saving = mutableStateOf(true)
        val error = mutableStateOf<AllowanceMessage?>(null)
        compose.setContent { ZarTheme(darkTheme=false) {
            AllowanceEditorDialog(AllowanceEditorState(amount="8000",categoryIds=setOf("groceries"),weekStart=DayOfWeek.SUNDAY),
                saving.value,error.value,{},{},{},{},{},{})
        } }
        compose.onNodeWithTag("allowance-amount").assertIsNotEnabled()
        compose.onNodeWithTag("allowance-week-sunday").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("plans-save").performScrollTo().assertIsNotEnabled(); capture("12-editor-saving")
        compose.runOnIdle { saving.value=false; error.value=AllowanceMessage.SAVE_FAILED }
        compose.onNodeWithTag("allowance-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("plans-save").assertIsEnabled(); capture("13-editor-save-failed")
    }
}
