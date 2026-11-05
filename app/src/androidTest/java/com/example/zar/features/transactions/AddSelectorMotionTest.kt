package com.example.zar.features.transactions

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.presentation.components.SelectorIndicatorState
import com.example.zar.core.presentation.components.rememberSelectorIndicator
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormActions
import com.example.zar.features.transactions.presentation.income.screen.IncomeFormContent
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormUiState
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryActions
import com.example.zar.features.transactions.presentation.manual.screen.ManualEntryContent
import com.example.zar.features.transactions.presentation.manual.screen.Segmented
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryKind
import com.example.zar.features.transactions.presentation.manual.viewmodel.ManualEntryUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Add transaction selectors on a controlled Compose clock: nothing advances until the test says so, so the
 * intermediate frames, an interruption, repeat taps and the reduced/instant paths are deterministic. Frames are 16 ms
 * here; this is not a refresh-rate test.
 */
class AddSelectorMotionTest {
    @get:Rule val compose = createComposeRule()

    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)
    private val instant = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = false)

    private var selected by mutableIntStateOf(0)
    private var policy by mutableStateOf(full)
    private val taps = mutableListOf<Int>()
    private lateinit var indicator: SelectorIndicatorState

    private val labels = listOf("Expense", "Income", "Transfer")
    private val tags = listOf("expense", "income", "transfer")

    private fun show(
        start: Int = 0,
        initial: MotionPolicy = full,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
    ) {
        selected = start
        policy = initial
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(
                    LocalZarMotion provides policy,
                    LocalLayoutDirection provides direction,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    indicator = rememberSelectorIndicator(selected)
                    Segmented(
                        labels = labels,
                        selectedIndex = selected,
                        onSelect = { taps += it; selected = it },
                        segmentHeight = 38.dp,
                        tagPrefix = "kind",
                        tags = tags,
                        indicator = indicator,
                    )
                }
            }
        }
        frames(4)
    }

    private fun frames(count: Int = 1) = repeat(count) { compose.mainClock.advanceTimeByFrame() }
    private fun tap(index: Int) { compose.onNodeWithTag("kind-${tags[index]}").performClick() }
    private fun left() = indicator.indicatorBounds()!!.left
    private fun slotLeft(index: Int) = with(compose.density) { compose.onNodeWithTag("kind-${tags[index]}").getUnclippedBoundsInRoot().left.toPx() }
    private fun slotWidth() = with(compose.density) { compose.onNodeWithTag("kind-expense").getUnclippedBoundsInRoot().width.toPx() }
    private fun pitch() = slotWidth() + 4.dp.toPxF()

    @Test
    fun opensAlignedWithTheSelectedSlotAndStaysThere() {
        show(start = 2)
        val placed = left()
        assertEquals(2 * pitch(), placed, 1f)
        frames(30)
        assertEquals("no entrance motion", placed, left(), 0f)
    }

    @Test
    fun travelsContinuouslyAndSettlesAtTheRequestedSlotOnly() {
        show()
        val start = left()
        tap(2)
        // Logical selection lands on the very next frame, before the indicator has travelled anywhere.
        frames()
        compose.onNodeWithTag("kind-transfer").assertIsSelected()
        assertEquals(listOf(2), taps)
        assertTrue("indicator has barely started (${left() - start})", left() - start < slotWidth() / 4)
        val trail = mutableListOf(left())
        repeat(60) { frames(); trail += left() }
        val end = trail.last()
        val target = start + 2 * pitch()
        assertEquals(target, end, 1f)
        assertTrue("moves on the first frames", trail[3] > start)
        assertTrue("an intermediate frame lies between the slots", trail.any { it > start + 10f && it < target - 10f })
        // Continuous: no frame jumps further than a slot.
        trail.zipWithNext().forEach { (a, b) -> assertTrue("jump ${b - a}", b - a < slotWidth()) }
        // Bounce is bounded and stays in the track.
        val maxOvershoot = trail.max() - target
        assertTrue("overshoot $maxOvershoot", maxOvershoot <= 6.dp.toPxF() + 1f)
        assertTrue("stays inside the track", trail.max() <= target + 0.5f)
        assertEquals("only the requested choice fires", listOf(2), taps)
    }

    @Test
    fun overshootsBeyondTheTargetWhenThereIsRoom() {
        show()
        tap(1)
        val target = pitch()
        val trail = mutableListOf<Float>()
        repeat(60) { frames(); trail += left() }
        val over = trail.max() - target
        assertTrue("bounces past the slot ($over)", over > 0.5f)
        assertTrue("capped at 6dp ($over)", over <= 6.dp.toPxF() + 1f)
    }

    @Test
    fun rapidReversalRetargetsWithoutSnappingOrQueueing() {
        show()
        tap(2)
        frames(6)
        val mid = left()
        tap(1)
        frames(1)
        assertTrue("responds from where it is (no snap to a slot)", kotlin.math.abs(left() - mid) < slotWidth())
        frames(3)
        tap(0)
        repeat(90) { frames() }
        assertEquals(listOf(2, 1, 0), taps)
        assertEquals(0f, left(), 0.5f)
        compose.onNodeWithTag("kind-expense").assertIsSelected()
    }

    @Test
    fun repeatTapOnTheSelectedSlotDoesNothing() {
        show(start = 1)
        val before = left()
        tap(1)
        frames(10)
        assertTrue(taps.isEmpty())
        assertEquals(before, left(), 0f)
    }

    @Test
    fun slotBoundsNeverMoveWhileTheIndicatorTravels() {
        show()
        val before = tags.indices.map { slotLeft(it) }
        tap(2)
        repeat(12) {
            frames()
            assertEquals(before, tags.indices.map { slotLeft(it) })
        }
    }

    @Test
    fun reducedLandsOnTheNextFrameWithNoTravel() = landsDirectly(reduced)

    @Test
    fun instantLandsOnTheNextFrameWithNoTravel() = landsDirectly(instant)

    private fun landsDirectly(level: MotionPolicy) {
        show(initial = level)
        tap(2)
        frames(2)
        assertEquals("direct for $level", 2 * pitch(), left(), 1f)
        frames(10)
        assertEquals(2 * pitch(), left(), 1f)
        compose.onNodeWithTag("kind-transfer").assertIsSelected()
    }

    @Test
    fun policyChangeMidSpringResolvesToTheSelectedSlot() {
        show()
        tap(2)
        frames(4)
        policy = reduced
        frames(2)
        assertEquals(2 * pitch(), left(), 1f)
        compose.onNodeWithTag("kind-transfer").assertIsSelected()
        assertEquals(listOf(2), taps)
    }

    @Test
    fun rtlPutsTheFirstSlotOnTheRightAndStaysInsideTheTrack() {
        show(direction = LayoutDirection.Rtl)
        val track = indicator.indicatorBounds()!!
        assertTrue("first slot is at the right", slotLeft(0) > slotLeft(2))
        tap(2)
        val trail = mutableListOf<Float>()
        repeat(60) { frames(); trail += left() }
        assertEquals("Transfer is the left slot", 0f, trail.last(), 1f)
        assertTrue("never left of the track", trail.min() >= 0f)
        assertTrue(track.right > 0f)
    }

    @Test
    fun largeTextKeepsTheIndicatorOnTheSelectedSlot() {
        show(start = 1, fontScale = 2f)
        assertEquals(pitch(), left(), 1f)
    }

    @Test
    fun typeIndicatorKeepsTravellingAcrossTheIncomeBodySwitch() {
        // The host's structure: one indicator above the body switch, two bodies that each draw the selector from it.
        val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1))
        var tab by mutableIntStateOf(0)
        val manual = ManualEntryUiState(isLoading = false, kind = ManualEntryKind.EXPENSE, accounts = listOf(bank), accountId = bank.id)
        val income = IncomeFormUiState(isLoading = false, accounts = listOf(bank), accountId = bank.id)
        val manualActions = ManualEntryActions(
            onClose = {}, onOpenIncome = { tab = 1 }, onAddAccount = {}, onKind = { tab = if (it == ManualEntryKind.TRANSFER) 2 else 0 },
            onAmount = {}, onDate = {}, onTime = {}, onAccount = {}, onCategory = {}, onAddCategoryStart = {}, onNewCategoryName = {},
            onAddCategoryCancel = {}, onAddCategorySave = {}, onSpendingClass = {}, onTitle = {}, onDescription = {}, onSuggestion = {},
            onFee = {}, onFrom = {}, onTo = {}, onSwap = {}, onTransferNote = {}, onSave = {},
        )
        val incomeActions = IncomeFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onSelectKind = { tab = it })
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ZarTheme {
                indicator = rememberSelectorIndicator(tab)
                if (tab == 1) IncomeFormContent(income, incomeActions, indicator)
                else ManualEntryContent(manual, false, manualActions, indicator)
            }
        }
        frames(6)
        val positions = mutableListOf(left())
        compose.onNodeWithTag("manual-kind-income").performClick()
        repeat(4) { frames(); positions += left() }
        compose.onNodeWithTag("income-kind-transfer").performClick()
        repeat(80) { frames(); positions += left() }

        val slot = positions.last() / 2f
        positions.zipWithNext().forEach { (a, b) -> assertTrue("jump ${b - a} (slot $slot)", kotlin.math.abs(b - a) < slot) }
        assertTrue("moved on from Expense without resetting", positions[3] > positions.first())
        assertTrue("never snapped back to the first slot mid-flight", positions.drop(3).all { it > 0f })
        assertEquals(2f * slot, positions.last(), 1f)
    }

    private fun androidx.compose.ui.unit.Dp.toPxF() = with(compose.density) { toPx() }
}
