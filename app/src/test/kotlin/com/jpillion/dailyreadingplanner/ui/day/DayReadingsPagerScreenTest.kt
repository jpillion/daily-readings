package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.bcReadingStats
import com.jpillion.dailyreadingplanner.testing.bcYearStrips
import com.jpillion.dailyreadingplanner.ui.stats.StatsPanelUiState
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

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

    private val toggleCalls = mutableListOf<Pair<LocalDate, Int>>()
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

    private var openSettingsCalls = 0

    private val sampleStats =
        bcReadingStats(
            currentStreakDays = 4,
            longestStreakDays = 12,
            yearReadCount = 438,
            streamReadCounts = mapOf(1 to 150, 2 to 144, 3 to 144),
        )

    /** S17: neutral strips are enough for panel-level tests; StatsContentTest pins states. */
    private fun sampleStrips(today: LocalDate) =
        bcYearStrips(year = today.year, todayIndex = today.dayOfYear - 1) {
            List(today.lengthOfYear()) { StripDayState.NEUTRAL }
        }

    private fun setScreen(
        today: LocalDate,
        statsPanel: StatsPanelUiState? = null,
        onReadingTapped: (Portion) -> Unit = {},
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayReadingsPagerScreen(
                    today = today,
                    uiStateFor = ::stateFor,
                    monthCompletionFor = { MutableStateFlow(emptyMap()) },
                    statsPanel = statsPanel,
                    onToggleReading = { date, reading -> toggleCalls += date to reading.portion.streamNumber },
                    onReadingTapped = onReadingTapped,
                    onRetry = {},
                    onOpenSettings = { openSettingsCalls++ },
                )
            }
        }
    }

    // D-S16-1 title pins are LITERAL strings (never computed via the production
    // formatters, so a formatter mutation cannot rewrite the expectation with it).

    private fun swipeToNextDay() {
        composeRule.onNodeWithTag("day-pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    private fun swipeToPreviousDay() {
        composeRule.onNodeWithTag("day-pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
    }

    @Test
    fun launch_showsSingleLineTodayTitle_withoutJumpAffordance() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        // D-S16-1: one line — "Today \u2013 June 10"; no separate heading, no year.
        composeRule.onNodeWithText("Today \u2013 June 10").assertIsDisplayed()
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertDoesNotExist()
    }

    @Test
    fun swipeLeft_showsTomorrow_andJumpToTodayReturns() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        swipeToNextDay()
        // D-S16-1: no "Readings" heading — just the date, year omitted (same year as today).
        composeRule.onNodeWithText("Readings").assertDoesNotExist()
        composeRule.onNodeWithText("Thursday, June 11").assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today \u2013 June 10").assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertDoesNotExist()
    }

    @Test
    fun swipeRight_showsYesterday() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        swipeToPreviousDay()
        composeRule.onNodeWithText("Tuesday, June 9").assertIsDisplayed()
    }

    @Test
    fun toggleCallbacks_carryTheDisplayedDate_notToday() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithTag("toggle-2").performClick()
        assertThat(toggleCalls).containsExactly(today.plusDays(1) to 2)
    }

    @Test
    fun leapYear_swipingFromFeb28_hitsFeb29NoReadings_thenMar1() {
        val today = LocalDate.of(2028, 2, 28)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText("No scheduled readings for Feb 29th").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-1").assertDoesNotExist()
        swipeToNextDay()
        composeRule.onNodeWithText("Wednesday, March 1").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-1").assertExists()
    }

    @Test
    fun yearBoundary_swipingFromDec31_landsOnJan1OfNextYear() {
        val today = LocalDate.of(2026, 12, 31)
        setScreen(today)
        swipeToNextDay()
        // D-S16-1: a different year than today's IS shown in the title.
        composeRule.onNodeWithText("Friday, January 1, 2027").assertIsDisplayed()
    }

    @Test
    fun settingsAction_invokesOnOpenSettings() {
        setScreen(LocalDate.of(2026, 6, 10))
        composeRule.onNodeWithTag("open-settings").assertIsDisplayed().performClick()
        assertThat(openSettingsCalls).isEqualTo(1)
    }

    // --- Sprint 15 (D-S15-4): the inline stats panel ---

    @Test
    fun statsPanel_rendersBelowTheReadings_onceNotPerPage() {
        val today = LocalDate.of(2026, 6, 10)
        setScreen(today, statsPanel = StatsPanelUiState(sampleStats, showStreaks = true, strips = sampleStrips(today)))
        composeRule.onNodeWithTag("stats-panel").assertExists()
        composeRule.onNodeWithText("Current streak").assertExists()
        composeRule.onNodeWithText("This year").assertExists()
        // The readings stay the focus: the day content is still displayed above the panel.
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        // Swiping to another day keeps the same year-level panel.
        swipeToNextDay()
        composeRule.onNodeWithTag("stats-panel").assertExists()
        composeRule.onNodeWithText("438 of 1,095 readings").assertExists()
    }

    @Test
    fun statsPanel_absentUntilStatsExist_andNoStatsTopBarAction() {
        setScreen(LocalDate.of(2026, 6, 10), statsPanel = null)
        composeRule.onNodeWithTag("stats-panel").assertDoesNotExist()
        // S15: the stats route/icon is gone — the panel IS the stats surface.
        composeRule.onNodeWithTag("open-stats").assertDoesNotExist()
    }

    @Test
    fun statsPanel_hidesStreakRows_whenShowStreaksIsOff() {
        // D-S15-5: streaks off hides ONLY the two streak rows; year + stream remain.
        setScreen(
            LocalDate.of(2026, 6, 10),
            statsPanel =
                StatsPanelUiState(
                    sampleStats,
                    showStreaks = false,
                    strips = sampleStrips(LocalDate.of(2026, 6, 10)),
                ),
        )
        composeRule.onNodeWithText("Current streak").assertDoesNotExist()
        composeRule.onNodeWithText("Longest streak").assertDoesNotExist()
        composeRule.onNodeWithText("This year").assertExists()
        // ("Law & History" also appears on the reading card, so the panel's stream rows
        // are asserted by tag.)
        composeRule.onNodeWithTag("stats-stream-1").assertExists()
    }

    @Test
    fun datePicker_opensAndCancelDismisses() {
        setScreen(LocalDate.of(2026, 6, 10))
        composeRule.onNodeWithTag("open-date-picker").performClick()
        composeRule.onNodeWithTag("picker-month-pager").assertIsDisplayed()
        composeRule.onNodeWithTag("date-picker-cancel").performClick()
        composeRule.onNodeWithTag("picker-month-pager").assertDoesNotExist()
    }

    @Test
    fun datePicker_oneTap_selectsDayAndNavigatesPager() {
        // BACKLOG #7: one tap on a day cell closes the dialog and jumps the pager to that date.
        setScreen(LocalDate.of(2026, 6, 10))
        composeRule.onNodeWithTag("open-date-picker").performClick()
        composeRule.onNodeWithTag("picker-day-20").performClick()
        composeRule.waitForIdle()
        // Dialog closed and the pager moved to June 20.
        composeRule.onNodeWithTag("picker-month-pager").assertDoesNotExist()
        composeRule.onNodeWithText("Saturday, June 20").assertIsDisplayed()
        composeRule.onNodeWithTag("jump-to-today").assertIsDisplayed()
    }

    @Test
    fun datePicker_opensOnDisplayedDate_acrossYearBoundary() {
        // BACKLOG #6: the picker is no longer year-anchored — from a next-year page it opens on
        // that month/year, and a one-tap pick navigates within that year.
        val today = LocalDate.of(2026, 12, 31)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText("Friday, January 1, 2027").assertIsDisplayed()
        composeRule.onNodeWithTag("open-date-picker").performClick()
        composeRule.onNodeWithText("January 2027").assertIsDisplayed()
        composeRule.onNodeWithTag("picker-day-5").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tuesday, January 5, 2027").assertIsDisplayed()
    }

    // --- S19 (D-S19-2): the first-run tracking-start prompt renders over the day screen. ---

    @Test
    fun `tracking-start prompt renders over the pager only when flagged`() {
        val today = LocalDate.of(2026, 6, 10)
        var dismissCalls = 0
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayReadingsPagerScreen(
                    today = today,
                    uiStateFor = ::stateFor,
                    monthCompletionFor = { MutableStateFlow(emptyMap()) },
                    statsPanel = null,
                    onToggleReading = { _, _ -> },
                    onReadingTapped = {},
                    onRetry = {},
                    onOpenSettings = {},
                    showTrackingStartPrompt = true,
                    onTrackingStartChosen = {},
                    onTrackingStartPromptDismissed = { dismissCalls++ },
                )
            }
        }
        composeRule.onNodeWithTag("tracking-start-prompt").assertIsDisplayed()
        // The day screen itself still renders underneath (same-day pager intact).
        composeRule.onNodeWithTag("day-pager").assertIsDisplayed()
        assertThat(dismissCalls).isEqualTo(0)
    }

    @Test
    fun `tracking-start prompt is absent by default`() {
        setScreen(today = LocalDate.of(2026, 6, 10))
        composeRule.onNodeWithTag("tracking-start-prompt").assertDoesNotExist()
    }
}
