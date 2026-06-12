package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import org.junit.Test

/**
 * S13 (supersedes BlbUrlBuilderTest, keeping its all-66 BLB pinning): every provider's URL
 * shape and portion semantics (D-S13-3), pinned to the live-verified forms
 * (docs/data/provider-link-checks.md). Two-book portion = Jun 19 / Dec 19 (2 John + 3 John).
 */
class ProviderUrlBuilderTest {
    private val builder = ProviderUrlBuilder()

    private fun portion(vararg refs: Pair<String, Int>): Portion =
        Portion(
            stream = Stream.NEW_TESTAMENT,
            refs = refs.map { (book, chapter) -> Reference(BookCatalog.requireByName(book), chapter) },
        )

    // --- Blue Letter Bible (unchanged shipped behavior). ---

    @Test
    fun `blb builds the documented url shape for all 66 books, first and last chapter`() {
        for (book in BookCatalog.books) {
            assertWithMessage(book.canonicalName)
                .that(builder.build(BibleProvider.BLB, portion(book.canonicalName to 1)))
                .isEqualTo("https://www.blueletterbible.org/kjv/${book.blbAbbrev}/1/")
            assertWithMessage("${book.canonicalName} last chapter")
                .that(builder.build(BibleProvider.BLB, portion(book.canonicalName to book.chapterCount)))
                .isEqualTo("https://www.blueletterbible.org/kjv/${book.blbAbbrev}/${book.chapterCount}/")
        }
    }

    @Test
    fun `blb opens the first chapter of a multi-chapter portion`() {
        assertThat(builder.build(BibleProvider.BLB, portion("Genesis" to 1, "Genesis" to 2)))
            .isEqualTo("https://www.blueletterbible.org/kjv/gen/1/")
    }

    @Test
    fun `blb opens the first book of the two-book portion`() {
        assertThat(builder.build(BibleProvider.BLB, portion("2 John" to 1, "3 John" to 1)))
            .isEqualTo("https://www.blueletterbible.org/kjv/2jo/1/")
    }

    // --- YouVersion / Bible.com (single-chapter; USFM codes; version 1 = KJV). ---

    @Test
    fun `youversion builds the documented url shape for all 66 books, first and last chapter`() {
        for (book in BookCatalog.books) {
            assertWithMessage(book.canonicalName)
                .that(builder.build(BibleProvider.YOUVERSION, portion(book.canonicalName to 1)))
                .isEqualTo("https://www.bible.com/bible/1/${book.usfmCode}.1.KJV")
            assertWithMessage("${book.canonicalName} last chapter")
                .that(
                    builder.build(
                        BibleProvider.YOUVERSION,
                        portion(book.canonicalName to book.chapterCount),
                    ),
                ).isEqualTo("https://www.bible.com/bible/1/${book.usfmCode}.${book.chapterCount}.KJV")
        }
    }

    @Test
    fun `youversion uses the awkward usfm codes a derivation would get wrong`() {
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("Philippians" to 4)))
            .isEqualTo("https://www.bible.com/bible/1/PHP.4.KJV")
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("Philemon" to 1)))
            .isEqualTo("https://www.bible.com/bible/1/PHM.1.KJV")
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("Ezekiel" to 48)))
            .isEqualTo("https://www.bible.com/bible/1/EZK.48.KJV")
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("Psalms" to 119)))
            .isEqualTo("https://www.bible.com/bible/1/PSA.119.KJV")
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("Jude" to 1)))
            .isEqualTo("https://www.bible.com/bible/1/JUD.1.KJV")
    }

    @Test
    fun `youversion is single-chapter - first chapter of a range, first book of the two-book portion`() {
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("Genesis" to 1, "Genesis" to 2)))
            .isEqualTo("https://www.bible.com/bible/1/GEN.1.KJV")
        assertThat(builder.build(BibleProvider.YOUVERSION, portion("2 John" to 1, "3 John" to 1)))
            .isEqualTo("https://www.bible.com/bible/1/2JN.1.KJV")
    }

    // --- Bible Gateway (multi-ref capable: the whole portion rides one URL). ---

    @Test
    fun `bible gateway builds a single-chapter passage search`() {
        assertThat(builder.build(BibleProvider.BIBLE_GATEWAY, portion("Matthew" to 5)))
            .isEqualTo("https://www.biblegateway.com/passage/?search=Matthew+5&version=KJV")
    }

    @Test
    fun `bible gateway collapses a consecutive range into one segment`() {
        assertThat(builder.build(BibleProvider.BIBLE_GATEWAY, portion("Genesis" to 1, "Genesis" to 2)))
            .isEqualTo("https://www.biblegateway.com/passage/?search=Genesis+1-2&version=KJV")
    }

    @Test
    fun `bible gateway carries the whole two-book portion in one url`() {
        assertThat(builder.build(BibleProvider.BIBLE_GATEWAY, portion("2 John" to 1, "3 John" to 1)))
            .isEqualTo(
                "https://www.biblegateway.com/passage/?search=2+John+1%2C3+John+1&version=KJV",
            )
    }

    @Test
    fun `bible gateway url-encodes multi-word book names`() {
        assertThat(builder.build(BibleProvider.BIBLE_GATEWAY, portion("Song of Solomon" to 8)))
            .isEqualTo(
                "https://www.biblegateway.com/passage/?search=Song+of+Solomon+8&version=KJV",
            )
    }

    @Test
    fun `bible gateway range collapse includes the last chapter - no off-by-one`() {
        val threeChapters = portion("Psalms" to 120, "Psalms" to 121, "Psalms" to 122)
        assertThat(builder.build(BibleProvider.BIBLE_GATEWAY, threeChapters))
            .isEqualTo("https://www.biblegateway.com/passage/?search=Psalms+120-122&version=KJV")
    }

    // --- MySword (S15, D-S15-1: numeric vendor form; single-chapter). ---

    @Test
    fun `mysword builds the numeric vendor form for all 66 books, first and last chapter`() {
        for (book in BookCatalog.books) {
            assertWithMessage(book.canonicalName)
                .that(builder.build(BibleProvider.MYSWORD, portion(book.canonicalName to 1)))
                .isEqualTo("https://mysword.info/b?r=${book.order}.1")
            assertWithMessage("${book.canonicalName} last chapter")
                .that(builder.build(BibleProvider.MYSWORD, portion(book.canonicalName to book.chapterCount)))
                .isEqualTo("https://mysword.info/b?r=${book.order}.${book.chapterCount}")
        }
    }

    @Test
    fun `mysword is single-chapter - first chapter of a range, first book of the two-book portion`() {
        assertThat(builder.build(BibleProvider.MYSWORD, portion("Genesis" to 1, "Genesis" to 2)))
            .isEqualTo("https://mysword.info/b?r=1.1")
        assertThat(builder.build(BibleProvider.MYSWORD, portion("2 John" to 1, "3 John" to 1)))
            .isEqualTo("https://mysword.info/b?r=63.1")
    }

    @Test
    fun `bible gateway does not collapse non-consecutive chapters of one book`() {
        assertThat(builder.build(BibleProvider.BIBLE_GATEWAY, portion("Psalms" to 1, "Psalms" to 5)))
            .isEqualTo(
                "https://www.biblegateway.com/passage/?search=Psalms+1%2CPsalms+5&version=KJV",
            )
    }
}
