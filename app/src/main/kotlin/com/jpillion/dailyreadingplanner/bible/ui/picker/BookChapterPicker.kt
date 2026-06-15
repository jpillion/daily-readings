package com.jpillion.dailyreadingplanner.bible.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog

private const val PICKER_COLUMNS = 5

/**
 * VC-T4 — the two-step book → chapter picker content (FR-V3-4). Stateless and directly testable
 * without Hilt; [BookChapterPickerSheet] wraps it in an M3 bottom sheet.
 *
 * Step 1 (owner redesign): a dense [LazyVerticalGrid] of the 66 books shown as abbreviated labels
 * ([Book.displayAbbrev] — the one live-verified catalog abbreviation, never a second table) so all
 * books fit on a single screen without scrolling; OT/NT section headers span the full grid width.
 * Step 2: a chapter-number grid using the SAME grid idiom (left-to-right, top-to-bottom), which
 * scrolls within the sheet for high-chapter books (Psalms = 150). Every cell shows the abbreviated
 * label but speaks the FULL book name via contentDescription; every cell meets the 48dp target.
 */
@Composable
fun BookChapterPicker(
    onChapterSelected: (Book, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    val book = selectedBook
    if (book == null) {
        BookGrid(onBookSelected = { selectedBook = it }, modifier = modifier)
    } else {
        ChapterGrid(
            book = book,
            onChapterSelected = { onChapterSelected(book, it) },
            onBack = { selectedBook = null },
            modifier = modifier,
        )
    }
}

@Composable
private fun BookGrid(
    onBookSelected: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ot = BookCatalog.books.filter { it.order <= 39 }
    val nt = BookCatalog.books.filter { it.order >= 40 }
    LazyVerticalGrid(
        columns = GridCells.Fixed(PICKER_COLUMNS),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag("picker-book-list"),
    ) {
        item("hdr-ot", span = { GridItemSpan(maxLineSpan) }) {
            TestamentHeader(stringResource(R.string.picker_testament_ot), "picker-testament-ot")
        }
        items(ot, key = { it.order }, span = { GridItemSpan(1) }) { BookCell(it, onBookSelected) }
        item("hdr-nt", span = { GridItemSpan(maxLineSpan) }) {
            TestamentHeader(stringResource(R.string.picker_testament_nt), "picker-testament-nt")
        }
        items(nt, key = { it.order }, span = { GridItemSpan(1) }) { BookCell(it, onBookSelected) }
    }
}

@Composable
private fun TestamentHeader(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp)
                .semantics { heading() }
                .testTag(tag),
    )
}

@Composable
private fun BookCell(
    book: Book,
    onBookSelected: (Book) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onBookSelected(book) }
                .padding(4.dp)
                .semantics { contentDescription = book.canonicalName }
                .testTag("picker-book-${book.order}"),
    ) {
        Text(
            text = book.displayAbbrev,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun ChapterGrid(
    book: Book,
    onChapterSelected: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.testTag("picker-chapter-back"),
        ) { Text(stringResource(R.string.picker_back_to_books)) }
        Text(
            text = book.canonicalName,
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() }
                    .testTag("picker-chapter-title"),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(PICKER_COLUMNS),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("picker-chapter-grid"),
        ) {
            items((1..book.chapterCount).toList(), key = { it }) { chapter ->
                ChapterCell(book, chapter, onChapterSelected)
            }
        }
    }
}

@Composable
private fun ChapterCell(
    book: Book,
    chapter: Int,
    onChapterSelected: (Int) -> Unit,
) {
    val cd = stringResource(R.string.picker_chapter_cd, book.canonicalName, chapter)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { onChapterSelected(chapter) }
                .padding(4.dp)
                .semantics { contentDescription = cd }
                .testTag("picker-chapter-$chapter"),
    ) {
        Text(
            text = chapter.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
