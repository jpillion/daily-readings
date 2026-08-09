package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation
import com.jpillion.dailyreadingplanner.bible.domain.model.BibleVersion
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.ui.day.externalBibleAppNameRes
import com.jpillion.dailyreadingplanner.ui.day.readerVerseTapHintRes
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
 *
 * **Q2** added the verse action menu + selection pins: a tap opens the menu (it no longer ejects the
 * reader into an external app), the menu's three owner-chosen items each fire their own callback, a
 * long-press and the "Select verses" item share one entry point, a tap toggles while selecting, and
 * a selected verse SPEAKS its state (never colour alone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val titleId = VerseId.encode(19, 23, 0)
    private val verse1Id = VerseId.encode(19, 23, 1)
    private val verse2Id = VerseId.encode(19, 23, 2)

    private fun psalm23(): ChapterContent =
        ChapterContent(
            bookNo = 19,
            bookName = "Psalms",
            chapter = 23,
            verses =
                listOf(
                    VerseText(titleId, "", isTitle = true, markup = "A Psalm of David."),
                    VerseText(verse1Id, "1", isTitle = false, markup = "The <a>LORD</a> is my shepherd"),
                    VerseText(verse2Id, "2", isTitle = false, markup = "He maketh me to lie down"),
                ),
        )

    private fun content() = ReaderUiState.Content(blocks = listOf(psalm23()), title = "Psalm 23")

    private val kjv = BibleTranslation("KJV", "King James Version")

    // --- Sprint 00R step 6: the D-OT-9 copyright notice. ---

    /** The bundled public-domain KJV carries no copyright, so nothing must be rendered for it. */
    @Test
    fun `no copyright notice for the bundled KJV`() {
        setContent()
        composeRule.onNodeWithTag("reader-copyright").assertDoesNotExist()
    }

    /**
     * D-OT-9 — API.Bible's licence: "please include the copyright information along side all of the
     * Scripture you display". Not optional, so it is pinned as visible text, not just present.
     */
    @Test
    fun `licensed text shows its publisher copyright`() {
        val notice = "NKJV © 1982 Thomas Nelson"
        setContent(
            stateProvider = {
                content().copy(blocks = listOf(psalm23().copy(copyright = notice)))
            },
        )

        composeRule.onNodeWithTag("reader-copyright").assertIsDisplayed()
        composeRule.onNodeWithText(notice).assertIsDisplayed()
    }

    // --- Sprint 00R step 5: the D-OT-2 degraded banner. ---

    /**
     * The banner must NOT appear on a normal read. This is the pin that matters most: every KJV
     * reader would otherwise see a permanent error strip above their scripture.
     */
    @Test
    fun `no banner on a normal page`() {
        setContent()
        composeRule.onNodeWithTag("reader-degraded-banner").assertDoesNotExist()
    }

    /**
     * D-OT-2 case 3 — the substituted text is announced. Pinned as the LITERAL owner wording, and
     * pinned as text rather than colour: the banner must never rely on colour alone.
     */
    @Test
    fun `a degraded page shows the banner and still renders the verses`() {
        setContent(stateProvider = { content().copy(degradedFrom = BibleVersion.NKJV) })

        composeRule.onNodeWithTag("reader-degraded-banner").assertIsDisplayed()
        // Names the version that failed, so the notice explains WHY KJV is on screen.
        composeRule.onNodeWithText("Unable to download NKJV, displaying KJV").assertIsDisplayed()
        // The warning sits ABOVE the text; it must never replace or occlude scripture.
        composeRule.onNodeWithTag("reader-list").assertIsDisplayed()
        composeRule.onNodeWithText("The LORD is my shepherd", substring = true).assertIsDisplayed()
    }

    @Suppress("LongParameterList")
    private fun setContent(
        stateProvider: @Composable () -> ReaderUiState = { content() },
        externalApp: ExternalBibleApp = ExternalBibleApp.BLB,
        versionState: ReaderVersionState = ReaderVersionState(available = listOf(kjv), selected = kjv),
        selection: VerseSelection = VerseSelection.NONE,
        onOpenPicker: () -> Unit = {},
        onVerseLongPressed: (Int, Long) -> Unit = { _, _ -> },
        onVerseSelectionToggled: (Int, Long) -> Unit = { _, _ -> },
        onOpenVerseExternally: (Long) -> Unit = {},
        onCopyVerse: (Int, Long) -> Unit = { _, _ -> },
        onCopySelection: () -> Unit = {},
        onClearSelection: () -> Unit = {},
    ) {
        composeRule.setContent {
            DailyReadingPlannerTheme(dynamicColor = false) {
                ReaderScreen(
                    pagerState =
                        androidx.compose.foundation.pager
                            .rememberPagerState(initialPage = 0) { 1 },
                    stateForPage = { stateProvider() },
                    externalApp = externalApp,
                    versionState = versionState,
                    selection = selection,
                    onOpenPicker = onOpenPicker,
                    onSelectVersion = {},
                    onVerseLongPressed = onVerseLongPressed,
                    onVerseSelectionToggled = onVerseSelectionToggled,
                    onOpenVerseExternally = onOpenVerseExternally,
                    onCopyVerse = onCopyVerse,
                    onCopySelection = onCopySelection,
                    onClearSelection = onClearSelection,
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

    /** Opens the verse action menu the way a user does: a short tap on the verse. */
    private fun openMenuOn(verseId: Long) {
        composeRule.onNodeWithTag("reader-verse-$verseId").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `each verse is an item keyed by canonicalId`() {
        setContent()
        composeRule.onNodeWithTag("reader-list").assertIsDisplayed()
        // Tags carry the canonical verse_id — the list is individually addressable (D-V3-12).
        composeRule.onNodeWithTag("reader-verse-$verse1Id").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-verse-$verse2Id").assertIsDisplayed()
    }

    @Test
    fun `superscription renders as an unnumbered heading - not a numbered verse`() {
        setContent()
        // It is a title node with heading semantics...
        composeRule.onNodeWithTag("reader-title-$titleId").assert(hasHeading())
        // ...and is NOT rendered through the numbered-verse path.
        composeRule.onNodeWithTag("reader-verse-$titleId").assertDoesNotExist()
    }

    @Test
    fun `TalkBack speaks the plain stripped title text - never markup`() {
        setContent()
        // Q2 REWORDED: the label no longer starts with "Open " (a tap opens the verse menu, not the
        // external app); it is the reference + the plain title text, verse 0 clamped to verse 1.
        composeRule
            .onNodeWithTag("reader-title-$titleId")
            .assert(contentDescriptionIs("Psalm 23:1. A Psalm of David."))
    }

    @Test
    fun `verse contentDescription is the stripped markup with added words kept`() {
        setContent()
        // <a>LORD</a> -> "LORD" kept, tags gone (mutation target: stripping for a11y).
        composeRule
            .onNodeWithTag("reader-verse-$verse1Id")
            .assert(contentDescriptionIs("Psalm 23:1. The LORD is my shepherd"))
    }

    @Test
    fun `verse label is the seam nativeLabel - never derived from the canonicalId - D-V3-4`() {
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
        setContent({ state })
        composeRule
            .onNodeWithText("1a For God so loved", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `list is keyed by canonicalId so same-label verses across chapters never collide - D-V3-12`() {
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
        setContent({ state })
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
        setContent({ state.value })
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
        setContent({ ReaderUiState.Loading })
        composeRule.onNodeWithTag("reader-loading").assertIsDisplayed()
    }

    @Test
    fun `error state renders a retryable message`() {
        setContent({ ReaderUiState.Error() })
        composeRule.onNodeWithTag("reader-error").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-retry").assertIsDisplayed()
    }

    @Test
    fun `a tapped verse carries a button role and a reference-first contentDescription`() {
        setContent()
        composeRule.onNodeWithTag("reader-verse-$verse1Id").assert(
            SemanticsMatcher("contentDescription startsWith 'Psalm 23:1'") { node ->
                node.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { it.startsWith("Psalm 23:1") } == true
            },
        )
    }

    // --- Q2 / Ticket 2: the verse action menu (a short tap no longer takes the reader out). ---

    @Test
    fun `a short tap opens the verse action menu instead of opening the external app`() {
        // THE owner-reported problem, pinned: "the click takes you out". A tap must now open the
        // three-item menu and open NOTHING externally.
        var openedExternally: Long? = null
        setContent(onOpenVerseExternally = { openedExternally = it })
        openMenuOn(verse1Id)
        composeRule.onNodeWithTag("verse-menu-open").assertIsDisplayed()
        composeRule.onNodeWithTag("verse-menu-copy").assertIsDisplayed()
        composeRule.onNodeWithTag("verse-menu-select").assertIsDisplayed()
        assertThat(openedExternally).isNull()
    }

    @Test
    fun `the menu is closed until a verse is tapped`() {
        setContent()
        composeRule.onNodeWithTag("verse-menu-open").assertDoesNotExist()
        composeRule.onNodeWithTag("verse-menu-copy").assertDoesNotExist()
        composeRule.onNodeWithTag("verse-menu-select").assertDoesNotExist()
    }

    @Test
    fun `the menu's Open in item opens THAT verse externally by canonicalId`() {
        // Replaces the Sprint-H "tap opens the verse" pin: the same canonicalId still reaches the
        // external open, now one deliberate step behind the menu.
        var openedExternally: Long? = null
        setContent(onOpenVerseExternally = { openedExternally = it })
        openMenuOn(verse2Id)
        composeRule.onNodeWithTag("verse-menu-open").performClick()
        composeRule.waitForIdle()
        assertThat(openedExternally).isEqualTo(verse2Id)
    }

    @Test
    fun `the Open in item names the user's chosen external app - LITERAL`() {
        // LITERAL expected string: a wrong external-app -> name mapping reddens this pin.
        setContent(externalApp = ExternalBibleApp.MYSWORD)
        openMenuOn(verse1Id)
        composeRule.onNodeWithText("Open in MySword").assertIsDisplayed()
    }

    @Test
    fun `the menu's Copy this verse item invokes onCopyVerse with the page and canonicalId`() {
        var copied: Pair<Int, Long>? = null
        setContent(onCopyVerse = { page, id -> copied = page to id })
        openMenuOn(verse1Id)
        composeRule.onNodeWithTag("verse-menu-copy").performClick()
        composeRule.waitForIdle()
        assertThat(copied).isEqualTo(0 to verse1Id)
    }

    @Test
    fun `the menu's Select verses item enters selection mode - the same entry point as long-press`() {
        // The TalkBack-reachable path into selection (long-press is not reliably reachable).
        var selected: Pair<Int, Long>? = null
        setContent(onVerseLongPressed = { page, id -> selected = page to id })
        openMenuOn(verse1Id)
        composeRule.onNodeWithTag("verse-menu-select").performClick()
        composeRule.waitForIdle()
        assertThat(selected).isEqualTo(0 to verse1Id)
    }

    @Test
    fun `a superscription is tappable into the same menu - both verse branches share the gesture`() {
        setContent()
        composeRule.onNodeWithTag("reader-title-$titleId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("verse-menu-copy").assertIsDisplayed()
    }

    @Test
    fun `every menu action dismisses the menu - an action never leaves it open`() {
        // Riley (Q3 verification): VerseActionMenu's contract is "every item dismisses the menu
        // before acting", but dropping the onMenuDismissed() call from an item left the whole suite
        // green. An undismissed menu sits over the reader after a copy, and on the "Open in <app>"
        // path it is still there when the user returns from the Custom Tab. Pinned per item.
        setContent()
        for (tag in listOf("verse-menu-copy", "verse-menu-open", "verse-menu-select")) {
            openMenuOn(verse1Id)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    // --- Q2 / Ticket 1: long-press selection, the contextual action bar, spoken selected state. ---

    @Test
    fun `a long-press enters selection mode with that verse`() {
        var selected: Pair<Int, Long>? = null
        setContent(onVerseLongPressed = { page, id -> selected = page to id })
        composeRule.onNodeWithTag("reader-verse-$verse2Id").performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertThat(selected).isEqualTo(0 to verse2Id)
    }

    @Test
    fun `while selecting - a tap toggles the verse instead of opening the menu`() {
        var toggled: Pair<Int, Long>? = null
        setContent(
            selection = VerseSelection.start(page = 0, verseId = verse1Id),
            onVerseSelectionToggled = { page, id -> toggled = page to id },
        )
        composeRule.onNodeWithTag("reader-verse-$verse2Id").performClick()
        composeRule.waitForIdle()
        assertThat(toggled).isEqualTo(0 to verse2Id)
        // ...and no menu opened.
        composeRule.onNodeWithTag("verse-menu-copy").assertDoesNotExist()
    }

    @Test
    fun `while selecting - every verse speaks its selected state - never colour alone`() {
        setContent(selection = VerseSelection.start(page = 0, verseId = verse1Id))
        composeRule
            .onNodeWithTag("reader-verse-$verse1Id")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "selected"))
        composeRule
            .onNodeWithTag("reader-verse-$verse2Id")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not selected"))
    }

    @Test
    fun `outside selection mode no verse carries selected semantics - it would be noise`() {
        setContent()
        composeRule
            .onNodeWithTag("reader-verse-$verse1Id")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected).not())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription).not())
    }

    @Test
    fun `the contextual action bar replaces the reader top bar while selecting`() {
        setContent(selection = VerseSelection.start(page = 0, verseId = verse1Id))
        composeRule.onNodeWithTag("verse-selection-count").assertTextEquals("1 selected")
        composeRule.onNodeWithTag("verse-selection-copy").assertIsDisplayed()
        composeRule.onNodeWithTag("verse-selection-close").assertIsDisplayed()
        // The normal reader bar (chapter heading + pencil) is gone for the duration.
        composeRule.onNodeWithTag("reader-title").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-open-picker").assertDoesNotExist()
    }

    @Test
    fun `the selection count reports how many verses are selected and is a polite live region`() {
        val two = VerseSelection.start(page = 0, verseId = verse1Id).toggle(page = 0, verseId = verse2Id)
        setContent(selection = two)
        composeRule
            .onNodeWithTag("verse-selection-count")
            .assertTextEquals("2 selected")
            // Announced on entering selection mode and as the count changes (a spec requirement).
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `the action bar's Copy invokes onCopySelection and the X invokes onClearSelection`() {
        var copied = false
        var cleared = false
        setContent(
            selection = VerseSelection.start(page = 0, verseId = verse1Id),
            onCopySelection = { copied = true },
            onClearSelection = { cleared = true },
        )
        composeRule.onNodeWithTag("verse-selection-copy").performClick()
        composeRule.waitForIdle()
        assertThat(copied).isTrue()
        composeRule.onNodeWithTag("verse-selection-close").performClick()
        composeRule.waitForIdle()
        assertThat(cleared).isTrue()
    }

    // --- Sprint K: reader footer hint (owner request, read-here / study-there bridge). ---
    //
    // The hint TEXT is intentionally hidden from TalkBack via clearAndSetSemantics (every verse
    // already carries its own spoken label and click labels), which also removes the visible Text
    // from the semantics tree — so the literal wording is pinned through the resolved string
    // resources (`hintString` below), and the rendered node is pinned for existence + a11y silence.

    private fun hintString(app: ExternalBibleApp): String {
        val ctx =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val name = ctx.getString(externalBibleAppNameRes(app))
        return ctx.getString(readerVerseTapHintRes(app), name)
    }

    @Test
    fun `footer hint wording is the BLB phrasing for BLB - LITERAL - mutation-pins the mapping`() {
        // LITERAL expected string (NOT computed via the prod mapping): a wrong external-app -> hint
        // mapping or a wrong app-name mapping reddens this pin. Q2 REWORDED: a tap opens the verse
        // menu, from which the user can copy OR open externally, so the hint says both.
        assertThat(hintString(ExternalBibleApp.BLB))
            .isEqualTo("Tap a verse to copy it or open it on Blue Letter Bible")
    }

    @Test
    fun `footer hint wording is the MySword phrasing with the in preposition - LITERAL`() {
        // MySword takes "in", proving the per-provider preposition mapping is not flattened.
        assertThat(hintString(ExternalBibleApp.MYSWORD))
            .isEqualTo("Tap a verse to copy it or open it in MySword")
    }

    @Test
    fun `footer hint wording is the Bible Gateway phrasing - LITERAL`() {
        assertThat(hintString(ExternalBibleApp.BIBLE_GATEWAY))
            .isEqualTo("Tap a verse to copy it or open it on Bible Gateway")
    }

    @Test
    fun `footer hint wording is the YouVersion phrasing - LITERAL`() {
        assertThat(hintString(ExternalBibleApp.YOUVERSION))
            .isEqualTo("Tap a verse to copy it or open it on YouVersion")
    }

    @Test
    fun `footer hint renders and is hidden from TalkBack - no text - no contentDescription`() {
        // The footer is present (findable by tag), but clearAndSetSemantics strips its spoken text
        // and any description so TalkBack does not restate what every verse already carries.
        setContent(externalApp = ExternalBibleApp.BLB)
        composeRule.onNodeWithTag("reader-footer-hint").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-footer-hint").assert(
            SemanticsMatcher("no text and no contentDescription (skipped from TalkBack)") { node ->
                node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                    node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
            },
        )
    }

    @Test
    fun `footer hint does not break verse-keyed items - D-V3-12`() {
        // The footer sits below the pager; the verse items must still be addressable by their
        // canonicalId and the footer coexists with them on the same page.
        setContent(externalApp = ExternalBibleApp.BLB)
        composeRule.onNodeWithTag("reader-verse-$verse1Id").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-verse-$verse2Id").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-footer-hint").assertIsDisplayed()
    }

    // --- Owner reader top-bar redesign: pencil (left, opens the picker) + version (right). ---

    @Test
    fun `the pencil action opens the picker - onOpenPicker fires - and keeps the reader-open-picker tag`() {
        // The picker affordance moved from the actions list-icon to a pencil in the title slot, but
        // keeps the reader-open-picker tag + onOpenPicker callback (the a11y gate pins that tag).
        var opened = false
        setContent(onOpenPicker = { opened = true })
        composeRule.onNodeWithTag("reader-open-picker").performClick()
        composeRule.waitForIdle()
        assertThat(opened).isTrue()
    }

    @Test
    fun `the chapter heading still renders beside the pencil`() {
        // The pencil is INLINE to the left of the chapter heading in the title slot — the heading
        // ("Psalm 23") still shows and is still tagged reader-title.
        setContent()
        composeRule.onNodeWithTag("reader-title").assertTextEquals("Psalm 23")
    }

    @Test
    fun `with one bundled version the version renders as a static title KJV - not a dropdown`() {
        // D-N-3 single-version branch: a static title shows the version CODE; the dropdown row does
        // not exist. (Mutation target: forcing the dropdown branch with one version reddens this.)
        setContent()
        composeRule.onNodeWithTag("reader-version-title").assertTextEquals("KJV")
        composeRule.onNodeWithTag("reader-version-dropdown").assertDoesNotExist()
    }

    @Test
    fun `the single-version title speaks the unabbreviated name to TalkBack - D-N-2`() {
        // The visible label is "KJV" but TalkBack announces the full "King James Version".
        setContent()
        composeRule
            .onNodeWithTag("reader-version-title")
            .assert(contentDescriptionIs("King James Version"))
    }

    @Test
    fun `with more than one bundled version the version renders as a dropdown - D-N-3 - mutation pin`() {
        // D-N-3 multi-version branch: forces the OTHER branch — the static title must NOT exist and a
        // selectable dropdown must. (Mutation target: collapsing the size check to always-title, or
        // always-dropdown, reddens exactly one of these two version-shape tests.)
        val esv = BibleTranslation("ESV", "English Standard Version")
        setContent(versionState = ReaderVersionState(available = listOf(kjv, esv), selected = kjv))
        composeRule.onNodeWithTag("reader-version-dropdown").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-version-title").assertDoesNotExist()
    }
}
