package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import org.junit.Test

/**
 * SEG-1 (D-SEG-1) — the segmentation rule, pinned on the shapes that actually occur in the bundled
 * plans: a book change splits (the Ticket-1 repro), a chapter gap splits, a verse window does NOT
 * split (the M'Cheyne 02/28 merge case), and the split always partitions the portion in order.
 *
 * The exhaustive proof over the real assets — that every segment is a contiguous global-chapter run
 * that `ReadingPagerIndex` accepts — is the data gate `data/plan/PlanSegmentGateTest`.
 */
class ReadingSegmentsTest {
    private fun ref(
        book: String,
        chapter: Int,
        verses: ReferenceVerses? = null,
    ) = Reference(BookCatalog.requireByName(book), chapter, verses)

    private fun names(segment: Portion): List<Pair<String, Int>> =
        segment.refs.map {
            it.book.canonicalName to
                it.chapter
        }

    @Test
    fun `same book with consecutive chapters is one segment`() {
        val segments = ReadingSegments.segmentsOf(portion(1, "Genesis" to 1, "Genesis" to 2, "Genesis" to 3))

        assertThat(segments).hasSize(1)
        assertThat(names(segments.single()))
            .containsExactly("Genesis" to 1, "Genesis" to 2, "Genesis" to 3)
            .inOrder()
    }

    @Test
    fun `a single-run portion returns a one-element list equal to the original`() {
        val original = portion(2, "Psalms" to 1, "Psalms" to 2)

        val segments = ReadingSegments.segmentsOf(original)

        assertThat(segments).hasSize(1)
        assertThat(segments.single()).isEqualTo(original)
    }

    @Test
    fun `a book change splits - Chronological 07-25 Isaiah 37-39 plus Psalms 76`() {
        // THE Ticket-1 repro: Psalms 76's global chapter index is LOWER than Isaiah 37's, so the
        // undivided portion is not a contiguous ascending run and ReadingPagerIndex rejects it.
        val segments =
            ReadingSegments.segmentsOf(
                portion(1, "Isaiah" to 37, "Isaiah" to 38, "Isaiah" to 39, "Psalms" to 76),
            )

        assertThat(segments).hasSize(2)
        assertThat(names(segments[0]))
            .containsExactly("Isaiah" to 37, "Isaiah" to 38, "Isaiah" to 39)
            .inOrder()
        assertThat(names(segments[1])).containsExactly("Psalms" to 76)
    }

    @Test
    fun `a chapter gap splits - Genesis 3-4 and Genesis 8-10 are two cards`() {
        // The owner's literal rule: same book, non-consecutive chapters = different cards.
        val segments =
            ReadingSegments.segmentsOf(
                portion(1, "Genesis" to 3, "Genesis" to 4, "Genesis" to 8, "Genesis" to 9, "Genesis" to 10),
            )

        assertThat(segments).hasSize(2)
        assertThat(names(segments[0])).containsExactly("Genesis" to 3, "Genesis" to 4).inOrder()
        assertThat(names(segments[1]))
            .containsExactly("Genesis" to 8, "Genesis" to 9, "Genesis" to 10)
            .inOrder()
    }

    @Test
    fun `a verse window does NOT split - M'Cheyne 02-28 Exodus 11 plus Exodus 12 1-21 is ONE segment`() {
        // D-SEG-1: segmentation asks only "same book, next chapter?". The verse window is a display
        // concern handled INSIDE the card by ReadingFormatter's own (deliberately different) run
        // logic, which is why the single card reads "Exodus 11; Exodus 12:1–21".
        val original =
            Portion(
                streamNumber = 1,
                refs = listOf(ref("Exodus", 11), ref("Exodus", 12, ReferenceVerses(1, 21))),
            )

        val segments = ReadingSegments.segmentsOf(original)

        assertThat(segments).hasSize(1)
        assertThat(segments.single()).isEqualTo(original)
        assertThat(ReadingFormatter.format(segments.single())).isEqualTo("Exodus 11; Exodus 12:1–21")
    }

    @Test
    fun `the two-book Jun 19 - Dec 19 portion splits into two segments`() {
        // 2 John 1 and 3 John 1 are globally ADJACENT (so the undivided portion builds a
        // ReadingPagerIndex today), but they are different books — so they are two cards.
        val segments = ReadingSegments.segmentsOf(portion(3, "2 John" to 1, "3 John" to 1))

        assertThat(segments).hasSize(2)
        assertThat(names(segments[0])).containsExactly("2 John" to 1)
        assertThat(names(segments[1])).containsExactly("3 John" to 1)
    }

    @Test
    fun `segments partition the portion - concatenation reproduces the refs in order`() {
        val original =
            portion(
                1,
                "Isaiah" to 37,
                "Isaiah" to 38,
                "Psalms" to 76,
                "Genesis" to 3,
                "Genesis" to 4,
                "Genesis" to 8,
            )

        val segments = ReadingSegments.segmentsOf(original)

        assertThat(segments.flatMap { it.refs }).containsExactlyElementsIn(original.refs).inOrder()
    }

    @Test
    fun `every segment carries the original stream number`() {
        val original = portion(4, "Isaiah" to 37, "Psalms" to 76, "Genesis" to 3)

        val segments = ReadingSegments.segmentsOf(original)

        assertThat(segments).hasSize(3)
        assertThat(segments.map { it.streamNumber }).containsExactly(4, 4, 4)
    }
}
