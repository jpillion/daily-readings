package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog

/**
 * VC-T5 — pure adjacent-chapter computation over canon order (FR-V3-5). Prev/Next walk the whole
 * Bible: chapter rollover crosses book boundaries (Genesis 50 → Exodus 1), and the ends are
 * bounded (Genesis 1 has no previous, Revelation 22 has no next → null). Used by the reader's
 * visible Prev/Next controls; no Compose, JVM-unit-testable.
 */
object ChapterNavigator {
    /** The (book, chapter) before [book]:[chapter], or null at the very start (Genesis 1). */
    fun previous(
        book: Book,
        chapter: Int,
    ): Pair<Book, Int>? =
        when {
            chapter > 1 -> book to (chapter - 1)
            else ->
                BookCatalog.books
                    .firstOrNull { it.order == book.order - 1 }
                    ?.let { prev -> prev to prev.chapterCount }
        }

    /** The (book, chapter) after [book]:[chapter], or null at the very end (Revelation 22). */
    fun next(
        book: Book,
        chapter: Int,
    ): Pair<Book, Int>? =
        when {
            chapter < book.chapterCount -> book to (chapter + 1)
            else ->
                BookCatalog.books
                    .firstOrNull { it.order == book.order + 1 }
                    ?.let { nxt -> nxt to 1 }
        }
}
