package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
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
        val portion = Portion(1, listOf(ref("Genesis", 1), ref("Genesis", 2)))
        val ranges = bridge.rangesFor(portion)
        assertEquals(2, ranges.size)
        assertEquals(VerseId.chapterRange(1, 1), ranges[0])
        assertEquals(VerseId.chapterRange(1, 2), ranges[1])
    }

    @Test
    fun `two-book portion yields ranges across two books — never assumes shared book`() {
        val portion = Portion(3, listOf(ref("2 John", 1), ref("3 John", 1)))
        val ranges = bridge.rangesFor(portion)
        assertEquals(2, ranges.size)
        assertEquals(63, VerseId.book(ranges[0].startVerseId)) // 2 John
        assertEquals(64, VerseId.book(ranges[1].startVerseId)) // 3 John
    }

    @Test
    fun `range starts at verse 0 so superscriptions are covered`() {
        val portion = Portion(2, listOf(ref("Psalms", 23)))
        assertEquals(0, VerseId.verse(bridge.rangesFor(portion)[0].startVerseId))
    }

    private fun windowedRef(
        name: String,
        ch: Int,
        start: Int,
        end: Int,
    ) = Reference(BookCatalog.requireByName(name), ch, ReferenceVerses(start, end))

    @Test
    fun `a verse-windowed ref maps to exactly that verse_id window — Psalm 119 day 1`() {
        // D-READER-1: Mar 9 = Psalms 119:1-40. The bridge produces [encode(19,119,1) .. (19,119,40)],
        // NOT the whole chapter — so the reader renders only verses 1-40.
        val portion = Portion(2, listOf(windowedRef("Psalms", 119, 1, 40)))
        val range = bridge.rangesFor(portion).single()
        assertEquals(VerseId.encode(19, 119, 1), range.startVerseId)
        assertEquals(VerseId.encode(19, 119, 40), range.endVerseId)
        // a body window never reaches verse 0 (no superscription pulled in)
        assertEquals(1, VerseId.verse(range.startVerseId))
    }

    @Test
    fun `the four Psalm 119 windows map to four contiguous non-overlapping verse_id ranges`() {
        val windows = listOf(1 to 40, 41 to 80, 81 to 128, 129 to 176)
        val ranges =
            windows.map { (s, e) ->
                bridge
                    .rangesFor(
                        Portion(2, listOf(windowedRef("Psalms", 119, s, e))),
                    ).single()
            }
        assertEquals(VerseId.encode(19, 119, 1), ranges.first().startVerseId)
        assertEquals(VerseId.encode(19, 119, 176), ranges.last().endVerseId)
        for (i in 1 until ranges.size) {
            assertEquals(ranges[i - 1].endVerseId + 1, ranges[i].startVerseId)
        }
    }

    @Test
    fun `a whole-chapter ref still maps to the whole chapter (windowing is opt-in)`() {
        val portion = Portion(2, listOf(ref("Psalms", 23)))
        assertEquals(VerseId.chapterRange(19, 23), bridge.rangesFor(portion).single())
    }
}
