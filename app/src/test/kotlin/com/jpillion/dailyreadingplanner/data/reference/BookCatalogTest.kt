package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * S3-T2 reconciliation: the production BookCatalog supersedes the Sprint 1 CSV, and this
 * test pins them together field-by-field so they can never drift (execution plan §5.2).
 */
class BookCatalogTest {
    private val csvBooks: List<Book> =
        checkNotNull(javaClass.classLoader?.getResource("book_catalog.csv")) { "book_catalog.csv not found" }
            .readText()
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val (order, name, count, abbrev) = line.split(",")
                Book(order.toInt(), name, count.toInt(), abbrev)
            }

    @Test
    fun `catalog matches the link-verified sprint 1 csv exactly`() {
        assertThat(csvBooks).hasSize(66)
        assertWithMessage("BookCatalog must reconcile field-by-field with book_catalog.csv")
            .that(BookCatalog.books)
            .isEqualTo(csvBooks)
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
