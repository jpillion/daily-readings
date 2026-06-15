package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.FakeBibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.bible.domain.PortionVerseBridge
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * VC-T1 — ReaderViewModel pins. Builds REAL Sprint-B use cases over FakeBibleTextSource (the
 * synthetic verses prove ordering/title passthrough; the real asset is the Sprint-A gate's job).
 */
class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val source = FakeBibleTextSource()
    private val getChapter = GetChapterUseCase(source)
    private val getPortionText = GetPortionTextUseCase(PortionVerseBridge(), source)

    private val handoff = ReaderHandoff()

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        ReaderViewModel(getChapter, getPortionText, handle, handoff)

    @Test
    fun `openChapter emits content for the requested chapter`() =
        runTest {
            val model = vm()
            model.openChapter(BookCatalog.requireByName("Genesis"), 1)
            val state = model.uiState.value as ReaderUiState.Content
            assertThat(state.blocks).hasSize(1)
            assertThat(state.blocks.single().bookName).isEqualTo("Genesis")
            assertThat(state.blocks.single().chapter).isEqualTo(1)
            assertThat(state.blocks.single().verses).isNotEmpty()
            assertThat(state.title).isEqualTo("Genesis 1")
            assertThat(state.activeVerseId).isNull()
        }

    @Test
    fun `default first open lands on Genesis 1`() =
        runTest {
            val state = vm().uiState.value as ReaderUiState.Content
            assertThat(state.blocks.single().bookName).isEqualTo("Genesis")
            assertThat(state.blocks.single().chapter).isEqualTo(1)
        }

    @Test
    fun `in-session restore reopens the saved chapter and keeps the superscription`() =
        runTest {
            val handle = SavedStateHandle(mapOf("reader_book_no" to 19, "reader_chapter" to 23))
            val state = vm(handle).uiState.value as ReaderUiState.Content
            assertThat(state.blocks.single().bookName).isEqualTo("Psalms")
            assertThat(state.blocks.single().chapter).isEqualTo(23)
            assertThat(
                state.blocks
                    .single()
                    .verses
                    .first()
                    .isTitle,
            ).isTrue()
        }

    @Test
    fun `openPortion renders the two-book portion in order`() =
        runTest {
            val portion =
                Portion(
                    Stream.NEW_TESTAMENT,
                    listOf(
                        Reference(BookCatalog.requireByName("2 John"), 1),
                        Reference(BookCatalog.requireByName("3 John"), 1),
                    ),
                )
            val model = vm()
            model.openPortion(portion)
            val state = model.uiState.value as ReaderUiState.Content
            assertThat(state.blocks.map { it.bookName }).containsExactly("2 John", "3 John").inOrder()
        }

    @Test
    fun `out-of-range chapter degrades to Error without crashing`() =
        runTest {
            val model = vm()
            model.openChapter(BookCatalog.requireByName("John"), 99)
            assertThat(model.uiState.value).isInstanceOf(ReaderUiState.Error::class.java)
        }
}
