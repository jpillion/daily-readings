package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.bible.domain.model.ChapterContent
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.OpenVerseUseCase
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.ui.day.ReadingFormatter
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * I2 (D-I-1 / D-I-2) — drives the two-context reader.
 *
 * **Browse** (the Bible tab; the picker): one chapter per page over [GlobalChapterIndex.TOTAL_CHAPTERS]
 * pages, page == global chapter index — the Sprint-H single-chapter swipe, unchanged.
 *
 * **Reading** (a Schedule reading tap, IN_APP): a portion-anchored pager over a [ReadingPagerIndex],
 * where the whole multi-chapter portion collapses to ONE atomic page rendered via
 * [GetPortionTextUseCase] (revives the multi-block render for this context only — D-I-3). Swiping out
 * to the next single chapter and back returns to the SAME combined portion page (the consistency
 * guarantee falls out of the portion being one fixed page index in [ReadingPagerIndex]).
 *
 * The two contexts are SEPARATE pager configurations ([context]); they never share a page-index
 * space, so the same chapters are "one combined page" in Reading and "individual pages" in Browse
 * without contradiction. The route rebuilds the pager (page count + initial page) whenever [context]
 * changes; the per-page state cache is cleared on every context switch (the page→content mapping is
 * context-specific).
 *
 * In-session last-read (D-V3-13, D-I-4): the last-shown *single* global chapter is held in
 * [SavedStateHandle], so a Browse session restores its chapter and re-entering Browse from a Reading
 * session lands on a sensible chapter. The collapsed portion page never overwrites it.
 *
 * **Tab-reset (OQ-A, D-I-2):** tapping the Bible tab — or jumping via the picker — forces Browse at
 * the last-read chapter ([resetToBrowse]); only a Schedule reading tap enters Reading.
 */
