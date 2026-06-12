package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * S15 token gate (spec §3, the UsfmCodeCatalogTest pattern) for MySword's numeric reference
 * form (D-S15-1): `r={bookNumber}.{chapter}` where the book number is the standard 66-book
 * canon position — the vendor documents `19.37.3-6` (mysword-bible.info, "Link or open
 * MySword from other apps"), and 19 = Psalms pins the numbering scheme. The map below is the
 * full hand-authored table, written independently of [BookCatalog], so a catalog reorder or
 * an off-by-one can never slip through. MySword links resolve only inside the installed app
 * (the https URL serves an install stub, verified 2026-06-11), so the live half of the gate
 * is the owner's on-device pass — adb spot-check list in the Sprint 15 handoff.
 */
class MySwordTokenCatalogTest {
    private val verified =
        mapOf(
            "Genesis" to 1,
            "Exodus" to 2,
            "Leviticus" to 3,
            "Numbers" to 4,
            "Deuteronomy" to 5,
            "Joshua" to 6,
            "Judges" to 7,
            "Ruth" to 8,
            "1 Samuel" to 9,
            "2 Samuel" to 10,
            "1 Kings" to 11,
            "2 Kings" to 12,
            "1 Chronicles" to 13,
            "2 Chronicles" to 14,
            "Ezra" to 15,
            "Nehemiah" to 16,
            "Esther" to 17,
            "Job" to 18,
            "Psalms" to 19,
            "Proverbs" to 20,
            "Ecclesiastes" to 21,
            "Song of Solomon" to 22,
            "Isaiah" to 23,
            "Jeremiah" to 24,
            "Lamentations" to 25,
            "Ezekiel" to 26,
            "Daniel" to 27,
            "Hosea" to 28,
            "Joel" to 29,
            "Amos" to 30,
            "Obadiah" to 31,
            "Jonah" to 32,
            "Micah" to 33,
            "Nahum" to 34,
            "Habakkuk" to 35,
            "Zephaniah" to 36,
            "Haggai" to 37,
            "Zechariah" to 38,
            "Malachi" to 39,
            "Matthew" to 40,
            "Mark" to 41,
            "Luke" to 42,
            "John" to 43,
            "Acts" to 44,
            "Romans" to 45,
            "1 Corinthians" to 46,
            "2 Corinthians" to 47,
            "Galatians" to 48,
            "Ephesians" to 49,
            "Philippians" to 50,
            "Colossians" to 51,
            "1 Thessalonians" to 52,
            "2 Thessalonians" to 53,
            "1 Timothy" to 54,
            "2 Timothy" to 55,
            "Titus" to 56,
            "Philemon" to 57,
            "Hebrews" to 58,
            "James" to 59,
            "1 Peter" to 60,
            "2 Peter" to 61,
            "1 John" to 62,
            "2 John" to 63,
            "3 John" to 64,
            "Jude" to 65,
            "Revelation" to 66,
        )

    @Test
    fun `the catalog order matches the hand-pinned 66-book numbering, field by field`() {
        assertThat(verified).hasSize(66)
        for (book in BookCatalog.books) {
            assertWithMessage(book.canonicalName)
                .that(book.order)
                .isEqualTo(verified.getValue(book.canonicalName))
        }
    }

    @Test
    fun `every pinned book exists in the catalog - no extras either way`() {
        assertThat(BookCatalog.books.map { it.canonicalName })
            .containsExactlyElementsIn(verified.keys)
    }
}
