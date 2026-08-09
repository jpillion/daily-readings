package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * S13 token gate (spec §3, the Sprint 1 pattern): every USFM/OSIS book code used in
 * YouVersion URLs, pinned book-by-book against the exact 66 values that passed the live
 * link check on 2026-06-11 (docs/data/provider-link-checks.md — bible.com chapter 1 + last
 * chapter of every book, content-asserted). The live check proved the tokens once; this test
 * prevents regression forever after. Mind the USFM quirks a derivation would get wrong:
 * Ezekiel = EZK, Joel = JOL, Nahum = NAM, Mark = MRK, Philippians = PHP, Jude = JUD,
 * 1–3 John = 1JN/2JN/3JN.
 */
class UsfmCodeCatalogTest {
    private val verified =
        mapOf(
            "Genesis" to "GEN",
            "Exodus" to "EXO",
            "Leviticus" to "LEV",
            "Numbers" to "NUM",
            "Deuteronomy" to "DEU",
            "Joshua" to "JOS",
            "Judges" to "JDG",
            "Ruth" to "RUT",
            "1 Samuel" to "1SA",
            "2 Samuel" to "2SA",
            "1 Kings" to "1KI",
            "2 Kings" to "2KI",
            "1 Chronicles" to "1CH",
            "2 Chronicles" to "2CH",
            "Ezra" to "EZR",
            "Nehemiah" to "NEH",
            "Esther" to "EST",
            "Job" to "JOB",
            "Psalms" to "PSA",
            "Proverbs" to "PRO",
            "Ecclesiastes" to "ECC",
            "Song of Solomon" to "SNG",
            "Isaiah" to "ISA",
            "Jeremiah" to "JER",
            "Lamentations" to "LAM",
            "Ezekiel" to "EZK",
            "Daniel" to "DAN",
            "Hosea" to "HOS",
            "Joel" to "JOL",
            "Amos" to "AMO",
            "Obadiah" to "OBA",
            "Jonah" to "JON",
            "Micah" to "MIC",
            "Nahum" to "NAM",
            "Habakkuk" to "HAB",
            "Zephaniah" to "ZEP",
            "Haggai" to "HAG",
            "Zechariah" to "ZEC",
            "Malachi" to "MAL",
            "Matthew" to "MAT",
            "Mark" to "MRK",
            "Luke" to "LUK",
            "John" to "JHN",
            "Acts" to "ACT",
            "Romans" to "ROM",
            "1 Corinthians" to "1CO",
            "2 Corinthians" to "2CO",
            "Galatians" to "GAL",
            "Ephesians" to "EPH",
            "Philippians" to "PHP",
            "Colossians" to "COL",
            "1 Thessalonians" to "1TH",
            "2 Thessalonians" to "2TH",
            "1 Timothy" to "1TI",
            "2 Timothy" to "2TI",
            "Titus" to "TIT",
            "Philemon" to "PHM",
            "Hebrews" to "HEB",
            "James" to "JAS",
            "1 Peter" to "1PE",
            "2 Peter" to "2PE",
            "1 John" to "1JN",
            "2 John" to "2JN",
            "3 John" to "3JN",
            "Jude" to "JUD",
            "Revelation" to "REV",
        )

    @Test
    fun `every catalog usfm code matches the live-verified value - book by book`() {
        assertThat(verified).hasSize(66)
        for (book in BookCatalog.books) {
            assertWithMessage(book.canonicalName)
                .that(book.usfmCode)
                .isEqualTo(verified.getValue(book.canonicalName))
        }
    }

    @Test
    fun `usfm codes are unique across the catalog`() {
        assertThat(BookCatalog.books.map { it.usfmCode }.toSet()).hasSize(66)
    }
}
