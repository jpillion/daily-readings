package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog

/**
 * H1 (D-H-1) — a contiguous integer index over EVERY chapter of the canon, in order:
 * Genesis 1 = 0, Genesis 50 = 49, Exodus 1 = 50, … Revelation 22 = [TOTAL_CHAPTERS] - 1.
 *
 * This is the single identity model for the reader's chapter [androidx.compose.foundation.pager.HorizontalPager]
 * (D-H-2): page == global chapter index, so continuous left/right swipe walks the whole Bible —
 * crossing book boundaries automatically (Genesis 50 → Exodus 1) and bounded at the ends (the
 * pager simply cannot scroll past page 0 or [TOTAL_CHAPTERS] - 1, giving the Genesis-1 / Rev-22
 * bounds for free). Adjacency therefore matches [ChapterNavigator] exactly — pinned field-by-field
 * in GlobalChapterIndexTest against the navigator across all book boundaries and both bounds.
 *
 * Pure / JVM-unit-testable; the [Book] is resolved against [BookCatalog] canon order, never derived.
 */
object GlobalChapterIndex {
    /** Cumulative (book, chapter) listing in canon order; size == [TOTAL_CHAPTERS]. */
    private val chapters: List<Pair<Book, Int>> =
        BookCatalog.books.flatMap { book -> (1..book.chapterCount).map { ch -> book to ch } }

    /** Total chapters in the canon (1189): the [HorizontalPager] page count. */
    val TOTAL_CHAPTERS: Int = chapters.size

    /** The (book, chapter) at global [index]; throws for an out-of-range index (a programming error). */
    fun chapterAt(index: Int): Pair<Book, Int> {
        require(index in 0 until TOTAL_CHAPTERS) { "global chapter index $index out of 0..${TOTAL_CHAPTERS - 1}" }
        return chapters[index]
    }

    /** The global index of [book]:[chapter]; throws if the pair is not a real canon chapter. */
    fun indexOf(
        book: Book,
        chapter: Int,
    ): Int {
        val i = chapters.indexOfFirst { it.first.order == book.order && it.second == chapter }
        require(i >= 0) { "${book.canonicalName} $chapter is not a canon chapter" }
        return i
    }
}
