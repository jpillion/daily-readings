package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.core.date.ScheduleDateResolver
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.CompleteReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.CompleteTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.CompleteUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.DayCompletionClassifier
import com.jpillion.dailyreadingplanner.domain.FakeActivePlanRepository
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.FakeReadingPlanRepository
import com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase
import com.jpillion.dailyreadingplanner.domain.GetMonthCompletionUseCase
import com.jpillion.dailyreadingplanner.domain.GetPartialSegmentsUseCase
import com.jpillion.dailyreadingplanner.domain.GetReadingStatsUseCase
import com.jpillion.dailyreadingplanner.domain.GetYearStripsUseCase
import com.jpillion.dailyreadingplanner.domain.MarkReadOnOpenUseCase
import com.jpillion.dailyreadingplanner.domain.MarkSegmentReadOnOpenUseCase
import com.jpillion.dailyreadingplanner.domain.MarkWholeDayUseCase
import com.jpillion.dailyreadingplanner.domain.OpenReferenceUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveReadingDestinationPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveTrackingStartPromptUseCase
import com.jpillion.dailyreadingplanner.domain.ResolveUpgradeNoteUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleReadingUseCase
import com.jpillion.dailyreadingplanner.domain.ToggleSegmentCheckUseCase
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.FakePartialReadingRepository
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.bcReadingStats
import com.jpillion.dailyreadingplanner.testing.bcYearStrips
import com.jpillion.dailyreadingplanner.testing.singleSegmentStates
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import com.jpillion.dailyreadingplanner.ui.stats.StatsPanelUiState
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

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
                        segments = singleSegmentStates(threePortions),
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
        onSegmentTapped: (LocalDate, ReadingSegmentUiState) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayReadingsPagerScreen(
                    today = today,
                    uiStateFor = ::stateFor,
                    monthCompletionFor = { MutableStateFlow(emptyMap()) },
                    statsPanel = statsPanel,
                    onToggleSegment = { date, segment -> toggleCalls += date to segment.streamNumber },
                    onSegmentTapped = onSegmentTapped,
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
        composeRule.onNodeWithTag("toggle-2-0").performClick()
        assertThat(toggleCalls).containsExactly(today.plusDays(1) to 2)
    }

    @Test
    fun leapYear_swipingFromFeb28_hitsFeb29NoReadings_thenMar1() {
        val today = LocalDate.of(2028, 2, 28)
        setScreen(today)
        swipeToNextDay()
        composeRule.onNodeWithText("No scheduled readings for Feb 29th").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-1-0").assertDoesNotExist()
        swipeToNextDay()
        composeRule.onNodeWithText("Wednesday, March 1").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-1-0").assertExists()
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
                    onToggleSegment = { _, _ -> },
                    onSegmentTapped = { _, _ -> },
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

    // --- Sprint 00O (T3): the pager wrapper carries the page's date into onSegmentTapped. ---

    @Test
    fun readingCardTap_onTodayPage_carriesTodaysDateAndSegment() {
        // Pins the `{ segment -> onSegmentTapped(date, segment) }` wrapper: a tap on the
        // displayed (today) page must invoke the callback with TODAY's date and that card's
        // portion. A dropped/swapped date in the wrapper fails this.
        val today = LocalDate.of(2026, 6, 10)
        val taps = mutableListOf<Pair<LocalDate, Int>>()
        setScreen(today, onSegmentTapped = { date, segment -> taps += date to segment.streamNumber })
        composeRule.onNodeWithTag("reading-3-0").performClick()
        assertThat(taps).hasSize(1)
        assertThat(taps.single()).isEqualTo(today to 3)
    }

    @Test
    fun readingCardTap_afterSwipingToNextDay_carriesThatPageActualDate() {
        // T3 mutation guard with teeth: after swiping forward a day, a card tap must carry the
        // NEXT day's date — not `today`. Replacing `date` with `today` in the pager wrapper
        // (or in DayReadingsScreen's lambda) reddens this.
        val today = LocalDate.of(2026, 6, 10)
        val tomorrow = today.plusDays(1)
        val taps = mutableListOf<Pair<LocalDate, Int>>()
        setScreen(today, onSegmentTapped = { date, segment -> taps += date to segment.streamNumber })
        swipeToNextDay()
        composeRule.onNodeWithText("Thursday, June 11").assertIsDisplayed()
        composeRule.onNodeWithTag("reading-1-0").performClick()
        assertThat(taps).hasSize(1)
        assertThat(taps.single()).isEqualTo(tomorrow to 1)
    }

    // --- Sprint 00O end-to-end: a card tap through the REAL ViewModel marks the reading read
    // and the checkbox re-renders checked (mark-on-open, D-O-1). Proves T1+T2+T3 together. ---

    @Test
    fun readingCardTap_throughRealViewModel_marksReadAndChecksTheBox() {
        val today = LocalDate.of(2026, 6, 10)
        val progress = FakeProgressRepository()
        val vm = realViewModel(today, progress)
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayReadingsPagerScreen(
                    today = vm.today,
                    uiStateFor = vm::uiStateFor,
                    monthCompletionFor = vm::monthCompletionFor,
                    statsPanel = null,
                    onToggleSegment = vm::onToggleSegment,
                    onSegmentTapped = vm::onSegmentTapped,
                    onRetry = vm::onRetry,
                    onOpenSettings = {},
                )
            }
        }
        // Stream 2's checkbox starts unchecked, then a card tap marks it read live.
        composeRule.onNodeWithTag("toggle-2-0").assertIsOff()
        composeRule.onNodeWithTag("reading-2-0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("toggle-2-0").assertIsOn()
        assertThat(progress.marksFor(today)).containsExactly(2)
    }

    /** A real DayReadingsViewModel over the fakes — mirrors DayReadingsViewModelTest's harness. */
    private fun realViewModel(
        today: LocalDate,
        progress: FakeProgressRepository,
    ): DayReadingsViewModel {
        val resolver = ScheduleDateResolver()
        val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        val classifier = DayCompletionClassifier(resolver)
        val activePlan = FakeActivePlanRepository()
        // EXTERNAL/BLB so a tap resolves a Web destination (no in-app handoff needed here); the
        // already-initialized marker keeps first-run dialogs out of the way.
        val settings = FakeSettingsRepository().apply { storedTrackingStartInitialized.value = true }
        val partials = FakePartialReadingRepository()
        return DayReadingsViewModel(
            getDayReadings = GetDayReadingsUseCase(resolver, FakeReadingPlanRepository(), progress, activePlan),
            getMonthCompletion = GetMonthCompletionUseCase(classifier, progress, settings, activePlan, clock),
            getPartialSegments = GetPartialSegmentsUseCase(partials, activePlan),
            toggleSegmentCheck =
                ToggleSegmentCheckUseCase(ToggleReadingUseCase(progress, activePlan), partials, activePlan),
            markSegmentReadOnOpen =
                MarkSegmentReadOnOpenUseCase(MarkReadOnOpenUseCase(progress, activePlan), partials, activePlan),
            markWholeDay = MarkWholeDayUseCase(progress, activePlan),
            openReference = OpenReferenceUseCase(settings, ProviderUrlBuilder()),
            widgetRefresher = FakeWidgetRefresher(),
            readerHandoff = ReaderHandoff(),
            completeTrackingStartPrompt = CompleteTrackingStartPromptUseCase(settings),
            resolveTrackingStartPrompt = ResolveTrackingStartPromptUseCase(settings, progress),
            completeReadingDestinationPrompt = CompleteReadingDestinationPromptUseCase(settings),
            resolveReadingDestinationPrompt = ResolveReadingDestinationPromptUseCase(settings, progress),
            completeUpgradeNote = CompleteUpgradeNoteUseCase(settings),
            resolveUpgradeNote = ResolveUpgradeNoteUseCase(settings, progress),
            getReadingStats = GetReadingStatsUseCase(classifier, progress, settings, activePlan, clock),
            getYearStrips = GetYearStripsUseCase(classifier, progress, settings, activePlan, clock),
            settingsRepository = settings,
            clock = clock,
        )
    }
}
