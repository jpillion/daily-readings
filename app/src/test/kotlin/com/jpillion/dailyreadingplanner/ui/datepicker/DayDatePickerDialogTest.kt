package com.jpillion.dailyreadingplanner.ui.datepicker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 8 custom calendar picker (D-S8-2), as reworked in Sprint 21:
 *  - **One-tap select (BACKLOG #7):** tapping a day cell returns that full date and closes the
 *    dialog in one tap — there is no confirm button.
 *  - **Cross-year month swipe (BACKLOG #6):** the months ride a HorizontalPager; swiping
 *    left/right crosses year boundaries (Dec → Jan of the next year and back). The chevrons
 *    drive the same pager and are no longer bounded to a year.
 *  - Per-day completion indicators (green = complete for any day, red = missed for past days
 *    only, nothing otherwise) — spoken via contentDescription, never color alone — carry over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayDatePickerDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val confirmed = mutableListOf<LocalDate>()
    private var dismissed = 0

    private fun setDialog(
        today: LocalDate = LocalDate(2026, 6, 10),
        initialDate: LocalDate = LocalDate(2026, 6, 10),
        completion: Map<LocalDate, DayCompletion> = emptyMap(),
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayDatePickerDialog(
                    today = today,
                    initialDate = initialDate,
                    completionFor = { month ->
                        MutableStateFlow(completion.filterKeys { it.yearMonth == month })
                    },
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun `opens on the initial date's month`() {
        setDialog()
        composeRule.onNodeWithTag("picker-month-title").assertIsDisplayed()
        composeRule.onNodeWithText("June 2026").assertIsDisplayed()
    }

    @Test
    fun `tapping a day returns that full date in one tap and there is no confirm button`() {
        setDialog()
        composeRule.onNodeWithTag("date-picker-confirm").assertDoesNotExist()
        composeRule.onNodeWithTag("picker-day-15").performClick()
        assertThat(confirmed).containsExactly(LocalDate(2026, 6, 15))
    }

    @Test
    fun `cancel dismisses without selecting`() {
        setDialog()
        composeRule.onNodeWithTag("date-picker-cancel").performClick()
        assertThat(dismissed).isEqualTo(1)
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `next-month chevron crosses the year boundary December to January`() {
        setDialog(initialDate = LocalDate(2026, 12, 10))
        composeRule.onNodeWithText("December 2026").assertIsDisplayed()
        // Previously this chevron was disabled at December; now it advances into the next year.
        composeRule.onNodeWithTag("picker-next-month").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("January 2027").assertIsDisplayed()
        // And a tapped day in the new year returns that full date.
        composeRule.onNodeWithTag("picker-day-5").performClick()
        assertThat(confirmed).containsExactlyInAnyOrder(LocalDate(2027, 1, 5))
    }

    @Test
    fun `prev-month chevron crosses the year boundary January to December`() {
        setDialog(initialDate = LocalDate(2026, 1, 10))
        composeRule.onNodeWithText("January 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-prev-month").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("December 2025").assertIsDisplayed()
    }

    @Test
    fun `swiping the calendar left advances a month`() {
        setDialog(initialDate = LocalDate(2026, 6, 10))
        composeRule.onNodeWithTag("picker-month-pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("July 2026").assertIsDisplayed()
    }

    @Test
    fun `swiping right across January reaches the previous year`() {
        setDialog(initialDate = LocalDate(2026, 1, 10))
        composeRule.onNodeWithTag("picker-month-pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("December 2025").assertIsDisplayed()
    }

    @Test
    fun `complete and missed days speak their state - incomplete days stay plain`() {
        setDialog(
            completion =
                mapOf(
                    LocalDate(2026, 6, 1) to DayCompletion.COMPLETE,
                    LocalDate(2026, 6, 2) to DayCompletion.MISSED,
                    LocalDate(2026, 6, 3) to DayCompletion.NONE,
                ),
        )
        composeRule
            .onNodeWithContentDescription("Monday, June 1, 2026, All readings done")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Tuesday, June 2, 2026, Readings missed")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Wednesday, June 3, 2026")
            .assertIsDisplayed()
    }

    @Test
    fun `a future complete day is marked complete too`() {
        setDialog(completion = mapOf(LocalDate(2026, 6, 25) to DayCompletion.COMPLETE))
        composeRule
            .onNodeWithContentDescription("Thursday, June 25, 2026, All readings done")
            .assertIsDisplayed()
    }

    @Test
    fun `Feb 29 is selectable in a leap year in one tap`() {
        setDialog(
            today = LocalDate(2028, 2, 28),
            initialDate = LocalDate(2028, 2, 28),
        )
        composeRule.onNodeWithText("February 2028").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-day-29").performClick()
        assertThat(confirmed).containsExactlyInAnyOrder(LocalDate(2028, 2, 29))
    }

    @Test
    fun `month-for-page maps offsets across year boundaries`() {
        val dec2026 = YearMonth(2026, 12)
        assertThat(monthForPage(dec2026, MONTH_CENTER_PAGE)).isEqualTo(YearMonth(2026, 12))
        assertThat(monthForPage(dec2026, MONTH_CENTER_PAGE + 1)).isEqualTo(YearMonth(2027, 1))
        assertThat(monthForPage(dec2026, MONTH_CENTER_PAGE - 12)).isEqualTo(YearMonth(2025, 12))
        assertThat(monthForPage(YearMonth(2026, 1), MONTH_CENTER_PAGE - 1))
            .isEqualTo(YearMonth(2025, 12))
    }

    @Test
    fun `grid math - leading empty cells and weekday order`() {
        assertThat(leadingEmptyCells(YearMonth(2026, 6), DayOfWeek.SUNDAY)).isEqualTo(1)
        assertThat(leadingEmptyCells(YearMonth(2026, 2), DayOfWeek.SUNDAY)).isEqualTo(0)
        assertThat(leadingEmptyCells(YearMonth(2026, 2), DayOfWeek.MONDAY)).isEqualTo(6)
        assertThat(weekdayOrder(DayOfWeek.SUNDAY))
            .containsExactlyInAnyOrder(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            )
    }
}
