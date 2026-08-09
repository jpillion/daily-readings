package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.portion
import com.jpillion.dailyreadingplanner.domain.threePortions
import com.jpillion.dailyreadingplanner.testing.bcStreamDescriptors
import com.jpillion.dailyreadingplanner.testing.singleSegmentStates
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private val date = LocalDate(2026, 6, 10)

    private fun scheduled(read: Set<Int> = emptySet()): DayUiState.Scheduled =
        DayUiState.Scheduled(
            date = date,
            // sprint-00P: each Bible-Companion reading is ONE contiguous passage, so the familiar
            // three cards are three single-segment card states (segmentIndex 0, segmentCount 1).
            segments =
                singleSegmentStates(
                    portions = threePortions,
                    read = read,
                    // D-ALT-22: the title is plan data carried onto the reading by the use case.
                    titleFor = { stream -> bcStreamDescriptors.first { it.number == stream }.title },
                ),
            dayComplete = read == setOf(1, 2, 3),
        )

    private fun setContent(
        state: DayUiState,
        onToggleSegment: (ReadingSegmentUiState) -> Unit = {},
        onSegmentTapped: (ReadingSegmentUiState) -> Unit = {},
        onRetry: () -> Unit = {},
        destinationMode: ReadingDestinationMode = ReadingDestinationMode.EXTERNAL,
        externalApp: ExternalBibleApp = ExternalBibleApp.BLB,
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                DayContent(
                    state = state,
                    onToggleSegment = onToggleSegment,
                    onSegmentTapped = onSegmentTapped,
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
    fun checkboxClick_invokesToggleForThatSegment() {
        val toggled = mutableListOf<ReadingSegmentUiState>()
        setContent(scheduled(), onToggleSegment = { toggled += it })
        composeRule.onNodeWithTag("toggle-2-0").performClick()
        assertThat(toggled).hasSize(1)
        assertThat(toggled.single().streamNumber).isEqualTo(2)
        assertThat(toggled.single().segmentIndex).isEqualTo(0)
    }

    @Test
    fun cardClick_invokesSegmentTappedForThatSegment() {
        val tapped = mutableListOf<ReadingSegmentUiState>()
        setContent(scheduled(), onSegmentTapped = { tapped += it })
        composeRule.onNodeWithTag("reading-3-0").performClick()
        assertThat(tapped).hasSize(1)
        assertThat(tapped.single().streamNumber).isEqualTo(3)
        assertThat(tapped.single().portion.streamNumber).isEqualTo(3)
    }

    @Test
    fun completeDay_showsNoBadgeAndNoWholeDayButton() {
        // H4: a fully-read day shows neither the "All readings done" badge nor any whole-day button;
        // the three checked checkboxes are the only completion cue (owner).
        setContent(scheduled(read = setOf(1, 2, 3)))
        composeRule.onNodeWithText("All readings done").assertDoesNotExist()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        for (stream in 1..3) composeRule.onNodeWithTag("toggle-$stream-0").assertIsDisplayed()
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
        setContent(DayUiState.NoScheduledReadings(LocalDate(2028, 2, 29)))
        composeRule.onNodeWithText("No scheduled readings for Feb 29th").assertIsDisplayed()
        composeRule.onNodeWithTag("whole-day-button").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle-1-0").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetryThatInvokesCallback() {
        var retries = 0
        setContent(DayUiState.LoadFailed(date), onRetry = { retries++ })
        composeRule.onNodeWithText("Couldn't load the reading plan").assertIsDisplayed()
        composeRule.onNodeWithTag("retry-button").performClick()
        assertThat(retries).isEqualTo(1)
    }

    // The list-level destination caption (owner one-screen-fit: the per-card hint was retired and
    // replaced by ONE caption below the day's readings, testTag "reading-list-hint"). It reflects the
    // EFFECTIVE destination reactively — the in-app reader for in-app mode, otherwise the chosen
    // external app, with a natural preposition — and carries NO reference substitution (it is
    // list-level, can't name a specific reading). Expectations resolve the string through the resource
    // (so the resource is proven wired) AND are pinned LITERALLY, so a wrong destination -> hint
    // mapping reddens exactly the offending pin. The node is asserted by tag (one per list) and by text.

    /** Resolve the caption through the resource, mirroring the prod mapping for the assert-by-tag path. */
    private fun listHint(
        mode: ReadingDestinationMode,
        app: ExternalBibleApp,
    ): String {
        val ctx =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        return ctx.getString(readingListHintRes(mode, app))
    }

    @Test
    fun externalBlb_listHintReadsOnBlueLetterBible() {
        setContent(scheduled(), destinationMode = ReadingDestinationMode.EXTERNAL, externalApp = ExternalBibleApp.BLB)
        // Resource is wired (resolve == literal) AND the caption node renders that exact text.
        assertThat(listHint(ReadingDestinationMode.EXTERNAL, ExternalBibleApp.BLB))
            .isEqualTo("Tap a reading to open it on Blue Letter Bible")
        composeRule.onNodeWithTag("reading-list-hint").assertTextEquals("Tap a reading to open it on Blue Letter Bible")
        composeRule.onNodeWithText("Tap a reading to open it on Blue Letter Bible").assertIsDisplayed()
    }

    @Test
    fun inAppMode_listHintReadsInThisApp_ignoringStoredExternalApp() {
        // MUTATION-PIN (IN_APP ignores the external axis): in-app MODE reads "in this app" regardless
        // of the remembered external app — here MySword is stored but the caption must ignore it. A
        // mutation that maps IN_APP to an external-app string reddens here.
        setContent(scheduled(), destinationMode = ReadingDestinationMode.IN_APP, externalApp = ExternalBibleApp.MYSWORD)
        assertThat(listHint(ReadingDestinationMode.IN_APP, ExternalBibleApp.MYSWORD))
            .isEqualTo("Tap a reading to open it in this app")
        composeRule.onNodeWithTag("reading-list-hint").assertTextEquals("Tap a reading to open it in this app")
        composeRule.onNodeWithText("Tap a reading to open it on Blue Letter Bible").assertDoesNotExist()
        composeRule.onNodeWithText("Tap a reading to open it in MySword").assertDoesNotExist()
    }

    @Test
    fun externalGateway_listHintReadsOnBibleGateway() {
        setContent(
            scheduled(),
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalApp = ExternalBibleApp.BIBLE_GATEWAY,
        )
        assertThat(listHint(ReadingDestinationMode.EXTERNAL, ExternalBibleApp.BIBLE_GATEWAY))
            .isEqualTo("Tap a reading to open it on Bible Gateway")
        composeRule.onNodeWithText("Tap a reading to open it on Bible Gateway").assertIsDisplayed()
    }

    @Test
    fun externalYouVersion_listHintReadsOnYouVersion() {
        setContent(
            scheduled(),
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalApp = ExternalBibleApp.YOUVERSION,
        )
        assertThat(listHint(ReadingDestinationMode.EXTERNAL, ExternalBibleApp.YOUVERSION))
            .isEqualTo("Tap a reading to open it on YouVersion")
        composeRule.onNodeWithText("Tap a reading to open it on YouVersion").assertIsDisplayed()
    }

    @Test
    fun externalMySword_listHintReadsInMySword_withInPreposition() {
        // MUTATION-PIN (MySword "in" preposition distinct from the "on …" providers): the caption
        // mirrors the *setting*, not install-aware tap-time resolution — even when MySword isn't
        // installed (tap falls back to BLB), the selected external-app hint still reads "in MySword".
        setContent(
            scheduled(),
            destinationMode = ReadingDestinationMode.EXTERNAL,
            externalApp = ExternalBibleApp.MYSWORD,
        )
        assertThat(listHint(ReadingDestinationMode.EXTERNAL, ExternalBibleApp.MYSWORD))
            .isEqualTo("Tap a reading to open it in MySword")
        composeRule.onNodeWithTag("reading-list-hint").assertTextEquals("Tap a reading to open it in MySword")
        // Distinguishes the MySword "in" preposition from the "on …" external providers.
        composeRule.onNodeWithText("Tap a reading to open it on MySword").assertDoesNotExist()
    }

    @Test
    fun listHint_isSingleCaption_andNoPerCardHintRemains() {
        // T4 step 2: exactly ONE list-level caption renders (not one per card), and the retired
        // per-card hint (with its "%1$s" reference substitution, e.g. "Opens Genesis 1–2 …") is GONE.
        // A regression re-adding a per-card hint, or rendering the caption per card, reddens here.
        setContent(scheduled(), destinationMode = ReadingDestinationMode.EXTERNAL, externalApp = ExternalBibleApp.BLB)
        composeRule.onAllNodesWithTag("reading-list-hint").assertCountEquals(1)
        // The old per-card, reference-substituted hint must not appear on any card.
        composeRule.onNodeWithText("Opens Genesis 1–2 on Blue Letter Bible").assertDoesNotExist()
        composeRule.onNodeWithText("Opens Genesis 1–2 in this app").assertDoesNotExist()
    }

    // --- SC-T10 (D-ALT-9/22/23): the card surface renders the active plan's ACTUAL stream count ---

    @Test
    fun nFourPlan_rendersFourCardsWithTheirPlanSuppliedTitles() {
        // A 4-stream M'Cheyne day: four cards, each titled from plan data (carried on the reading).
        // A mutation that renders a fixed three would drop the fourth card/title -> reddens here.
        val mcheyneTitles =
            listOf("Family — Old Testament", "Family — Gospels", "Personal — Psalms & Prophets", "Personal — Epistles")
        val state =
            DayUiState.Scheduled(
                date = date,
                segments =
                    singleSegmentStates(
                        portions = (1..4).map { n -> portion(n, "Genesis" to n) },
                        titleFor = { stream -> mcheyneTitles[stream - 1] },
                    ),
                dayComplete = false,
            )
        setContent(state)
        mcheyneTitles.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        for (n in 1..4) composeRule.onNodeWithTag("reading-$n-0").assertExists()
    }

    @Test
    fun nOnePlan_rendersOneCardWithNoStreamLabel() {
        // D-ALT-23: a single-stream plan supplies a null title; the lone reading renders the
        // reference ALONE — no "which stream" label. A mutation that always renders a label
        // (e.g. a non-null fallback) would surface an unexpected title node.
        val state =
            DayUiState.Scheduled(
                date = date,
                segments = singleSegmentStates(listOf(portion(1, "Genesis" to 1, "Genesis" to 2))),
                dayComplete = false,
            )
        setContent(state)
        composeRule.onNodeWithText("Genesis 1–2").assertIsDisplayed()
        composeRule.onNodeWithTag("reading-1-0").assertExists()
        // None of the Bible-Companion stream titles appear for a single-stream plan.
        composeRule.onNodeWithText("Law & History").assertDoesNotExist()
    }

    // --- sprint-00P (SEG-6): one card per contiguous passage ---

    /** The Chronological 07/25 reading (`Isaiah 37, 38, 39, Psalms 76`) split into its two D-SEG-1
     * segments, as card states. Built LITERALLY, not via the ViewModel mapping, so these render pins
     * stand on their own.
     */
    private fun twoSegmentStates(
        first: ReadingCheckState = ReadingCheckState.UNCHECKED,
        second: ReadingCheckState = ReadingCheckState.UNCHECKED,
    ): List<ReadingSegmentUiState> =
        listOf(
            ReadingSegmentUiState(
                streamNumber = 1,
                segmentIndex = 0,
                segmentCount = 2,
                streamTitle = "Law & History",
                portion = portion(1, "Isaiah" to 37, "Isaiah" to 38, "Isaiah" to 39),
                checkState = first,
            ),
            ReadingSegmentUiState(
                streamNumber = 1,
                segmentIndex = 1,
                segmentCount = 2,
                streamTitle = "Law & History",
                portion = portion(1, "Psalms" to 76),
                checkState = second,
            ),
        )

    private fun scheduledSegments(segments: List<ReadingSegmentUiState>): DayUiState.Scheduled =
        DayUiState.Scheduled(date = date, segments = segments, dayComplete = false)

    private fun hasStateDescription(expected: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    @Test
    fun twoSegmentReading_rendersOneCardPerPassage_withTheStreamTitleOnBoth() {
        // The owner's rule: two different passages on one day's reading => two cards. Each card
        // shows ONLY its own passage, and the stream title repeats on both (per spec).
        setContent(scheduledSegments(twoSegmentStates()))
        composeRule.onNodeWithTag("reading-1-0").assertExists()
        composeRule.onNodeWithTag("reading-1-1").assertExists()
        composeRule.onNodeWithText("Isaiah 37\u201339").assertIsDisplayed()
        composeRule.onNodeWithText("Psalm 76").assertIsDisplayed()
        // No card carries the whole reading's combined reference any more.
        composeRule.onNodeWithText("Isaiah 37\u201339; Psalm 76").assertDoesNotExist()
        composeRule.onAllNodesWithText("Law & History").assertCountEquals(2)
        // Two cards => two checks, each segment-indexed.
        composeRule.onNodeWithTag("toggle-1-0").assertExists()
        composeRule.onNodeWithTag("toggle-1-1").assertExists()
    }

    @Test
    fun tappingTheSecondCard_handsBackThatSegmentAlone() {
        // D-SEG-6 at the render layer: the card hands out ITS segment, so the ViewModel (and from
        // there the reader / the external URL) can only ever see the tapped passage.
        val tapped = mutableListOf<ReadingSegmentUiState>()
        setContent(scheduledSegments(twoSegmentStates()), onSegmentTapped = { tapped += it })
        composeRule.onNodeWithTag("reading-1-1").performClick()
        assertThat(tapped).hasSize(1)
        assertThat(
            tapped
                .single()
                .portion.refs
                .map { it.book.canonicalName to it.chapter },
        ).containsExactlyInAnyOrder("Psalms" to 76)
        assertThat(tapped.single().segmentIndex).isEqualTo(1)
        assertThat(tapped.single().streamNumber).isEqualTo(1)
    }

    @Test
    fun partialAndCompleteCards_areDistinguishableBySpokenState() {
        // Never colour alone: PARTIAL and COMPLETE differ in the checkbox's spoken stateDescription,
        // so TalkBack (and this test) can tell them apart without seeing the hue. Literal strings.
        val segments =
            twoSegmentStates(first = ReadingCheckState.PARTIAL) +
                ReadingSegmentUiState(
                    streamNumber = 3,
                    segmentIndex = 0,
                    segmentCount = 1,
                    streamTitle = "New Testament",
                    portion = portion(3, "Matthew" to 1, "Matthew" to 2),
                    checkState = ReadingCheckState.COMPLETE,
                )
        setContent(scheduledSegments(segments))
        composeRule.onNodeWithTag("toggle-1-0").assert(hasStateDescription("partially read"))
        composeRule.onNodeWithTag("toggle-1-1").assert(hasStateDescription("not read"))
        composeRule.onNodeWithTag("toggle-3-0").assert(hasStateDescription("read"))
    }
}
