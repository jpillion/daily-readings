package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.bible.data.markup.MarkupStripper
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseText

/**
 * VC-T3 — the stateless in-app KJV reader. Renders one [ReaderUiState]; the route owns the VM and
 * side-effects. The body is a `LazyColumn` whose every verse is an item keyed by `canonicalId`
 * (D-V3-12, the rewrite-forcing P0): it reads as flowing prose but each verse is individually
 * addressable / scroll-locatable for V3.x highlights and V4 audio.
 *
 * Faithful rendering: `<a>` added words italic via [VerseRenderer]; the verse's de-emphasized
 * label is the seam's [VerseText.nativeLabel] (NEVER derived from the id, D-V3-4); a verse-0
 * superscription ([VerseText.isTitle]) is an unnumbered italic heading with `heading()` semantics
 * (D-V3-7, FR-V3-6). TalkBack speaks `MarkupStripper.strip(markup)`, never the tags (NFR-V3-C).
 *
 * The empty audio seam (D-V3-14): [ReaderUiState.Content.activeVerseId] is read per verse for a
 * (V4) highlight background — always null in V3.0 — and the [bottomBar] hosts an empty audio slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onOpenPicker: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    prevEnabled: Boolean = true,
    nextEnabled: Boolean = true,
) {
    // A freshly-opened chapter/portion must start at the top: the LazyListState persists across
    // content swaps (Prev/Next, tap-a-reading, picker), so without this it keeps the prior
    // chapter's scroll offset and opens part way down. Key on the displayed chapter identity so
    // the reset fires on every chapter change (and on first load — a no-op at offset 0).
    val chapterKey =
        (state as? ReaderUiState.Content)?.blocks?.firstOrNull()?.let { "${it.bookNo}-${it.chapter}" }
    LaunchedEffect(chapterKey) {
        if (chapterKey != null) listState.scrollToItem(0)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? ReaderUiState.Content)?.title.orEmpty()
                    Text(text = title, modifier = Modifier.testTag("reader-title"))
                },
                actions = {
                    IconButton(onClick = onOpenPicker, modifier = Modifier.testTag("reader-open-picker")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.reader_pick_chapter),
                        )
                    }
                },
            )
        },
        // D-V3-14: reserved audio seam — an empty bottom bar slot. Renders nothing in V3.0; V4
        // audio transport drops in here without restructuring the screen.
        bottomBar = { ReaderAudioSlot() },
    ) { innerPadding ->
        when (state) {
            is ReaderUiState.Loading ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.testTag("reader-loading")) }

            is ReaderUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
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

            is ReaderUiState.Content ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("reader-list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        state.blocks.forEach { block ->
                            item(key = "hdr-${block.bookNo}-${block.chapter}") { ChapterHeader(block) }
                            items(block.verses, key = { it.canonicalId }) { verse ->
                                VerseItem(verse, isActive = verse.canonicalId == state.activeVerseId)
                            }
                        }
                    }
                    ChapterNavBar(onPrevChapter, onNextChapter, prevEnabled, nextEnabled)
                }
        }
    }
}

@Composable
private fun ChapterHeader(block: ChapterContent) {
    Text(
        text = "${block.bookName} ${block.chapter}",
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
) {
    val rendered = VerseRenderer.render(verse.markup)
    if (verse.isTitle) {
        // D-V3-7 / FR-V3-6: superscription — unnumbered italic heading, never a numbered verse.
        Text(
            text = rendered,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics {
                        heading()
                        // NFR-V3-C: TalkBack speaks the plain title text, never markup tags.
                        contentDescription = MarkupStripper.strip(verse.markup)
                    }.testTag("reader-title-${verse.canonicalId}"),
        )
        return
    }
    // A numbered verse: de-emphasized native label (D-V3-4: from the seam, never derived) + body.
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
                .padding(vertical = 4.dp)
                .semantics { contentDescription = MarkupStripper.strip(verse.markup) }
                .testTag("reader-verse-${verse.canonicalId}"),
    )
}

@Composable
private fun ChapterNavBar(
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = onPrevChapter,
            enabled = prevEnabled,
            modifier = Modifier.testTag("reader-prev-chapter"),
        ) { Text(stringResource(R.string.reader_prev_chapter)) }
        OutlinedButton(
            onClick = onNextChapter,
            enabled = nextEnabled,
            modifier = Modifier.testTag("reader-next-chapter"),
        ) { Text(stringResource(R.string.reader_next_chapter)) }
    }
}
