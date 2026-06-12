package com.jpillion.dailyreadingplanner.ui.stats

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.domain.model.StripDayState
import com.jpillion.dailyreadingplanner.domain.model.YearStrips
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S11 sober stat groups (PRD §13.1, FR-15/FR-16), rendered inline since S15 (D-S15-4).
 * The "no guilt mechanics" contract is pinned here, deliberately AMENDED by D-S17-1: the
 * year strips may paint red segments (information, not commentary), but no COPY ever
 * mentions missing/failure — and since S17 that ban extends to contentDescriptions too
 * (the spoken summaries say "not read", D-S17-3).
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

    /** Per stream: 120 READ, 3 MISSED, 242 NEUTRAL — exact spoken-summary fixture. */
    private val sampleStrips =
        YearStrips(
            year = 2026,
            todayIndex = 160,
            dayStates =
                Stream.entries.associateWith {
                    List(365) { index ->
                        when {
                            index < 120 -> StripDayState.READ
                            index < 123 -> StripDayState.MISSED
                            else -> StripDayState.NEUTRAL
                        }
                    }
                },
        )

    private fun setContent(
        stats: ReadingStats = sampleStats,
        strips: YearStrips = sampleStrips,
        showStreaks: Boolean = true,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                StatsContent(stats = stats, strips = strips, showStreaks = showStreaks)
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
        // S18: the "By stream" section header was deliberately removed (vertical budget);
        // the stream rows themselves are the group.
        composeRule.onNodeWithText("By stream").assertDoesNotExist()
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
        composeRule.onNodeWithText("Law & History").assertExists()
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

    // --- S17: year strips replace the progress bars (D-S17-1/2/3) ---

    @Test
    fun stripsReplaceTheBars_perStreamAndStackedYear() {
        setContent()
        // One strip per stream plus the stacked year heat-strip; the old bars are gone.
        Stream.entries.forEach { stream ->
            composeRule
                .onNodeWithTag("strip-stream-${stream.number}", useUnmergedTree = true)
                .assertExists()
        }
        composeRule.onNodeWithTag("strip-year", useUnmergedTree = true).assertExists()
        composeRule.onAllNodes(hasProgressBarSemantics()).assertCountEquals(0)
        // The numeric copy the strips sit beside is unchanged.
        composeRule.onNodeWithText("438 of 1,095 readings").assertExists()
        composeRule.onNodeWithText("150 of 365").assertExists()
    }

    @Test
    fun stripSummaries_speakExactCounts_sayingNotReadNeverMissed() {
        // D-S17-3: color-only strips carry spoken summaries; "not read", never "missed".
        setContent()
        composeRule
            .onNodeWithContentDescription("Law & History: 120 read, 3 not read, 242 upcoming")
            .assertExists()
        composeRule
            .onNodeWithContentDescription("New Testament: 120 read, 3 not read, 242 upcoming")
            .assertExists()
        // The stacked year view speaks ONE combined summary across all three streams.
        composeRule
            .onNodeWithContentDescription("All streams: 360 read, 9 not read, 726 upcoming")
            .assertExists()
        assertNoGuiltCopy()
    }

    /**
     * FR-16 / §13.0 as amended by D-S17-1: red strip *segments* are allowed; missed-day
     * COPY is not — in text or in speech (contentDescriptions included since S17).
     */
    private fun assertNoGuiltCopy() {
        for (banned in listOf("miss", "Miss", "broke", "Broke", "fail", "Fail", "behind", "Behind")) {
            composeRule.onAllNodes(textContains(banned)).assertCountEquals(0)
            composeRule
                .onAllNodes(contentDescriptionContains(banned), useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    private fun textContains(substring: String): SemanticsMatcher =
        SemanticsMatcher("text contains '$substring'") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(substring) } == true
        }

    private fun contentDescriptionContains(substring: String): SemanticsMatcher =
        SemanticsMatcher("contentDescription contains '$substring'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(substring) } == true
        }

    private fun hasProgressBarSemantics(): SemanticsMatcher =
        SemanticsMatcher("has ProgressBarRangeInfo") { node ->
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null
        }
}
