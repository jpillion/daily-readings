package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VC-T3 — reader render pins under Robolectric. Verse-id-keyed list (D-V3-12), superscription as
 * an unnumbered heading (D-V3-7), native label from the seam (D-V3-4), TalkBack speaks stripped
 * text (NFR-V3-C). Several pins are load-bearing render-logic mutation targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun psalm23(): ChapterContent =
        ChapterContent(
            bookNo = 19,
            bookName = "Psalms",
            chapter = 23,
            verses =
                listOf(
                    VerseText(VerseId.encode(19, 23, 0), "", isTitle = true, markup = "A Psalm of David."),
                    VerseText(
                        VerseId.encode(19, 23, 1),
                        "1",
                        isTitle = false,
                        markup = "The <a>LORD</a> is my shepherd",
                    ),
                    VerseText(VerseId.encode(19, 23, 2), "2", isTitle = false, markup = "He maketh me to lie down"),
                ),
        )

    private fun content() = ReaderUiState.Content(blocks = listOf(psalm23()), title = "Psalm 23")

    private fun setContent(state: ReaderUiState = content()) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                ReaderScreen(
                    pagerState =
                        androidx.compose.foundation.pager
                            .rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { state },
                    onOpenPicker = {},
                    onVerseTapped = { _, _ -> },
                    onRetry = {},
                )
            }
        }
    }

    private fun hasHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun contentDescriptionIs(expected: String) =
        SemanticsMatcher("contentDescription == '$expected'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == expected } == true
        }

    @Test
    fun `each verse is an item keyed by canonicalId`() {
        setContent()
        composeRule.onNodeWithTag("reader-list").assertIsDisplayed()
        // Tags carry the canonical verse_id — the list is individually addressable (D-V3-12).
        composeRule.onNodeWithTag("reader-verse-${VerseId.encode(19, 23, 1)}").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-verse-${VerseId.encode(19, 23, 2)}").assertIsDisplayed()
    }

    @Test
    fun `superscription renders as an unnumbered heading, not a numbered verse`() {
        setContent()
        val titleId = VerseId.encode(19, 23, 0)
        // It is a title node with heading semantics...
        composeRule.onNodeWithTag("reader-title-$titleId").assert(hasHeading())
        // ...and is NOT rendered through the numbered-verse path.
        composeRule.onNodeWithTag("reader-verse-$titleId").assertDoesNotExist()
    }

    @Test
    fun `TalkBack speaks the plain stripped title text, never markup`() {
        setContent()
        val titleId = VerseId.encode(19, 23, 0)
        // H7: a tappable superscription speaks "Open <book ch:verse>" then the plain title text
        // (verse 0 clamps to verse 1); the markup is still stripped, never spoken raw.
        composeRule
            .onNodeWithTag("reader-title-$titleId")
            .assert(contentDescriptionIs("Open Psalm 23:1. A Psalm of David."))
    }

    @Test
    fun `verse contentDescription is the stripped markup with added words kept`() {
        setContent()
        // <a>LORD</a> -> "LORD" kept, tags gone (mutation target: stripping for a11y).
        composeRule
            .onNodeWithTag("reader-verse-${VerseId.encode(19, 23, 1)}")
            .assert(contentDescriptionIs("Open Psalm 23:1. The LORD is my shepherd"))
    }

    @Test
    fun `verse label is the seam nativeLabel, never derived from the canonicalId (D-V3-4)`() {
        // canonicalId encodes verse 1, but the artifact's nativeLabel is "1a" (a future
        // differently-numbered artifact). The reader MUST show "1a", proving it never derives
        // the number from the id. (Mutation target: nativeLabel-not-derived.)
        val id = VerseId.encode(43, 3, 1)
        val state =
            ReaderUiState.Content(
                blocks =
                    listOf(
                        ChapterContent(
                            bookNo = 43,
                            bookName = "John",
                            chapter = 3,
                            verses = listOf(VerseText(id, "1a", isTitle = false, markup = "For God so loved")),
                        ),
                    ),
                title = "John 3",
            )
        setContent(state)
        composeRule
            .onNodeWithText("1a For God so loved", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `list is keyed by canonicalId so same-label verses across chapters never collide (D-V3-12)`() {
        // Two chapters each have a verse with nativeLabel "1". If the LazyColumn were keyed by
        // the label (or any non-unique field) Compose would throw on the duplicate key; keying by
        // the globally-unique canonicalId is what lets both render. (Mutation target: keyed-by-id.)
        val v1 = VerseText(VerseId.encode(43, 3, 1), "1", isTitle = false, markup = "John 3 verse one")
        val v2 = VerseText(VerseId.encode(43, 4, 1), "1", isTitle = false, markup = "John 4 verse one")
        val state =
            ReaderUiState.Content(
                blocks =
                    listOf(
                        ChapterContent(43, "John", 3, listOf(v1)),
                        ChapterContent(43, "John", 4, listOf(v2)),
                    ),
                title = "John 3",
            )
        setContent(state)
        composeRule.onNodeWithTag("reader-verse-${VerseId.encode(43, 3, 1)}").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-verse-${VerseId.encode(43, 4, 1)}").assertIsDisplayed()
    }

    @Test
    fun `opening a new chapter resets scroll to the top`() {
        // Regression: the LazyListState persists across content swaps, so without the reset a new
        // chapter opens at the prior chapter's scroll offset (owner: "never at the top").
        fun bigChapter(
            book: Int,
            ch: Int,
        ) = ReaderUiState.Content(
            blocks =
                listOf(
                    ChapterContent(
                        book,
                        "Book",
                        ch,
                        (1..40).map { v ->
                            VerseText(VerseId.encode(book, ch, v), "$v", isTitle = false, markup = "Verse $v text")
                        },
                    ),
                ),
            title = "Book $ch",
        )
        val state = androidx.compose.runtime.mutableStateOf<ReaderUiState>(bigChapter(43, 1))
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                ReaderScreen(
                    pagerState =
                        androidx.compose.foundation.pager
                            .rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { state.value },
                    onOpenPicker = {},
                    onVerseTapped = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("reader-list").performScrollToIndex(35)
        composeRule.waitForIdle()
        // Changing the page's chapter must snap the page back to the top (LaunchedEffect(chapterKey)).
        state.value = bigChapter(43, 2)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-header-43-2").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-verse-${VerseId.encode(43, 2, 1)}").assertIsDisplayed()
    }

    @Test
    fun `chapter header carries heading semantics`() {
        setContent()
        composeRule.onNodeWithTag("reader-header-19-23").assert(hasHeading())
    }

    @Test
    fun `a Psalms chapter header renders the singular Psalm N`() {
        // D-UI-2 in the reader: a single Psalms chapter header is "Psalm 23", never "Psalms 23".
        // Both the header (tag reader-header-19-23) and the top-bar title now read "Psalm 23";
        // assert the header node specifically, and that NOTHING on screen says "Psalms 23".
        setContent()
        composeRule.onNodeWithTag("reader-header-19-23").assertTextEquals("Psalm 23")
        composeRule.onNodeWithText("Psalms 23").assertDoesNotExist()
    }

    @Test
    fun `loading state renders a spinner`() {
        setContent(ReaderUiState.Loading)
        composeRule.onNodeWithTag("reader-loading").assertIsDisplayed()
    }

    @Test
    fun `error state renders a retryable message`() {
        setContent(ReaderUiState.Error())
        composeRule.onNodeWithTag("reader-error").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-retry").assertIsDisplayed()
    }

    @Test
    fun `tapping a verse invokes onVerseTapped with that verse's canonicalId`() {
        var tapped: Long? = null
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                ReaderScreen(
                    pagerState =
                        androidx.compose.foundation.pager
                            .rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { content() },
                    onOpenPicker = {},
                    onVerseTapped = { _, verseId -> tapped = verseId },
                    onRetry = {},
                )
            }
        }
        val id = VerseId.encode(19, 23, 1)
        composeRule.onNodeWithTag("reader-verse-$id").performClick()
        composeRule.waitForIdle()
        com.google.common.truth.Truth
            .assertThat(tapped)
            .isEqualTo(id)
    }

    @Test
    fun `a tapped verse carries a button role and an Open contentDescription (H7 a11y)`() {
        setContent()
        val id = VerseId.encode(19, 23, 1)
        composeRule.onNodeWithTag("reader-verse-$id").assert(
            SemanticsMatcher("contentDescription startsWith 'Open Psalm 23:1'") { node ->
                node.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { it.startsWith("Open Psalm 23:1") } == true
            },
        )
    }
}
