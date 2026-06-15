package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.FakeBibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.bible.domain.PortionVerseBridge
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.OpenVerseUseCase
import com.jpillion.dailyreadingplanner.domain.model.BibleProvider
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.domain.model.Stream
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * I2 — ReaderViewModel pins for the two-context reader: Browse single-chapter pages, a Schedule
 * reading tap entering the Reading context on the combined portion page, the swipe-out-and-back
 * consistency (the portion is one fixed page), the tab-reset-to-Browse rule (D-I-2), the two-book
 * portion as one page, and the per-verse tap-out working unchanged inside the portion page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val source = FakeBibleTextSource()
    private val getChapter = GetChapterUseCase(source)
    private val getPortionText = GetPortionTextUseCase(PortionVerseBridge(), source)
    private val settings = FakeSettingsRepository()
    private val openVerse = OpenVerseUseCase(settings, ProviderUrlBuilder())
    private val handoff = ReaderHandoff()

    private val genesis = BookCatalog.requireByName("Genesis")
    private val james = BookCatalog.requireByName("James")
    private val firstPeter = BookCatalog.requireByName("1 Peter")
    private val psalms = BookCatalog.requireByName("Psalms")
    private val genesis1Page = GlobalChapterIndex.indexOf(genesis, 1)
    private val psalms23Page = GlobalChapterIndex.indexOf(psalms, 23)

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        ReaderViewModel(getChapter, getPortionText, openVerse, handle, handoff)

    private fun nt(vararg refs: Reference) = Portion(Stream.NEW_TESTAMENT, refs.toList())

    @Test
    fun `Browse - a page emits content for that global chapter index`() =
        runTest {
            val model = vm()
            val state = model.uiStateForPage(genesis1Page).value as ReaderUiState.Content
            assertThat(state.blocks).hasSize(1)
            assertThat(state.blocks.single().bookName).isEqualTo("Genesis")
            assertThat(state.blocks.single().chapter).isEqualTo(1)
            assertThat(state.title).isEqualTo("Genesis 1")
            assertThat(state.activeVerseId).isNull()
        }

    @Test
    fun `a single Psalms chapter title is the singular Psalm N (D-UI-2)`() =
        runTest {
            // Mutation anchor for the reader's singular branch: one Psalms chapter -> "Psalm 23".
            val model = vm()
            val state = model.uiStateForPage(psalms23Page).value as ReaderUiState.Content
            assertThat(state.title).isEqualTo("Psalm 23")
        }

    @Test
    fun `a multi-chapter Psalms portion title is the plural Psalms M to N (D-UI-2)`() =
        runTest {
            // A portion spanning two Psalms chapters stays plural -> "Psalms 1–2" (en dash),
            // mirroring ReadingFormatter's run rule.
            val model = vm()
            handoff.request(
                Portion(
                    Stream.PSALMS_AND_PROPHECY,
                    listOf(Reference(psalms, 1), Reference(psalms, 2)),
                ),
            )
            advanceUntilIdle()
            val ctx = model.context.value as ReaderContext.Reading
            val state = model.uiStateForPage(ctx.index.portionPage).value as ReaderUiState.Content
            assertThat(state.title).isEqualTo("Psalms 1–2")
        }

    @Test
    fun `the default context is Browse and the default page is Genesis 1`() =
        runTest {
            val model = vm()
            assertThat(model.context.value).isEqualTo(ReaderContext.Browse)
            assertThat(model.initialPage.value).isEqualTo(genesis1Page)
        }

    @Test
    fun `Browse in-session restore reopens the saved page`() =
        runTest {
            val handle = SavedStateHandle(mapOf("reader_page" to psalms23Page))
            val model = vm(handle)
            assertThat(model.initialPage.value).isEqualTo(psalms23Page)
            val state = model.uiStateForPage(psalms23Page).value as ReaderUiState.Content
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
    fun `a multi-chapter reading tap enters Reading on the combined portion page`() =
        runTest {
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            val ctx = model.context.value
            assertThat(ctx).isInstanceOf(ReaderContext.Reading::class.java)
            val readingCtx = ctx as ReaderContext.Reading
            // The pager opens on the portion page (James 1 = the portion's first global chapter).
            val portionPage = readingCtx.index.portionPage
            assertThat(model.initialPage.value).isEqualTo(portionPage)
            assertThat(GlobalChapterIndex.indexOf(james, 1)).isEqualTo(portionPage)
            // That page renders BOTH chapters as ordered blocks (the revived multi-block render).
            val state = model.uiStateForPage(portionPage).value as ReaderUiState.Content
            assertThat(state.blocks.map { it.bookName to it.chapter })
                .containsExactly("James" to 1, "James" to 2)
                .inOrder()
            assertThat(state.title).isEqualTo("James 1–2")
        }

    @Test
    fun `swiping out of the portion and back shows the SAME combined portion page (consistency)`() =
        runTest {
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            val index = (model.context.value as ReaderContext.Reading).index
            val portionPage = index.portionPage

            // Swipe right out of the portion -> the next single chapter (James 3), not James 2.
            val nextPage = portionPage + 1
            val outState = model.uiStateForPage(nextPage).value as ReaderUiState.Content
            assertThat(outState.blocks.map { it.bookName to it.chapter }).containsExactly("James" to 3)
            model.onPageSettled(nextPage)

            // Swipe back left -> the portion page is STILL the combined James 1 + 2 page.
            val backState = model.uiStateForPage(portionPage).value as ReaderUiState.Content
            assertThat(backState.blocks.map { it.bookName to it.chapter })
                .containsExactly("James" to 1, "James" to 2)
                .inOrder()
        }

    @Test
    fun `an end-of-book portion flanks into the next book (James 4-5 then 1 Peter 1)`() =
        runTest {
            // James has exactly 5 chapters, so the spec's "James 4–5" portion ends the book — the
            // page right of the portion is 1 Peter 1 (the canon successor), then 1 Peter 2.
            val model = vm()
            handoff.request(nt(Reference(james, 4), Reference(james, 5)))
            advanceUntilIdle()
            val index = (model.context.value as ReaderContext.Reading).index
            val after1 = model.uiStateForPage(index.portionPage + 1).value as ReaderUiState.Content
            assertThat(after1.blocks.single().let { it.bookName to it.chapter }).isEqualTo("1 Peter" to 1)
            val after2 = model.uiStateForPage(index.portionPage + 2).value as ReaderUiState.Content
            assertThat(after2.blocks.single().let { it.bookName to it.chapter }).isEqualTo("1 Peter" to 2)
        }

    @Test
    fun `the two-book portion (2 John + 3 John) renders as one combined page`() =
        runTest {
            val model = vm()
            handoff.request(
                nt(
                    Reference(BookCatalog.requireByName("2 John"), 1),
                    Reference(BookCatalog.requireByName("3 John"), 1),
                ),
            )
            advanceUntilIdle()
            val ctx = model.context.value as ReaderContext.Reading
            val state = model.uiStateForPage(ctx.index.portionPage).value as ReaderUiState.Content
            assertThat(state.blocks.map { it.bookName to it.chapter })
                .containsExactly("2 John" to 1, "3 John" to 1)
                .inOrder()
            assertThat(state.title).isEqualTo("2 John 1; 3 John 1")
        }

    @Test
    fun `tapping the Bible tab resets a Reading context to single-chapter Browse (D-I-2)`() =
        runTest {
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            assertThat(model.context.value).isInstanceOf(ReaderContext.Reading::class.java)

            // The nav bar tapped the Bible tab.
            handoff.requestBrowse()
            advanceUntilIdle()
            assertThat(model.context.value).isEqualTo(ReaderContext.Browse)
            // It lands on the last-read single chapter; James was never single-paged in Reading, so
            // it falls back to the restored Browse page (Genesis 1 with an empty handle).
            assertThat(model.initialPage.value).isEqualTo(genesis1Page)
            // Browse pages are single chapters again.
            val state = model.uiStateForPage(genesis1Page).value as ReaderUiState.Content
            assertThat(state.blocks).hasSize(1)
        }

    @Test
    fun `resetToBrowse restores the last single chapter read inside the Reading context`() =
        runTest {
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            val index = (model.context.value as ReaderContext.Reading).index
            // Read James 3 (a single-chapter flank page) inside the Reading context, settling it.
            model.uiStateForPage(index.portionPage + 1)
            model.onPageSettled(index.portionPage + 1)

            // Tab back to Browse: it restores James 3 as the global chapter, NOT the collapsed page.
            handoff.requestBrowse()
            advanceUntilIdle()
            assertThat(model.context.value).isEqualTo(ReaderContext.Browse)
            assertThat(model.initialPage.value).isEqualTo(GlobalChapterIndex.indexOf(james, 3))
        }

    @Test
    fun `a verse tap inside the portion page emits an external destination at canonical coords`() =
        runTest {
            settings.setBibleProvider(BibleProvider.BLB)
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            val results = mutableListOf<ReadingDestination>()
            val job = launch { model.openDestinationEvents.collect { results += it } }
            // James 2:3 — a verse in the SECOND chapter of the combined page; the tap-out must use
            // its own canonical coords (chapter 2), not the portion's first chapter (chapter 1).
            model.onVerseTapped(VerseId.encode(james.order, 2, 3))
            advanceUntilIdle()
            assertThat(results).containsExactly(
                ReadingDestination.Web("https://www.blueletterbible.org/kjv/jas/2/3/"),
            )
            job.cancel()
        }
}
