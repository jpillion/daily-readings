package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I1 (D-I-1) pins the portion-anchored pager index field-by-field: the span-collapse, both flanks,
 * the bounds, and the two-book portion. The flanking pages are pinned against [GlobalChapterIndex]
 * so the Reading context can never disagree with Browse about which chapter sits where; the
 * span-collapse + shift is the load-bearing logic (mutation-pinned).
 */
class ReadingPagerIndexTest {
    private val genesis = BookCatalog.requireByName("Genesis")
    private val james = BookCatalog.requireByName("James")
    private val firstPeter = BookCatalog.requireByName("1 Peter")
    private val revelation = BookCatalog.requireByName("Revelation")
    private val twoJohn = BookCatalog.requireByName("2 John")
    private val threeJohn = BookCatalog.requireByName("3 John")

    private fun portion(vararg refs: Reference) = Portion(Stream.NEW_TESTAMENT, refs.toList())

    // The spec's canonical example is "James 4–5"; James has exactly 5 chapters, so 4–5 is the
    // END of the book (the chapter after the portion is 1 Peter 1 — pinned separately below). For
    // the same-book flank assertions we use James 1–2, whose successor (James 3) is in-book.
    private val james12 =
        ReadingPagerIndex(portion(Reference(james, 1), Reference(james, 2)))

    @Test
    fun `single-chapter portion is collapsedSpan 0 and behaves like one-chapter-per-page`() {
        val index = ReadingPagerIndex(portion(Reference(james, 1)))
        assertEquals(0, index.collapsedSpan)
        assertEquals(GlobalChapterIndex.TOTAL_CHAPTERS, index.pageCount)
        val jamesGlobal = GlobalChapterIndex.indexOf(james, 1)
        assertEquals(jamesGlobal, index.portionPage)
        assertTrue(index.isPortionPage(jamesGlobal))
        // Every other page maps 1:1 to its global chapter (it IS Browse with one page flagged).
        assertEquals(genesis to 1, index.chapterAt(0))
        assertEquals(revelation to 22, index.chapterAt(GlobalChapterIndex.TOTAL_CHAPTERS - 1))
    }

    @Test
    fun `multi-chapter portion collapses its span to one page and shrinks the page count`() {
        // James 1 and 2 are adjacent global chapters; the portion folds chapter 2 into the page.
        assertEquals(1, james12.collapsedSpan)
        assertEquals(GlobalChapterIndex.TOTAL_CHAPTERS - 1, james12.pageCount)
        val james1Global = GlobalChapterIndex.indexOf(james, 1)
        assertEquals(james1Global, james12.portionPage)
        assertTrue(james12.isPortionPage(james1Global))
        // James 2 is NOT a separate page — only the portion page covers it.
        assertFalse(james12.isPortionPage(james1Global + 1))
    }

    @Test
    fun `the page before the portion is the chapter before the portion's first chapter`() {
        // Hebrews 13 (James 1's predecessor in canon) is the page immediately left of the portion.
        val priorPage = james12.portionPage - 1
        val hebrews = BookCatalog.requireByName("Hebrews")
        assertEquals(hebrews to hebrews.chapterCount, james12.chapterAt(priorPage))
        assertEquals(
            GlobalChapterIndex.chapterAt(james12.portionPage - 1),
            james12.chapterAt(priorPage),
        )
    }

    @Test
    fun `the page after the portion is the chapter AFTER the portion's last chapter, not within it`() {
        // Swipe right from the portion (James 1+2) -> James 3 (last+1), shifted over the collapsed run.
        val pageAfter = james12.portionPage + 1
        assertEquals(james to 3, james12.chapterAt(pageAfter))
        // And the next one is James 4 (NOT James 2 again — the collapsed chapter never reappears).
        assertEquals(james to 4, james12.chapterAt(pageAfter + 1))
    }

