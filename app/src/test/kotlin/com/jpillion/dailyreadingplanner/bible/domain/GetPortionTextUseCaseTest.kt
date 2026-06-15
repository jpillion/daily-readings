package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
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
            val portion = Portion(Stream.LAW_AND_HISTORY, listOf(ref("Genesis", 1), ref("Genesis", 2)))
            val content = useCase(portion)
            assertEquals(2, content.blocks.size)
            assertEquals(1, content.blocks[0].chapter)
            assertEquals(2, content.blocks[1].chapter)
            assertEquals("Genesis", content.blocks[0].bookName)
        }

    @Test
    fun `two-book portion renders both books in order — M-V3-4`() =
        runTest {
            val portion = Portion(Stream.NEW_TESTAMENT, listOf(ref("2 John", 1), ref("3 John", 1)))
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
            val portion = Portion(Stream.PSALMS_AND_PROPHECY, listOf(ref("Psalms", 23)))
            val content = useCase(portion)
            assertEquals(
                true,
                content.blocks[0]
                    .verses
                    .first()
                    .isTitle,
            )
        }
}
