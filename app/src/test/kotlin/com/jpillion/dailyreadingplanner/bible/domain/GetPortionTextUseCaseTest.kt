package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.ReferenceVerses
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPortionTextUseCaseTest {
    private val source = FakeBibleTextSource()
    private val useCase = GetPortionTextUseCase(PortionVerseBridge(), source)

    private fun ref(
        name: String,
        ch: Int,
    ) = Reference(BookCatalog.requireByName(name), ch)

    @Test
    fun `multi-chapter portion renders one block per chapter in order`() =
        runTest {
            val portion = Portion(1, listOf(ref("Genesis", 1), ref("Genesis", 2)))
            val content = useCase(portion)
            assertEquals(2, content.blocks.size)
            assertEquals(1, content.blocks[0].chapter)
            assertEquals(2, content.blocks[1].chapter)
            assertEquals("Genesis", content.blocks[0].bookName)
        }

    @Test
    fun `two-book portion renders both books in order — M-V3-4`() =
        runTest {
            val portion = Portion(3, listOf(ref("2 John", 1), ref("3 John", 1)))
            val content = useCase(portion)
            assertEquals(2, content.blocks.size)
            assertEquals(63, content.blocks[0].bookNo)
            assertEquals("2 John", content.blocks[0].bookName)
            assertEquals(64, content.blocks[1].bookNo)
            assertEquals("3 John", content.blocks[1].bookName)
        }

    @Test
    fun `psalms portion preserves the verse-0 title in its block`() =
        runTest {
            val portion = Portion(2, listOf(ref("Psalms", 23)))
            val content = useCase(portion)
            assertEquals(
                true,
                content.blocks[0]
                    .verses
                    .first()
                    .isTitle,
            )
        }

    @Test
    fun `a verse-windowed portion renders ONLY the in-range verses — Psalm 119 day 1 = 1 to 40`() =
        runTest {
            val ref = Reference(BookCatalog.requireByName("Psalms"), 119, ReferenceVerses(1, 40))
            val portion = Portion(2, listOf(ref))
            val content = useCase(portion)
            val block = content.blocks.single()
            val verseNumbers = block.verses.map { it.nativeLabel }
            // exactly verses 1..40, in order; no verse 0 title (body window), no verse 41+
            assertEquals((1..40).map { it.toString() }, verseNumbers)
            assertEquals(false, block.verses.any { it.isTitle })
            assertEquals("40", verseNumbers.last())
        }

    @Test
    fun `verse-windowed days 2 to 4 render their own windows only`() =
        runTest {
            suspend fun verses(
                start: Int,
                end: Int,
            ) = useCase(
                Portion(
                    2,
                    listOf(Reference(BookCatalog.requireByName("Psalms"), 119, ReferenceVerses(start, end))),
                ),
            ).blocks.single().verses.map { it.nativeLabel.toInt() }

            assertEquals((41..80).toList(), verses(41, 80))
            assertEquals((81..128).toList(), verses(81, 128))
            assertEquals((129..176).toList(), verses(129, 176))
        }
}
