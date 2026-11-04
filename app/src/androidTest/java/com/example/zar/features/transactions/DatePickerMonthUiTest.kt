package com.example.zar.features.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.zar.core.presentation.theme.ZarTheme
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.features.transactions.presentation.manual.screen.ManualDatePickerSheet
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Rule
import org.junit.Test

/** The Add transaction date picker: the month title opens a month chooser, and swiping the calendar changes month. */
class DatePickerMonthUiTest {
    @get:Rule val compose = createComposeRule()

    private val today = ZarTime.today()
    private fun title(month: YearMonth) = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    private fun show(date: LocalDate = today, onSet: (LocalDate) -> Unit = {}) {
        compose.setContent { ZarTheme { ManualDatePickerSheet(date = date, onCancel = {}, onSetDate = onSet) } }
    }

    @Test
    fun monthTitleOpensAChooserAndPickingAMonthShowsIt() {
        show()
        compose.onNodeWithTag("date-month-grid").assertDoesNotExist()
        compose.onNodeWithTag("date-month-title").performClick()
        compose.onNodeWithTag("date-month-grid").assertIsDisplayed()
        // A month of the previous year, so it is never in the future.
        compose.onNodeWithContentDescription("Previous year").performClick()
        compose.onNodeWithTag("date-month-3").performClick()
        compose.onNodeWithTag("date-month-grid").assertDoesNotExist()
        compose.onNodeWithTag("date-month-title").assertTextContains(title(YearMonth.of(today.year - 1, 3)))
    }

    @Test
    fun futureMonthsAreOff() {
        show()
        compose.onNodeWithTag("date-month-title").performClick()
        val future = YearMonth.from(today).plusMonths(1)
        if (future.year == today.year) {
            compose.onNodeWithTag("date-month-${future.monthValue}").performClick()
            // Disabled: the chooser stays open on the same month.
            compose.onNodeWithTag("date-month-grid").assertIsDisplayed()
        }
    }

    @Test
    fun swipingTheCalendarChangesMonthButNotPastToday() {
        show()
        val current = YearMonth.from(today)
        // Swipe toward the end: the next month is in the future, so nothing changes.
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current))
        compose.onNodeWithTag("date-calendar").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current))
        compose.onNodeWithTag("date-calendar").performTouchInput { swipeRight() }
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current.minusMonths(1)))
        compose.onNodeWithTag("date-calendar").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current))
    }

    @Test
    fun aShortDragSpringsBackAndALongOneSlidesToTheEarlierMonth() {
        show()
        val current = YearMonth.from(today)
        compose.onNodeWithTag("date-calendar").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.1f, 0f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current))
        compose.onNodeWithTag("date-calendar").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.5f, 0f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current.minusMonths(1)))
    }

    @Test
    fun theArrowsAlsoSlideAndLandOnTheNewMonth() {
        show()
        val current = YearMonth.from(today)
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current.minusMonths(1)))
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("date-month-title").assertTextContains(title(current))
    }
}