@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        private val getChapter: GetChapterUseCase,
        private val getPortionText: GetPortionTextUseCase,
        private val openVerse: OpenVerseUseCase,
        private val savedStateHandle: SavedStateHandle,
        private val readerHandoff: ReaderHandoff,
    ) : ViewModel() {
        private var pageStates = mutableMapOf<Int, MutableStateFlow<ReaderUiState>>()

        /** The active reader context (Browse or Reading); the route reads this to build the pager. */
        private val _context = MutableStateFlow<ReaderContext>(ReaderContext.Browse)
        val context: StateFlow<ReaderContext> = _context.asStateFlow()

        /** The pager page the reader should open on for the CURRENT [context]. */
        private val _initialPage = MutableStateFlow(restoredBrowsePage())
        val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

        init {
            // VD-T5 (D-D-1) / I2 (D-I-1): a Schedule reading tap hands off a portion to read in-app —
            // enter the Reading context anchored on that portion and open on the combined portion
            // page. consume() is single-shot, so a config change or a manual Bible-tab visit does not
            // re-enter Reading.
            viewModelScope.launch {
                readerHandoff.pending.filterNotNull().collect { portion ->
                    readerHandoff.consume()
                    enterReading(portion)
                }
            }
            // I2 (D-I-2, OQ-A): the nav bar tapped the Bible tab — reset to single-chapter Browse.
            viewModelScope.launch {
                readerHandoff.browseRequested.filter { it }.collect {
                    readerHandoff.consumeBrowseRequest()
                    resetToBrowse()
                }
            }
        }

        /**
         * The [StateFlow] for one pager page in the CURRENT context. The first request triggers the
         * load; later swipes back to the page reuse the cached flow. Records a single-chapter page as
         * the in-session last-read (the collapsed portion page does not — D-I-4).
         */
        fun uiStateForPage(page: Int): StateFlow<ReaderUiState> =
            pageStates.getOrPut(page) {
                MutableStateFlow<ReaderUiState>(ReaderUiState.Loading).also { flow ->
                    recordLastReadFor(page)
                    loadPage(page, flow)
                }
            }

        private fun loadPage(
            page: Int,
            flow: MutableStateFlow<ReaderUiState>,
        ) {
            flow.value = ReaderUiState.Loading
            val ctx = _context.value
            viewModelScope.launch {
                flow.value =
                    try {
                        if (ctx is ReaderContext.Reading && ctx.index.isPortionPage(page)) {
                            // The atomic portion page: render the WHOLE portion as ordered blocks.
                            val portionContent = getPortionText(ctx.portion)
                            ReaderUiState.Content(
                                blocks = portionContent.blocks,
                                title = portionTitle(portionContent.blocks),
                            )
                        } else {
                            val (book, chapter) = chapterForPage(ctx, page)
                            val content = getChapter(book, chapter)
                            ReaderUiState.Content(
                                blocks = listOf(content),
                                title = "${ReadingFormatter.singularizeBookName(
                                    book.canonicalName,
                                    singleChapter = true,
                                )} $chapter",
                            )
                        }
                    } catch (e: Exception) {
                        ReaderUiState.Error()
                    }
            }
        }

        private fun chapterForPage(
            ctx: ReaderContext,
            page: Int,
        ): Pair<Book, Int> =
            when (ctx) {
                ReaderContext.Browse -> GlobalChapterIndex.chapterAt(page)
                is ReaderContext.Reading -> ctx.index.chapterAt(page)
            }

        private val openDestinationChannel = Channel<ReadingDestination>(Channel.BUFFERED)

        /** One-shot resolved verse-tap destinations (BACKLOG #5, D-H-4); collect exactly once from the UI. */
        val openDestinationEvents: Flow<ReadingDestination> = openDestinationChannel.receiveAsFlow()

        /**
         * H7 — a verse was tapped: open it in the user's external Bible app at the exact
         * (book, chapter, verse) decoded from [verseId] (D-H-3, canonical coords, NOT the display
         * label). Works UNCHANGED inside the combined portion page — each verse keeps its canonical id.
         * IN_APP falls back to BLB (D-H-4). The launch side-effect runs in the UI.
         */
        fun onVerseTapped(verseId: Long) {
            val bookNo = VerseId.book(verseId)
            val chapter = VerseId.chapter(verseId)
            val verse = VerseId.verse(verseId)
            val book = BookCatalog.books.firstOrNull { it.order == bookNo } ?: return
            viewModelScope.launch {
                openDestinationChannel.send(openVerse(Reference(book, chapter), verse))
            }
        }

        /** Retry a failed page load in the current context. */
        fun retry(page: Int) {
            val flow = pageStates[page] ?: return
            loadPage(page, flow)
        }

        /** Records the displayed page as the in-session last-read (called as the pager settles). */
        fun onPageSettled(page: Int) {
            recordLastReadFor(page)
        }

        /**
         * D-I-2 (OQ-A) — entering the Bible tab (or a picker jump) forces the Browse context at the
         * last-read single chapter. Idempotent: a no-op if already Browsing (so re-selecting the tab
         * doesn't reset an in-progress Browse). The picker-jump page is handled by the route after.
         */
        fun resetToBrowse() {
            if (_context.value is ReaderContext.Browse) return
            switchContext(ReaderContext.Browse, restoredBrowsePage())
        }

        private fun enterReading(portion: Portion) {
            val index = runCatching { ReadingPagerIndex(portion) }.getOrNull() ?: return
            switchContext(ReaderContext.Reading(portion, index), index.portionPage)
        }

        private fun switchContext(
            ctx: ReaderContext,
            initialPage: Int,
        ) {
            // The page→content mapping is context-specific; a stale cache would serve the wrong
            // chapter for a reused page index, so drop it on every switch.
            pageStates = mutableMapOf()
            _context.value = ctx
            _initialPage.value = initialPage
        }

        /** Records [page] as the in-session last-read GLOBAL chapter — only for single-chapter pages. */
        private fun recordLastReadFor(page: Int) {
            val global = globalChapterFor(_context.value, page) ?: return
            savedStateHandle[KEY_PAGE] = global
        }

        /** The underlying global chapter index for [page], or null if it is the collapsed portion page. */
        private fun globalChapterFor(
            ctx: ReaderContext,
            page: Int,
        ): Int? =
            when (ctx) {
                ReaderContext.Browse -> page.takeIf { it in 0 until GlobalChapterIndex.TOTAL_CHAPTERS }
                is ReaderContext.Reading ->
                    if (ctx.index.isPortionPage(page)) {
                        null
                    } else {
                        val (book, chapter) = runCatching { ctx.index.chapterAt(page) }.getOrNull() ?: return null
                        GlobalChapterIndex.indexOf(book, chapter)
                    }
            }

        private fun restoredBrowsePage(): Int {
            val page = savedStateHandle.get<Int>(KEY_PAGE)
            if (page != null && page in 0 until GlobalChapterIndex.TOTAL_CHAPTERS) return page
            return GENESIS_1_PAGE
        }

        private fun portionTitle(blocks: List<ChapterContent>): String {
            if (blocks.isEmpty()) return ""
            val first = blocks.first()
            val last = blocks.last()
            return if (first.bookNo == last.bookNo) {
                if (first.chapter == last.chapter) {
                    // One chapter (incl. a verse-windowed Psalm 119 day): singular -> "Psalm 119".
                    "${ReadingFormatter.singularizeBookName(first.bookName, singleChapter = true)} ${first.chapter}"
                } else {
                    // Spans chapters within one book: plural -> "Psalms 1–2" (en dash).
                    "${ReadingFormatter.singularizeBookName(
                        first.bookName,
                        singleChapter = false,
                    )} ${first.chapter}–${last.chapter}"
                }
            } else {
                // Two-book portion (Jun 19 / Dec 19): "2 John 1; 3 John 1". Each block is one chapter.
                blocks.joinToString("; ") {
                    "${ReadingFormatter.singularizeBookName(it.bookName, singleChapter = true)} ${it.chapter}"
                }
            }
        }

        private companion object {
            const val KEY_PAGE = "reader_page"
            val GENESIS_1_PAGE = GlobalChapterIndex.indexOf(BookCatalog.requireByName("Genesis"), 1)
        }
    }
