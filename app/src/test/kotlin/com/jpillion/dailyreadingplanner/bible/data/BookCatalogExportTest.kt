package com.jpillion.dailyreadingplanner.bible.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import org.junit.Test

/** VA-T2 — the committed book_catalog_export.json MUST stay field-identical to BookCatalog
 *  (D-S9-1 anti-drift). The importer and the gate both read this fixture; drift here = a build
 *  break, not a silent divergence. */
class BookCatalogExportTest {
    private data class Row(
        val bookNo: Int,
        val name: String,
        val usfm: String,
        val testament: String,
        val chapterCount: Int,
    )

    private fun export(): List<Row> {
        val raw =
            checkNotNull(javaClass.classLoader?.getResource("bible/book_catalog_export.json"))
                .readText()
        return Regex("\\{[^}]*}")
            .findAll(raw)
            .map { obj ->
                fun str(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(obj.value)!!.groupValues[1]

                fun int(k: String) = Regex("\"$k\"\\s*:\\s*(\\d+)").find(obj.value)!!.groupValues[1].toInt()
                Row(int("book_no"), str("name"), str("usfm_code"), str("testament"), int("chapter_count"))
            }.toList()
    }

    @Test
    fun `export is field-identical to BookCatalog with derived testament`() {
        val expected =
            BookCatalog.books.map {
                Row(it.order, it.canonicalName, it.usfmCode, if (it.order <= 39) "OT" else "NT", it.chapterCount)
            }
        val actual = export().sortedBy { it.bookNo }
        assertWithMessage("export must have 66 rows").that(actual).hasSize(66)
        assertThat(actual).isEqualTo(expected)
    }
}
