package com.jpillion.dailyreadingplanner.bible.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog

/**
 * VC-T4 — the two-step book → chapter picker content (FR-V3-4). Stateless and directly testable
 * without Hilt (mirrors the app's stateless-content idiom); [BookChapterPickerSheet] wraps it in
 * an M3 bottom sheet. Step 1: the 66 books grouped OT/NT (testament derived from canon order,
 * `order <= 39`). Step 2: a chapter grid sized to the chosen book's `chapterCount`. Selecting a
 * chapter calls [onChapterSelected]. Every interactive cell/row meets the 48dp touch target.
 */
@Composable
fun BookChapterPicker(
    onChapterSelected: (Book, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    val book = selectedBook
    if (book == null) {
        BookList(onBookSelected = { selectedBook = it }, modifier = modifier)
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
private fun BookList(
    onBookSelected: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ot = BookCatalog.books.filter { it.order <= 39 }
    val nt = BookCatalog.books.filter { it.order >= 40 }
    LazyColumn(modifier = modifier.testTag("picker-book-list")) {
        item("hdr-ot") { TestamentHeader(stringResource(R.string.picker_testament_ot), "picker-testament-ot") }
        items(ot, key = { it.order }) { BookRow(it, onBookSelected) }
        item("hdr-nt") { TestamentHeader(stringResource(R.string.picker_testament_nt), "picker-testament-nt") }
        items(nt, key = { it.order }) { BookRow(it, onBookSelected) }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
                .testTag(tag),
    )
}

@Composable
private fun BookRow(
    book: Book,
    onBookSelected: (Book) -> Unit,
) {
    Text(
        text = book.canonicalName,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onBookSelected(book) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("picker-book-${book.order}"),
    )
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
            columns = GridCells.Adaptive(minSize = 56.dp),
            contentPadding = PaddingValues(16.dp),
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
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { onChapterSelected(chapter) }
                .testTag("picker-chapter-$chapter"),
    ) {
        Text(
            text = chapter.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
