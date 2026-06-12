package com.jpillion.dailyreadingplanner.ui.stats

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S11 sober stat groups (PRD §13.1, FR-15/FR-16), rendered inline since S15 (D-S15-4).
 * The "no guilt mechanics" contract is pinned here: no copy ever mentions missing/failure,
 * whatever the numbers say — and it must keep holding wherever the stats render.
 * D-S15-5: the streak rows are gated by showStreaks; year + stream always show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleStats =
        ReadingStats(
            currentStreakDays = 4,
            longestStreakDays = 12,
            yearReadCount = 438,
            streamReadCounts =
                mapOf(
                    Stream.LAW_AND_HISTORY to 150,
                    Stream.PSALMS_AND_PROPHECY to 144,
                    Stream.NEW_TESTAMENT to 144,
                ),
        )

    private fun setContent(
        stats: ReadingStats = sampleStats,
        showStreaks: Boolean = true,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                StatsContent(stats = stats, showStreaks = showStreaks)
            }
        }
    }

    @Test
    fun rendersAllFourStatGroups() {
        setContent()
        composeRule.onNodeWithText("Current streak").assertExists()
        composeRule.onNodeWithText("4 days").assertExists()
        composeRule.onNodeWithText("Longest streak").assertExists()
        composeRule.onNodeWithText("12 days").assertExists()
        composeRule.onNodeWithText("This year").assertExists()
        composeRule.onNodeWithText("40%").assertExists() // 438 * 100 / 1095 = 40
        composeRule.onNodeWithText("438 of 1,095 readings").assertExists()
        composeRule.onNodeWithText("By stream").assertExists()
        composeRule.onNodeWithText("Law & History").assertExists()
        composeRule.onNodeWithText("Psalms & Prophecy").assertExists()
        composeRule.onNodeWithText("New Testament").assertExists()
        composeRule.onNodeWithText("150 of 365").assertExists()
    }

    @Test
    fun showStreaksOff_hidesOnlyTheStreakRows() {
        // D-S15-5: display-only gate — year and per-stream progress always remain.
        setContent(showStreaks = false)
        composeRule.onNodeWithTag("stats-current-streak").assertDoesNotExist()
        composeRule.onNodeWithTag("stats-longest-streak").assertDoesNotExist()
        composeRule.onNodeWithText("Current streak").assertDoesNotExist()
        composeRule.onNodeWithText("Longest streak").assertDoesNotExist()
        composeRule.onNodeWithText("This year").assertExists()
        composeRule.onNodeWithText("By stream").assertExists()
    }

    @Test
    fun singleDayStreakUsesSingularCopy() {
        setContent(stats = sampleStats.copy(currentStreakDays = 1, longestStreakDays = 1))
        composeRule.onAllNodes(textContains("1 day")).assertCountEquals(2)
        composeRule.onAllNodes(textContains("1 days")).assertCountEquals(0)
    }

    @Test
    fun percentRoundsDown_neverClaimsCompletionEarly() {
        // D-S11-4: 1,094 of 1,095 readings is 99%, not 100%.
        setContent(stats = sampleStats.copy(yearReadCount = 1_094))
        composeRule.onNodeWithText("99%").assertExists()
        composeRule.onNodeWithText("1,094 of 1,095 readings").assertExists()
    }

    @Test
    fun fullYearShowsExactlyOneHundredPercent() {
        setContent(stats = sampleStats.copy(yearReadCount = 1_095))
        composeRule.onNodeWithText("100%").assertExists()
    }

    @Test
    fun zeroStats_renderPlainZeros_withoutGuiltCopy() {
        setContent(
            stats =
                ReadingStats(
                    currentStreakDays = 0,
                    longestStreakDays = 0,
                    yearReadCount = 0,
                    streamReadCounts = Stream.entries.associateWith { 0 },
                ),
        )
        composeRule.onAllNodes(textContains("0 days")).assertCountEquals(2)
        composeRule.onNodeWithText("0%").assertExists()
        assertNoGuiltCopy()
    }

    @Test
    fun noCopyMentionsMissingOrFailure_streaksOnOrOff() {
        setContent(stats = sampleStats.copy(currentStreakDays = 0))
        assertNoGuiltCopy()
    }

    /** FR-16 / §13.0: missed days are never called out — no shame copy anywhere. */
    private fun assertNoGuiltCopy() {
        for (banned in listOf("miss", "Miss", "broke", "Broke", "fail", "Fail", "behind", "Behind")) {
            composeRule.onAllNodes(textContains(banned)).assertCountEquals(0)
        }
    }

    private fun textContains(substring: String): SemanticsMatcher =
        SemanticsMatcher("text contains '$substring'") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(substring) } == true
        }
}
