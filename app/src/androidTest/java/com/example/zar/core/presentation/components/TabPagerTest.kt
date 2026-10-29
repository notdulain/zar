package com.example.zar.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.theme.ZarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The four tabs as one pager with the navbar outside it: tap and swipe stay in sync, the capsule follows the finger one for
 * one, a long jump never composes the pages in between, nested horizontal controls keep their own drags, and the pager is
 * locked while a sheet or the keyboard is up.
 */
class TabPagerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val capsule = NavCapsuleController()
    private lateinit var tabs: TabPagerController
    private val composedPages = linkedSetOf<Int>()
    private var scrollEnabled by mutableStateOf(true)
    private var rowOffset = 0

    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)

    private fun show(policy: MotionPolicy = full, warmUp: Boolean = false) {
        compose.setContent {
            ZarTheme {
                CompositionLocalProvider(LocalZarMotion provides policy) {
                    tabs = rememberTabPagerController()
                    Box(Modifier.fillMaxSize()) {
                        TabPagerHost(tabs, userScrollEnabled = scrollEnabled, warmUpPages = warmUp) { destination ->
                            LaunchedEffect(destination) { composedPages += destination.ordinal }
                            Box(Modifier.fillMaxSize().testTag("page-${destination.ordinal}")) {
                                Text("Page ${destination.name}")
                                if (destination == TopLevelDestination.Activity) {
                                    val scroll = rememberScrollState()
                                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(60.dp).testTag("chips").ownHorizontalDrags().horizontalScroll(scroll)) {
                                        Box(Modifier.width(900.dp).height(60.dp))
                                    }
                                }
                            }
                        }
                        TabPagerBackHandler(tabs, enabled = true)
                        tabs.DriveCapsule(capsule)
                        ZarBottomNav(
                            selected = tabs.selected,
                            onNavigate = { tabs.select(it, policy) },
                            controller = capsule,
                            driveCapsule = false,
                            modifier = Modifier.align(Alignment.BottomCenter).width(293.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun slotBounds() = listOf("nav-home", "nav-activity", "nav-plans", "nav-more").map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }

    @Test
    fun swipingLeftAdvancesToTheNextTabAndTheNavbarFollows() {
        show()
        val slotsBefore = slotBounds()

        compose.onNodeWithTag("page-0").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(1, tabs.pagerState.settledPage)
        compose.onNodeWithTag("nav-activity").assertIsSelected()
        assertEquals("the navbar did not move while swiping", slotsBefore, slotBounds())
        assertEquals(1f, capsule.position, 0.001f)
    }

    @Test
    fun theCapsuleTracksTheFingerOneForOneMidDrag() {
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("page-0").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.4f, 0f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        val expected = tabs.pagerState.currentPage + tabs.pagerState.currentPageOffsetFraction
        assertTrue("the pager moved with the finger ($expected)", expected > 0.2f && expected < 0.6f)
        assertEquals("capsule position equals pager position while dragging", expected, capsule.position, 0.05f)
        assertEquals("the page has not settled, so the selection does not change early", 0, tabs.selectedIndex)

        compose.onNodeWithTag("page-0").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test
    fun aShortDragReturnsToTheSamePage() {
        show()

        compose.onNodeWithTag("page-0").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.12f, 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals(0, tabs.pagerState.settledPage)
        assertEquals(0f, capsule.position, 0.001f)
    }

    @Test
    fun tappingAnAdjacentTabAnimatesThePagerAndSpringsTheCapsule() {
        show()

        compose.onNodeWithTag("nav-activity").performClick()
        compose.waitForIdle()

        assertEquals(1, tabs.pagerState.currentPage)
        compose.onNodeWithTag("nav-activity").assertIsSelected()
        assertEquals(1f, capsule.position, 0.01f)
    }

    @Test
    fun idleWarmUpComposesEveryTabSoASwipeComposesNothingNew() {
        show(warmUp = true)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals("every tab is composed once the screen has been idle", setOf(0, 1, 2, 3), composedPages)

        composedPages.clear()
        compose.onNodeWithTag("page-0").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, tabs.pagerState.currentPage)
        assertTrue("a swipe after warm-up composed $composedPages", composedPages.isEmpty())
    }

    @Test
    fun warmUpWaitsWhileASheetOrKeyboardHasTheScreen() {
        scrollEnabled = false
        show(warmUp = true)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals("nothing is warmed while scrolling is locked", setOf(0), composedPages)

        scrollEnabled = true
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(setOf(0, 1, 2, 3), composedPages)
    }

    private fun pressBack() = compose.runOnUiThread {
        compose.activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun backFromAnOuterTabSlidesThePageRightOverHomeComingInFromBehind() {
        show(warmUp = true)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithTag("nav-plans").performClick()
        compose.waitForIdle()
        assertEquals(2, tabs.pagerState.currentPage)
        val width = compose.onNodeWithTag("page-2").getUnclippedBoundsInRoot().let { it.right - it.left }

        compose.mainClock.autoAdvance = false
        pressBack()
        compose.mainClock.advanceTimeBy(120)
        val leaving = compose.onNodeWithTag("page-2").getUnclippedBoundsInRoot()
        val home = compose.onNodeWithTag("page-0").getUnclippedBoundsInRoot()
        assertTrue("Plans did not move right: ${leaving.left}", leaving.left > 4.dp)
        assertTrue("Home is not receding in from behind: ${home.left}", home.left < 0.dp && home.left.value > -(width.value * 0.2f) - 1f)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0, tabs.pagerState.currentPage)
        assertEquals(0.dp, compose.onNodeWithTag("page-0").getUnclippedBoundsInRoot().left)
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun aLongJumpNeverComposesThePagesBetween() {
        show()
        composedPages.clear()

        compose.onNodeWithTag("nav-more").performClick()
        compose.waitForIdle()

        assertEquals(3, tabs.pagerState.currentPage)
        compose.onNodeWithTag("nav-more").assertIsSelected()
        assertFalse("Activity flashed by on a Home to More jump: $composedPages", 1 in composedPages)
        assertFalse("Plans flashed by on a Home to More jump: $composedPages", 2 in composedPages)
        assertEquals(3f, capsule.position, 0.01f)
    }

    @Test
    fun reducedMotionSwitchesAtOnceAndSnapsTheCapsule() {
        show(reduced)

        compose.onNodeWithTag("nav-plans").performClick()
        compose.waitForIdle()

        assertEquals(2, tabs.pagerState.currentPage)
        assertEquals(2f, capsule.position, 0.001f)
        assertFalse(capsule.isMoving)
    }

    @Test
    fun aHorizontalControlKeepsItsOwnDragsAtItsEnd() {
        show()
        compose.onNodeWithTag("nav-activity").performClick()
        compose.waitForIdle()
        assertEquals(1, tabs.pagerState.currentPage)

        // Drag the chip row to its start end and past it: the leftover must not page the tab.
        compose.onNodeWithTag("chips").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("chips").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals("the chip row's leftover drag moved the tab", 1, tabs.pagerState.settledPage)
    }

    @Test
    fun theLockStopsSwipingButNotTaps() {
        show()
        scrollEnabled = false
        compose.waitForIdle()

        compose.onNodeWithTag("page-0").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("a swipe paged while the pager was locked", 0, tabs.pagerState.settledPage)

        compose.onNodeWithTag("nav-activity").performClick()
        compose.waitForIdle()
        assertEquals(1, tabs.pagerState.settledPage)
    }

    @Test
    fun aSecondTapMidFlightRetargetsAndTheLastTapWins() {
        show()

        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithTag("nav-activity").performClick()
        compose.waitForIdle()

        assertEquals(1, tabs.pagerState.settledPage)
        compose.onNodeWithTag("nav-activity").assertIsSelected()
        assertEquals(1f, capsule.position, 0.01f)
    }
}
