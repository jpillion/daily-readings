package com.jpillion.dailyreadingplanner.domain.model

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * D-MODEL-1 — the planner-domain verse window invariants. `ReferenceVerses` is chapter-relative
 * 1-based; it never depends on the bible spine. The per-chapter upper bound is the gate's job, not
 * the model's (the planner has no verse-count table), so it is deliberately NOT tested here.
 */
class ReferenceVersesTest {
    @Test
    fun `a valid window is accepted`() {
        val v = ReferenceVerses(1, 40)
        assertEquals(1, v.start)
        assertEquals(40, v.end)
    }

    @Test
    fun `a single-verse window is accepted`() {
        assertEquals(7, ReferenceVerses(7, 7).start)
    }

    @Test
    fun `a reversed window is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ReferenceVerses(40, 1) }
    }

    @Test
    fun `a verse-0 start is rejected - verse 0 is never a plan boundary`() {
        assertThrows(IllegalArgumentException::class.java) { ReferenceVerses(0, 40) }
    }

    @Test
    fun `a Reference can carry a verse window and defaults to null`() {
        val plain = Reference(BookCatalog.requireByName("Genesis"), 1)
        assertEquals(null, plain.verses)
        val windowed = Reference(BookCatalog.requireByName("Psalms"), 119, ReferenceVerses(1, 40))
        assertEquals(ReferenceVerses(1, 40), windowed.verses)
    }
}
