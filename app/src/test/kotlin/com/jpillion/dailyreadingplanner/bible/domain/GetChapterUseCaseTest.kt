package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GetChapterUseCaseTest {
    private val source = FakeBibleTextSource()
    private val useCase = GetChapterUseCase(source)

    @Test
    fun `returns chapter content with verses in order`() =
        runTest {
            val content = useCase(BookCatalog.requireByName("John"), 3)
            assertEquals(43, content.bookNo)
            assertEquals("John", content.bookName)
            assertEquals(3, content.chapter)
            assertEquals(3, content.verses.size)
            assertEquals(listOf("1", "2", "3"), content.verses.map { it.nativeLabel })
        }

    @Test
    fun `psalms chapter surfaces the verse-0 title first`() =
        runTest {
            val content = useCase(BookCatalog.requireByName("Psalms"), 23)
            assertTrue(content.verses.first().isTitle)
            assertEquals(
                0,
                com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
                    .verse(content.verses.first().canonicalId),
            )
        }

    @Test
    fun `out-of-range chapter throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(BookCatalog.requireByName("John"), 22) }
        }
    }
}
