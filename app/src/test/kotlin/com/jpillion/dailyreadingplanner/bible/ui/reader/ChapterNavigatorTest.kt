package com.jpillion.dailyreadingplanner.bible.ui.reader

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import org.junit.Test

/** VC-T5 — adjacent-chapter walk pins, incl. the book-boundary crossings and the two ends. */
class ChapterNavigatorTest {
    private val genesis = BookCatalog.requireByName("Genesis")
    private val exodus = BookCatalog.requireByName("Exodus")
    private val revelation = BookCatalog.requireByName("Revelation")

    @Test
    fun `next within a book increments the chapter`() {
        assertThat(ChapterNavigator.next(genesis, 1)).isEqualTo(genesis to 2)
    }

    @Test
    fun `next at the last chapter crosses into the next book at chapter 1`() {
        assertThat(ChapterNavigator.next(genesis, 50)).isEqualTo(exodus to 1)
    }

    @Test
    fun `previous within a book decrements the chapter`() {
        assertThat(ChapterNavigator.previous(genesis, 2)).isEqualTo(genesis to 1)
    }

    @Test
    fun `previous at chapter 1 crosses into the previous book's last chapter`() {
        assertThat(ChapterNavigator.previous(exodus, 1)).isEqualTo(genesis to 50)
    }

    @Test
    fun `no previous before Genesis 1`() {
        assertThat(ChapterNavigator.previous(genesis, 1)).isNull()
    }

    @Test
    fun `no next after Revelation 22`() {
        assertThat(ChapterNavigator.next(revelation, 22)).isNull()
    }
}
