package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.OpenVerseUseCase
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestination
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * H2 (D-H-2) — drives the chapter-swipe reader over the Sprint B spine use cases. The reader is a
 * [androidx.compose.foundation.pager.HorizontalPager] of [GlobalChapterIndex.TOTAL_CHAPTERS] pages,
 * page == global chapter index; this VM provides the per-page [ReaderUiState] via [uiStateForPage]
 * (per-index cache, loaded lazily on first request — mirrors DayReadingsViewModel.uiStateFor).
 *
 * In-session last-read (D-V3-13): the last opened global chapter index is held in [SavedStateHandle],
 * driving the pager's initial page across config change / process death within a session.
 *
 * Portion handoff (D-H-7): a Schedule reading tap that opens IN_APP lands the pager on the portion's
 * FIRST chapter ([pendingInitialPage]); the user then swipes through the (contiguous) portion. The
 * reader is a single-chapter-per-page surface — the prior multi-block render is superseded by swipe.
 */
@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        private val getChapter: GetChapterUseCase,
        private val openVerse: OpenVerseUseCase,
        private val savedStateHandle: SavedStateHandle,
        private val readerHandoff: ReaderHandoff,
    ) : ViewModel() {
        private val pageStates = mutableMapOf<Int, MutableStateFlow<ReaderUiState>>()

        /** The pager page the reader should open on this composition (last-read, or a pending handoff). */
        private val _initialPage = MutableStateFlow(restoredInitialPage())
        val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

        init {
            // VD-T5 (D-D-1 / D-H-7): a Schedule reading tap hands off a portion to read in-app — open
            // the pager on the portion's first chapter. consume() is single-shot.
            viewModelScope.launch {
                readerHandoff.pending.filterNotNull().collect { portion ->
                    readerHandoff.consume()
                    portionFirstIndex(portion)?.let { _initialPage.value = it }
                }
            }
        }

        /**
         * The [StateFlow] for one pager page (global chapter index). The first request triggers the
         * chapter load; later swipes back to the page reuse the cached flow. Records the page as the
         * in-session last-read so a config change restores the same page.
         */
        fun uiStateForPage(page: Int): StateFlow<ReaderUiState> =
            pageStates.getOrPut(page) {
                MutableStateFlow<ReaderUiState>(ReaderUiState.Loading).also { flow ->
                    savedStateHandle[KEY_PAGE] = page
                    loadPage(page, flow)
                }
            }

        private fun loadPage(
            page: Int,
            flow: MutableStateFlow<ReaderUiState>,
        ) {
            val (book, chapter) = GlobalChapterIndex.chapterAt(page)
            flow.value = ReaderUiState.Loading
            viewModelScope.launch {
                flow.value =
                    try {
                        val content = getChapter(book, chapter)
                        ReaderUiState.Content(
                            blocks = listOf(content),
                            title = "${book.canonicalName} $chapter",
                        )
                    } catch (e: Exception) {
                        ReaderUiState.Error()
                    }
            }
        }

        private val openDestinationChannel = Channel<ReadingDestination>(Channel.BUFFERED)

        /** One-shot resolved verse-tap destinations (BACKLOG #5, D-H-4); collect exactly once from the UI. */
        val openDestinationEvents: Flow<ReadingDestination> = openDestinationChannel.receiveAsFlow()

        /**
         * H7 — a verse was tapped: open it in the user's external Bible app at the exact
         * (book, chapter, verse) decoded from [verseId] (D-H-3, canonical coords, NOT the display
         * label). IN_APP falls back to BLB (D-H-4). The launch side-effect runs in the UI.
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

        /** Retry a failed page load. */
        fun retry(page: Int) {
            val flow = pageStates[page] ?: return
            loadPage(page, flow)
        }

        /** Records the displayed page as the in-session last-read (called as the pager settles). */
        fun onPageSettled(page: Int) {
            savedStateHandle[KEY_PAGE] = page
        }

        private fun restoredInitialPage(): Int {
            val page = savedStateHandle.get<Int>(KEY_PAGE)
            if (page != null && page in 0 until GlobalChapterIndex.TOTAL_CHAPTERS) return page
            return GENESIS_1_PAGE
        }

        private fun portionFirstIndex(portion: Portion): Int? {
            val ref = portion.firstRef
            val book: Book = ref.book
            return runCatching { GlobalChapterIndex.indexOf(book, ref.chapter) }.getOrNull()
        }

        private companion object {
            const val KEY_PAGE = "reader_page"
            val GENESIS_1_PAGE = GlobalChapterIndex.indexOf(BookCatalog.requireByName("Genesis"), 1)
        }
    }
