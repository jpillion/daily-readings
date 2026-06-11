package com.jpillion.dailyreadingplanner.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Compose UI tests for the stateless TodayScreen, run under Robolectric so they execute in
 * the standard testDebugUnitTest pipeline (D-S4-1; EXECUTION_PLAN §5.2 Sprint 4 gate).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 6, 10)

    private fun scheduled(read: Set<Stream> = emptySet()): TodayUiState.Scheduled =
        TodayUiState.Scheduled(
            date = date,
            readings = threePortions.map { ReadingStatus(it, it.stream in read) },
            dayComplete = read == Stream.entries.toSet(),
        )

    private fun setScreen(
        state: TodayUiState,
        onToggleReading: (ReadingStatus) -> Unit = {},
        onMarkWholeDay: () -> Unit = {},
        onReadingTapped: (Portion) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                TodayScreen(
                    state = state,
                    onToggleReading = onToggleReading,
                    onMarkWholeDay = onMarkWholeDay,
                    onReadingTapped = onReadingTapped,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun scheduledDay_rendersThreeStreamsWithFormattedReferences() {
        setScreen(scheduled())
        composeRule.onNodeWithText("Law & History").assertIsDisplayed()
        composeRule.onNodeWithText("Psalms & Prophecy").assertIsDisplayed()
        composeRule.onNodeWithText("New Testament").assertIsDisplayed()
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        composeRule.onNodeWithText("Psalms 1–2").assertIsDisplayed()
        composeRule.onNodeWithText("Matthew 1–2").assertIsDisplayed()
        composeRule.onNodeWithText("0 of 3 readings done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun checkboxClick_invokesToggleForThatReading() {
        val toggled = mutableListOf<ReadingStatus>()
        setScreen(scheduled(), onToggleReading = { toggled += it })
        composeRule.onNodeWithTag("toggle-2").performClick()
        assertThat(toggled).hasSize(1)
        assertThat(toggled.single().portion.stream).isEqualTo(Stream.PSALMS_AND_PROPHECY)
    }

    @Test
    fun cardClick_invokesReadingTappedForThatPortion() {
        val tapped = mutableListOf<Portion>()
        setScreen(scheduled(), onReadingTapped = { tapped += it })
        composeRule.onNodeWithTag("reading-3").performClick()
        assertThat(tapped).hasSize(1)
        assertThat(tapped.single().stream).isEqualTo(Stream.NEW_TESTAMENT)
    }

    @Test
    fun wholeDayButton_invokesMarkWholeDay_singleTap() {
        var taps = 0
        setScreen(scheduled(), onMarkWholeDay = { taps++ })
        composeRule.onNodeWithTag("whole-day-button").performScrollTo().performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun completeDay_showsDoneIndicatorAndUnmarkLabel() {
        setScreen(scheduled(read = Stream.entries.toSet()))
        composeRule.onNodeWithText("All readings done for today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Unmark whole day").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun partialDay_showsProgressCount() {
        setScreen(scheduled(read = setOf(Stream.LAW_AND_HISTORY, Stream.NEW_TESTAMENT)))
        composeRule.onNodeWithText("2 of 3 readings done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun feb29_showsNoScheduledReadingsMessage() {
        setScreen(TodayUiState.NoScheduledReadings(LocalDate.of(2028, 2, 29)))
        composeRule.onNodeWithText("No scheduled readings for Feb 29th").assertIsDisplayed()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle-1").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetryThatInvokesCallback() {
        var retries = 0
        setScreen(TodayUiState.LoadFailed(date), onRetry = { retries++ })
        composeRule.onNodeWithText("Couldn't load the reading plan").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-button").performClick()
        assertThat(retries).isEqualTo(1)
    }
}
