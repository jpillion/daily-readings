package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
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
 * Compose UI tests for the stateless single-day content, run under Robolectric so they
 * execute in the standard testDebugUnitTest pipeline (D-S4-1). Adapted from Sprint 4's
 * TodayScreenTest; the top bar moved up into DayReadingsPagerScreen (D-S5-1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 6, 10)

    private fun scheduled(read: Set<Stream> = emptySet()): DayUiState.Scheduled =
        DayUiState.Scheduled(
            date = date,
            readings = threePortions.map { ReadingStatus(it, it.stream in read) },
            dayComplete = read == Stream.entries.toSet(),
        )

    private fun setContent(
        state: DayUiState,
        onToggleReading: (ReadingStatus) -> Unit = {},
        onReadingTapped: (Portion) -> Unit = {},
        onRetry: () -> Unit = {},
        provider: BibleProvider = BibleProvider.BLB,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayContent(
                    state = state,
                    onToggleReading = onToggleReading,
                    onReadingTapped = onReadingTapped,
                    onRetry = onRetry,
                    provider = provider,
                )
            }
        }
    }

    @Test
    fun scheduledDay_rendersThreeStreamsWithFormattedReferences() {
        setContent(scheduled())
        composeRule.onNodeWithText("Law & History").assertIsDisplayed()
        composeRule.onNodeWithText("Psalms & Prophecy").assertIsDisplayed()
        composeRule.onNodeWithText("New Testament").assertIsDisplayed()
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        composeRule.onNodeWithText("Psalms 1–2").assertIsDisplayed()
        composeRule.onNodeWithText("Matthew 1–2").assertIsDisplayed()
        // H2/H3/H4: the count line, the whole-day button, and the "All readings done" badge are all
        // gone — the three reading cards and their checkboxes are the entire scheduled surface.
        composeRule.onNodeWithText("0 of 3 readings done").assertDoesNotExist()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        composeRule.onNodeWithText("All readings done").assertDoesNotExist()
    }

    @Test
    fun checkboxClick_invokesToggleForThatReading() {
        val toggled = mutableListOf<ReadingStatus>()
        setContent(scheduled(), onToggleReading = { toggled += it })
        composeRule.onNodeWithTag("toggle-2").performClick()
        assertThat(toggled).hasSize(1)
        assertThat(toggled.single().portion.stream).isEqualTo(Stream.PSALMS_AND_PROPHECY)
    }

    @Test
    fun cardClick_invokesReadingTappedForThatPortion() {
        val tapped = mutableListOf<Portion>()
        setContent(scheduled(), onReadingTapped = { tapped += it })
        composeRule.onNodeWithTag("reading-3").performClick()
        assertThat(tapped).hasSize(1)
        assertThat(tapped.single().stream).isEqualTo(Stream.NEW_TESTAMENT)
    }

    @Test
    fun completeDay_showsNoBadgeAndNoWholeDayButton() {
        // H4: a fully-read day shows neither the "All readings done" badge nor any whole-day button;
        // the three checked checkboxes are the only completion cue (owner).
        setContent(scheduled(read = Stream.entries.toSet()))
        composeRule.onNodeWithText("All readings done").assertDoesNotExist()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        for (stream in 1..3) composeRule.onNodeWithTag("toggle-$stream").assertIsDisplayed()
    }

    @Test
    fun partialDay_showsNoProgressLineNoCompleteBadgeNoButton() {
        // S16 + H3/H4: no count line, no complete badge, and no whole-day button at any partial state.
        setContent(scheduled(read = setOf(Stream.LAW_AND_HISTORY, Stream.NEW_TESTAMENT)))
        composeRule.onNodeWithText("2 of 3 readings done").assertDoesNotExist()
        composeRule.onNodeWithText("All readings done").assertDoesNotExist()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
    }

    @Test
    fun feb29_showsNoScheduledReadingsMessage() {
        setContent(DayUiState.NoScheduledReadings(LocalDate.of(2028, 2, 29)))
        composeRule.onNodeWithText("No scheduled readings for Feb 29th").assertIsDisplayed()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle-1").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetryThatInvokesCallback() {
        var retries = 0
        setContent(DayUiState.LoadFailed(date), onRetry = { retries++ })
        composeRule.onNodeWithText("Couldn't load the reading plan").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-button").performClick()
        assertThat(retries).isEqualTo(1)
    }

    // Owner fix: the per-tile hint text reflects the *selected* "Open readings in" provider, with a
    // natural preposition per destination. Expectations are LITERAL strings (never computed from the
    // production mapping) so a wrong provider -> hint mapping reddens exactly the offending pin.
    @Test
    fun blbProvider_hintReadsOnBlueLetterBible() {
        setContent(scheduled(), provider = BibleProvider.BLB)
        composeRule.onNodeWithText("Opens Genesis 1–2 on Blue Letter Bible").assertIsDisplayed()
    }

    @Test
    fun inAppProvider_hintReadsInThisApp() {
        setContent(scheduled(), provider = BibleProvider.IN_APP)
        composeRule.onNodeWithText("Opens Genesis 1–2 in this app").assertIsDisplayed()
        // The default BLB wording must NOT leak through when in-app is selected.
        composeRule.onNodeWithText("Opens Genesis 1–2 on Blue Letter Bible").assertDoesNotExist()
    }

    @Test
    fun bibleGatewayProvider_hintReadsOnBibleGateway() {
        setContent(scheduled(), provider = BibleProvider.BIBLE_GATEWAY)
        composeRule.onNodeWithText("Opens Genesis 1–2 on Bible Gateway").assertIsDisplayed()
    }

    @Test
    fun youVersionProvider_hintReadsOnYouVersion() {
        setContent(scheduled(), provider = BibleProvider.YOUVERSION)
        composeRule.onNodeWithText("Opens Genesis 1–2 on YouVersion").assertIsDisplayed()
    }

    @Test
    fun mySwordProvider_hintReadsInMySword() {
        // The hint mirrors the *setting*, not install-aware tap-time resolution: even when MySword
        // isn't installed (tap falls back to BLB), the selected-provider hint still reads MySword.
        setContent(scheduled(), provider = BibleProvider.MYSWORD)
        composeRule.onNodeWithText("Opens Genesis 1–2 in MySword").assertIsDisplayed()
    }
}
