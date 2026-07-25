package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.bible.data.markup.MarkupStripper
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import com.jpillion.dailyreadingplanner.ui.day.externalBibleAppNameRes
import com.jpillion.dailyreadingplanner.ui.day.readerVerseTapHintRes
import androidx.compose.ui.semantics.testTag as semanticsTestTag

/**
 * H2 (D-H-2) — the stateless in-app KJV reader. The Scaffold + top bar host a
 * [HorizontalPager] over the WHOLE canon ([GlobalChapterIndex.TOTAL_CHAPTERS] pages, page ==
 * global chapter index): the user swipes left/right between chapters, continuously across book
 * boundaries (Genesis 50 → Exodus 1) and bounded at Genesis 1 / Revelation 22 (the pager simply
 * cannot scroll past the first/last page).
 *
 * Each page renders the chapter's verse-id-keyed [LazyColumn] (D-V3-12): markup via [VerseRenderer],
 * de-emphasized [VerseText.nativeLabel] (D-V3-4), superscription as an unnumbered heading (D-V3-7).
 *
 * **Q2 — what a verse touch does.** A short tap no longer ejects the reader into an external app
 * (the owner: "the click takes you out"). Instead:
 *  - **short tap, not selecting** → the [VerseActionMenu] anchored at that verse (Open in `<app>` /
 *    Copy this verse / Select verses);
 *  - **long-press** → enters selection mode with that verse selected (`onVerseLongPressed`);
 *  - **short tap, while selecting** → toggles that verse (`onVerseSelectionToggled`).
 *
 * While [selection] is active the [VerseSelectionBar] REPLACES the top bar, and system back exits
 * the selection instead of leaving the reader ([BackHandler] below).
 *
 * **P-Q-1 — Copy does NOT exit selection mode.** The spec enumerates the exits as "X, system back,
 * or deselecting the last verse", and Copy is not among them; this follows it literally, so the
 * user can copy, extend the selection and copy again, and a mis-tapped Copy does not throw the
 * selection away. **Flagged for owner confirmation** — the alternative (copy-then-exit) is the
 * other common Android idiom.
 *
 * The route owns the VM, the [PagerState], the picker, and the launch/clipboard side effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    pagerState: PagerState,
    stateForPage: @Composable (Int) -> ReaderUiState,
    externalApp: ExternalBibleApp,
    versionState: ReaderVersionState,
    selection: VerseSelection,
    onOpenPicker: () -> Unit,
    onSelectVersion: (com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation) -> Unit,
    onVerseLongPressed: (page: Int, verseId: Long) -> Unit,
    onVerseSelectionToggled: (page: Int, verseId: Long) -> Unit,
    onOpenVerseExternally: (verseId: Long) -> Unit,
    onCopyVerse: (page: Int, verseId: Long) -> Unit,
    onCopySelection: () -> Unit,
    onClearSelection: () -> Unit,
    onRetry: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection is a mode, so system back must leave the mode before it leaves the reader.
    BackHandler(enabled = selection.isActive) { onClearSelection() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (selection.isActive) {
                // Ticket 1: the contextual action bar takes over the top bar while selecting.
                VerseSelectionBar(
                    count = selection.count,
                    onCopy = onCopySelection,
                    onClose = onClearSelection,
                )
            } else {
                TopAppBar(
                    title = {
                        // Owner request (Priya): the pencil (open the book/chapter picker) sits in the
                        // title slot, INLINE and to the RIGHT of the chapter heading. The version control
                        // lives further right (actions). The pencil keeps the prior reader-open-picker tag
                        // + onOpenPicker callback (the a11y gate pins that tag at a 48dp target).
                        val title = (stateForPage(pagerState.currentPage) as? ReaderUiState.Content)?.title.orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = title, modifier = Modifier.testTag("reader-title"))
                            IconButton(onClick = onOpenPicker, modifier = Modifier.testTag("reader-open-picker")) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.reader_pick_chapter),
                                )
                            }
                        }
                    },
                    actions = {
                        ReaderVersionSelector(
                            versions = versionState.available,
                            selected = versionState.selected,
                            onSelect = onSelectVersion,
                        )
                    },
                )
            }
        },
        // D-V3-14: reserved audio seam — an empty bottom bar slot.
        bottomBar = { ReaderAudioSlot() },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("reader-pager"),
            ) { page ->
                ReaderPage(
                    state = stateForPage(page),
                    selection = selection,
                    externalApp = externalApp,
                    onVerseLongPressed = { verseId -> onVerseLongPressed(page, verseId) },
                    onVerseSelectionToggled = { verseId -> onVerseSelectionToggled(page, verseId) },
                    onOpenVerseExternally = onOpenVerseExternally,
                    onCopyVerse = { verseId -> onCopyVerse(page, verseId) },
                    onRetry = { onRetry(page) },
                )
            }
            // Owner request: the verse-tap hint is PINNED at the bottom of the reader — always
            // visible while reading, below the pager and above the reserved audio slot / nav bar.
            // It does not scroll with the verses or swipe with chapters.
            ReaderFooterHint(externalApp)
        }
    }
}

@Composable
private fun ReaderPage(
    state: ReaderUiState,
    selection: VerseSelection,
    externalApp: ExternalBibleApp,
    onVerseLongPressed: (Long) -> Unit,
    onVerseSelectionToggled: (Long) -> Unit,
    onOpenVerseExternally: (Long) -> Unit,
    onCopyVerse: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    // D-Q-2: the open verse menu is EPHEMERAL PRESENTATION STATE owned by the page, not the
    // ViewModel — it is dismissed by every action and means nothing across a recomposition of a
    // different page. Keeping it here also keeps ReaderScreen directly testable (the a11y gate
    // composes the screen, not the route, and must be able to open the menu by tapping a verse).
    // Selection state, by contrast, is hoisted to the ViewModel: it must survive recomposition,
    // drive the top bar, and feed the copy action.
    var menuVerseId by remember { mutableStateOf<Long?>(null) }
    when (state) {
        is ReaderUiState.Loading ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("reader-loading"))
            }

        is ReaderUiState.Error ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.reader_load_failed), modifier = Modifier.testTag("reader-error"))
                if (state.canRetry) {
                    Button(onClick = onRetry, modifier = Modifier.testTag("reader-retry")) {
                        Text(stringResource(R.string.reader_retry))
                    }
                }
            }

        is ReaderUiState.Content -> {
            val listState = rememberLazyListState()
            // A freshly-swiped page must open at the top (it is a new composition, but be explicit
            // so a recomposed/retained page still resets to verse 1 of its chapter).
            val chapterKey = state.blocks.firstOrNull()?.let { "${it.bookNo}-${it.chapter}" }
            LaunchedEffect(chapterKey) { if (chapterKey != null) listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("reader-list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                state.blocks.forEach { block ->
                    item(key = "hdr-${block.bookNo}-${block.chapter}") { ChapterHeader(block) }
                    items(block.verses, key = { it.canonicalId }) { verse ->
                        VerseItem(
                            verse = verse,
                            isActive = verse.canonicalId == state.activeVerseId,
                            selection = selection,
                            externalApp = externalApp,
                            menuOpen = menuVerseId == verse.canonicalId,
                            onMenuRequested = { menuVerseId = verse.canonicalId },
                            onMenuDismissed = { menuVerseId = null },
                            onVerseLongPressed = onVerseLongPressed,
                            onVerseSelectionToggled = onVerseSelectionToggled,
                            onOpenVerseExternally = onOpenVerseExternally,
                            onCopyVerse = onCopyVerse,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterHeader(block: ChapterContent) {
    Text(
        text =
            "${ReadingFormatter.singularizeBookName(block.bookName, singleChapter = true)} ${block.chapter}",
        style = MaterialTheme.typography.titleLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .semantics { heading() }
                .testTag("reader-header-${block.bookNo}-${block.chapter}"),
    )
}

/**
 * One verse row — the numbered-verse and the superscription (D-V3-7) branches share ONE interaction
 * modifier so the two can never diverge.
 *
 * Q2 touch contract: long-press selects, a tap toggles while selecting and otherwise opens the
 * [VerseActionMenu] anchored here (hence the wrapping [Box] — the menu must anchor at the tapped
 * verse, not at the list).
 *
 * A11y:
 *  - `onLongClickLabel` is the "Select verses" string, because **long-press is not reliably
 *    reachable under TalkBack** — that label plus the menu's "Select verses" item are the required
 *    equivalent paths to selection;
 *  - `onClickLabel` says what a tap will do *right now* ("Open verse actions" vs "Change selection");
 *  - while selecting, every verse carries `selected` + a spoken `stateDescription`. **The
 *    [MaterialTheme.colorScheme.secondaryContainer] highlight is decoration; the semantics are the
 *    mechanism** — never colour alone. Outside selection mode neither property is set (they would
 *    be noise on every verse of every chapter).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerseItem(
    verse: VerseText,
    isActive: Boolean,
    selection: VerseSelection,
    externalApp: ExternalBibleApp,
    menuOpen: Boolean,
    onMenuRequested: () -> Unit,
    onMenuDismissed: () -> Unit,
    onVerseLongPressed: (Long) -> Unit,
    onVerseSelectionToggled: (Long) -> Unit,
    onOpenVerseExternally: (Long) -> Unit,
    onCopyVerse: (Long) -> Unit,
) {
    val id = verse.canonicalId
    val rendered = VerseRenderer.render(verse.markup)
    val selecting = selection.isActive
    val isSelected = selection.contains(id)
    val description = verseDescription(verse)

    val selectedLabel = stringResource(R.string.verse_state_selected)
    val notSelectedLabel = stringResource(R.string.verse_state_not_selected)
    // Only while selecting: outside selection mode a per-verse selected state is noise.
    val spokenState =
        if (!selecting) {
            null
        } else if (isSelected) {
            selectedLabel
        } else {
            notSelectedLabel
        }
    val clickLabel =
        stringResource(
            if (selecting) R.string.verse_selection_toggle_label else R.string.verse_actions_label,
        )
    val longClickLabel = stringResource(R.string.verse_menu_select)

    val interaction =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = clickLabel,
                onLongClickLabel = longClickLabel,
                onLongClick = { onVerseLongPressed(id) },
                onClick = { if (selecting) onVerseSelectionToggled(id) else onMenuRequested() },
            )
            // Before the padding, so the WHOLE row highlights rather than just the text box.
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(vertical = 4.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (verse.isTitle) {
            // D-V3-7 / FR-V3-6: superscription — unnumbered italic heading, never a numbered verse.
            // Its verse-0 id clamps to verse 1 wherever a verse coordinate is needed (D-H-3).
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    interaction
                        .semantics {
                            heading()
                            contentDescription = description
                            if (spokenState != null) {
                                selected = isSelected
                                stateDescription = spokenState
                            }
                        }.testTag("reader-title-$id"),
            )
        } else {
            val body =
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ) { append(verse.nativeLabel + " ") }
                    append(rendered)
                }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier =
                    interaction
                        .semantics {
                            contentDescription = description
                            if (spokenState != null) {
                                selected = isSelected
                                stateDescription = spokenState
                            }
                        }.testTag("reader-verse-$id"),
            )
        }
        VerseActionMenu(
            expanded = menuOpen,
            externalApp = externalApp,
            onDismissRequest = onMenuDismissed,
            onOpenExternally = {
                onMenuDismissed()
                onOpenVerseExternally(id)
            },
            onCopy = {
                onMenuDismissed()
                onCopyVerse(id)
            },
            onSelectVerses = {
                onMenuDismissed()
                onVerseLongPressed(id)
            },
        )
    }
}

/**
 * Sprint K (owner request, Priya's approved design) — the reader footer hint. An italic,
 * de-emphasized line at the END of the reading pane keying the verse tap-out to the user's chosen
 * external Bible app ("Tap a verse to copy it or open it on Blue Letter Bible" / "…in MySword").
 * ALWAYS shown, regardless of the reading destination — most useful when reading IN_APP (the
 * read-here / study-there bridge), and it updates reactively when the app is changed in Settings
 * (the value flows from [ReaderViewModel.externalApp]).
 *
 * Q2 reworded the strings: a tap now opens the verse action menu, from which the user can copy OR
 * open externally, so the hint says both.
 *
 * Placement: pinned below the pager (NOT a [LazyColumn] item, and NOT the [ReaderAudioSlot] bottom
 * bar — that stays reserved for V4 audio), so it stays visible while reading and does not scroll with
 * the verses or swipe with chapters. Start-aligned under the 20dp horizontal padding to match the
 * verse text above; tight 6dp vertical padding (owner — don't waste space).
 *
 * A11y (NFR-V3-C): [Modifier.clearAndSetSemantics] removes it from TalkBack — every verse already
 * carries its own spoken label and click/long-click labels, so a second vague restatement is noise.
 * It is not a tap target.
 */
