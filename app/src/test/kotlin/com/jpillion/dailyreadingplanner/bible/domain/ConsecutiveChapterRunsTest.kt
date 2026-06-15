package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Reference
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsecutiveChapterRunsTest {
    private fun ref(
        name: String,
        ch: Int,
    ) = Reference(BookCatalog.requireByName(name), ch)

    @Test
    fun `consecutive same-book chapters collapse into one run`() {
        val runs = ConsecutiveChapterRuns.group(listOf(ref("Genesis", 1), ref("Genesis", 2)))
        assertEquals(1, runs.size)
        assertEquals(2, runs[0].size)
    }

    @Test
    fun `two-book portion yields two singleton runs`() {
        val runs = ConsecutiveChapterRuns.group(listOf(ref("2 John", 1), ref("3 John", 1)))
        assertEquals(2, runs.size)
        assertEquals(1, runs[0].size)
        assertEquals(1, runs[1].size)
    }

    @Test
    fun `non-consecutive chapters of the same book split`() {
        val runs = ConsecutiveChapterRuns.group(listOf(ref("Genesis", 1), ref("Genesis", 3)))
        assertEquals(2, runs.size)
    }

    @Test
    fun `same chapter-arithmetic across different books does NOT merge`() {
        // Jude (ch 1) then Revelation 2: chapter arithmetic alone would merge (1+1==2) if the
        // book guard were dropped — the book check must keep them separate. Mutation pin.
        val runs = ConsecutiveChapterRuns.group(listOf(ref("Jude", 1), ref("Revelation", 2)))
        assertEquals(2, runs.size)
    }

    @Test
    fun `order is preserved`() {
        val runs = ConsecutiveChapterRuns.group(listOf(ref("2 John", 1), ref("3 John", 1)))
        assertEquals("2 John", runs[0][0].book.canonicalName)
        assertEquals("3 John", runs[1][0].book.canonicalName)
    }
}
