package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GetChapterUseCaseTest {
    private val source = FakeBibleTextSource()
    private val settings = FakeSettingsRepository()
    private val useCase = GetChapterUseCase(bundledResolver(source), settings)

    @Test
    fun `returns chapter content with verses in order`() =
        runTest {
            val content = useCase(BookCatalog.requireByName("John"), 3)
            assertEquals(43, content.bookNo)
            assertEquals("John", content.bookName)
            assertEquals(3, content.chapter)
            // The fake models a full chapter; the use case returns the verses in canonical order.
            assertEquals(listOf("1", "2", "3"), content.verses.take(3).map { it.nativeLabel })
            assertEquals(content.verses.map { it.canonicalId }.sorted(), content.verses.map { it.canonicalId })
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
