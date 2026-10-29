package com.example.zar.features.plans

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.presentation.screen.NewPlanSheet
import com.example.zar.features.plans.presentation.screen.PlansScreenContent
import com.example.zar.features.plans.presentation.viewmodel.PlansCounts
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "+ New plan" and its sheet: every row opens a real flow, the hero is the 420dp Z04 box, and an existing goal is never overwritten. */
class PlansNewPlanUiTest {
    @get:Rule val compose = createComposeRule()

    private var created: Triple<String, String, Any?>? = null
    private var actions = mutableListOf<String>()

    private fun sheet(goalName: String?, hasLimit: Boolean = false) {
        compose.setContent {
            ZarTheme {
                NewPlanSheet(
                    goalName = goalName, hasWeeklyLimit = hasLimit, isSaving = false,
                    onDismiss = { actions += "dismiss" },
                    onCreateGoal = { n, t, d -> created = Triple(n, t, d) },
                    onEditGoal = { actions += "edit-goal" },
                    onSetLimit = { actions += "limit" },
                    onAddBill = { actions += "bill" },
                    onAddProject = { actions += "project" },
                )
            }
        }
    }

    @Test
    fun withNoGoalTheFormCreatesOneAfterValidInput() {
        sheet(goalName = null)
        compose.onNodeWithTag("new-plan-create").assertIsNotEnabled()

        compose.onNodeWithTag("new-plan-name").performTextInput("New phone")
        compose.onNodeWithTag("new-plan-target").performTextInput("150000")
        compose.onNodeWithTag("new-plan-create").assertIsEnabled().performClick()

        assertEquals("New phone", created?.first)
        assertEquals("150000", created?.second)
    }

    @Test
    fun withAGoalTheSheetOffersEditAndNeverACreateForm() {
        sheet(goalName = "MacBook fund")

        compose.onNodeWithTag("new-plan-type-goal").performClick()
        compose.onNodeWithText("MacBook fund is your savings goal").assertIsDisplayed()
        compose.onNodeWithTag("new-plan-create").assertDoesNotExist()
        compose.onNodeWithTag("new-plan-name").assertDoesNotExist()
        compose.onNodeWithTag("new-plan-edit-goal").performClick()

        assertEquals(listOf("edit-goal"), actions)
        assertEquals(null, created)
    }

    @Test
    fun theOtherTypesOpenTheirRealFlows() {
        sheet(goalName = "MacBook fund", hasLimit = false)
        // A goal exists and no limit is set, so the sheet opens on the weekly limit.
        compose.onNodeWithTag("new-plan-set-limit").performClick()
        compose.onNodeWithTag("new-plan-type-bill").performClick()
        compose.onNodeWithTag("new-plan-add-bill").performClick()
        compose.onNodeWithTag("new-plan-type-project").performClick()
        compose.onNodeWithTag("new-plan-add-project").performClick()

        assertEquals(listOf("limit", "bill", "project"), actions)
    }

    @Test
    fun theHeroIsAtLeast420dpTallWithTheNewPlanAction() {
        var opened = 0
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides MotionPolicy(false, true)) {
                    Box(Modifier) {
                        PlansScreenContent(
                            state = PlansUiState(isLoading = false, overview = PlansOverview(null, emptyList())),
                            onNavigate = {}, onAddOption = {}, onRetry = {}, onEditGoal = {}, onAllocate = {},
                            onNewPlan = { opened++ }, counts = PlansCounts(bills = 3, projects = 0),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("plans-hero").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("plans-hero").getUnclippedBoundsInRoot().height >= 420.dp - 0.5.dp)
        compose.onNodeWithTag("plans-new-plan").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("3 bills").assertIsDisplayed()
        compose.onNodeWithText("No projects added").assertIsDisplayed()
        compose.onNodeWithText("Not set · choose an amount").assertIsDisplayed()
    }
}
