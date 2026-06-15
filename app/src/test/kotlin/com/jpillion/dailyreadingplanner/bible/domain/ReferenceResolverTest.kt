package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferenceResolverTest {
    private val resolver = ReferenceResolver()

    private fun range(
        book: Int,
        ch: Int,
        v: Int,
    ) = VerseRange(VerseId.encode(book, ch, v), VerseId.encode(book, ch, v))

    // ---- valid forms ↦ exact ranges ----

    @Test
    fun `single verse`() {
        assertEquals(range(43, 3, 16), resolver.resolve("John 3:16"))
    }

    @Test
    fun `same-chapter verse range`() {
        assertEquals(
            VerseRange(VerseId.encode(43, 3, 16), VerseId.encode(43, 3, 18)),
            resolver.resolve("John 3:16-18"),
        )
    }

    @Test
    fun `whole chapter includes verse 0`() {
        assertEquals(VerseId.chapterRange(43, 3), resolver.resolve("John 3"))
    }

    @Test
    fun `whole book`() {
        assertEquals(VerseId.bookRange(43), resolver.resolve("John"))
    }

    @Test
    fun `verse 0 superscription resolves`() {
        assertEquals(range(19, 23, 0), resolver.resolve("Psalms 23:0"))
    }

    @Test
    fun `osis dotted form`() {
        assertEquals(range(43, 3, 16), resolver.resolve("John.3.16"))
        assertEquals(VerseId.chapterRange(43, 3), resolver.resolve("John.3"))
    }

    // ---- book-name ambiguity: "John" vs "1/2/3 John" ----

    @Test
    fun `bare John is the Gospel never a numbered John`() {
        val r = resolver.resolve("John 3")!!
        assertEquals(43, VerseId.book(r.startVerseId)) // Gospel, order 43
    }

    @Test
    fun `numbered Johns resolve to their own books`() {
        assertEquals(62, VerseId.book(resolver.resolve("1 John 2")!!.startVerseId))
        assertEquals(63, VerseId.book(resolver.resolve("2 John 1")!!.startVerseId))
        assertEquals(64, VerseId.book(resolver.resolve("3 John 1")!!.startVerseId))
    }

    @Test
    fun `numbered-book aliases`() {
        assertEquals(62, VerseId.book(resolver.resolve("1john 2")!!.startVerseId))
        assertEquals(62, VerseId.book(resolver.resolve("i john 2")!!.startVerseId))
    }

    @Test
    fun `psalm and psalms aliases`() {
        assertEquals(19, VerseId.book(resolver.resolve("Psalm 23")!!.startVerseId))
        assertEquals(19, VerseId.book(resolver.resolve("Ps 23")!!.startVerseId))
        assertEquals(19, VerseId.book(resolver.resolve("Psalms 23")!!.startVerseId))
    }

    @Test
    fun `all 66 canonical names resolve as whole books`() {
        for (b in BookCatalog.books) {
            val r = resolver.resolve(b.canonicalName)
            assertEquals("failed for ${b.canonicalName}", b.order, VerseId.book(r!!.startVerseId))
        }
    }

    // ---- malformed ↦ null, NEVER plausible-but-wrong ----

    @Test
    fun `unknown book is null`() {
        assertNull(resolver.resolve("Hezekiah 3"))
        assertNull(resolver.resolve("Maccabees 1"))
    }

    @Test
    fun `out-of-range chapter is null`() {
        assertNull(resolver.resolve("John 22")) // John has 21
        assertNull(resolver.resolve("John 0"))
    }

    @Test
    fun `reversed verse range is null`() {
        assertNull(resolver.resolve("John 3:18-16"))
    }

    @Test
    fun `cross-chapter range is null in V3_0`() {
        assertNull(resolver.resolve("John 3:16-4:2"))
    }

    @Test
    fun `garbage is null`() {
        assertNull(resolver.resolve(""))
        assertNull(resolver.resolve("   "))
        assertNull(resolver.resolve("xyzzy"))
        assertNull(resolver.resolve("John three"))
        assertNull(resolver.resolve("123"))
        assertNull(resolver.resolve("John 3:abc"))
    }

    @Test
    fun `verse above 999 is null`() {
        assertNull(resolver.resolve("John 3:1000"))
    }

    // ---- resolveChapter / format ----

    @Test
    fun `resolveChapter whole chapter`() {
        assertEquals(
            VerseId.chapterRange(43, 3),
            resolver.resolveChapter(BookCatalog.requireByName("John"), 3),
        )
    }

    @Test
    fun `format round-trips common forms`() {
        assertEquals("John 3:16", resolver.format(range(43, 3, 16)))
        assertEquals("John 3", resolver.format(VerseId.chapterRange(43, 3)))
        assertEquals(
            "John 3:16-18",
            resolver.format(VerseRange(VerseId.encode(43, 3, 16), VerseId.encode(43, 3, 18))),
        )
    }
}
