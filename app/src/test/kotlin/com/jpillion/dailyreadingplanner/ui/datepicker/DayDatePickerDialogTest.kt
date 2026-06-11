package com.jpillion.dailyreadingplanner.ui.datepicker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.DayCompletion
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Sprint 8 custom calendar picker (D-S8-2): grid correctness, the pinned-year contract carried
 * over from D-S5-3, and the per-day completion indicators (green = complete for any day,
 * red = missed for past days only, nothing otherwise) — spoken via contentDescription, never
 * color alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayDatePickerDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val confirmed = mutableListOf<LocalDate>()
    private var dismissed = 0

    private fun setDialog(
        year: Int = 2026,
        today: LocalDate = LocalDate.of(2026, 6, 10),
        initialDate: LocalDate = LocalDate.of(2026, 6, 10),
        completion: Map<LocalDate, DayCompletion> = emptyMap(),
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayDatePickerDialog(
                    year = year,
                    today = today,
                    initialDate = initialDate,
                    completionFor = { month ->
                        MutableStateFlow(completion.filterKeys { YearMonth.from(it) == month })
                    },
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun `opens on the initial date's month with that day selected`() {
        setDialog()
        composeRule.onNodeWithTag("picker-month-title").assertIsDisplayed()
        composeRule.onNodeWithText("June 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-day-10").assertIsSelected()
    }

    @Test
    fun `selecting a day and confirming returns that full date`() {
        setDialog()
        composeRule.onNodeWithTag("picker-day-15").performClick()
        composeRule.onNodeWithTag("picker-day-15").assertIsSelected()
        composeRule.onNodeWithTag("date-picker-confirm").performClick()
        assertThat(confirmed).containsExactly(LocalDate.of(2026, 6, 15))
    }

    @Test
    fun `cancel dismisses without confirming`() {
        setDialog()
        composeRule.onNodeWithTag("date-picker-cancel").performClick()
        assertThat(dismissed).isEqualTo(1)
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `month navigation steps months and is bounded to the pinned year`() {
        setDialog(initialDate = LocalDate.of(2026, 1, 10))
        composeRule.onNodeWithText("January 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-prev-month").assertIsNotEnabled()
        composeRule.onNodeWithTag("picker-next-month").performClick()
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()
        // Selection survives month navigation; confirming still returns the January pick.
        composeRule.onNodeWithTag("date-picker-confirm").performClick()
        assertThat(confirmed).containsExactly(LocalDate.of(2026, 1, 10))
    }

    @Test
    fun `December is the upper bound of the pinned year`() {
        setDialog(initialDate = LocalDate.of(2026, 12, 10))
        composeRule.onNodeWithText("December 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-next-month").assertIsNotEnabled()
    }

    @Test
    fun `complete and missed days speak their state - incomplete days stay plain`() {
        setDialog(
            completion =
                mapOf(
                    LocalDate.of(2026, 6, 1) to DayCompletion.COMPLETE,
                    LocalDate.of(2026, 6, 2) to DayCompletion.MISSED,
                    LocalDate.of(2026, 6, 3) to DayCompletion.NONE,
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
        // Green applies to past, today and future alike (owner spec).
        setDialog(completion = mapOf(LocalDate.of(2026, 6, 25) to DayCompletion.COMPLETE))
        composeRule
            .onNodeWithContentDescription("Thursday, June 25, 2026, All readings done")
            .assertIsDisplayed()
    }

    @Test
    fun `Feb 29 is selectable in a leap year`() {
        setDialog(
            year = 2028,
            today = LocalDate.of(2028, 2, 28),
            initialDate = LocalDate.of(2028, 2, 28),
        )
        composeRule.onNodeWithText("February 2028").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-day-29").performClick()
        composeRule.onNodeWithTag("date-picker-confirm").performClick()
        assertThat(confirmed).containsExactly(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `a leap-day initial date anchors to Feb 28 in a common year`() {
        // Documents the withYear fallback carried over from D-S5-3.
        setDialog(
            year = 2026,
            today = LocalDate.of(2026, 6, 10),
            initialDate = LocalDate.of(2028, 2, 29),
        )
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-day-28").assertIsSelected()
    }

    @Test
    fun `grid math - leading empty cells and weekday order`() {
        // June 2026 starts on a Monday; with a Sunday-start week that is one leading blank.
        assertThat(leadingEmptyCells(YearMonth.of(2026, 6), DayOfWeek.SUNDAY)).isEqualTo(1)
        // February 2026 starts on a Sunday: zero blanks for Sunday-start, six for Monday-start.
        assertThat(leadingEmptyCells(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)).isEqualTo(0)
        assertThat(leadingEmptyCells(YearMonth.of(2026, 2), DayOfWeek.MONDAY)).isEqualTo(6)
        assertThat(weekdayOrder(DayOfWeek.SUNDAY))
            .containsExactly(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            ).inOrder()
    }
}
