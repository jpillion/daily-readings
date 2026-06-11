package com.jpillion.dailyreadingplanner.data.reference

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jpillion.dailyreadingplanner.domain.model.Reference
import org.junit.Test

/** Sprint 3 gate (execution plan §5.2): BlbUrlBuilder tested for all 66 books. */
class BlbUrlBuilderTest {
    private val builder = BlbUrlBuilder()

    @Test
    fun `builds the documented url shape`() {
        val genesis = BookCatalog.requireByName("Genesis")
        assertThat(builder.build(Reference(genesis, 1)))
            .isEqualTo("https://www.blueletterbible.org/kjv/gen/1/")
        val psalms = BookCatalog.requireByName("Psalms")
        assertThat(builder.build(Reference(psalms, 119)))
            .isEqualTo("https://www.blueletterbible.org/kjv/psa/119/")
    }

    @Test
    fun `builds a valid first and last chapter url for all 66 books`() {
        for (book in BookCatalog.books) {
            assertWithMessage(book.canonicalName)
                .that(builder.build(Reference(book, 1)))
                .isEqualTo("https://www.blueletterbible.org/kjv/${book.blbAbbrev}/1/")
            assertWithMessage("${book.canonicalName} last chapter")
                .that(builder.build(Reference(book, book.chapterCount)))
                .isEqualTo("https://www.blueletterbible.org/kjv/${book.blbAbbrev}/${book.chapterCount}/")
        }
    }

    @Test
    fun `multi-book portion refs build distinct urls`() {
        val secondJohn = Reference(BookCatalog.requireByName("2 John"), 1)
        val thirdJohn = Reference(BookCatalog.requireByName("3 John"), 1)
        assertThat(builder.build(secondJohn)).isEqualTo("https://www.blueletterbible.org/kjv/2jo/1/")
        assertThat(builder.build(thirdJohn)).isEqualTo("https://www.blueletterbible.org/kjv/3jo/1/")
    }

    @Test
    fun `a reference cannot be built with an out-of-range chapter`() {
        val philemon = BookCatalog.requireByName("Philemon")
        assertThat(runCatching { Reference(philemon, 2) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { Reference(philemon, 0) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
