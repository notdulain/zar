package com.example.zar.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.zar.core.presentation.motion.LocalZarMotion
import com.example.zar.core.presentation.motion.MotionPolicy
import com.example.zar.core.presentation.motion.ZarMotion
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The parallax layers against a real scroll owner on a controlled clock: a lazy hero (keyed, and with content padding),
 * an ordinary scroll owner, reduced motion, offscreen return and a control inside a displaced layer. Bounds are read in
 * root coordinates, so they include the layer's transform exactly as a touch or a screen reader sees it.
 */
class HeroParallaxUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = MotionPolicy(reduceMotionSetting = false, systemAnimationsEnabled = true)
    private val reduced = MotionPolicy(reduceMotionSetting = true, systemAnimationsEnabled = true)
    private var clicks by mutableIntStateOf(0)

    private lateinit var scroller: (Float) -> Unit

    private fun showLazy(policy: MotionPolicy, contentPaddingTop: Int = 0) = compose.setContent {
        CompositionLocalProvider(LocalZarMotion provides policy) {
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            scroller = { delta -> scope.launch { state.scrollBy(delta) } }
            val offset = state.heroDisplacement()
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(640.dp).testTag("list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = contentPaddingTop.dp)) {
                item(key = ZarHeroItemKey) {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        Text("hero title", Modifier.heroParallax(offset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap).testTag("title"))
                        Box(
                            Modifier.heroParallax(offset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap)
                                .padding(top = 120.dp).height(56.dp).fillMaxWidth().clickable { clicks++ }.testTag("control"),
                        ) { Text("range") }
                    }
                }
                items(40) { index -> Box(Modifier.fillMaxWidth().height(80.dp).testTag("row-$index")) { Text("row $index") } }
            }
        }
    }

    private fun titleTop() = compose.onNodeWithTag("title").getUnclippedBoundsInRoot().top.value
    private fun listTop() = compose.onNodeWithTag("list").getUnclippedBoundsInRoot().top.value

    @Test
    fun atRestTheLayerSitsExactlyWhereItsLayoutPutsIt() {
        showLazy(full)
        compose.waitForIdle()

        assertEquals(listTop(), titleTop(), 0.5f)
    }

    @Test
    fun scrollingMovesTheTextSlowerThanTheContentByTheTextFraction() {
        showLazy(full)
        val density = compose.density.density
        val before = titleTop()

        val px = 100f * density
        scrollOnMain(px)

        val moved = before - titleTop()
        val expectedMoved = px / density - minOf(px * ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap.value * density) / density
        assertEquals("text moves less than the 100dp the content scrolled", expectedMoved, moved, 0.6f)
        assertTrue(moved < 100f)
    }

    @Test
    fun theLayerSaturatesAtItsCapAndComesBackContinuouslyAfterTheHeroLeaves() {
        showLazy(full)
        val density = compose.density.density
        val restTop = titleTop()
        scrollOnMain(250f * density) // hero still partly visible: displacement well past the text cap (8dp / 0.06 = 133dp)
        val partial = restTop - titleTop()
        assertEquals("capped at 8dp behind the content", 250f - ZarMotion.ParallaxTextCap.value, partial, 0.8f)

        scrollOnMain(2_000f * density) // hero fully off, thousands of px away
        scrollOnMain(-2_000f * density)
        val back = restTop - titleTop()
        assertEquals("re-entering at the same scroll offset gives the same displaced position", partial, back, 0.8f)
    }

    @Test
    fun aControlInsideTheDisplacedLayerIsHitWhereItIsDrawn() {
        showLazy(full)
        val density = compose.density.density
        clicks = 0
        scrollOnMain(120f * density)

        compose.onNodeWithTag("control").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun reducedMotionLeavesTheLayerAtItsLayoutPositionWhileScrolling() {
        showLazy(reduced)
        val density = compose.density.density
        val before = titleTop()
        scrollOnMain(100f * density)

        assertEquals(100f, before - titleTop(), 0.5f)
    }

    @Test
    fun contentPaddingAboveTheHeroDoesNotStartItDisplaced() {
        showLazy(full, contentPaddingTop = 16)
        compose.waitForIdle()

        assertEquals(listTop() + 16f, titleTop(), 0.5f)
    }

    @Test
    fun anOrdinaryScrollOwnerDisplacesTheSameWay() {
        val density = compose.density.density
        lateinit var state: androidx.compose.foundation.ScrollState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        compose.setContent {
            CompositionLocalProvider(LocalZarMotion provides full) {
                state = rememberScrollState()
                scope = rememberCoroutineScope()
                val offset = state.heroDisplacement()
                Column(Modifier.height(500.dp).verticalScroll(state)) {
                    Text("hero title", Modifier.heroParallax(offset, ZarMotion.PARALLAX_TEXT, ZarMotion.ParallaxTextCap).testTag("title"))
                    Box(Modifier.height(2_000.dp))
                }
            }
        }
        val before = titleTop()
        compose.runOnIdle { scope.launch { state.scrollBy(100f * density) } }
        compose.waitForIdle()

        assertTrue("moved ${before - titleTop()}", before - titleTop() in 90f..100f)
    }

    private fun scrollOnMain(px: Float) {
        compose.runOnIdle { scroller(px) }
        compose.waitForIdle()
    }
}
