package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.PlansOverview
import com.example.zar.features.plans.domain.model.SavingsGoal
import com.example.zar.features.plans.presentation.screen.PlansScreenContent
import com.example.zar.features.plans.presentation.viewmodel.PlansUiState
import com.example.zar.features.settings.presentation.screen.MoreScreen
import com.example.zar.features.transactions.domain.activity.ActivityQuery
import com.example.zar.features.transactions.domain.activity.model.ActivityFilter
import com.example.zar.features.transactions.domain.activity.model.ActivitySnapshot
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityContent
import com.example.zar.features.transactions.presentation.activity.viewmodel.ActivityUiState
import com.example.zar.features.transactions.presentation.screen.ActivityActions
import com.example.zar.features.transactions.presentation.screen.ActivityBody
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Activity, Plans and More top cards collapse into the status bar like Home's (see HomeCollapse): the text group drifts at
 * 0.30 of the scroll, lines fade and stop taking taps, a pinned band takes over, and under reduced motion nothing drifts.
 */
class CollapsingHeroesUiTest {
    @get:Rule val compose = createComposeRule()
    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)
    private val today = LocalDate.of(2026, 10, 21)
    private fun money(major: Long) = FiatMoney(major * 100, Currencies.LKR)

    private fun host(policy: MotionPolicy, content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        ZarTheme {
            CompositionLocalProvider(LocalZarMotion provides policy) {
                // A fixed phone-size frame so the fixtures always overflow the screen.
                Box(Modifier.size(width = 412.dp, height = 732.dp)) { content() }
            }
        }
    }

    private fun showPlans(policy: MotionPolicy) = host(policy) {
        val goal = SavingsGoal(GoalId("fund"), "MacBook fund", money(490_000))
        PlansScreenContent(PlansUiState(isLoading = false, overview = PlansOverview(goal, emptyList())), {}, {}, {}, {}, {}, allowanceSection = { Box(Modifier.size(600.dp)) })
    }

    private fun showMore(policy: MotionPolicy) = host(policy) {
        MoreScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    private fun showActivity(policy: MotionPolicy) {
        val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(money(0)), today.minusDays(60))
        val entries = (1..30).map { i ->
            val t = Transaction(
                TransactionId("e$i"), bank.id, TransactionType.EXPENSE, money(100L + i), today, ReportingConversion.SameCurrency,
                spendingClass = SpendingClass.DISCRETIONARY, merchant = "Purchase $i", createdAt = Instant.parse("2026-10-21T10:00:00Z").plusSeconds(i.toLong()),
            )
            LedgerEntry(t, t, emptyList(), null, emptyList())
        }
        val snapshot = ActivitySnapshot(entries, listOf(bank))
        host(policy) {
            ZarScreenScaffold(TopLevelDestination.Activity, {}, {}, heroUnderStatusBar = true) { padding ->
                ActivityBody(
                    ActivityUiState(ActivityFilter(), ActivityContent.Ready(ActivityQuery.apply(snapshot, ActivityFilter(), today)), snapshot.accounts, true, today),
                    ActivityActions({}, {}, {}, {}, {}, {}, { 0 }),
                    contentPadding = padding,
                )
            }
        }
    }

    private fun scrollBy(px: Float) {
        compose.onAllNodes(hasScrollAction()).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value

    /** The tag's text group follows at 0.30 of the scroll, so after [px] it has moved up by 0.70 of [px]. */
    private fun drifts(tag: String, fadesAway: Boolean) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val rest = top(tag)
        val px = 60f
        scrollBy(px)
        assertEquals("text group drifts", rest - 0.70f * px / compose.density.density, top(tag), 1.5f)
        if (fadesAway) {
            scrollBy(2000f)
            compose.onNodeWithTag(tag).assertDoesNotExist()
            scrollBy(-4000f)
            compose.onNodeWithTag(tag).assertIsDisplayed()
            assertEquals("back at rest after reverse scroll", rest, top(tag), 1.5f)
        }
    }

    private fun bandIsSolid(tag: String) {
        scrollBy(2000f)
        val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val c = image[image.width / 2, image.height / 2]
        assertTrue("pinned band is #05070B once the card has gone, got $c", c.red < 0.05f && c.green < 0.06f && c.blue < 0.08f)
    }

    @Test fun plansTextDriftsFadesAndComesBack() { showPlans(full); compose.waitForIdle(); com.example.zar.core.presentation.evidence.PrScreenshots.capture("plans-hero"); drifts("plans-backed", fadesAway = true) }
    @Test fun moreTextDriftsFadesAndComesBack() { showMore(full); compose.waitForIdle(); com.example.zar.core.presentation.evidence.PrScreenshots.capture("more-hero"); drifts("more-title", fadesAway = true) }
    @Test fun activityTextDriftsFadesAndComesBack() { showActivity(full); drifts("activity-received", fadesAway = false) }

    @Test fun activityTotalsFadeAway() {
        showActivity(full)
        compose.onNodeWithTag("activity-received").assertIsDisplayed()
        scrollBy(2000f)
        compose.onNodeWithTag("activity-received").assertDoesNotExist()
        scrollBy(-4000f)
        compose.onNodeWithTag("activity-received").assertIsDisplayed()
    }

    @Test fun plansPinsTheStatusBand() { showPlans(full); bandIsSolid("plans-status-band") }
    @Test fun morePinsTheStatusBand() { showMore(full); bandIsSolid("more-status-band") }
    @Test fun activityPinsTheStatusBand() { showActivity(full); bandIsSolid("activity-status-band") }

    @Test fun reducedMotionScrollsTheTextOneForOne() {
        showPlans(reduced)
        val rest = top("plans-backed")
        scrollBy(60f)
        assertEquals("spatial zero: the group scrolls 1:1", rest - 60f / compose.density.density, top("plans-backed"), 1.5f)
    }
}
