package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Pager-level behavior (Sprint 5): swiping between real calendar days, the Feb 29 and
 * Dec 31 -> Jan 1 edges (D-S5-3), jump-to-today (FR-12), the date picker dialog (FR-5),
 * and that mark callbacks always carry the *displayed* date.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayReadingsPagerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val markCalls = mutableListOf<Pair<LocalDate, Boolean>>()
    private val toggleCalls = mutableListOf<Pair<LocalDate, Stream>>()
    private val dayStates = mutableMapOf<LocalDate, MutableStateFlow<DayUiState>>()

    private fun stateFor(date: LocalDate): StateFlow<DayUiState> =
        dayStates.getOrPut(date) {
            MutableStateFlow(
                if (date.monthValue == 2 && date.dayOfMonth == 29) {
                    DayUiState.NoScheduledReadings(date)
                } else {
                    DayUiState.Scheduled(
                        date = date,
                        readings = threePortions.map { ReadingStatus(it, false) },
                        dayComplete = false,
                    )
                },
            )
        }

    private fun setScreen(
        today: LocalDate,
        onReadingTapped: (Portion) -> Unit = {},
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayReadingsPagerScreen(
                    today = today,
                    uiStateFor = ::stateFor,
                    onToggleReading = { date, reading -> toggleCalls += date to reading.portion.stream },
                    onMarkWholeDay = { date, dayComplete -> markCalls += date to dayComplete },
                    onReadingTapped = onReadingTapped,
                    onRetry = {},
                )
            }
        }
    }

    private fun formatted(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    private fun swipeToNextDay() {
        composeRule.onNodeWithTag("day-pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    private fun swipeToPreviousDay() {
        composeRule.onNodeWithTag("day-pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
    }

    @Test
    fun launch_showsTodayTitleAndDate_withoutJumpAffordance() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText(formatted(today)).assertIsDisplayed()
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertDoesNotExist()
    }

    @Test
    fun swipeLeft_showsTomorrow_andJumpToTodayReturns() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText("Readings").assertIsDisplayed()
        composeRule.onNodeWithText(formatted(today.plusDays(1))).assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(formatted(today)).assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertDoesNotExist()
    }

    @Test
    fun swipeRight_showsYesterday() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        swipeToPreviousDay()
        composeRule.onNodeWithText(formatted(today.minusDays(1))).assertIsDisplayed()
    }

    @Test
    fun markCallbacks_carryTheDisplayedDate_notToday() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithTag("whole-day-button").performScrollTo().performClick()
        composeRule.onNodeWithTag("toggle-2").performClick()
        assertThat(markCalls).containsExactly(today.plusDays(1) to false)
        assertThat(toggleCalls).containsExactly(today.plusDays(1) to Stream.PSALMS_AND_PROPHECY)
    }

    @Test
    fun leapYear_swipingFromFeb28_hitsFeb29NoReadings_thenMar1() {
        val today = LocalDate.of(2028, 2, 28)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText("No scheduled readings for Feb 29th").assertIsDisplayed()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        swipeToNextDay()
        composeRule.onNodeWithText(formatted(LocalDate.of(2028, 3, 1))).assertIsDisplayed()
        composeRule.onNodeWithTag("whole-day-button").assertExists()
    }

    @Test
    fun yearBoundary_swipingFromDec31_landsOnJan1OfNextYear() {
        val today = LocalDate.of(2026, 12, 31)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText(formatted(LocalDate.of(2027, 1, 1))).assertIsDisplayed()
        composeRule.onNodeWithTag("whole-day-button").performScrollTo().performClick()
        assertThat(markCalls).containsExactly(LocalDate.of(2027, 1, 1) to false)
    }

    @Test
    fun datePicker_opensAndCancelDismisses() {
        setScreen(LocalDate.of(2026, 6, 10))
        composeRule.onNodeWithTag("open-date-picker").performClick()
        composeRule.onNodeWithTag("date-picker-confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("date-picker-cancel").performClick()
        composeRule.onNodeWithTag("date-picker-confirm").assertDoesNotExist()
    }

    @Test
    fun datePicker_confirm_jumpsToCurrentYearOccurrence() {
        // From a page in the *next* year, the picker anchors back to today's year (D-S5-3):
        // confirming its initial selection (today) returns the pager to today's page.
        val today = LocalDate.of(2026, 12, 31)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText(formatted(LocalDate.of(2027, 1, 1))).assertIsDisplayed()
        composeRule.onNodeWithTag("open-date-picker").performClick()
        composeRule.onNodeWithTag("date-picker-confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(formatted(today)).assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertDoesNotExist()
    }
}