    @Test
    fun `a portion at the end of a book flanks into the next book (James 4-5 to 1 Peter 1)`() {
        // James has exactly 5 chapters, so the spec's "James 4–5" portion ends the book: the page
        // right of the portion is 1 Peter 1 (the canon successor), proving the flank crosses books.
        val james45 = ReadingPagerIndex(portion(Reference(james, 4), Reference(james, 5)))
        assertEquals(firstPeter to 1, james45.chapterAt(james45.portionPage + 1))
    }

    @Test
    fun `every non-portion page agrees with GlobalChapterIndex after accounting for the collapse`() {
        for (page in 0 until james12.pageCount) {
            if (james12.isPortionPage(page)) continue
            val expectedGlobal = if (page < james12.portionPage) page else page + james12.collapsedSpan
            assertEquals(GlobalChapterIndex.chapterAt(expectedGlobal), james12.chapterAt(page))
        }
    }

    @Test
    fun `bounds hold - first page is Genesis 1 and last page is Revelation 22`() {
        assertEquals(genesis to 1, james12.chapterAt(0))
        assertEquals(revelation to 22, james12.chapterAt(james12.pageCount - 1))
    }

    @Test
    fun `a portion at the very start keeps Genesis 1 as the portion page`() {
        val gen12 = ReadingPagerIndex(portion(Reference(genesis, 1), Reference(genesis, 2)))
        assertEquals(0, gen12.portionPage)
        assertTrue(gen12.isPortionPage(0))
        // Page 1 is the chapter after the portion's last (Genesis 3), not Genesis 2.
        assertEquals(genesis to 3, gen12.chapterAt(1))
    }

    @Test
    fun `a portion at the very end keeps the portion page as the last page`() {
        val rev2122 = ReadingPagerIndex(portion(Reference(revelation, 21), Reference(revelation, 22)))
        assertEquals(rev2122.pageCount - 1, rev2122.portionPage)
        assertTrue(rev2122.isPortionPage(rev2122.pageCount - 1))
        // Page before is Revelation 20.
        assertEquals(revelation to 20, rev2122.chapterAt(rev2122.pageCount - 2))
    }

    @Test
    fun `the two-book Jun19 Dec19 portion is one contiguous page (2 John 1 then 3 John 1)`() {
        val twoBook = ReadingPagerIndex(portion(Reference(twoJohn, 1), Reference(threeJohn, 1)))
        // 2 John 1 and 3 John 1 are globally adjacent chapters -> collapsedSpan 1, one page.
        assertEquals(1, twoBook.collapsedSpan)
        val twoJohnGlobal = GlobalChapterIndex.indexOf(twoJohn, 1)
        assertEquals(twoJohnGlobal, twoBook.portionPage)
        assertTrue(twoBook.isPortionPage(twoJohnGlobal))
        // The page after the two-book portion is Jude 1 (3 John's successor in canon), never 3 John alone.
        val jude = BookCatalog.requireByName("Jude")
        assertEquals(jude to 1, twoBook.chapterAt(twoBook.portionPage + 1))
    }

    @Test
    fun `chapterAt rejects the portion page and out-of-range pages`() {
        assertThrows(IllegalArgumentException::class.java) { james12.chapterAt(james12.portionPage) }
        assertThrows(IllegalArgumentException::class.java) { james12.chapterAt(-1) }
        assertThrows(IllegalArgumentException::class.java) { james12.chapterAt(james12.pageCount) }
    }

    @Test
    fun `a three-chapter portion collapses all three into one page`() {
        // Find a real 3-chapter span: Genesis 1-2-3.
        val gen123 =
            ReadingPagerIndex(
                portion(Reference(genesis, 1), Reference(genesis, 2), Reference(genesis, 3)),
            )
        assertEquals(2, gen123.collapsedSpan)
        assertEquals(GlobalChapterIndex.TOTAL_CHAPTERS - 2, gen123.pageCount)
        // Page 1 (right of the portion) is Genesis 4.
        assertEquals(genesis to 4, gen123.chapterAt(1))
    }
}
