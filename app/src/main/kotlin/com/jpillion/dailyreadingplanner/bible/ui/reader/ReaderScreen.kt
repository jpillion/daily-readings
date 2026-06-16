package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 * cannot scroll past the first/last page). The old Prev/Next [androidx.compose.material3.OutlinedButton]
 * row is gone — its vertical space goes to the text.
 *
 * Each page renders the chapter's verse-id-keyed [LazyColumn] (D-V3-12): markup via [VerseRenderer],
 * de-emphasized [VerseText.nativeLabel] (D-V3-4), superscription as an unnumbered heading (D-V3-7).
 * Each verse is individually TAPPABLE (H7, BACKLOG #5) — a tap opens that exact verse in the user's
 * external Bible app via [onVerseTapped]; a ≥48dp row with a spoken "Open <book ch:verse>" role.
 * The list resets to the top on every page so a freshly-swiped chapter opens at verse 1.
 *
 * The route owns the VM, the [PagerState], the picker, and the per-page state provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    pagerState: PagerState,
    stateForPage: @Composable (Int) -> ReaderUiState,
    externalApp: ExternalBibleApp,
    versionState: ReaderVersionState,
    onOpenPicker: () -> Unit,
    onSelectVersion: (com.jpillion.dailyreadingplanner.bible.domain.model.BibleTranslation) -> Unit,
    onVerseTapped: (page: Int, verseId: Long) -> Unit,
    onRetry: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    // Owner request (Priya): the pencil (open the book/chapter picker) sits in the
                    // title slot, INLINE and to the LEFT of the chapter heading. The version control
                    // lives on the right (actions). The pencil keeps the prior reader-open-picker tag
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
                    onVerseTapped = { verseId -> onVerseTapped(page, verseId) },
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
    onVerseTapped: (Long) -> Unit,
    onRetry: () -> Unit,
) {
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
                            onVerseTapped = onVerseTapped,
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

@Composable
private fun VerseItem(
    verse: VerseText,
    isActive: Boolean,
    onVerseTapped: (Long) -> Unit,
) {
    val rendered = VerseRenderer.render(verse.markup)
    if (verse.isTitle) {
        // D-V3-7 / FR-V3-6: superscription — unnumbered italic heading, never a numbered verse.
        // A title tap opens verse 1 of the chapter (the URL builder clamps verse 0 -> 1).
        Text(
            text = rendered,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onVerseTapped(verse.canonicalId) }
                    .padding(vertical = 4.dp)
                    .semantics {
                        heading()
                        contentDescription = verseTapDescription(verse)
                    }.testTag("reader-title-${verse.canonicalId}"),
        )
        return
    }
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
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onVerseTapped(verse.canonicalId) }
                .padding(vertical = 4.dp)
                .semantics { contentDescription = verseTapDescription(verse) }
                .testTag("reader-verse-${verse.canonicalId}"),
    )
}

/**
 * Sprint K (owner request, Priya's approved design) — the reader footer hint. An italic,
 * de-emphasized line at the END of the verse list keying the verse-tap-out to the user's chosen
 * external Bible app ("Tap a verse to open it on Blue Letter Bible" / "…in MySword"). ALWAYS shown,
 * regardless of the reading destination — most useful when reading IN_APP (the read-here /
 * study-there bridge), and it updates reactively when the app is changed in Settings (the value
 * flows from [ReaderViewModel.externalApp]).
 *
 * Placement: a [LazyColumn] item, NOT the [ReaderAudioSlot] bottom bar (that stays reserved for V4
 * audio). Start-aligned under the 20dp horizontal content padding; ~24dp top padding separates it
 * from the final verse so it reads as a footer, ~16dp bottom gives breathing room.
 *
 * A11y (NFR-V3-C): [Modifier.clearAndSetSemantics] removes it from TalkBack — every verse already
 * speaks "Open <Book> <ch:verse>…" which carries the affordance, so a second vague restatement is
 * noise. It is not a tap target.
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
                // Hide the hint from TalkBack (every verse already speaks its "Open …" affordance),
                // but keep the testTag — clearAndSetSemantics{} would otherwise drop the tag too, so
                // re-declare it inside the cleared semantics block.
                .clearAndSetSemantics { semanticsTestTag = "reader-footer-hint" },
    )
}

/**
 * H7 — the spoken label for a tappable verse: "Open <Book> <ch>:<verse>" (a superscription, verse
 * 0, speaks "verse 1" — the clamp target). Generic wording (not the provider name) keeps the
 * stateless screen provider-agnostic; the destination app is resolved at tap time by OpenVerseUseCase.
 */
private fun verseTapDescription(verse: VerseText): String {
    val id = verse.canonicalId
    val rawBook = BookCatalog.books.firstOrNull { it.order == VerseId.book(id) }?.canonicalName ?: ""
    // A single tapped verse is always one chapter: speak "Psalm 23", never "Psalms 23".
    val book = ReadingFormatter.singularizeBookName(rawBook, singleChapter = true)
    val ch = VerseId.chapter(id)
    val v = VerseId.verse(id).coerceAtLeast(1)
    val plain = MarkupStripper.strip(verse.markup)
    return "Open $book $ch:$v. $plain"
}
