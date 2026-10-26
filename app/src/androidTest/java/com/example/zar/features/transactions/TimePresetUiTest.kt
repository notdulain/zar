package com.example.zar.features.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.features.transactions.presentation.manual.screen.ManualTimePickerSheet
import java.time.Duration
import java.time.LocalTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The time picker's "Now / 15 min ago / 1 hour ago" chips are relative to the clock at the tap. */
class TimePresetUiTest {
    @get:Rule val compose = createComposeRule()

    private fun minutesBetween(a: LocalTime, b: LocalTime): Long {
        val diff = Math.floorMod(Duration.between(a, b).toMinutes(), 24 * 60L)
        return minOf(diff, 24 * 60L - diff)
    }

    private fun chosenAfterTapping(chip: String): Pair<LocalTime, LocalTime> {
        var chosen: LocalTime? = null
        compose.setContent {
            ZarTheme { ManualTimePickerSheet(time = LocalTime.of(3, 0), onCancel = {}, onSetTime = { chosen = it }) }
        }
        compose.onNodeWithText(chip).performClick()
        val tappedAt = LocalTime.now(ZarTime.ZONE)
        compose.onNodeWithText("Set time").performClick()
        compose.waitForIdle()
        return checkNotNull(chosen) to tappedAt
    }

    @Test
    fun fifteenMinutesAgoIsFifteenMinutesBeforeTheTap() {
        val (chosen, tappedAt) = chosenAfterTapping("15 min ago")
        assertNotNull(chosen)
        assertTrue("chosen $chosen vs tap $tappedAt", minutesBetween(chosen, tappedAt.minusMinutes(15)) <= 1)
    }

    @Test
    fun oneHourAgoIsAnHourBeforeTheTap() {
        val (hourAgo, t1) = chosenAfterTapping("1 hour ago")
        assertTrue(minutesBetween(hourAgo, t1.minusHours(1)) <= 1)
    }
}
