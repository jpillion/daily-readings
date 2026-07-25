package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Q2 / Ticket 1 — **system back is one of the three specified exits from selection mode** ("Exit
 * selection via X, system back, or deselecting the last verse"), and it is the only one that is
 * NOT an on-screen control, so nothing else in the suite can reach it.
 *
 * Riley (Q3 verification): deleting [ReaderScreen]'s `BackHandler` outright left the whole suite
 * green — a surviving mutation on a spec-named exit. These pins close that hole. They need a real
 * `OnBackPressedDispatcher`, hence [createAndroidComposeRule] over a [ComponentActivity] rather
 * than the plain `createComposeRule()` the sibling reader tests use.
 *
 * Both directions matter:
 *  - while selecting, back must consume the event and leave the MODE (not the reader);
 *  - with nothing selected, back must NOT be intercepted, or the user is trapped in the reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderSelectionBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val verse1Id = VerseId.encode(19, 23, 1)
    private val verse2Id = VerseId.encode(19, 23, 2)

    private fun psalm23(): ReaderUiState.Content =
        ReaderUiState.Content(
            blocks =
                listOf(
                    ChapterContent(
                        bookNo = 19,
                        bookName = "Psalms",
                        chapter = 23,
                        verses =
                            listOf(
                                VerseText(verse1Id, "1", isTitle = false, markup = "The LORD is my shepherd"),
                                VerseText(verse2Id, "2", isTitle = false, markup = "He maketh me to lie down"),
                            ),
                    ),
                ),
            title = "Psalm 23",
        )

    /** The reader with its selection HOISTED into the test, exactly as `ReaderRoute` hoists it. */
    private fun setReader(initialSelection: VerseSelection) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                var selection by remember { mutableStateOf(initialSelection) }
                val kjv = BibleTranslation("KJV", "King James Version")
                ReaderScreen(
                    pagerState = rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { psalm23() },
                    externalApp = ExternalBibleApp.BLB,
                    versionState = ReaderVersionState(available = listOf(kjv), selected = kjv),
                    selection = selection,
                    onOpenPicker = {},
                    onSelectVersion = {},
                    onVerseLongPressed = { page, id -> selection = VerseSelection.start(page, id) },
                    onVerseSelectionToggled = { page, id -> selection = selection.toggle(page, id) },
                    onOpenVerseExternally = {},
                    onCopyVerse = { _, _ -> },
                    onCopySelection = {},
                    onClearSelection = { selection = selection.cleared() },
                    onRetry = {},
                )
            }
        }
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `system back exits selection mode and restores the reader top bar`() {
        setReader(VerseSelection.start(page = 0, verseId = verse1Id).toggle(page = 0, verseId = verse2Id))
        composeRule.onNodeWithTag("verse-selection-count").assertIsDisplayed()

        pressBack()

        // The mode is gone...
        composeRule.onNodeWithTag("verse-selection-count").assertDoesNotExist()
        composeRule.onNodeWithTag("verse-selection-copy").assertDoesNotExist()
        composeRule.onNodeWithTag("verse-selection-close").assertDoesNotExist()
        // ...and the reader's own top bar is back, i.e. back left the MODE, not the reader.
        composeRule.onNodeWithTag("reader-title").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-open-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-list").assertIsDisplayed()
    }

    @Test
    fun `back is intercepted only while selecting - never traps the user in the reader`() {
        setReader(VerseSelection.start(page = 0, verseId = verse1Id))
        // While selecting, the reader owns the back gesture...
        assertThat(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isTrue()

        pressBack()

        // ...and the moment the selection ends it hands it straight back to the system, so a
        // second back leaves the reader normally instead of being swallowed.
        assertThat(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse()
    }

    @Test
    fun `with nothing selected the reader does not intercept back at all`() {
        setReader(VerseSelection.NONE)
        composeRule.onNodeWithTag("verse-selection-count").assertDoesNotExist()
        assertThat(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse()
    }
}
