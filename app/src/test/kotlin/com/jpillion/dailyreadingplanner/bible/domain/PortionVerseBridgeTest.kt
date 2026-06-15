package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class PortionVerseBridgeTest {
    private val bridge = PortionVerseBridge()

    private fun ref(
        name: String,
        ch: Int,
    ) = Reference(BookCatalog.requireByName(name), ch)

    @Test
    fun `multi-chapter portion yields one range per chapter`() {
        val portion = Portion(Stream.LAW_AND_HISTORY, listOf(ref("Genesis", 1), ref("Genesis", 2)))
        val ranges = bridge.rangesFor(portion)
        assertEquals(2, ranges.size)
        assertEquals(VerseId.chapterRange(1, 1), ranges[0])
        assertEquals(VerseId.chapterRange(1, 2), ranges[1])
    }

    @Test
    fun `two-book portion yields ranges across two books — never assumes shared book`() {
        val portion = Portion(Stream.NEW_TESTAMENT, listOf(ref("2 John", 1), ref("3 John", 1)))
        val ranges = bridge.rangesFor(portion)
        assertEquals(2, ranges.size)
        assertEquals(63, VerseId.book(ranges[0].startVerseId)) // 2 John
        assertEquals(64, VerseId.book(ranges[1].startVerseId)) // 3 John
    }

    @Test
    fun `range starts at verse 0 so superscriptions are covered`() {
        val portion = Portion(Stream.PSALMS_AND_PROPHECY, listOf(ref("Psalms", 23)))
        assertEquals(0, VerseId.verse(bridge.rangesFor(portion)[0].startVerseId))
    }
}
