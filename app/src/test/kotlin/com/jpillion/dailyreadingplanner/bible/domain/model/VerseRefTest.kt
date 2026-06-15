package com.jpillion.dailyreadingplanner.bible.domain.model

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerseRefTest {
    private val psalms = BookCatalog.requireByName("Psalms")
    private val john = BookCatalog.requireByName("John")

    @Test
    fun `verse 0 superscription is allowed — the Psalm-title trap`() {
        // A require(verse >= 1) regression drops every Psalm title and MUST fail here.
        val title = VerseRef(psalms, 23, 0)
        assertEquals(VerseId.encode(19, 23, 0), title.verseId)
    }

    @Test
    fun `verse 999 upper bound is allowed`() {
        VerseRef(psalms, 23, 999)
    }

    @Test
    fun `negative verse is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { VerseRef(psalms, 23, -1) }
    }

    @Test
    fun `verse above 999 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { VerseRef(psalms, 23, 1000) }
    }

    @Test
    fun `chapter out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { VerseRef(john, 22, 1) } // John has 21
    }

    @Test
    fun `verseId encodes correctly`() {
        assertEquals(43_003_016L, VerseRef(john, 3, 16).verseId)
    }
}
