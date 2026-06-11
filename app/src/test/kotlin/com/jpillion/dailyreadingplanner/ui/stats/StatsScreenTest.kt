package com.jpillion.dailyreadingplanner.ui.stats

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ReadingStats
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S11: the four sober stat groups (PRD §13.1, FR-15/FR-16). Read-only — the only action is
 * back. The "no guilt mechanics" contract is pinned here: no copy on the screen ever
 * mentions missing/failure, whatever the numbers say.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsScreenTest {
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

    private fun setScreen(
        stats: ReadingStats? = sampleStats,
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                StatsScreen(stats = stats, onBack = onBack)
            }
        }
    }

    @Test
    fun rendersAllFourStatGroups() {
        setScreen()
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
    fun singleDayStreakUsesSingularCopy() {
        setScreen(
            stats =
                sampleStats.copy(currentStreakDays = 1, longestStreakDays = 1),
        )
        composeRule.onAllNodes(textContains("1 day")).assertCountEquals(2)
        composeRule.onAllNodes(textContains("1 days")).assertCountEquals(0)
    }

    @Test
    fun percentRoundsDown_neverClaimsCompletionEarly() {
        // D-S11-4: 1,094 of 1,095 readings is 99%, not 100%.
        setScreen(stats = sampleStats.copy(yearReadCount = 1_094))
        composeRule.onNodeWithText("99%").assertExists()
        composeRule.onNodeWithText("1,094 of 1,095 readings").assertExists()
    }

    @Test
    fun fullYearShowsExactlyOneHundredPercent() {
        setScreen(stats = sampleStats.copy(yearReadCount = 1_095))
        composeRule.onNodeWithText("100%").assertExists()
    }

    @Test
    fun zeroStats_renderPlainZeros_withoutGuiltCopy() {
        setScreen(
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
    fun noCopyOnTheScreenMentionsMissingOrFailure() {
        setScreen(stats = sampleStats.copy(currentStreakDays = 0))
        assertNoGuiltCopy()
    }

    @Test
    fun backActionInvokesCallback() {
        var backCalls = 0
        setScreen(onBack = { backCalls++ })
        composeRule.onNodeWithTag("stats-back").performClick()
        assertThat(backCalls).isEqualTo(1)
    }

    @Test
    fun nullStatsRendersOnlyTheTopBar() {
        setScreen(stats = null)
        composeRule.onNodeWithText("Stats").assertExists()
        composeRule.onNodeWithText("Current streak").assertDoesNotExist()
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
