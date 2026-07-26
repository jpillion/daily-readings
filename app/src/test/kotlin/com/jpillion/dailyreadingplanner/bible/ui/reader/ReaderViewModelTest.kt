package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.FakeBibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetTranslationsUseCase
import com.jpillion.dailyreadingplanner.bible.domain.PortionVerseBridge
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.OpenVerseUseCase
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
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
    private val getTranslations = GetTranslationsUseCase(source)
    private val settings = FakeSettingsRepository()
    private val openVerse = OpenVerseUseCase(settings, ProviderUrlBuilder())
    private val handoff = ReaderHandoff()

    private val genesis = BookCatalog.requireByName("Genesis")
    private val james = BookCatalog.requireByName("James")
    private val firstPeter = BookCatalog.requireByName("1 Peter")
    private val psalms = BookCatalog.requireByName("Psalms")
    private val isaiah = BookCatalog.requireByName("Isaiah")
    private val genesis1Page = GlobalChapterIndex.indexOf(genesis, 1)
    private val psalms23Page = GlobalChapterIndex.indexOf(psalms, 23)

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        ReaderViewModel(getChapter, getPortionText, getTranslations, openVerse, handle, handoff, settings)

    private fun nt(vararg refs: Reference) = Portion(3, refs.toList())

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
                    2,
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
    fun `a non-contiguous portion opens the first ref's chapter in Browse, never Genesis 1 (D-SEG-7)`() =
        runTest {
            // THE Ticket-1 regression pin. Chronological 07/25 is ONE portion whose refs are NOT a
            // contiguous ascending global-chapter run: Psalms 76 precedes Isaiah 37 in canon order,
            // so ReadingPagerIndex's init throws. Before D-SEG-7 that throw was swallowed by a silent
            // `return`, the reader never left Browse, and it opened at its DEFAULT page — Genesis 1,
            // the wrong end of the Bible. It must now degrade to the portion's FIRST ref's chapter.
            val model = vm()
            handoff.request(
                Portion(
                    1,
                    listOf(
                        Reference(isaiah, 37),
                        Reference(isaiah, 38),
                        Reference(isaiah, 39),
                        Reference(psalms, 76),
                    ),
                ),
            )
            advanceUntilIdle()

            assertThat(model.context.value).isEqualTo(ReaderContext.Browse)
            assertThat(model.initialPage.value).isEqualTo(GlobalChapterIndex.indexOf(isaiah, 37))
            // Explicitly: NOT the Genesis 1 page. This is the bug the owner saw on device.
            assertThat(model.initialPage.value).isNotEqualTo(genesis1Page)
            // And the page it opens on really renders Isaiah 37 as a single Browse chapter.
            val state = model.uiStateForPage(model.initialPage.value).value as ReaderUiState.Content
            assertThat(state.blocks.map { it.bookName to it.chapter }).containsExactly("Isaiah" to 37)
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
            settings.setExternalBibleApp(ExternalBibleApp.BLB)
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            val results = mutableListOf<ReadingDestination>()
            val job = launch { model.openDestinationEvents.collect { results += it } }
            // James 2:3 — a verse in the SECOND chapter of the combined page; the tap-out must use
            // its own canonical coords (chapter 2), not the portion's first chapter (chapter 1).
            // Q2 renamed onVerseTapped -> openVerseExternally (a tap opens the menu now; only its
            // "Open in <app>" item lands here); the resolution path is deliberately unchanged.
            model.openVerseExternally(VerseId.encode(james.order, 2, 3))
            advanceUntilIdle()
            assertThat(results).containsExactly(
                ReadingDestination.Web("https://www.blueletterbible.org/kjv/jas/2/3/"),
            )
            job.cancel()
        }

    @Test
    fun `externalApp reflects the stored setting and updates reactively (Sprint K footer hint)`() =
        runTest {
            // Seeds with the default, then mirrors a Settings change live (the footer hint is driven
            // by this flow). A collector keeps the WhileSubscribed stateIn hot.
            settings.setExternalBibleApp(ExternalBibleApp.BLB)
            val model = vm()
            val seen = mutableListOf<ExternalBibleApp>()
            val job = launch { model.externalApp.collect { seen += it } }
            advanceUntilIdle()
            assertThat(model.externalApp.value).isEqualTo(ExternalBibleApp.BLB)
            settings.setExternalBibleApp(ExternalBibleApp.MYSWORD)
            advanceUntilIdle()
            assertThat(model.externalApp.value).isEqualTo(ExternalBibleApp.MYSWORD)
            job.cancel()
        }

    // --- Q2: verse selection state (Ticket 1) and the copy pipeline (D-Q-4). ---

    @Test
    fun `long-press starts a selection on that page with exactly that verse`() =
        runTest {
            val model = vm()
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            assertThat(model.selection.value.isActive).isTrue()
            assertThat(model.selection.value.page).isEqualTo(genesis1Page)
            assertThat(model.selection.value.verseIds).containsExactly(VerseId.encode(1, 1, 1))
        }

    @Test
    fun `toggling adds a second verse and deselecting the last exits selection mode`() =
        runTest {
            val model = vm()
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            model.onVerseSelectionToggled(genesis1Page, VerseId.encode(1, 1, 2))
            assertThat(model.selection.value.count).isEqualTo(2)
            model.onVerseSelectionToggled(genesis1Page, VerseId.encode(1, 1, 2))
            assertThat(model.selection.value.count).isEqualTo(1)
            model.onVerseSelectionToggled(genesis1Page, VerseId.encode(1, 1, 1))
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }

    @Test
    fun `the X affordance and system back clear the selection`() =
        runTest {
            val model = vm()
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            model.onSelectionCleared()
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }

    @Test
    fun `settling on another page clears the selection (D-Q-3)`() =
        runTest {
            // Selection scope is the current page: swiping away must not leave an invisible
            // off-screen selection alive. (Mutation target: dropping the onPageChanged call.)
            val model = vm()
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            model.onPageSettled(genesis1Page) // same page: survives
            assertThat(model.selection.value.isActive).isTrue()
            model.onPageSettled(genesis1Page + 1) // a swipe: clears
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }

    @Test
    fun `a context switch resets the selection (a whole new page-index space)`() =
        runTest {
            val model = vm()
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            assertThat(model.context.value).isInstanceOf(ReaderContext.Reading::class.java)
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }

    @Test
    fun `copySelection emits the D-Q-1 clipboard string for the selected verses`() =
        runTest {
            val model = vm()
            advanceUntilIdle() // let the version list (the "KJV" citation code) load
            model.uiStateForPage(genesis1Page)
            advanceUntilIdle()
            val gen11 = VerseId.encode(1, 1, 1)
            val gen12 = VerseId.encode(1, 1, 2)
            val copied = mutableListOf<String>()
            val job = launch { model.copyEvents.collect { copied += it } }
            model.onVerseLongPressed(genesis1Page, gen11)
            model.onVerseSelectionToggled(genesis1Page, gen12)
            model.copySelection()
            advanceUntilIdle()
            // Text first, reference at the end, translation code from the seam (never hardcoded).
            assertThat(copied).containsExactly("verse 1 of 1:1 verse 2 of 1:1\n\n— Genesis 1:1–2 (KJV)")
            job.cancel()
        }

    @Test
    fun `copySelection exits selection mode (P-Q-1, owner-decided)`() =
        runTest {
            // P-Q-1: the owner chose the note-taking / mail idiom — Copy completes the task and
            // returns the user to reading. The sprint originally shipped the spec-literal reading
            // (Copy was not among the listed exits), which left the selection standing.
            val model = vm()
            advanceUntilIdle()
            model.uiStateForPage(genesis1Page)
            advanceUntilIdle()
            val job = launch { model.copyEvents.collect { } }
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            model.copySelection()
            advanceUntilIdle()
            assertThat(model.selection.value.isActive).isFalse()
            assertThat(model.selection.value.count).isEqualTo(0)
            job.cancel()
        }

    @Test
    fun `copySelection copies the full selection even though it then exits`() =
        runTest {
            // Order pin: the clipboard string is built from the selection captured BEFORE the
            // exit, so making Copy an exit cannot truncate or empty what lands on the clipboard.
            // Mutating copySelection to clear before emitting reddens exactly this test.
            val model = vm()
            advanceUntilIdle()
            model.uiStateForPage(genesis1Page)
            advanceUntilIdle()
            val copied = mutableListOf<String>()
            val job = launch { model.copyEvents.collect { copied += it } }
            model.onVerseLongPressed(genesis1Page, VerseId.encode(1, 1, 1))
            model.onVerseSelectionToggled(genesis1Page, VerseId.encode(1, 1, 2))
            model.copySelection()
            advanceUntilIdle()
            assertThat(copied).hasSize(1)
            assertThat(copied.single()).contains("Genesis 1:1–2")
            assertThat(model.selection.value.isActive).isFalse()
            job.cancel()
        }

    @Test
    fun `copyVerse produces exactly the same string as a one-verse selection`() =
        runTest {
            // A spec requirement: the menu's "Copy this verse" and a one-verse selection Copy must
            // never drift, so both route through the identical helper.
            val model = vm()
            advanceUntilIdle()
            model.uiStateForPage(genesis1Page)
            advanceUntilIdle()
            val gen11 = VerseId.encode(1, 1, 1)
            val copied = mutableListOf<String>()
            val job = launch { model.copyEvents.collect { copied += it } }

            model.copyVerse(genesis1Page, gen11)
            advanceUntilIdle()
            model.onVerseLongPressed(genesis1Page, gen11)
            model.copySelection()
            advanceUntilIdle()

            assertThat(copied).hasSize(2)
            assertThat(copied[0]).isEqualTo(copied[1])
            assertThat(copied[0]).isEqualTo("verse 1 of 1:1\n\n— Genesis 1:1 (KJV)")
            job.cancel()
        }

    @Test
    fun `copy emits nothing when there is no selection and nothing when the verses are not loaded`() =
        runTest {
            val model = vm()
            advanceUntilIdle()
            val copied = mutableListOf<String>()
            val job = launch { model.copyEvents.collect { copied += it } }
            // No selection at all.
            model.copySelection()
            // A verse on a page that was never loaded — nothing to format, so nothing is emitted
            // (an empty clipboard write would be worse than a no-op).
            model.copyVerse(genesis1Page, VerseId.encode(1, 1, 1))
            advanceUntilIdle()
            assertThat(copied).isEmpty()
            job.cancel()
        }

    @Test
    fun `a copy from the combined portion page spans its chapters (the reader's real selection scope)`() =
        runTest {
            // D-Q-3: a page is one chapter in Browse but a WHOLE portion in Reading, so a selection
            // inside a portion legitimately crosses chapters — the citation must group by chapter.
            val model = vm()
            handoff.request(nt(Reference(james, 1), Reference(james, 2)))
            advanceUntilIdle()
            val portionPage = (model.context.value as ReaderContext.Reading).index.portionPage
            model.uiStateForPage(portionPage)
            advanceUntilIdle()
            val copied = mutableListOf<String>()
            val job = launch { model.copyEvents.collect { copied += it } }
            model.onVerseLongPressed(portionPage, VerseId.encode(james.order, 1, 1))
            model.onVerseSelectionToggled(portionPage, VerseId.encode(james.order, 2, 3))
            model.copySelection()
            advanceUntilIdle()
            assertThat(copied.single()).endsWith("— James 1:1; 2:3 (KJV)")
            job.cancel()
        }

    @Test
    fun `versionState exposes the bundled versions from the seam (D-N-1)`() =
        runTest {
            // The fake seam returns the single KJV row; the VM surfaces it as the available versions
            // and the selected one, so the top-bar version control sources its label from the data.
            val model = vm()
            advanceUntilIdle()
            assertThat(
                model.versionState.value.available
                    .map { it.code },
            ).containsExactly("KJV")
            assertThat(
                model.versionState.value.selected
                    ?.code,
            ).isEqualTo("KJV")
            assertThat(
                model.versionState.value.selected
                    ?.name,
            ).isEqualTo("King James Version")
        }
}