@Composable
private fun ReaderFooterHint(externalApp: ExternalBibleApp) {
    val appName = stringResource(externalBibleAppNameRes(externalApp))
    Text(
        text = stringResource(readerVerseTapHintRes(externalApp), appName),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                // Pinned footer: tight vertical padding (owner — don't waste space); 20dp
                // horizontal aligns it with the verse text above (it's no longer inside the
                // LazyColumn's content padding).
                .padding(horizontal = 20.dp, vertical = 6.dp)
                // Hide the hint from TalkBack (every verse already carries its own affordance
                // labels), but keep the testTag — clearAndSetSemantics{} would otherwise drop the
                // tag too, so re-declare it inside the cleared semantics block.
                .clearAndSetSemantics { semanticsTestTag = "reader-footer-hint" },
    )
}

/**
 * The spoken label for a verse row: "`<Book> <ch>:<verse>`. `<stripped text>`" (a superscription,
 * verse 0, speaks "verse 1" — the clamp target).
 *
 * **Q2 dropped the "Open " prefix.** Sprint H spoke "Open Psalm 23:1. …" because a tap opened the
 * external app directly; a tap now opens the verse action menu, so that promise had become false.
 * The affordance is carried by `onClickLabel` / `onLongClickLabel` on the row instead, which is
 * both the idiomatic place for it and honest about what each gesture does.
 */
private fun verseDescription(verse: VerseText): String {
    val id = verse.canonicalId
    val rawBook = BookCatalog.books.firstOrNull { it.order == VerseId.book(id) }?.canonicalName ?: ""
    // A single verse is always one chapter: speak "Psalm 23", never "Psalms 23".
    val book = ReadingFormatter.singularizeBookName(rawBook, singleChapter = true)
    val ch = VerseId.chapter(id)
    val v = VerseId.verse(id).coerceAtLeast(1)
    val plain = MarkupStripper.strip(verse.markup)
    return "$book $ch:$v. $plain"
}
