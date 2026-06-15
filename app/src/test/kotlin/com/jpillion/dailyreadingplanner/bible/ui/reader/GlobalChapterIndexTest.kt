package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1 pins the global chapter index field-by-field and proves it is the SAME walk as the
 * mutation-pinned [ChapterNavigator] across every book boundary and both bounds (D-H-1/D-H-2).
 */
class GlobalChapterIndexTest {
    private val genesis = BookCatalog.requireByName("Genesis")
    private val exodus = BookCatalog.requireByName("Exodus")
    private val revelation = BookCatalog.requireByName("Revelation")

    @Test
    fun total_is_1189_the_sum_of_all_chapter_counts() {
        assertEquals(BookCatalog.books.sumOf { it.chapterCount }, GlobalChapterIndex.TOTAL_CHAPTERS)
        assertEquals(1189, GlobalChapterIndex.TOTAL_CHAPTERS)
    }

    @Test
    fun bounds_genesis1_is_zero_and_revelation22_is_last() {
        assertEquals(0, GlobalChapterIndex.indexOf(genesis, 1))
        assertEquals(genesis to 1, GlobalChapterIndex.chapterAt(0))
        val last = GlobalChapterIndex.TOTAL_CHAPTERS - 1
        assertEquals(last, GlobalChapterIndex.indexOf(revelation, 22))
        assertEquals(revelation to 22, GlobalChapterIndex.chapterAt(last))
    }

    @Test
    fun book_boundary_genesis50_to_exodus1_is_consecutive() {
        val g50 = GlobalChapterIndex.indexOf(genesis, 50)
        assertEquals(genesis to 50, GlobalChapterIndex.chapterAt(g50))
        assertEquals(exodus to 1, GlobalChapterIndex.chapterAt(g50 + 1))
    }

    @Test
    fun round_trip_for_every_canon_chapter() {
        var idx = 0
        BookCatalog.books.forEach { book ->
            (1..book.chapterCount).forEach { ch ->
                assertEquals(book to ch, GlobalChapterIndex.chapterAt(idx))
                assertEquals(idx, GlobalChapterIndex.indexOf(book, ch))
                idx++
            }
        }
        assertEquals(GlobalChapterIndex.TOTAL_CHAPTERS, idx)
    }

    @Test
    fun adjacency_matches_ChapterNavigator_at_every_index() {
        for (i in 0 until GlobalChapterIndex.TOTAL_CHAPTERS) {
            val (book, ch) = GlobalChapterIndex.chapterAt(i)
            val nav = ChapterNavigator.next(book, ch)
            if (i == GlobalChapterIndex.TOTAL_CHAPTERS - 1) {
                assertNull("Revelation 22 has no next", nav)
            } else {
                assertEquals(GlobalChapterIndex.chapterAt(i + 1), nav)
            }
            val prev = ChapterNavigator.previous(book, ch)
            if (i == 0) {
                assertNull("Genesis 1 has no previous", prev)
            } else {
                assertEquals(GlobalChapterIndex.chapterAt(i - 1), prev)
            }
        }
    }

    @Test
    fun out_of_range_index_throws() {
        assertThrows(IllegalArgumentException::class.java) { GlobalChapterIndex.chapterAt(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            GlobalChapterIndex.chapterAt(GlobalChapterIndex.TOTAL_CHAPTERS)
        }
        assertTrue(true)
    }
}
