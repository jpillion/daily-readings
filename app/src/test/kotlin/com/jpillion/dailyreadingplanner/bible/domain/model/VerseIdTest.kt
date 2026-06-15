package com.jpillion.dailyreadingplanner.bible.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VerseIdTest {
    @Test
    fun `encodes famous verses`() {
        assertEquals(1_001_001L, VerseId.encode(1, 1, 1)) // Gen 1:1
        assertEquals(43_003_016L, VerseId.encode(43, 3, 16)) // John 3:16
        assertEquals(19_023_001L, VerseId.encode(19, 23, 1)) // Ps 23:1
        assertEquals(66_022_021L, VerseId.encode(66, 22, 21)) // Rev 22:21
    }

    @Test
    fun `decodes round-trip`() {
        val id = VerseId.encode(43, 3, 16)
        assertEquals(43, VerseId.book(id))
        assertEquals(3, VerseId.chapter(id))
        assertEquals(16, VerseId.verse(id))
    }

    @Test
    fun `decodes maxima`() {
        val id = VerseId.encode(19, 119, 176) // Ps 119:176
        assertEquals(19, VerseId.book(id))
        assertEquals(119, VerseId.chapter(id))
        assertEquals(176, VerseId.verse(id))
    }

    @Test
    fun `chapterRange starts at verse 0 so titles are included`() {
        val r = VerseId.chapterRange(19, 23)
        assertEquals(VerseId.encode(19, 23, 0), r.startVerseId)
        assertEquals(VerseId.encode(19, 23, 999), r.endVerseId)
        assertEquals(0, VerseId.verse(r.startVerseId))
    }

    @Test
    fun `bookRange spans the whole book`() {
        val r = VerseId.bookRange(43)
        assertEquals(VerseId.encode(43, 0, 0), r.startVerseId)
        assertEquals(VerseId.encode(43, 999, 999), r.endVerseId)
    }
}
