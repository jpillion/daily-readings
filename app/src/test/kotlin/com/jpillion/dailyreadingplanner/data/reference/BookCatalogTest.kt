package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * S3-T2 reconciliation: the production BookCatalog supersedes the Sprint 1 CSV, and this
 * test pins them together field-by-field so they can never drift (execution plan §5.2).
 */
class BookCatalogTest {
    // The CSV stays exactly Sprint 1's four columns (the 7-test plan gate parses it too);
    // catalog columns added later (S13 usfmCode) are pinned by their own gate tests.
    private data class CsvBook(
        val order: Int,
        val canonicalName: String,
        val chapterCount: Int,
        val blbAbbrev: String,
    )

    private val csvBooks: List<CsvBook> =
        checkNotNull(javaClass.classLoader?.getResource("book_catalog.csv")) { "book_catalog.csv not found" }
            .readText()
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val (order, name, count, abbrev) = line.split(",")
                CsvBook(order.toInt(), name, count.toInt(), abbrev)
            }

    @Test
    fun `catalog matches the link-verified sprint 1 csv exactly`() {
        assertThat(csvBooks).hasSize(66)
        assertWithMessage("BookCatalog must reconcile field-by-field with book_catalog.csv")
            .that(
                BookCatalog.books.map { CsvBook(it.order, it.canonicalName, it.chapterCount, it.blbAbbrev) },
            ).isEqualTo(csvBooks)
    }

    @Test
    fun `catalog is ordered 1 to 66 with unique names and abbreviations`() {
        assertThat(BookCatalog.books.map { it.order }).isEqualTo((1..66).toList())
        assertThat(BookCatalog.books.map { it.canonicalName }.toSet()).hasSize(66)
        assertThat(BookCatalog.books.map { it.blbAbbrev }.toSet()).hasSize(66)
    }

    @Test
    fun `lookup by canonical name resolves every book and rejects unknowns`() {
        for (book in BookCatalog.books) {
            assertThat(BookCatalog.findByName(book.canonicalName)).isEqualTo(book)
            assertThat(BookCatalog.requireByName(book.canonicalName)).isEqualTo(book)
        }
        assertThat(BookCatalog.findByName("Psalm")).isNull()
        assertThat(runCatching { BookCatalog.requireByName("Opinions of Hezekiah") }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
