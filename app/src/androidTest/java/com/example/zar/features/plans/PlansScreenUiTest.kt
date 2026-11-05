package com.example.zar.features.plans

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.core.domain.calculation.AccountBalanceCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.plans.domain.model.*
import com.example.zar.features.plans.presentation.screen.GoalEditorDialog
import com.example.zar.features.plans.presentation.screen.PlansScreenContent
import com.example.zar.features.plans.presentation.viewmodel.GoalEditorState
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlansScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 20)
    private fun money(major: Long) = FiatMoney(major * 100, Currencies.LKR)
    private val account = Account(AccountId("savings"), "Savings", AccountType.BANK, Currencies.LKR,
        OpeningBalance.Known(money(4_000)), today.minusDays(10))
    private val goal = SavingsGoal(GoalId(MACBOOK_GOAL_ID), "MacBook fund", money(20_000),
        listOf(GoalAllocation(account.id, money(8_000))), today.plusDays(14))

    @Test fun backedAndAllocatedStayDistinctAndActionsWork() {
        var edits = 0
        var allocations = 0
        show(overview(), onEdit = { edits++ }, onAllocate = { allocations++ })
        compose.onNodeWithTag("plans-backed").assertTextEquals("LKR 4,000")
        compose.onNodeWithText("Allocated LKR 8,000").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("plans-backed").performScrollTo()
        compose.onNodeWithTag("plans-change-main").performClick()
        compose.onNodeWithTag("plans-set-aside").performClick()
        assertEquals(1, edits)
        assertEquals(1, allocations)
        screenshot("u05-plans-shortfall.png")
        compose.onNodeWithTag("plans-weekly-pace").performScrollTo().assertTextContains("LKR 8,000", substring = true)
        screenshot("u05-plans-pace.png")
    }

    @Test fun mainGoalLineSitsCloseUnderTheGoalNameAndChangeKeepsItsTouchTarget() {
        var changes = 0
        show(overview(), onEdit = { changes++ })
        val name = compose.onNodeWithText("MacBook fund").getUnclippedBoundsInRoot()
        val mainGoal = compose.onNodeWithText("Main goal").getUnclippedBoundsInRoot()
        assertEquals("no gap between the two lines", 0f, (mainGoal.top - name.bottom).value, 1f)
        // Shorter than 48dp on screen, still tappable across 48dp.
        val change = compose.onNodeWithTag("plans-change-main").getUnclippedBoundsInRoot()
        val height = change.bottom - change.top
        assertEquals(true, height < 48.dp)
        val reach = with(compose.density) { ((48.dp - height) / 2 - 2.dp).toPx() }
        compose.onNodeWithTag("plans-change-main").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, -reach)) }
        compose.waitForIdle()
        assertEquals(1, changes)
    }

    @Test fun yourPlansCardsSitCloseUnderTheirHeadingAndShareAHeight() {
        val short = SavingsGoal(GoalId("phone"), "New phone", money(120_000), emptyList(), today.plusDays(90))
        val long = SavingsGoal(GoalId("fund"), "Emergency fund for the whole family", money(50_000), listOf(GoalAllocation(account.id, money(1_000))), null)
        val goals = listOf(goal, short, long)
        var seeAll = 0
        compose.setContent {
            ZarTheme(darkTheme = false) {
                PlansScreenContent(
                    PlansUiState(isLoading = false, overview = PlansOverview(goal, emptyList(), null, goals, GoalBackingCalculator.calculateAll(goals, listOf(account), emptyList(), today))),
                    {}, {}, {}, {}, {}, onSeeAll = { seeAll++ },
                )
            }
        }
        val first = compose.onNodeWithTag("plan-card-phone").performScrollTo().getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("plan-card-fund").getUnclippedBoundsInRoot()
        assertEquals("same height with a two-line name beside a one-line name", (first.bottom - first.top).value, (second.bottom - second.top).value, 1f)
        val header = compose.onNodeWithTag("plans-see-all").getUnclippedBoundsInRoot()
        assertEquals("10dp from the heading to the cards", 10f, (first.top - header.bottom).value, 2f)
        compose.onNodeWithTag("plans-see-all").performClick()
        assertEquals(1, seeAll)
    }

    @Test fun emptyStateCreatesGoal() {
        var edits = 0
        show(PlansOverview(null, emptyList()), onEdit = { edits++ })
        compose.onNodeWithTag("plans-set-aside").performClick()
        assertEquals(1, edits)
        screenshot("u05-plans-empty-after.png")
    }

    @Test fun unknownBalanceAndPaceRemainReachableAtLargeText() {
        show(overview(account.copy(openingBalance = OpeningBalance.Unknown)), fontScale = 2f, narrow = true)
        compose.onNodeWithTag("plans-balance-warning").performScrollTo().assertTextContains("no known balance", substring = true)
        compose.onNodeWithTag("plans-weekly-pace").performScrollTo().assertTextContains("when backing can be valued", substring = true)
        // Content scrolls behind the floating nav; the end of the list must still come to rest clear of it.
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        org.junit.Assert.assertTrue("Pace must scroll clear of floating navigation",
            compose.onNodeWithTag("plans-weekly-pace").getUnclippedBoundsInRoot().bottom <=
                compose.onNodeWithTag("nav-more").getUnclippedBoundsInRoot().top)
        screenshot("u05-plans-large-text.png")
        compose.onNodeWithTag("nav-more").assertIsDisplayed()
    }

    @Test fun darkThemeShowsEstimatedBackingAndPace() {
        show(overview(account.copy(openingBalance = OpeningBalance.Approximate(money(4_000)))), dark = true)
        compose.onNodeWithText("Estimate: a backing account has an approximate opening balance.").assertIsDisplayed()
        screenshot("u05-plans-dark.png")
        compose.onNodeWithTag("plans-weekly-pace").performScrollTo().assertTextContains("LKR 8,000", substring = true)
    }

    @Test fun dateCanBeRemovedAndChosenBeforeSaving() {
        var savedDate: LocalDate? = null
        compose.setContent {
            var editor by remember { mutableStateOf(GoalEditorState(targetDate = today.plusDays(14))) }
            ZarTheme(darkTheme = false) {
                GoalEditorDialog(editor, false, null,
                    { editor = editor.copy(name = it) }, { editor = editor.copy(target = it) },
                    { editor = editor.copy(targetDate = it) }, {}, { savedDate = editor.targetDate })
            }
        }
        screenshot("u05-goal-date-editor.png", "plans-editor")
        compose.onNodeWithText("Remove target date").performClick()
        compose.onNodeWithTag("plans-target-date").assertTextEquals("Set target date").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("plans-save").performScrollTo().performClick()
        org.junit.Assert.assertNotNull(savedDate)
    }

    @Test fun savingDisablesEditorActions() {
        compose.setContent {
            ZarTheme {
                GoalEditorDialog(GoalEditorState(), true, null, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("plans-save").assertIsNotEnabled()
        compose.onNodeWithTag("plans-target-date").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    private fun overview(backing: Account = account) = PlansOverview(goal,
        listOf(AccountBalanceCalculator.calculate(backing, emptyList(), today)),
        GoalBackingCalculator.calculate(goal, listOf(backing), emptyList(), today))

    private fun show(overview: PlansOverview, fontScale: Float = 1f, narrow: Boolean = false,
        dark: Boolean = false, onEdit: () -> Unit = {}, onAllocate: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ZarTheme(darkTheme = dark) {
                    Box(if (narrow) Modifier.width(320.dp) else Modifier) {
                        PlansScreenContent(PlansUiState(isLoading = false, overview = overview), {}, {}, {}, onEdit, onAllocate, onChangeMain = onEdit)
                    }
                }
            }
        }
    }

    private fun screenshot(name: String, tag: String? = null) {
        val bitmap = (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
