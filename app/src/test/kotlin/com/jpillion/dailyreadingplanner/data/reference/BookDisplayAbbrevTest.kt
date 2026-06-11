package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * D-S9-1: display abbreviations are *derived* from the live-verified BLB URL tokens
 * (first letter uppercased), never hand-authored — so they can't drift from the catalog.
 */
class BookDisplayAbbrevTest {
    @Test
    fun `plain books capitalize the first letter`() {
        assertThat(BookCatalog.requireByName("Genesis").displayAbbrev).isEqualTo("Gen")
        assertThat(BookCatalog.requireByName("Psalms").displayAbbrev).isEqualTo("Psa")
        assertThat(BookCatalog.requireByName("Matthew").displayAbbrev).isEqualTo("Mat")
    }

    @Test
    fun `numbered books keep the digit and capitalize the first letter after it`() {
        assertThat(BookCatalog.requireByName("1 Samuel").displayAbbrev).isEqualTo("1Sa")
        assertThat(BookCatalog.requireByName("2 John").displayAbbrev).isEqualTo("2Jo")
        assertThat(BookCatalog.requireByName("3 John").displayAbbrev).isEqualTo("3Jo")
        assertThat(BookCatalog.requireByName("1 Corinthians").displayAbbrev).isEqualTo("1Co")
    }

    @Test
    fun `accepted BLB-convention quirks are pinned - D-S9-1`() {
        assertThat(BookCatalog.requireByName("John").displayAbbrev).isEqualTo("Jhn")
        assertThat(BookCatalog.requireByName("Song of Solomon").displayAbbrev).isEqualTo("Sng")
        assertThat(BookCatalog.requireByName("Ruth").displayAbbrev).isEqualTo("Rth")
        assertThat(BookCatalog.requireByName("Philippians").displayAbbrev).isEqualTo("Phl")
        assertThat(BookCatalog.requireByName("Jude").displayAbbrev).isEqualTo("Jde")
    }

    @Test
    fun `derivation invariants hold across all 66 books`() {
        for (book in BookCatalog.books) {
            assertThat(book.displayAbbrev.length).isEqualTo(book.blbAbbrev.length)
            assertThat(book.displayAbbrev.lowercase()).isEqualTo(book.blbAbbrev)
            val firstLetter = book.displayAbbrev.first { it.isLetter() }
            assertThat(firstLetter.isUpperCase()).isTrue()
        }
        // 66 distinct visual forms — no two books collapse to the same abbreviation.
        assertThat(BookCatalog.books.map { it.displayAbbrev }.toSet()).hasSize(66)
    }
}
