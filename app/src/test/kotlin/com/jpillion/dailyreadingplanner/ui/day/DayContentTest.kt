package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.bcStreamDescriptors
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

    private fun scheduled(read: Set<Int> = emptySet()): DayUiState.Scheduled =
        DayUiState.Scheduled(
            date = date,
            readings =
                threePortions.map { portion ->
                    ReadingStatus(
                        portion = portion,
                        isRead = portion.streamNumber in read,
                        // D-ALT-22: the title is plan data carried onto the reading by the use case.
                        streamTitle = bcStreamDescriptors.first { it.number == portion.streamNumber }.title,
                    )
                },
            dayComplete = read == setOf(1, 2, 3),
        )

    private fun setContent(
        state: DayUiState,
        onToggleReading: (ReadingStatus) -> Unit = {},
        onReadingTapped: (Portion) -> Unit = {},
        onRetry: () -> Unit = {},
        destinationMode: ReadingDestinationMode = ReadingDestinationMode.EXTERNAL,
        externalApp: ExternalBibleApp = ExternalBibleApp.BLB,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayContent(
                    state = state,
                    onToggleReading = onToggleReading,
                    onReadingTapped = onReadingTapped,
                    onRetry = onRetry,
                    destinationMode = destinationMode,
                    externalApp = externalApp,
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
        assertThat(toggled.single().portion.streamNumber).isEqualTo(2)
    }

    @Test
    fun cardClick_invokesReadingTappedForThatPortion() {
        val tapped = mutableListOf<Portion>()
        setContent(scheduled(), onReadingTapped = { tapped += it })
        composeRule.onNodeWithTag("reading-3").performClick()
        assertThat(tapped).hasSize(1)
        assertThat(tapped.single().streamNumber).isEqualTo(3)
    }

    @Test
    fun completeDay_showsNoBadgeAndNoWholeDayButton() {
        // H4: a fully-read day shows neither the "All readings done" badge nor any whole-day button;
        // the three checked checkboxes are the only completion cue (owner).
        setContent(scheduled(read = setOf(1, 2, 3)))
        composeRule.onNodeWithText("All readings done").assertDoesNotExist()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        for (stream in 1..3) composeRule.onNodeWithTag("toggle-$stream").assertIsDisplayed()
    }

    @Test
    fun partialDay_showsNoProgressLineNoCompleteBadgeNoButton() {
        // S16 + H3/H4: no count line, no complete badge, and no whole-day button at any partial state.
        setContent(scheduled(read = setOf(1, 3)))
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

    // Sprint K (D-23-1): the per-tile hint reflects the EFFECTIVE destination — the in-app reader
    // for in-app mode, otherwise the chosen external app, with a natural preposition. Expectations are
    // LITERAL strings (never computed from the production mapping) so a wrong destination -> hint
    // mapping reddens exactly the offending pin.
    @Test
    fun blbApp_hintReadsOnBlueLetterBible() {
        setContent(scheduled(), destinationMode = ReadingDestinationMode.EXTERNAL, externalApp = ExternalBibleApp.BLB)
        composeRule.onNodeWithText("Opens Genesis 1–2 on Blue Letter Bible").assertIsDisplayed()
    }

    @Test
    fun inAppMode_hintReadsInThisApp() {
        // MUTATION (reader-hint provider mapping): in-app MODE reads "in this app" regardless of the
        // remembered external app — here MySword is stored but the hint must ignore it. A mutation
        // that maps in-app mode to an external hint reddens here.
        setContent(scheduled(), destinationMode = ReadingDestinationMode.IN_APP, externalApp = ExternalBibleApp.MYSWORD)
        composeRule.onNodeWithText("Opens Genesis 1–2 in this app").assertIsDisplayed()
        composeRule.onNodeWithText("Opens Genesis 1–2 on Blue Letter Bible").assertDoesNotExist()
        composeRule.onNodeWithText("Opens Genesis 1–2 in MySword").assertDoesNotExist()
    }

    @Test
    fun bibleGatewayApp_hintReadsOnBibleGateway() {
        setContent(
            scheduled(),
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalApp = ExternalBibleApp.BIBLE_GATEWAY,
        )
        composeRule.onNodeWithText("Opens Genesis 1–2 on Bible Gateway").assertIsDisplayed()
    }

    @Test
    fun youVersionApp_hintReadsOnYouVersion() {
        setContent(
            scheduled(),
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalApp = ExternalBibleApp.YOUVERSION,
        )
        composeRule.onNodeWithText("Opens Genesis 1–2 on YouVersion").assertIsDisplayed()
    }

    @Test
    fun mySwordApp_hintReadsInMySword() {
        // The hint mirrors the *setting*, not install-aware tap-time resolution: even when MySword
        // isn't installed (tap falls back to BLB), the selected external-app hint still reads MySword.
        setContent(
            scheduled(),
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalApp = ExternalBibleApp.MYSWORD,
        )
        composeRule.onNodeWithText("Opens Genesis 1–2 in MySword").assertIsDisplayed()
    }

    // --- SC-T10 (D-ALT-9/22/23): the card surface renders the active plan's ACTUAL stream count ---

    @Test
    fun nFourPlan_rendersFourCardsWithTheirPlanSuppliedTitles() {
        // A 4-stream M'Cheyne day: four cards, each titled from plan data (carried on the reading).
        // A mutation that renders a fixed three would drop the fourth card/title -> reddens here.
        val mcheyneTitles =
            listOf("Family — Old Testament", "Family — Gospels", "Secret — Psalms & Prophets", "Secret — Epistles")
        val state =
            DayUiState.Scheduled(
                date = date,
                readings =
                    (1..4).map { n ->
                        ReadingStatus(
                            portion = portion(n, "Genesis" to n),
                            isRead = false,
                            streamTitle = mcheyneTitles[n - 1],
                        )
                    },
                dayComplete = false,
            )
        setContent(state)
        mcheyneTitles.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        for (n in 1..4) composeRule.onNodeWithTag("reading-$n").assertExists()
    }

    @Test
    fun nOnePlan_rendersOneCardWithNoStreamLabel() {
        // D-ALT-23: a single-stream plan supplies a null title; the lone reading renders the
        // reference ALONE — no "which stream" label. A mutation that always renders a label
        // (e.g. a non-null fallback) would surface an unexpected title node.
        val state =
            DayUiState.Scheduled(
                date = date,
                readings =
                    listOf(
                        ReadingStatus(portion(1, "Genesis" to 1, "Genesis" to 2), isRead = false, streamTitle = null),
                    ),
                dayComplete = false,
            )
        setContent(state)
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        composeRule.onNodeWithTag("reading-1").assertExists()
        // None of the Bible-Companion stream titles appear for a single-stream plan.
        composeRule.onNodeWithText("Law & History").assertDoesNotExist()
    }
}
